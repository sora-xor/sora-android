// SORA's cryptography library, built from source in this repository. It replaces the
// jp.co.soramitsu:xcrypto 1.2.8-sora.2 AAR: the Kotlin/Java sources are that release's
// (x-crypto 2346144a plus the SORA security patches), and the sr25519 JNI library is
// built with ndk-build from the in-repo C sources in src/main/jni (see README.md).
plugins {
    alias(libs.plugins.androidLibrary)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.xcrypto"
    compileSdk = 37
    ndkVersion = "28.0.12674087"

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    externalNativeBuild {
        ndkBuild {
            path("src/main/jni/Android.mk")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 37
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
    api(libs.bcprovDep)
    // web3j asks for jackson 2.10; align with the version the app ships, as :common does.
    api(platform(libs.jackson.bom))
    api(libs.web3jCryptoDep) {
        // The wallet uses BouncyCastle jdk18on everywhere (the app excludes jdk15on too).
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    implementation(libs.lz4JavaDep)

    testImplementation(libs.junitDep)
}
