/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal
import javax.inject.Inject

class AssetToAssetLocalMapper @Inject constructor() {

    fun map(asset: Asset): AssetLocal =
        with(asset) {
            AssetLocal(
                id,
                assetName,
                symbol,
                display,
                position,
                precision,
                isMintable,
                balance,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO
            )
        }
}
