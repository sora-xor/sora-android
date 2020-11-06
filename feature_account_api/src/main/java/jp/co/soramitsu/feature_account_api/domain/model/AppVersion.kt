package jp.co.soramitsu.feature_account_api.domain.model

data class AppVersion(
    val supported: Boolean,
    val downloadUrl: String
)