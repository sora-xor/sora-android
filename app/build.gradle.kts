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
    alias(libs.plugins.triplet)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
}

kotlin {
    jvmToolchain(17)
}

// soralution 138 3.8.5.0 2024.09.06
// sora dae 108 3.8.0.0 2023.12.04

android {
    namespace = "jp.co.soramitsu.sora"
    compileSdk = 34

    defaultConfig {
        applicationId = "jp.co.soramitsu.sora"
        minSdk = 26
        targetSdk = 34
        versionCode = System.getenv("CI_BUILD_ID")?.toInt() ?: 139
        versionName = "3.8.5.0"
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
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE-notice.md",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    flavorDimensions += listOf("default")
    productFlavors {
        create("develop") {
            dimension = "default"
            applicationIdSuffix = ".develop"
            resValue("string", "app_name", "SORA Develop")
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
            resValue("string", "app_name", "SORA")
            manifestPlaceholders["pathPrefix"] = "/#/referral"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_prod_launcher"
            manifestPlaceholders["roundedIcon"] = "@mipmap/ic_prod_launcher_rounded"
        }
    }

    applicationVariants.all {
        val variant = this
        this.outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach {
                it.outputFileName =
                    "SORA_Wallet_${variant.versionName}_${variant.versionCode}_${variant.flavorName}_${variant.buildType.name}.apk"
            }
    }

    configurations {
        all {
            exclude(module = "bcprov-jdk15on")
//            resolutionStrategy {
//                dependencySubstitution {
//                    substitute(module("")).using(module(""))
//                }
//            }
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
    releaseName = "3.8.5.0 - SORA Card Improvements"
    defaultToAppBundles = true
}

dependencies {
    // implementation(libs.fileTree(dir: 'libs', include: ['*.jar'])
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
    implementation(libs.soramitsu.android.foundation)

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

    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    testImplementation(libs.mockitoKotlinDep)
    testImplementation(libs.archCoreTestDep)

    kover(project(":common"))
    kover(project(":common_wallet"))
    kover(project(":core_db"))
    kover(project(":demeter"))
    kover(project(":feature_account_api"))
    kover(project(":feature_account_impl"))
    kover(project(":feature_assets_api"))
    kover(project(":feature_assets_impl"))
    kover(project(":feature_blockexplorer_api"))
    kover(project(":feature_blockexplorer_impl"))
    kover(project(":feature_ecosystem_impl"))
    kover(project(":feature_main_api"))
    kover(project(":feature_main_impl"))
    kover(project(":feature_multiaccount_api"))
    kover(project(":feature_multiaccount_impl"))
    kover(project(":feature_referral_api"))
    kover(project(":feature_referral_impl"))
    kover(project(":feature_polkaswap_api"))
    kover(project(":feature_polkaswap_impl"))
    kover(project(":feature_referral_impl"))
    kover(project(":feature_ethereum_api"))
    kover(project(":feature_wallet_api"))
    kover(project(":feature_wallet_impl"))
    kover(project(":feature_select_node_api"))
    kover(project(":feature_select_node_impl"))
    kover(project(":feature_sora_card_api"))
    kover(project(":feature_sora_card_impl"))
    kover(project(":sorasubstrate"))
    kover(project(":network"))
}

kapt {
    correctErrorTypes = true
}

kover {
    reports {
        variant("developDebug") {
            xml {
                onCheck = true
                title = "sora wallet xml report"
                xmlFile = file("${project.rootDir}/report/coverage.xml")
            }
            html {
                title = "sora wallet html report"
                onCheck = true
                charset = "UTF-8"
                htmlDir.set(file("${project.rootDir}/htmlreport"))
            }
            verify {
                rule {
                    minBound(14)
                }
            }
            filters {
                excludes {
                    classes(
                        "*.BuildConfig",
                        "**.models.*",
                        "**.core.network.*",
                        "**.di.*",
                        "**.shared_utils.wsrpc.*",
                        "*NetworkDataSource",
                        "*NetworkDataSource\$*",
                        "*ChainConnection",
                        "*ChainConnection\$*",
                        "**.runtime.definitions.TypeDefinitionsTreeV2",
                        "**.runtime.definitions.TypeDefinitionsTreeV2\$*",

                        // TODO: Coverage these modules by tests
                        "**.core.rpc.*",
                        "**.core.utils.*",
                        "**.core.extrinsic.*",
                    )
                }
            }
        }
    }
}
