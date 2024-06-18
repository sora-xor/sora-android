plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kapt)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "jp.co.soramitsu.network"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 34
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    implementation(libs.kotlinxSerializationJsonDep)

    api(libs.xnetworkingDep)
    api(libs.xnetworkingSoraWalletDep) {
        exclude(group = "jp.co.soramitsu.xnetworking", module = "basic")
    }
    implementation(libs.ktorWebSocketDep)
}
