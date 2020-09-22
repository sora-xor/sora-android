package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import javax.inject.Inject

class AssetToAssetLocalMapper @Inject constructor() {

    fun map(asset: Asset): AssetLocal {
        return with(asset) {
            val assetState = when (state) {
                Asset.State.NORMAL -> AssetLocal.State.NORMAL
                Asset.State.ASSOCIATING -> AssetLocal.State.ASSOCIATING
                Asset.State.ERROR -> AssetLocal.State.ERROR
                Asset.State.UNKNOWN -> AssetLocal.State.UNKNOWN
            }
            AssetLocal(id, assetFirstName, assetLastName, displayAsset, hidingAllowed, position, assetState, roundingPrecision, assetBalance?.balance)
        }
    }
}