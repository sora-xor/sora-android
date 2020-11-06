package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model

data class SoraTransaction(
    val id: String,
    val isIncoming: Boolean,
    val statusIconResource: Int,
    val initials: String,
    val title: String,
    val description: String,
    val dateString: String,
    val amountFormatted: String
)