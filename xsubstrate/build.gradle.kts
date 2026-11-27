// Substrate client library (runtime metadata, SCALE types, extrinsics and the websocket RPC
// client), built from source in this repository. It replaces the jp.co.soramitsu:xsubstrate
// 1.2.7 AAR: the Kotlin sources are that release's, unchanged. See README.md.
plugins {
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.xsubstrate"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
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
    // The AAR exposed polkaj-scale in its API and kept the rest as runtime dependencies.
    api(project(":polkaj-scale"))
    implementation(project(":xcrypto"))
    implementation(libs.gsonDep)
    implementation(libs.webSocketLibDep)
    implementation(libs.svgDep)
    implementation(libs.coroutineDep)
}
