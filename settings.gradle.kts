pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "SourceQualifiedSoramitsu"
                    url = uri(rootDir.resolve("vendor/soramitsu-maven"))
                    metadataSources {
                        gradleMetadata()
                        mavenPom()
                    }
                }
            }
            filter {
                includeModule("jp.co.soramitsu", "android-foundation")
                includeModule("jp.co.soramitsu", "android-sora-card")
                includeModule("jp.co.soramitsu", "ui-core")
                includeModule("jp.co.soramitsu", "xbackup")
                includeModule("jp.co.soramitsu", "xcrypto")
                includeModule("jp.co.soramitsu", "xsubstrate")
                includeModule("jp.co.soramitsu.xnetworking", "lib-android")
                includeModule("io.emeraldpay.polkaj", "polkaj-scale")
                includeModule("com.paywings.oauth", "android-sdk")
                includeModule("com.paywings.kyc", "android-sdk")
                includeModule(
                    "com.paywings.onboarding.kyc.android-libs",
                    "idensic-mobile-sdk",
                )
            }
        }
        google()
        mavenCentral()
        exclusiveContent {
            forRepository {
                maven {
                    name = "JitPack"
                    url = uri("https://jitpack.io")
                }
            }
            filter {
                includeModule("com.github.warchant", "ed25519-sha3-java")
                includeModule("com.github.WycliffeAssociates", "jdenticon-kotlin")
            }
        }
    }
}

rootProject.name = "passport-android"
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
