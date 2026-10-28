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
    namespace = "jp.co.soramitsu.feature_account_impl"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        targetSdk = 37
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

    // The wallet-upgrade device tests build released Room databases from the exported schemas.
    sourceSets {
        getByName("androidTest").assets.directories.add(file("../core_db/schemas").absolutePath)
        // Key vectors of the released native sr25519 library (generated, see
        // scripts/historical-sr25519-vectors): one copy for the JVM and the device tests.
        getByName("test").kotlin.directories.add(file("src/sharedTest/java").absolutePath)
        getByName("androidTest").kotlin.directories.add(file("src/sharedTest/java").absolutePath)
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
    ksp(libs.hiltCompilerDep)

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

    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
    androidTestImplementation(libs.coroutineTestDep)
    // Already used by :app device tests; stands in for CryptoAssistant/JsonAccountsEncoder,
    // which the upgrade scenarios never call.
    androidTestImplementation(libs.mockkAndroidDep)
    // Same Preferences DataStore as :common; the tests write it like the released apps did.
    androidTestImplementation(libs.datastoreDep)
    // The upgrade scenarios import and repair through the production MultiaccountInteractor
    // (not a copy of it) on the real repositories. :feature_wallet_api is only the type of a
    // constructor parameter the import never uses.
    androidTestImplementation(project(":feature_multiaccount_impl"))
    androidTestImplementation(project(":feature_wallet_api"))
    // The read-only scenarios open the account list and Account options (their view models)
    // on the real repositories; :feature_main_api is only the type of their router parameter.
    androidTestImplementation(project(":feature_main_api"))
}
