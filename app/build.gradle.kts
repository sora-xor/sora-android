import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
    id("maven-publish")
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kapt)
    alias(libs.plugins.googleServicesPlugin)
    alias(libs.plugins.firebaseCrashlyticsPlugin)
    alias(libs.plugins.firebaseAppDistributionPlugin)
    id("kotlin-parcelize")
    id("com.github.triplet.play") version "3.8.6"
}

kotlin {
    jvmToolchain(11)
}

android {
    namespace = "jp.co.soramitsu.sora"
    compileSdk = 34

    defaultConfig {
        applicationId = "jp.co.soramitsu.sora"
        minSdk = 24
        versionCode = 777
        versionName = "7.7.7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        // resConfigs "en", "ru", "es", "fr", "de", "nb", "in", "tr", "ar"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("cidebug") {
            storeFile = file(System.getenv("CI_KEYSTORE_PATH") ?: "../key/testdebug.jks")
            storePassword = System.getenv("CI_KEYSTORE_PASS") ?: "soratestpsw"
            keyAlias = System.getenv("CI_KEYSTORE_KEY_ALIAS") ?: "key0"
            keyPassword = System.getenv("CI_KEYSTORE_KEY_PASS") ?: "sorakeypw"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("cidebug")
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("cidebug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("firebasedebug") {
            initWith(getByName("debug"))
            matchingFallbacks += listOf("debug")
            versionNameSuffix = "-firebase"
            signingConfig = signingConfigs.getByName("cidebug")
            // Init firebase
            val firebaseReleaseNotes = System.getenv("CI_FIREBASE_RELEASENOTES") ?: ""
            val firebaseGroup = System.getenv("CI_FIREBASE_GROUP") ?: ""
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotes = firebaseReleaseNotes
                groups = firebaseGroup
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE-notice.md", "META-INF/LICENSE.md", "META-INF/LICENSE.txt", "META-INF/license.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/notice.txt", "META-INF/ASL2.0", "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/INDEX.LIST", "META-INF/io.netty.versions.properties")
        }
    }

    flavorDimensions += listOf("default")
    productFlavors {
        create("develop") {
            dimension = "default"
            applicationIdSuffix = ".develop"
            resValue("string", "app_name", "Sora Develop")
            manifestPlaceholders["pathPrefix"] = "/dev/#/referral"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_dev_launcher"
            manifestPlaceholders["roundedIcon"] = "@mipmap/ic_dev_launcher"
        }

        create("soralution") {
            dimension = "default"
            applicationIdSuffix = ".communitytesting"
            resValue("string", "app_name", "Soralution")
            manifestPlaceholders["pathPrefix"] = "/tst/#/referral"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_soralution_launcher"
            manifestPlaceholders["roundedIcon"] = "@mipmap/ic_soralution_launcher_rounded"
        }

        create("production") {
            dimension = "default"
            resValue("string", "app_name", "Sora")
            manifestPlaceholders["pathPrefix"] = "/#/referral"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_prod_launcher"
            manifestPlaceholders["roundedIcon"] = "@mipmap/ic_prod_launcher_rounded"
        }
    }

    applicationVariants.forEach { variant ->
        variant.outputs.forEach { output ->
            (output as BaseVariantOutputImpl).outputFileName = "Sora_" + variant.versionName + "_" + variant.flavorName + ".apk"
        }
    }

    configurations {
        all {
            exclude(module = "bcprov-jdk15on")
        }
    }
}

hilt {
    enableAggregatingTask = true
}

play {
    serviceAccountCredentials = file(System.getenv("CI_PLAY_KEY") ?: "../key/fake.json")
    track = "internal"
    releaseStatus = ReleaseStatus.DRAFT
    releaseName = "3.8.0.2 - Demeter Farming"
}

dependencies {
    // implementation(libs.fileTree(dir: 'libs', include: ['*.jar'])
    implementation(project(":android-foundation"))
    implementation(project(":common"))
    implementation(project(":core_db"))
    implementation(project(":demeter"))
    implementation(project(":feature_assets_api"))
    implementation(project(":feature_assets_impl"))
    implementation(project(":feature_main_api"))
    implementation(project(":feature_main_impl"))
    implementation(project(":feature_multiaccount_api"))
    implementation(project(":feature_multiaccount_impl"))
    implementation(project(":feature_referral_api"))
    implementation(project(":feature_referral_impl"))
    implementation(project(":feature_account_api"))
    implementation(project(":feature_account_impl"))
    implementation(project(":feature_ethereum_api"))
    implementation(project(":feature_ethereum_impl"))
    implementation(project(":feature_polkaswap_api"))
    implementation(project(":feature_polkaswap_impl"))
    implementation(project(":feature_wallet_api"))
    implementation(project(":feature_wallet_impl"))
    implementation(project(":feature_select_node_api"))
    implementation(project(":feature_select_node_impl"))
    implementation(project(":feature_blockexplorer_api"))
    implementation(project(":feature_blockexplorer_impl"))
    implementation(project(":feature_sora_card_api"))
    implementation(project(":feature_sora_card_impl"))
    implementation(project(":feature_ecosystem_impl"))
    implementation(project(":sorasubstrate"))
    implementation(project(":network"))

    implementation(libs.appcompatDep)
    implementation(libs.appcompatResDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)

    implementation(libs.navigationFragmentDep)
    implementation(libs.navigationUiDep)

    implementation(libs.lottieDep)
    implementation(libs.coilDep)
    implementation(libs.coilSvgDep)

    implementation(libs.timberDep)

    implementation(libs.xsubstrateDep)

    implementation(libs.daggerDep)
    kapt(libs.daggerKaptDep)
    implementation(libs.hiltWorkManagerDep)
    kapt(libs.hiltWorkManagerKaptDep)

    implementation(libs.lifecycleProcessDep)
    kapt(libs.lifecycleKaptDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)

    implementation(platform(libs.googleFirebaseBomDep))
    implementation(libs.googleCrashlyticsDep)

    implementation(libs.webSocketLibDep)

    // Tests
    testImplementation(project(":test_shared"))
}

kapt {
    correctErrorTypes = true
}
