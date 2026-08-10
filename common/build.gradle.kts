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

// Taira deployment identity is public, but it is authority-controlled. Read it only from the
// qualification runner environment and escape it as data; local.properties and source defaults
// must never select a current Taira epoch for a production build.
fun protectedBuildConfigString(name: String): String {
    val value = System.getenv(name).orEmpty()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "jp.co.soramitsu.common"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "TAIRA_DEPLOYMENT_MANIFEST_SHA256",
            protectedBuildConfigString("TAIRA_DEPLOYMENT_MANIFEST_SHA256")
        )
        buildConfigField(
            "String",
            "TAIRA_DEPLOYMENT_MANIFEST_SEQUENCE_NUMBER",
            protectedBuildConfigString("TAIRA_DEPLOYMENT_MANIFEST_SEQUENCE_NUMBER")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_EPOCH",
            protectedBuildConfigString("TAIRA_CURRENT_EPOCH")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_CHAIN_ID",
            protectedBuildConfigString("TAIRA_CURRENT_CHAIN_ID")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_GENESIS_SHA256",
            protectedBuildConfigString("TAIRA_CURRENT_GENESIS_SHA256")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_I105_DISCRIMINANT",
            protectedBuildConfigString("TAIRA_CURRENT_I105_DISCRIMINANT")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_TORII_BASE_URL",
            protectedBuildConfigString("TAIRA_CURRENT_TORII_BASE_URL")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_PUBLIC_MCP_ENDPOINT",
            protectedBuildConfigString("TAIRA_CURRENT_PUBLIC_MCP_ENDPOINT")
        )
        buildConfigField(
            "String",
            "TAIRA_CURRENT_EXPLORER_BASE_URL",
            protectedBuildConfigString("TAIRA_CURRENT_EXPLORER_BASE_URL")
        )
        buildConfigField(
            "String",
            "TAIRA_RETIRED_EPOCH",
            protectedBuildConfigString("TAIRA_RETIRED_EPOCH")
        )
        buildConfigField(
            "String",
            "TAIRA_RETIRED_CHAIN_ID",
            protectedBuildConfigString("TAIRA_RETIRED_CHAIN_ID")
        )
        buildConfigField(
            "String",
            "TAIRA_RETIRED_GENESIS_SHA256",
            protectedBuildConfigString("TAIRA_RETIRED_GENESIS_SHA256")
        )
        buildConfigField(
            "String",
            "TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256",
            protectedBuildConfigString("TAIRA_DEPLOYMENT_OPERATOR_KEY_SHA256")
        )
        buildConfigField(
            "String",
            "TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256",
            protectedBuildConfigString("TAIRA_DEPLOYMENT_REVIEWER_KEY_SHA256")
        )
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
    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    flavorDimensions += listOf("default")

    productFlavors {
        create("develop") {
            dimension = "default"

            buildConfigField("String", "X1_ENDPOINT_URL", maybeWrapQuotes(secret("X1_ENDPOINT_URL_DEBUG")))
            buildConfigField("String", "X1_WIDGET_ID", maybeWrapQuotes(secret("X1_WIDGET_ID_DEBUG")))
            buildConfigField("String", "SORACARD_BACKEND_URL", maybeWrapQuotes(secret("SORA_BACKEND_DEBUG")))

            buildConfigField("String", "SORA_CARD_API_KEY", maybeWrapQuotes(secret("SORA_CARD_API_KEY_TEST")))
            buildConfigField("String", "SORA_CARD_DOMAIN", maybeWrapQuotes(secret("SORA_CARD_DOMAIN_TEST")))
            buildConfigField(
                "String",
                "SORA_CARD_KYC_ENDPOINT_URL",
                maybeWrapQuotes(secret("SORA_CARD_KYC_ENDPOINT_URL_TEST"))
            )
            buildConfigField("String", "SORA_CARD_KYC_USERNAME", maybeWrapQuotes(secret("SORA_CARD_KYC_USERNAME_TEST")))
            buildConfigField("String", "SORA_CARD_KYC_PASSWORD", maybeWrapQuotes(secret("SORA_CARD_KYC_PASSWORD_TEST")))

            buildConfigField("String", "SORA_CARD_RECAPTCHA", maybeWrapQuotes(secret("SORA_ANDROID_RECAPTCHA_KEY_TEST")))
            buildConfigField("String", "SORA_CARD_PLATFORM", maybeWrapQuotes(secret("SORA_ANDROID_PLATFORM_ID_TEST")))

            buildConfigField("String", "GOOGLE_API_TOKEN", maybeWrapQuotes(secret("SORA_GOOGLE_TOKEN_TEST")))
        }
        create("soralution") {
            dimension = "default"

            buildConfigField("String", "X1_ENDPOINT_URL", maybeWrapQuotes(secret("X1_ENDPOINT_URL_DEBUG")))
            buildConfigField("String", "X1_WIDGET_ID", maybeWrapQuotes(secret("X1_WIDGET_ID_DEBUG")))
            buildConfigField("String", "SORACARD_BACKEND_URL", maybeWrapQuotes(secret("SORA_BACKEND_DEBUG")))

            buildConfigField("String", "SORA_CARD_API_KEY", maybeWrapQuotes(secret("SORA_CARD_API_KEY_TEST")))
            buildConfigField("String", "SORA_CARD_DOMAIN", maybeWrapQuotes(secret("SORA_CARD_DOMAIN_TEST")))
            buildConfigField(
                "String",
                "SORA_CARD_KYC_ENDPOINT_URL",
                maybeWrapQuotes(secret("SORA_CARD_KYC_ENDPOINT_URL_TEST"))
            )
            buildConfigField("String", "SORA_CARD_KYC_USERNAME", maybeWrapQuotes(secret("SORA_CARD_KYC_USERNAME_TEST")))
            buildConfigField("String", "SORA_CARD_KYC_PASSWORD", maybeWrapQuotes(secret("SORA_CARD_KYC_PASSWORD_TEST")))

            buildConfigField("String", "SORA_CARD_RECAPTCHA", maybeWrapQuotes(secret("SORA_ANDROID_RECAPTCHA_KEY_TEST")))
            buildConfigField("String", "SORA_CARD_PLATFORM", maybeWrapQuotes(secret("SORA_ANDROID_PLATFORM_ID_TEST")))

            buildConfigField("String", "GOOGLE_API_TOKEN", maybeWrapQuotes(secret("SORA_GOOGLE_TOKEN_TEST")))
        }
        create("production") {
            dimension = "default"

            buildConfigField("String", "X1_ENDPOINT_URL", maybeWrapQuotes(secret("X1_ENDPOINT_URL_RELEASE")))
            buildConfigField("String", "X1_WIDGET_ID", maybeWrapQuotes(secret("X1_WIDGET_ID_RELEASE")))
            buildConfigField("String", "SORACARD_BACKEND_URL", maybeWrapQuotes(secret("SORA_BACKEND_RELEASE")))

            buildConfigField("String", "SORA_CARD_API_KEY", maybeWrapQuotes(secret("SORA_CARD_API_KEY_PROD")))
            buildConfigField("String", "SORA_CARD_DOMAIN", maybeWrapQuotes(secret("SORA_CARD_DOMAIN_PROD")))
            buildConfigField(
                "String",
                "SORA_CARD_KYC_ENDPOINT_URL",
                maybeWrapQuotes(secret("SORA_CARD_KYC_ENDPOINT_URL_PROD"))
            )
            buildConfigField("String", "SORA_CARD_KYC_USERNAME", maybeWrapQuotes(secret("SORA_CARD_KYC_USERNAME_PROD")))
            buildConfigField("String", "SORA_CARD_KYC_PASSWORD", maybeWrapQuotes(secret("SORA_CARD_KYC_PASSWORD_PROD")))

            buildConfigField("String", "SORA_CARD_RECAPTCHA", maybeWrapQuotes(secret("SORA_ANDROID_RECAPTCHA_KEY_PROD")))
            buildConfigField("String", "SORA_CARD_PLATFORM", maybeWrapQuotes(secret("SORA_ANDROID_PLATFORM_ID_PROD")))

            buildConfigField("String", "GOOGLE_API_TOKEN", maybeWrapQuotes(secret("SORA_GOOGLE_TOKEN_PROD")))
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
//        exclude(module = "bcpkix-jdk15on")
    }
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

    testImplementation(libs.mockitoDep)
    testImplementation(libs.archCoreTestDep)
    testImplementation(libs.coroutineTestDep)
    androidTestImplementation(libs.androidxTestExtJunitDep)
    androidTestImplementation(libs.androidxTestEspressoCoreDep)
}
