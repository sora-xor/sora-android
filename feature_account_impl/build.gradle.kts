plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.feature_account_impl"
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
    implementation(project(":feature_account_api"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":core_db"))
    implementation(project(":sorasubstrate"))

    implementation(libs.xcryptoDep)
    implementation(libs.xsubstrateDep)
    implementation(libs.soramitsu.android.foundation)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    implementation(libs.roomDep)
    implementation(libs.roomKtxDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.viewmodelKtxDep)

    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    testImplementation(libs.mockitoKotlinDep)
    testImplementation(libs.archCoreTestDep)
}
