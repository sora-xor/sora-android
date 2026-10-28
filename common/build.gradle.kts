import java.io.FileInputStream
import java.util.Properties

plugins {
    id("maven-publish")
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
    alias(libs.plugins.kover)
}

fun secret(name: String): String {
    val fileProperties = File(rootProject.projectDir.absolutePath, "local.properties")
    val pr = runCatching { FileInputStream(fileProperties) }.getOrNull()?.use { file ->
        Properties().apply {
            load(file)
        }
    }
    return pr?.getProperty(name) ?: System.getenv(name).orEmpty()
}

fun maybeWrapQuotes(s: String): String {
    return if (s.startsWith("\"")) s else "\"" + s + "\""
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.common"
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
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    flavorDimensions += listOf("default")

    productFlavors {
        create("develop") {
            dimension = "default"

            buildConfigField("String", "GOOGLE_API_TOKEN", maybeWrapQuotes(secret("SORA_GOOGLE_TOKEN_TEST")))
        }
        create("soralution") {
            dimension = "default"

            buildConfigField("String", "GOOGLE_API_TOKEN", maybeWrapQuotes(secret("SORA_GOOGLE_TOKEN_TEST")))
        }
        create("production") {
            dimension = "default"

            buildConfigField("String", "GOOGLE_API_TOKEN", maybeWrapQuotes(secret("SORA_GOOGLE_TOKEN_PROD")))
        }
    }

    // Ancient-language fixtures stay available in non-production builds only.
    sourceSets {
        // Run the same bounded Torii wire/HTTP contracts on the JVM and Android runtime.
        // Keeping these fixtures shared prevents platform checks from drifting apart.
        getByName("test").kotlin.directories.add(file("src/contractTest/java").absolutePath)
        getByName("androidTest").kotlin.directories.add(file("src/contractTest/java").absolutePath)
        getByName("develop").res.directories.add(file("src/nonProduction/res").absolutePath)
        getByName("develop").assets.directories.add(file("src/nonProduction/assets").absolutePath)
        getByName("soralution").res.directories.add(file("src/nonProduction/res").absolutePath)
        getByName("soralution").assets.directories.add(file("src/nonProduction/assets").absolutePath)
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
    api(platform(libs.jackson.bom))
    api(platform("io.ktor:ktor-bom:${libs.versions.ktor.get()}"))
    // Keep transitive backup dependencies on patched, API-compatible lines.
    // Export constraints so every consumer resolves the same compile/runtime versions.
    constraints {
        api("org.bouncycastle:bcprov-jdk18on:1.86") {
            because("Use current security fixes consistently for signing and backups")
        }
        api("com.google.oauth-client:google-oauth-client:1.39.0") {
            because("Fix OAuth token signature verification (CVE-2021-22573)")
        }
        api("com.google.guava:guava:33.7.2-android") {
            because("Fix insecure temporary-directory handling (CVE-2023-2976)")
        }
    }
    api(project(":network"))

    implementation(libs.activityKtxDep)
    implementation(libs.coreKtxDep)
    implementation(libs.appcompatDep)
    implementation(libs.appcompatResDep)
    implementation(libs.materialDep)
    implementation(libs.constraintDep)

    implementation(libs.coroutineAndroidDep)
    implementation(libs.coroutineDep)

    implementation(libs.uiCoreDep)
    api(libs.soramitsu.android.foundation)

    api(libs.kotlinxSerializationJsonDep)

    api(libs.coilSvgDep)

    implementation(libs.webSocketLibDep)
    implementation(libs.uiCoreDep)

    implementation(libs.lifecycleProcessDep)

    implementation(libs.timberDep)
    implementation(libs.svgDep)
    implementation(libs.jdenticonDep)

    implementation(libs.daggerDep)
    ksp(libs.hiltCompilerDep)

    implementation(libs.datastoreDep)
    implementation(libs.navigationFragmentDep)
    implementation(libs.navigationUiDep)

    api(libs.xbackupDep)
    api(libs.xsubstrateDep)
    implementation(libs.xcryptoDep)
    implementation(libs.ed25519Dep) {
        // Ed25519Sha3 uses only the digest provider, never PKIX/TLS utilities.
        exclude(group = "com.madgag.spongycastle", module = "bcpkix-jdk15on")
    }
    implementation(libs.spongycastle.provider)
    implementation(libs.bcprovDep)
    implementation(libs.xercesDep)

    api(libs.gsonDep)
    implementation(libs.zXingCoreDep)

    implementation(platform(libs.googleFirebaseBomDep))
    implementation(libs.googleCrashlyticsDep)

    implementation(libs.lazySodiumDep) {
        artifact {
            type = "aar"
        }
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation(libs.jnaDep) {
        artifact {
            type = "aar"
        }
    }

    implementation(libs.coilDep)
    implementation(libs.coilComposeDep)

    implementation(libs.composeActivityDep)
    implementation(platform(libs.compose.bom))
    implementation(libs.composeUiDep)
    implementation(libs.composeLiveDataDep)
    implementation(libs.composeLifecycleDep)
    implementation(libs.composeFoundationDep)
    implementation(libs.composeMaterialDep)
    implementation(libs.composeConstraintLayoutDep)
    implementation(libs.composeAnimationGraphicsDep)
    implementation(libs.composeToolingPreviewDep)
    implementation(libs.navigationComposeDep)
    debugImplementation(libs.composeToolingDep)

    testImplementation(libs.junitDep)
    testImplementation(libs.mockkDep)
    testImplementation(libs.mockitoDep)
    testImplementation(libs.archCoreTestDep)
    testImplementation(libs.coroutineTestDep)
    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
}

// Development builds support local recovery without Google; distributed production builds must retain cloud recovery.
val validateProductionCloudBackupConfiguration by tasks.registering {
    group = "verification"
    description = "Checks that production Google backup has an OAuth client configured without exposing its value."
    doLast {
        check(secret("SORA_GOOGLE_TOKEN_PROD").trim().trim('"').isNotBlank()) {
            "Production Google backup requires SORA_GOOGLE_TOKEN_PROD. Configure the release OAuth client before packaging."
        }
    }
}
tasks.matching { it.name == "preProductionReleaseBuild" }.configureEach {
    dependsOn(validateProductionCloudBackupConfiguration)
}

// JsonAccountsEncoderExportTest reads the release keep rules: the one-account JSON export is
// written through a library model that only those rules protect from R8.
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("app/proguard-rules.pro"))
        .withPropertyName("releaseKeepRules")
        .withPathSensitivity(PathSensitivity.NONE)
}
