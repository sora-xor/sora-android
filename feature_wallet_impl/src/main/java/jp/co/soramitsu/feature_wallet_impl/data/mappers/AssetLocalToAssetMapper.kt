/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_db.model.AssetTokenLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import javax.inject.Inject

class AssetLocalToAssetMapper @Inject constructor() {

    fun map(asset: AssetTokenLocal, res: ResourceManager): Asset =
        Asset(
            token = map(asset.tokenLocal, res),
            balance = AssetBalance(
                asset.assetLocal.free - asset.assetLocal.miscFrozen.max(asset.assetLocal.feeFrozen),
                asset.assetLocal.reserved,
                asset.assetLocal.miscFrozen,
                asset.assetLocal.feeFrozen,
                asset.assetLocal.bonded,
                asset.assetLocal.redeemable,
                asset.assetLocal.unbonding,
            ),
            position = asset.assetLocal.position,
            isDisplaying = asset.assetLocal.displayAsset,
            isDisplayingBalance = asset.assetLocal.showMainBalance,
        )

    fun map(tokenLocal: TokenLocal, res: ResourceManager): Token =
        Token(
            id = tokenLocal.id,
            name = tokenLocal.name,
            symbol = tokenLocal.symbol,
            precision = tokenLocal.precision,
            isHidable = tokenLocal.isHidable,
            icon = runCatching { res.getResByName("ic_${tokenLocal.id}") }.getOrDefault(
                OptionsProvider.DEFAULT_ICON
            )
        )
}
