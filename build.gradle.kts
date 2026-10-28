import org.gradle.api.artifacts.dsl.LockMode

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidTest) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.baselineProfile) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.googleServicesPlugin) apply false
    alias(libs.plugins.firebaseCrashlyticsPlugin) apply false
    alias(libs.plugins.firebaseAppDistributionPlugin) apply false
    alias(libs.plugins.triplet) apply false
    alias(libs.plugins.kover)
}

subprojects {
    dependencies {
        components {
            // These exact vendor builds publish unused test dependencies at runtime.
            // Source and bytecode evidence: docs/security/android-test-dependency-usage-20261024.json.
            withModule("jp.co.soramitsu:xsubstrate") {
                if (id.version == "1.2.7") {
                    allVariants {
                        withDependencies {
                            removeIf {
                                "${it.group}:${it.name}" in setOf(
                                    "org.mockito.kotlin:mockito-kotlin", "junit:junit", "io.mockk:mockk",
                                )
                            }
                        }
                    }
                }
            }
            withModule("jp.co.soramitsu:android-foundation") {
                if (id.version == "0.0.4") {
                    allVariants {
                        withDependencies {
                            // Its bundled testing API still references JUnit, Mockito and coroutine-test.
                            removeIf { it.group == "io.mockk" && it.name == "mockk" }
                        }
                    }
                }
            }
        }
    }

    dependencyLocking {
        lockMode.set(LockMode.STRICT)
    }

    configurations.configureEach {
        val configurationName = name
        resolutionStrategy {
            dependencySubstitution {
                // Source-built local security fork replaces EdDSA with maintained BC Ed25519.
                substitute(module("jp.co.soramitsu:xcrypto"))
                    .using(module("jp.co.soramitsu:xcrypto:1.2.8-sora.2"))
                    .because("Remove vulnerable EdDSA implementation from every vendor consumer")
                // The original coordinate is unmaintained. This continuation preserves the
                // net.jpountz API used by xcrypto and fixes native/buffer bounds vulnerabilities.
                substitute(module("org.lz4:lz4-java"))
                    .using(module("at.yawk.lz4:lz4-java:1.12.1"))
                    .because("Replace vulnerable unmaintained LZ4 with its patched continuation")
            }
            if (configurationName.contains("productionRelease", ignoreCase = true)) {
                activateDependencyLocking()
            } else {
                failOnDynamicVersions()
                failOnChangingVersions()
            }
        }
    }
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}

val ktlint by configurations.creating

dependencies {
    ktlint("com.pinterest.ktlint:ktlint-cli:1.0.1") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    // ktlint(project(":custom-ktlint-ruleset")) // in case of custom ruleset
}

tasks.register<JavaExec>("ktlintCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
    val reportFile = layout.buildDirectory.file("reports/checkstyle/ktlint.xml")
    val sourceFiles = fileTree(rootDir) {
        include("**/src/**/*.kt")
        include("**/*.gradle.kts")
        include("settings.gradle.kts")
        exclude("**/build/**")
        exclude(".gradle/**")
    }
    args(sourceFiles.files.map { it.relativeTo(rootDir).path })
    args("--reporter=checkstyle,output=${reportFile.get().asFile}")
}

tasks.register<JavaExec>("ktlintFormat") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Check Kotlin code style and format"
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    // see https://pinterest.github.io/ktlint/install/cli/#command-line-usage for more information
    args(
        "-F",
        "**/src/**/*.kt",
        "**.kts",
        "!**/build/**",
    )
}
