plugins {
    alias(libs.plugins.androidTest)
    alias(libs.plugins.baselineProfile)
}

android {
    namespace = "jp.co.soramitsu.sora.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // StartupTimeTrace uses platform async trace sections, which are available from API 29.
        minSdk = 29
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Generate and measure the production startup graph. The local benchmark build types use
        // the test key, so this remains isolated from release signing and distribution.
        missingDimensionStrategy("default", "production")
    }

    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidxTestExtJunitDep)
    implementation(libs.benchmarkMacroJunit4Dep)
    implementation(libs.uiAutomatorDep)
}
