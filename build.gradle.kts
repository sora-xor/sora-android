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
    alias(libs.plugins.triplet) apply false
    alias(libs.plugins.kover)
}

subprojects {
    dependencies {
        components {
            // This exact vendor build publishes unused test dependencies at runtime.
            // Source and bytecode evidence: docs/security/android-test-dependency-usage-20261024.json.
            withModule("jp.co.soramitsu:android-foundation") {
                if (id.version == "0.0.4") {
                    allVariants {
                        withDependencies {
                            // Its bundled testing API still references JUnit, Mockito and coroutine-test.
                            removeIf { it.group == "io.mockk" && it.name == "mockk" }
                            // The only Material3 references are in the unused BottomSheetExt API.
                            // SORA uses its own Material2 sheets and theme throughout.
                            removeIf { it.group == "androidx.compose.material3" && it.name == "material3" }
                        }
                    }
                }
            }
        }
    }

    dependencyLocking {
        lockMode.set(LockMode.STRICT)
    }

    // Update-availability advisories report what was published upstream after the dependency
    // graph was pinned and verified, so with warningsAsErrors they fail lint on unchanged source.
    // Dependency upgrades are reviewed on their own; every other lint check stays an error.
    listOf("com.android.application", "com.android.library", "com.android.test").forEach { pluginId ->
        pluginManager.withPlugin(pluginId) {
            extensions.configure<com.android.build.api.dsl.CommonExtension>("android") {
                lint.disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
            }
        }
    }

    configurations.configureEach {
        val configurationName = name
        resolutionStrategy {
            dependencySubstitution {
                // Retain device previews with controls matching SORA's Material2 theme.
                // The pinned, reproducible patch changes only PreviewActivity and its dependency.
                substitute(module("androidx.compose.ui:ui-tooling"))
                    .using(module("androidx.compose.ui:ui-tooling-android:1.11.1-sora.1"))
                    .because("Keep preview parameter controls in the app's Material2 theme")
                substitute(module("androidx.compose.ui:ui-tooling-android"))
                    .using(module("androidx.compose.ui:ui-tooling-android:1.11.1-sora.1"))
                    .because("Keep preview parameter controls in the app's Material2 theme")
                // xcrypto is built from source in this repository (:xcrypto); vendored libraries
                // that name the jp.co.soramitsu:xcrypto coordinate get the in-repo module.
                substitute(module("jp.co.soramitsu:xcrypto"))
                    .using(project(":xcrypto"))
                    .because("xcrypto and its sr25519 JNI library are built from in-repo source")
                // The polkaj-scale 0.2.3 jar that xsubstrate depends on came from a fork whose source
                // was never published; :polkaj-scale is that SCALE codec built from in-repo source.
                substitute(module("io.emeraldpay.polkaj:polkaj-scale"))
                    .using(project(":polkaj-scale"))
                    .because("polkaj-scale is built from in-repo source")
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
