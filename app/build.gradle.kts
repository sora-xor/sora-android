import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

plugins {
    id("maven-publish")
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.firebaseCrashlyticsPlugin)
    alias(libs.plugins.firebaseAppDistributionPlugin)
    alias(libs.plugins.triplet)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
    alias(libs.plugins.composeCompiler)
}

val googleServicesJsonFiles = fileTree(projectDir) {
    include("google-services.json")
    include("src/**/google-services.json")
}
val hasGoogleServicesJson = !googleServicesJsonFiles.isEmpty
val productionKeystorePath = System.getenv("CI_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val productionKeystorePassword = System.getenv("CI_KEYSTORE_PASS")?.takeIf { it.isNotBlank() }
val productionKeyAlias = System.getenv("CI_KEYSTORE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val productionKeyPassword = System.getenv("CI_KEYSTORE_KEY_PASS")?.takeIf { it.isNotBlank() }
val productionVersionCodeRaw = System.getenv("CI_BUILD_ID")?.takeIf { it.isNotBlank() }
val productionVersionCode = productionVersionCodeRaw
    ?.takeIf { it.matches(Regex("[1-9][0-9]{0,8}")) }
    ?.toInt()
val productionReleaseSigningConfigured = listOf(
    productionKeystorePath,
    productionKeystorePassword,
    productionKeyAlias,
    productionKeyPassword,
).all { it != null }

if (hasGoogleServicesJson) {
    apply(plugin = "com.google.gms.google-services")
} else {
    logger.lifecycle("Skipping Google Services plugin because google-services.json is not present.")
}

kotlin {
    jvmToolchain(17)
}

// soralution 143 3.8.6.3 2024.10.31
// sora dae 122 3.8.6.3 2024.11.21
val appVersionCode = productionVersionCode ?: 122
val appVersionName = "3.8.6.3"

android {
    namespace = "jp.co.soramitsu.sora"
    compileSdk = 36

    defaultConfig {
        applicationId = "jp.co.soramitsu.sora"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true
        // resConfigs "en", "ru", "es", "fr", "de", "nb", "in", "tr", "ar"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        checkDependencies = true
    }

    signingConfigs {
        create("cidebug") {
            storeFile = file("../key/testdebug.jks")
            storePassword = "soratestpsw"
            keyAlias = "key0"
            keyPassword = "sorakeypw"
        }
        create("productionRelease") {
            storeFile = productionKeystorePath?.let { file(it) }
            storePassword = productionKeystorePassword
            keyAlias = productionKeyAlias
            keyPassword = productionKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("productionRelease")
            isShrinkResources = true
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = hasGoogleServicesJson
            }
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
        resValues = true
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

    // The isolated production-path qualification APK creates the retained
    // Room 76 source from the exact exported schema before opening it through
    // the production Room 77 builder. Keep the canonical schema inventory in
    // instrumentation assets; never approximate the old database by opening
    // the current entity model first.
    sourceSets {
        getByName("androidTest").assets.directories.add(file("../core_db/schemas").absolutePath)
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

        create("qualification") {
            dimension = "default"
            applicationIdSuffix = ".qualification"
            testApplicationId = "jp.co.soramitsu.sora.qualification.test"
            matchingFallbacks += listOf("production")
            resValue("string", "app_name", "SORA Migration Qualification")
            manifestPlaceholders["pathPrefix"] = "/qualification/#/referral"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_prod_launcher"
            manifestPlaceholders["roundedIcon"] = "@mipmap/ic_prod_launcher_rounded"
        }
    }

    configurations {
        all {
            exclude(module = "bcprov-jdk15on")
        }
    }
}

val verifyProductionReleaseSigning by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fail closed unless production release signing inputs are complete and local."
    doLast {
        if (!productionReleaseSigningConfigured) {
            throw GradleException(
                "Production release signing requires CI_KEYSTORE_PATH, CI_KEYSTORE_PASS, " +
                    "CI_KEYSTORE_KEY_ALIAS, and CI_KEYSTORE_KEY_PASS.",
            )
        }
        if (productionVersionCode == null) {
            throw GradleException(
                "Production release signing requires CI_BUILD_ID as a canonical positive " +
                    "version code with at most nine decimal digits.",
            )
        }
        val configuredKeystore = Paths.get(requireNotNull(productionKeystorePath))
        if (!configuredKeystore.isAbsolute) {
            throw GradleException("Production release keystore path must be absolute.")
        }
        val normalizedKeystore = configuredKeystore.normalize()
        if (
            normalizedKeystore != configuredKeystore ||
            !Files.isRegularFile(normalizedKeystore, LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(normalizedKeystore)
        ) {
            throw GradleException("Production release keystore must be a canonical regular non-symlink file.")
        }
        val realKeystore = normalizedKeystore.toRealPath(LinkOption.NOFOLLOW_LINKS)
        if (realKeystore != normalizedKeystore) {
            throw GradleException("Production release keystore path must not traverse symbolic links.")
        }
        val forbiddenPermissions = setOf(
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_WRITE,
            PosixFilePermission.OTHERS_EXECUTE,
        )
        if (Files.getPosixFilePermissions(realKeystore).any(forbiddenPermissions::contains)) {
            throw GradleException("Production release keystore must be owner-only.")
        }
    }
}

tasks.configureEach {
    if (
        name != verifyProductionReleaseSigning.name &&
        name.contains("productionRelease", ignoreCase = true)
    ) {
        dependsOn(verifyProductionReleaseSigning)
    }
}

androidComponents {
    beforeVariants(selector().withFlavor("default" to "qualification")) { variantBuilder ->
        if (variantBuilder.buildType != "debug") {
            variantBuilder.enable = false
        }
    }
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                "SORA_Wallet_${appVersionName}_${appVersionCode}_${variant.flavorName}_${variant.buildType}.apk"
            )
        }
    }
}

hilt {
    enableAggregatingTask = false
}

play {
    serviceAccountCredentials = file(System.getenv("CI_PLAY_KEY") ?: "../key/fake.json")
    track = "internal"
    releaseStatus = ReleaseStatus.DRAFT
    releaseName = "3.8.6.5 - SORA Card Improvements"
    defaultToAppBundles = true
}

dependencies {
    // implementation(libs.fileTree(dir: 'libs', include: ['*.jar'])
    implementation(project(":common"))
    implementation(project(":common_wallet"))
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

    implementation(libs.roomDep)
    implementation(libs.xcryptoDep)
    implementation(libs.xsubstrateDep)
    implementation(libs.soramitsu.android.foundation)

    implementation(libs.daggerDep)
    ksp(libs.hiltCompilerDep)
    implementation(libs.hiltWorkManagerDep)
    ksp(libs.hiltWorkManagerCompilerDep)
    implementation(libs.workManagerDep)

    implementation(libs.lifecycleProcessDep)
    implementation(platform(libs.compose.bom))
    implementation(libs.composeRuntimeDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)

    implementation(platform(libs.googleFirebaseBomDep))
    implementation(libs.googleCrashlyticsDep)

    implementation(libs.webSocketLibDep)

    testImplementation(project(":test_data"))
    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    testImplementation(libs.mockitoKotlinDep)
    testImplementation(libs.archCoreTestDep)
    testImplementation(libs.truthDep)

    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
    androidTestImplementation(libs.coroutineTestDep)
    androidTestImplementation(libs.junitDep)
    androidTestImplementation(libs.mockkAndroidDep)
    androidTestImplementation(libs.roomTestHelpersDep)

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
