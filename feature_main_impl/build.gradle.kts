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
    namespace = "jp.co.soramitsu.feature_main_impl"
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
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = composeCompilerVersion
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
    implementation(project(":core_db"))
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":feature_main_api"))
    implementation(project(":feature_account_api"))
    implementation(project(":feature_ethereum_api"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":feature_polkaswap_api"))
    implementation(project(":feature_multiaccount_api"))
    implementation(project(":feature_referral_api"))
    implementation(project(":feature_select_node_api"))
    implementation(project(":feature_sora_card_api"))
    implementation(project(":sorasubstrate"))

    implementation(libs.appcompatDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)
    implementation(libs.biometricDep)

    implementation(libs.uiCoreDep)

    implementation(libs.coreKtxDep)

    implementation(libs.navigationFragmentDep)
    implementation(libs.navigationUiDep)
    implementation(libs.insetterDep)

    implementation(libs.xbackupDep)

    implementation(platform(libs.compose.bom))
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

    implementation(libs.webSocketLibDep)
    implementation(libs.timberDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.viewmodelKtxDep)

    implementation(libs.roomDep)
    implementation(libs.roomKtxDep)

    implementation(libs.workManagerDep)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)
    implementation(libs.hiltWorkManagerDep)
    kapt(libs.hiltWorkManagerKaptDep)

    implementation(libs.lifecycleProcessDep)
    kapt(libs.lifecycleKaptDep)

    implementation(libs.insetterDep)

    testImplementation(project(":test_shared"))
    testImplementation(project(":test_data"))
}
