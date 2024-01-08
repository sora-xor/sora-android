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
    jvmToolchain(11)
}

android {
    namespace = "jp.co.soramitsu.feature_sora_card_impl"
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
}

dependencies {
    implementation(project(":android-foundation"))
    implementation(project(":common"))
    implementation(project(":common_wallet"))
    implementation(project(":demeter"))
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_sora_card_api"))
    implementation(project(":feature_polkaswap_api"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":feature_main_api"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":sorasubstrate"))
    implementation(project(":core_di"))
    implementation(project(":network"))

    implementation(libs.kotlinxSerializationJsonDep)

    implementation(libs.appcompatDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.viewmodelKtxDep)
    implementation(libs.runtimeKtxDep)
    implementation(libs.fragmentKtxDep)

    implementation(libs.lifecycleProcessDep)
    kapt(libs.lifecycleKaptDep)

    implementation(libs.navigationFragmentDep)
    implementation(libs.navigationUiDep)

    implementation(libs.uiCoreDep)

    implementation(libs.timberDep)

    implementation(libs.composeUiDep)
    implementation(libs.composeFoundationDep)
    implementation(libs.composeMaterialDep)
    implementation(libs.composeAnimationDep)
    implementation(libs.composeActivityDep)
    implementation(libs.composeViewModelDep)
    implementation(libs.composeToolingPreviewDep)
    implementation(libs.composeLiveDataDep)
    implementation(libs.composeConstraintLayoutDep)
    implementation(libs.composeLifecycleDep)
    debugImplementation(libs.composeToolingDep)
    implementation(libs.navigationComposeDep)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    testImplementation(project(":test_shared"))
    testImplementation(project(":test_data"))
}
