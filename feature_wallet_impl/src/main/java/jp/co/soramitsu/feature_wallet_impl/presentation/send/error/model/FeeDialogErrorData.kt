package jp.co.soramitsu.feature_wallet_impl.presentation.send.error.model

data class FeeDialogErrorData(
    val minerFee: String,
    val ethBalance: String
)