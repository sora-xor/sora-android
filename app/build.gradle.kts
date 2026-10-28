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
    alias(libs.plugins.baselineProfile)
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
// These legacy environment-variable names are also used by local release setups.
val productionKeystorePath = System.getenv("CI_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val productionKeystorePassword = System.getenv("CI_KEYSTORE_PASS")?.takeIf { it.isNotBlank() }
val productionKeyAlias = System.getenv("CI_KEYSTORE_KEY_ALIAS")?.takeIf { it.isNotBlank() }
val productionKeyPassword = System.getenv("CI_KEYSTORE_KEY_PASS")?.takeIf { it.isNotBlank() }
val canonicalVersionCode = Regex("[1-9][0-9]{0,8}")
// Play production builds take their versionCode only from SORA_VERSION_CODE (Gradle property
// -PSORA_VERSION_CODE=... or environment variable). verifyProductionReleaseSigning rejects a
// productionRelease build without it.
val explicitVersionCodeRaw = (findProperty("SORA_VERSION_CODE") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("SORA_VERSION_CODE")?.takeIf { it.isNotBlank() }
val explicitVersionCode = explicitVersionCodeRaw
    ?.trim()
    ?.takeIf { it.matches(canonicalVersionCode) }
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

// Google Play: 3.9.0 is versionCode 3090000; the next release is 3.9.1.
// From 3.9.0: versionCode = MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + BUILD (00..99),
// so 3.9.0 build 1 is 3090001.
val appVersionName = "3.9.1"
val appVersionCodeBase = appVersionName.split('.').map(String::toInt)
    .let { (major, minor, patch) -> major * 1_000_000 + minor * 10_000 + patch * 100 }
val playLastPublishedVersionCode = 3090000
// Local and debug builds default to the version base (3090100 for 3.9.1), above the
// currently published Play code. Release builds still require an explicit code.
val appVersionCode = explicitVersionCode ?: appVersionCodeBase

android {
    namespace = "jp.co.soramitsu.sora"
    compileSdk = 37

    defaultConfig {
        applicationId = "jp.co.soramitsu.sora"
        minSdk = 26
        targetSdk = 37
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
        warningsAsErrors = true
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
        create("benchmarkRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("cidebug")
            configure<CrashlyticsExtension> {
                // Benchmarks are local, unsigned production-shape measurements. They must not
                // create or upload a release mapping, especially when Google Services is absent.
                mappingFileUploadEnabled = false
            }
        }
        create("nonMinifiedRelease") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("cidebug")
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
        compose = true
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
        configureEach {
            if (name in setOf("develop", "soralution")) {
                res.directories.add(file("src/nonProduction/res").absolutePath)
            }
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

baselineProfile {
    // Generate from the production-shaped flavor, then share its startup hot paths with all
    // flavors. Development-only logging must not influence the shipped profile.
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
    warnings {
        // Qualification intentionally has no release-like variants; it is an isolated debug APK.
        disabledVariants = false
    }
}

// Compares the Google Drive backup client (SORA_GOOGLE_TOKEN_PROD) and the Firebase app in
// google-services.json with the SHA-256 pins in config/android-production-google-identity.json.
// Provides the two closures used below and the task verifyProductionGoogleIdentity.
apply(from = rootProject.file("scripts/verify-production-google-identity.gradle"))

@Suppress("UNCHECKED_CAST")
fun googleIdentityMessages(name: String): List<String> =
    (extra[name] as groovy.lang.Closure<List<String>>).call()

val verifyProductionReleaseSigning by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Fail closed unless production release signing, version and secret inputs are complete."
    doLast {
        val problems = mutableListOf<String>()
        if (!productionReleaseSigningConfigured) {
            problems += "signing: set CI_KEYSTORE_PATH, CI_KEYSTORE_PASS, CI_KEYSTORE_KEY_ALIAS " +
                "and CI_KEYSTORE_KEY_PASS"
        }
        if (!appVersionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) {
            problems += "versionName $appVersionName is not MAJOR.MINOR.PATCH"
        }
        val versionCode = explicitVersionCode
        when {
            explicitVersionCodeRaw == null -> problems += "versionCode: set SORA_VERSION_CODE " +
                "(-PSORA_VERSION_CODE=... or an environment variable)"
            versionCode == null -> problems += "versionCode: SORA_VERSION_CODE must be a " +
                "positive whole number with at most nine digits"
            versionCode <= playLastPublishedVersionCode -> problems += "versionCode: " +
                "SORA_VERSION_CODE $versionCode is not higher than the last Play code " +
                "$playLastPublishedVersionCode"
            versionCode !in appVersionCodeBase..(appVersionCodeBase + 99) -> problems +=
                "versionCode: SORA_VERSION_CODE $versionCode is outside " +
                    "$appVersionCodeBase..${appVersionCodeBase + 99} for versionName $appVersionName"
        }
        if (!hasGoogleServicesJson) {
            problems += "app/google-services.json is missing (Crashlytics would be off)"
        }
        // The Google Drive backup client must be the one 3.8.6.3 used, or people cannot see their
        // existing Drive backups. "Present" is not enough: the value is compared with its pinned
        // SHA-256, and while nobody has filled in that pin the build is refused. A pinned
        // Firebase app is compared the same way. :common checks again that a client is set.
        problems += googleIdentityMessages("productionGoogleIdentityProblems")
        if (problems.isNotEmpty()) {
            throw GradleException(
                "productionRelease cannot be built:\n" + problems.joinToString("\n") { "- $it" },
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
        googleIdentityMessages("productionGoogleIdentityNotes").forEach { logger.warn(it) }
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
                if (variant.name == "productionRelease") {
                    // Local release verification and device-smoke scripts use this exact APK path.
                    "app-production-release.apk"
                } else {
                    "SORA_Wallet_${appVersionName}_${appVersionCode}_${variant.flavorName}_${variant.buildType}.apk"
                }
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
    releaseName = "$appVersionName ($appVersionCode)"
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
    implementation(libs.profileInstallerDep)

    implementation(libs.lifecycleProcessDep)
    implementation(platform(libs.compose.bom))
    implementation(libs.composeRuntimeDep)
    implementation(libs.composeActivityDep)
    implementation(libs.composeUiDep)
    implementation(libs.composeLifecycleDep)
    implementation(libs.composeFoundationDep)
    implementation(libs.core.splashscreen)
    implementation(libs.composeMaterialDep)
    implementation(libs.uiCoreDep)
    implementation(libs.composeAnimationDep)
    implementation(libs.composeToolingPreviewDep)
    debugImplementation(libs.composeToolingDep)
    implementation(libs.biometricDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)

    implementation(platform(libs.googleFirebaseBomDep))
    implementation(libs.googleCrashlyticsDep)
    implementation(libs.firebaseAnalyticsDep)

    implementation(libs.webSocketLibDep)

    testImplementation(project(":test_data"))
    testImplementation(libs.coroutineTestDep)
    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    testImplementation(libs.mockitoKotlinDep)
    testImplementation(libs.archCoreTestDep)
    testImplementation(libs.truthDep)

    androidTestImplementation(libs.androidxTestExtJunitDep)
    // Compose UI tests of the Safe Mode screens (test-only; the ui-test manifest is already in
    // every debug app through feature_blockexplorer_api).
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.composeUiTestJunit4Dep)
    androidTestImplementation(libs.navigationComposeDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
    androidTestImplementation(libs.coroutineTestDep)
    androidTestImplementation(libs.junitDep)
    androidTestImplementation(libs.mockkAndroidDep)
    androidTestImplementation(libs.roomTestHelpersDep)
    androidTestImplementation(libs.zXingCoreDep)
    androidTestImplementation(libs.zXingEmbeddedDep)

    baselineProfile(project(":baselineprofile"))

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
                    )
                }
            }
        }
    }
}
