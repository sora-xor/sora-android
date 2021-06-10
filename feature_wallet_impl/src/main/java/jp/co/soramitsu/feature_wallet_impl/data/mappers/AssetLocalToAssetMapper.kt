/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import javax.inject.Inject

class AssetLocalToAssetMapper @Inject constructor() {

    fun map(asset: AssetLocal, assetHolder: AssetHolder): Asset =
        Asset(
            asset.id, asset.name, asset.symbol,
            asset.displayAsset, assetHolder.isHiding(asset.id),
            asset.position, assetHolder.rounding(asset.id), asset.precision, asset.free,
            assetHolder.iconShadow(asset.id), asset.isMintable
        )
}
