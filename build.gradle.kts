import org.gradle.api.artifacts.dsl.LockMode

plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
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
    dependencyLocking {
        lockMode.set(LockMode.STRICT)
    }

    configurations.configureEach {
        val configurationName = name
        resolutionStrategy {
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
tasks.register<Exec>("verifyProductionRollout") {
    group = "verification"
    description = "Validates an explicitly requested production rollout candidate or advancement receipt"
    workingDir(rootDir)
    commandLine("node", "scripts/verify-production-rollout.mjs")
    environment(
        "PRODUCTION_ROLLOUT_TARGET_PERCENT",
        System.getenv("PRODUCTION_ROLLOUT_TARGET_PERCENT") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_AAB_PATH",
        System.getenv("PRODUCTION_CANDIDATE_AAB_PATH") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_SOURCE_REVISION",
        System.getenv("PRODUCTION_CANDIDATE_SOURCE_REVISION") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_REPOSITORY",
        System.getenv("PRODUCTION_CANDIDATE_REPOSITORY") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_RUN_ID",
        System.getenv("PRODUCTION_CANDIDATE_RUN_ID") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH",
        System.getenv("PRODUCTION_CANDIDATE_RUN_RECEIPT_PATH") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH",
        System.getenv("PRODUCTION_CANDIDATE_ARTIFACT_RECEIPT_PATH") ?: "",
    )
    environment(
        "PI_PRODUCTION_PROBE_RECEIPT",
        System.getenv("PI_PRODUCTION_PROBE_RECEIPT") ?: "",
    )
    environment(
        "PRODUCTION_CANDIDATE_PI_RECEIPT_PATH",
        System.getenv("PRODUCTION_CANDIDATE_PI_RECEIPT_PATH") ?: "",
    )
    environment(
        "PRODUCTION_ROLLOUT_EVIDENCE_PATH",
        System.getenv("PRODUCTION_ROLLOUT_EVIDENCE_PATH") ?: "",
    )
    environment(
        "PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS",
        System.getenv("PRODUCTION_ROLLOUT_EVALUATED_AT_EPOCH_SECONDS") ?: "",
    )
    environment(
        "PRODUCTION_QUALIFICATION_RECEIPT_PATH",
        System.getenv("PRODUCTION_QUALIFICATION_RECEIPT_PATH") ?: "",
    )
    listOf(1, 5, 25).forEach { cohortPercent ->
        val environmentName = "PRODUCTION_ROLLOUT_RECEIPT_${cohortPercent}_PATH"
        environment(environmentName, System.getenv(environmentName) ?: "")
    }
    environment(
        "PRODUCTION_ROLLOUT_TRUST_SHA256",
        System.getenv("PRODUCTION_ROLLOUT_TRUST_SHA256") ?: "",
    )
}
