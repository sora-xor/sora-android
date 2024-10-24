plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
}

val composeCompilerVersion: String by project

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.demeter"
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

    composeOptions {
        kotlinCompilerExtensionVersion = composeCompilerVersion
    }

    buildFeatures {
        viewBinding = true
        compose = true
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

    packaging {
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            )
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common_wallet"))
    implementation(project(":core_db"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":feature_account_api"))
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_polkaswap_api"))
    implementation(project(":sorasubstrate"))
    implementation(project(":network"))

    implementation(libs.timberDep)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    implementation(libs.coroutineDep)
    implementation(libs.coroutineAndroidDep)

    implementation(libs.xsubstrateDep)
    implementation(libs.soramitsu.android.foundation)

    implementation(libs.composeRuntimeDep)

    implementation(libs.roomDep)
    implementation(libs.roomKtxDep)

    testImplementation(project(":test_data"))
}
