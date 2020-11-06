package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model

data class AssetModel(
    val id: String,
    val assetFirstName: String,
    val assetLastName: String,
    val assetIconResource: Int,
    val assetIconBackgroundColor: Int,
    val balance: String?,
    val state: State?,
    val roundingPrecision: Int,
    val position: Int
) {
    enum class State {
        NORMAL,
        ASSOCIATING,
        ERROR
    }
}