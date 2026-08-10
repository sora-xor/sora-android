plugins {
    id("maven-publish")
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
    namespace = "com.example.core_db"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
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

    // MigrationTestHelper resolves retained schemas from instrumentation assets by the
    // production database's canonical name. KSP exports the v74/v75/v76 compiler fixtures under
    // their own canonical-name directories first; after each reviewed JSON is copied
    // byte-for-byte into jp.co.soramitsu.core_db.AppDatabase/{74,75,76}.json, this source set makes
    // those canonical artifacts available to every flavored Android-test APK.
    sourceSets {
        getByName("androidTest").assets.directories.add(File(projectDir, "schemas").absolutePath)
    }
}

class RoomSchemaArgProvider(
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
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
    ksp(libs.hiltCompilerDep)

    implementation(libs.coroutineDep)
    implementation(libs.coroutineAndroidDep)

    implementation(libs.roomDep)
    implementation(libs.roomKtxDep)
    ksp(libs.roomCompilerDep)
    add("kspAndroidTest", libs.roomCompilerDep)

    androidTestImplementation(project(":test_data"))
    androidTestImplementation(libs.soramitsu.android.foundation)
    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
    androidTestImplementation(libs.archCoreTestDep)
    androidTestImplementation(libs.roomTestHelpersDep)
    androidTestImplementation(libs.coroutineTestDep)
}
