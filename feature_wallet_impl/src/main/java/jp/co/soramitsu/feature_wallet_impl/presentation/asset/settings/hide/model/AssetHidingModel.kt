package jp.co.soramitsu.feature_wallet_impl.presentation.asset.settings.hide.model

data class AssetHidingModel(
    val id: String,
    val assetFirstName: String,
    val assetLastName: String,
    val assetIconResource: Int,
    val balance: String?
)