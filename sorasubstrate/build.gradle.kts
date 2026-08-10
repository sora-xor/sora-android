plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.sora.substrate"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 36
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
    implementation(project(":common_wallet"))
    implementation(project(":core_db"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":network"))

    implementation(libs.xsubstrateDep)
    implementation(libs.xcryptoDep)
    implementation(libs.soramitsu.android.foundation)
    implementation(libs.roomDep)

    implementation(libs.gsonDep)
    implementation(libs.webSocketLibDep)
    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)
    implementation(libs.timberDep)
    implementation(libs.daggerDep)
    ksp(libs.hiltCompilerDep)

    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
}
