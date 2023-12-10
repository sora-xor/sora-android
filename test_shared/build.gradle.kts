plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kapt)
    id("kotlin-parcelize")
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "jp.co.soramitsu.test_shared"
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
    api(libs.coroutineTestDep)
    api(libs.xsubstrateDep)

    api(libs.junitDep)
    api(libs.mockitoDep)
    api(libs.mockitoKotlinDep)
    api(libs.archCoreTestDep)
    api(libs.archFragmentTestDep)
    api(libs.truthDep)
    api(libs.mockkDep)

    api(libs.roomTestHelpersDep)
}
