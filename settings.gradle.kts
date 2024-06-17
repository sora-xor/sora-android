import java.io.FileInputStream
import java.util.Properties

pluginManagement {
    repositories {
        maven { url = uri("https://nexus.iroha.tech/repository/maven-soramitsu/") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

fun secret(name: String): String {
    val fileProperties = File(rootProject.projectDir.absolutePath, "local.properties")
    val pr = runCatching { FileInputStream(fileProperties) }.getOrNull()?.let { file ->
        Properties().apply {
            load(file)
        }
    }
    return pr?.getProperty(name) ?: System.getenv(name)!!
}

fun maybeWrapQuotes(s: String): String {
    return if (s.startsWith("\"")) s else "\"" + s + "\""
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://nexus.iroha.tech/repository/maven-soramitsu/") }
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven {
            url = uri(secret("PAY_WINGS_REPOSITORY_URL"))
            credentials {
                username = secret("PAY_WINGS_USERNAME")
                password = secret("PAY_WINGS_PASSWORD")
            }
        }
    }
}

rootProject.name = "passport-android"
include(":android-foundation")
include(":app")
include(":common")
include(":common_wallet")
include(":core_db")
include(":demeter")
include(":feature_account_api")
include(":feature_account_impl")
include(":feature_assets_api")
include(":feature_assets_impl")
include(":feature_blockexplorer_api")
include(":feature_blockexplorer_impl")
include(":feature_ecosystem_impl")
include(":feature_ethereum_api")
include(":feature_ethereum_impl")
include(":feature_main_api")
include(":feature_main_impl")
include(":feature_multiaccount_api")
include(":feature_multiaccount_impl")
include(":feature_polkaswap_api")
include(":feature_polkaswap_impl")
include(":feature_referral_api")
include(":feature_referral_impl")
include(":feature_select_node_api")
include(":feature_select_node_impl")
include(":feature_sora_card_api")
include(":feature_sora_card_impl")
include(":feature_wallet_api")
include(":feature_wallet_impl")
include(":network")
include(":sorasubstrate")
include(":test_data")
include(":test_shared")
