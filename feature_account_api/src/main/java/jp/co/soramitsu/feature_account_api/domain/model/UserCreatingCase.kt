package jp.co.soramitsu.feature_account_api.domain.model

data class UserCreatingCase(
    val alreadyVerified: Boolean,
    val blockingTimeForSms: Int
)