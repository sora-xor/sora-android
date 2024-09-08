plugins {
    id("maven-publish")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "com.example.core_db"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
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

class RoomSchemaArgProvider(
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val schemaDir: File,
) : CommandLineArgumentProvider {

    override fun asArguments(): MutableIterable<String> {
        return mutableListOf("room.schemaLocation=${schemaDir.path}")
    }
}

ksp {
    arg(RoomSchemaArgProvider(File(projectDir, "schemas")))
}

dependencies {
    implementation(project(":common"))

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)

    implementation(libs.coroutineDep)
    implementation(libs.coroutineAndroidDep)

    implementation(libs.roomDep)
    implementation(libs.roomKtxDep)
    ksp(libs.roomKaptDep)

    androidTestImplementation(project(":test_data"))
    androidTestImplementation(libs.soramitsu.android.foundation)
    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
    androidTestImplementation(libs.archCoreTestDep)
    androidTestImplementation(libs.roomTestHelpersDep)
    androidTestImplementation(libs.coroutineTestDep)
}
