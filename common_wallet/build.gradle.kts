plugins {
    id("maven-publish")
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
    namespace = "jp.co.soramitsu.common_wallet"
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
    // implementation(libs.fileTree(dir: 'libs', include: ['*.jar']))

    implementation(project(":common"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":core_db"))
    implementation(project(":network"))

    implementation(libs.coreKtxDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)

    implementation(libs.uiCoreDep)
    implementation(libs.soramitsu.android.foundation)

    implementation(libs.coilSvgDep)

    implementation(libs.webSocketLibDep)
    implementation(libs.uiCoreDep)

    implementation(libs.lifecycleProcessDep)
    kapt(libs.lifecycleKaptDep)

    implementation(libs.timberDep)
    implementation(libs.svgDep)
    implementation(libs.jdenticonDep)

    api(libs.soramitsu.sora.card) {
        exclude(group = "com.paywings.onboarding.kyc.android-libs", module = "java-websocket-lib")
    }

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    implementation(libs.datastoreDep)

    implementation(platform(libs.googleFirebaseBomDep))
    implementation(libs.googleCrashlyticsDep)

    implementation(libs.coilDep)
    implementation(libs.coilComposeDep)

    implementation(libs.composeActivityDep)
    implementation(platform(libs.compose.bom))
    implementation(libs.composeUiDep)
    implementation(libs.composeLiveDataDep)
    implementation(libs.composeFoundationDep)
    implementation(libs.composeMaterialDep)
    implementation(libs.composeConstraintLayoutDep)
    implementation(libs.composeAnimationGraphicsDep)
    implementation(libs.composeToolingPreviewDep)
    debugImplementation(libs.composeToolingDep)

    testImplementation(project(":test_data"))
    testImplementation(libs.junitDep)
}
