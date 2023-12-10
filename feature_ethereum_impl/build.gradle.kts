plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    id("kotlin-parcelize")
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "jp.co.soramitsu.feature_ethereum_impl"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 34
    }

    buildTypes {
        release {
            isMinifyEnabled = true
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
    implementation(project(":common"))
    implementation(project(":core_db"))
    implementation(project(":feature_account_api"))
    implementation(project(":feature_ethereum_api"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":sorasubstrate"))

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    implementation(libs.xcryptoDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.web3jDep)

    implementation(libs.roomDep)

    testImplementation(project(":test_shared"))
}
