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
    namespace = "jp.co.soramitsu.feature_blockexplorer_impl"
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
    implementation(project(":core_di"))
    implementation(project(":sorasubstrate"))
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":feature_account_api"))
    implementation(project(":feature_main_api"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":network"))

    implementation(libs.appcompatDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)

    implementation(libs.coilDep)
    implementation(libs.coilComposeDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.viewmodelKtxDep)

    implementation(libs.uiCoreDep)
    implementation(libs.navigationComposeDep)

    implementation(platform(libs.compose.bom))
    implementation(libs.composeConstraintLayoutDep)
    implementation(libs.composeUiDep)
    implementation(libs.composeFoundationDep)
    implementation(libs.composeMaterialDep)
    implementation(libs.composeAnimationDep)
    implementation(libs.composeActivityDep)
    implementation(libs.composeViewModelDep)
    implementation(libs.composeToolingPreviewDep)
    implementation(libs.composeLiveDataDep)
    implementation(libs.composeLifecycleDep)
    debugImplementation(libs.composeToolingDep)

    implementation(libs.timberDep)

    implementation(libs.lifecycleProcessDep)
    kapt(libs.lifecycleKaptDep)

    implementation(libs.navigationFragmentDep)
    implementation(libs.navigationUiDep)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    testImplementation(project(":test_shared"))
    testImplementation(project(":test_data"))
}
