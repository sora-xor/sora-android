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
    namespace = "jp.co.soramitsu.sora.substrate"
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
    implementation(project(":common_wallet"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":network"))

    implementation(libs.xsubstrateDep)
    implementation(libs.xcryptoDep)
    implementation(libs.xnetworkingDep)

    implementation(libs.gsonDep)
    implementation(libs.webSocketLibDep)
    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.timberDep)
    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    testImplementation(project(":test_shared"))
}
