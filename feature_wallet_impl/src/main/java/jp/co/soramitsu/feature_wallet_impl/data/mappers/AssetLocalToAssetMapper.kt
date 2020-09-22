/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.AssetBalance
import javax.inject.Inject

class AssetLocalToAssetMapper @Inject constructor() {

    fun map(asset: AssetLocal): Asset {
        return with(asset) {
            val assetBalance = balance?.let { AssetBalance(id, it) }

            val assetState = when (state) {
                AssetLocal.State.NORMAL -> Asset.State.NORMAL
                AssetLocal.State.ASSOCIATING -> Asset.State.ASSOCIATING
                AssetLocal.State.ERROR -> Asset.State.ERROR
                AssetLocal.State.UNKNOWN -> Asset.State.UNKNOWN
            }
            Asset(id, assetFirstName, assetLastName, displayAsset, hidingAllowed, position, assetState, roundingPrecision, assetBalance)
        }
    }
}