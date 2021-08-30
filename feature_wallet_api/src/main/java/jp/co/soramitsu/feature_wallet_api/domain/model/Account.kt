package jp.co.soramitsu.feature_wallet_api.domain.model

data class Account(
    val firstName: String,
    val lastName: String,
    val address: String,
) {
    val fullName: String = listOf(firstName, lastName)
        .filter {
            !it.isBlank()
        }.joinToString(separator = " ")
}
