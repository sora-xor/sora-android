// Top-level build file where you can add configuration options common to all sub-projects/modules.
@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kapt) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.googleServicesPlugin) apply false
    alias(libs.plugins.firebaseCrashlyticsPlugin) apply false
    alias(libs.plugins.firebaseAppDistributionPlugin) apply false
    alias(libs.plugins.triplet) apply false
    id("org.sonarqube") version "4.4.1.3373"
    id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
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
    args(
        "**/src/**/*.kt",
        "**.kts",
        "!**/build/**",
        "--reporter=checkstyle,output=${project.buildDir}/reports/checkstyle/ktlint.xml"
    )
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

sonarqube {
    properties {
        property("sonar.projectKey", "sora:sora-android")
        property("sonar.projectName", "sora-android")
        property("sonar.junit.reportPaths", "${project.projectDir}/build/test-results/test/")
        property("sonar.coverage.jacoco.xmlReportPaths", "report/coverage.xml")
        property("sonar.exclusions", "${project.projectDir}/**/*.txt")
    }
}
