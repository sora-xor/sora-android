// Google Drive backup of encrypted accounts. In-repo replacement of the
// jp.co.soramitsu:xbackup AAR, without Google Play services: the account and
// its OAuth token come from the Android framework's AccountManager and Drive
// is called over its REST API with OkHttp.
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.xbackup"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 37
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    flavorDimensions += listOf("default")

    productFlavors {
        create("develop") {
            dimension = "default"
        }

        create("soralution") {
            dimension = "default"
        }

        create("production") {
            dimension = "default"
        }
    }
}

dependencies {
    implementation(libs.coreKtxDep)
    implementation(libs.activityKtxDep)
    implementation(libs.datastoreDep)
    implementation(libs.coroutineAndroidDep)
    implementation(libs.kotlinxSerializationJsonDep)
    implementation(project(":xcrypto"))
    implementation(libs.okhttpDep)

    testImplementation(libs.junitDep)
    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.mockkDep)

    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
    androidTestImplementation(libs.uiAutomatorDep)
}
