package jp.co.soramitsu.feature_account_api.domain.model

data class Language(
    val iso: String,
    val displayNameResource: Int,
    val nativeDisplayNameResource: Int
)