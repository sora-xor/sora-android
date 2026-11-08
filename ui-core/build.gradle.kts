// SORA's Compose UI components (jp.co.soramitsu.ui_core), built from source in this repository.
// It replaces the jp.co.soramitsu:ui-core 0.2.39 AAR: the Kotlin sources are that release's,
// unchanged, and src/main/res holds the AAR's drawables and fonts. See README.md.
plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.ui_core"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    // As in the AAR, every dependency is an implementation detail; the app declares its own Compose.
    implementation(libs.coreKtxDep)
    implementation(platform(libs.compose.bom))
    implementation(libs.composeUiDep)
    implementation(libs.composeLiveDataDep)
    implementation(libs.composeMaterialDep)
    implementation(libs.composeUiGraphicsDep)
    implementation(libs.composeToolingPreviewDep)
    implementation(libs.coilDep)
    implementation(libs.coilComposeDep)

    constraints {
        // Compose and Coil alone resolve older AndroidX and kotlinx releases than the app ships,
        // some of them not in gradle/verification-metadata.xml. Compile ui-core against the app's
        // versions instead; each family aligns its other artifacts to these.
        implementation(libs.composeLifecycleDep)
        implementation(libs.activityDep)
        implementation(libs.savedstateDep)
        implementation(libs.coroutineDep)
        implementation(libs.kotlinxSerializationCoreDep)
    }
}
