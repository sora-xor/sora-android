package jp.co.soramitsu.feature_wallet_api.domain.model

import java.io.Serializable

data class ReceiveAssetModel(
    val assetId: String,
    val tokenName: String,
    val networkName: String,
    val icon: Int = 0
) : Serializable
