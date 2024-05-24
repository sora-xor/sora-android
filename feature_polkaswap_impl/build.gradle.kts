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
    namespace = "jp.co.soramitsu.feature_polkaswap_impl"
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
    implementation(project(":core_di"))
    implementation(project(":demeter"))
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_polkaswap_api"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":feature_account_api"))
    implementation(project(":sorasubstrate"))
    implementation(project(":feature_main_api"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":network"))

    implementation(libs.xsubstrateDep)
    implementation(libs.xcryptoDep)

    implementation(libs.appcompatDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)

    implementation(libs.navigationFragmentDep)

    implementation(libs.insetterDep)

    implementation(libs.timberDep)

    implementation(libs.lottieDep)

    implementation(libs.coilDep)
    implementation(libs.coilComposeDep)

    implementation(libs.workManagerDep)
    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.viewmodelKtxDep)
    implementation(libs.runtimeKtxDep)
    implementation(libs.fragmentKtxDep)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)
    implementation(libs.hiltWorkManagerDep)
    kapt(libs.hiltWorkManagerKaptDep)

    implementation(libs.lifecycleProcessDep)
    kapt(libs.lifecycleKaptDep)

    implementation(libs.roomDep)
    implementation(libs.roomKtxDep)

    implementation(libs.uiCoreDep)

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

    testImplementation(project(":test_shared"))
    testImplementation(project(":test_data"))
}
