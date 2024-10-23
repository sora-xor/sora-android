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
    namespace = "jp.co.soramitsu.feature_multiaccount_impl"
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

    kotlinOptions {
        freeCompilerArgs += listOf("-Xstring-concat=inline")
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
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_account_api"))
    implementation(project(":feature_ethereum_api"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":feature_multiaccount_api"))
    implementation(project(":feature_main_api"))
    implementation(project(":sorasubstrate"))

    implementation(libs.timberDep)

    implementation(libs.xcryptoDep)
    implementation(libs.xsubstrateDep)

    implementation(libs.appcompatDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.viewmodelKtxDep)

    implementation(libs.uiCoreDep)
    implementation(libs.soramitsu.android.foundation)

    implementation(libs.coilDep)
    implementation(libs.coilComposeDep)

    implementation(libs.lifecycleProcessDep)

    kapt(libs.lifecycleKaptDep)

    implementation(libs.navigationFragmentDep)
    implementation(libs.navigationUiDep)

    implementation(libs.uiCoreDep)

    implementation(platform(libs.compose.bom))
    implementation(libs.composeUiDep)
    implementation(libs.composeFoundationDep)
    implementation(libs.composeMaterialDep)
    implementation(libs.composeActivityDep)
    implementation(libs.composeLifecycleDep)
    implementation(libs.composeViewModelDep)
    implementation(libs.composeToolingPreviewDep)
    implementation(libs.composeConstraintLayoutDep)
    implementation(libs.composeLiveDataDep)
    implementation(libs.navigationComposeDep)

    implementation(libs.xbackupDep)
    debugImplementation(libs.composeToolingDep)

    implementation(libs.daggerDep)
    implementation(libs.hiltNavComposeDep)
    kapt(libs.daggerKaptDep)

    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    testImplementation(libs.mockitoKotlinDep)
    testImplementation(libs.archCoreTestDep)
}
