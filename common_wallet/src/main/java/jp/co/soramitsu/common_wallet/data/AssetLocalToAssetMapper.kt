/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.TokenWithFiatLocal
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager

@Singleton
class AssetLocalToAssetMapper @Inject constructor(
    private val whitelistTokensManager: WhitelistTokensManager,
    private val soraConfigManager: SoraConfigManager,
) {

    @Throws(IllegalArgumentException::class)
    suspend fun map(asset: AssetTokenWithFiatLocal): Asset {
        val assetLocal = asset.assetLocal
        requireNotNull(assetLocal)
        return Asset(
            token = map(TokenWithFiatLocal(asset.token, asset.price)),
            balance = AssetBalance(
                assetLocal.free - assetLocal.miscFrozen.max(assetLocal.feeFrozen),
                assetLocal.reserved,
                assetLocal.miscFrozen,
                assetLocal.feeFrozen,
                assetLocal.bonded,
                assetLocal.redeemable,
                assetLocal.unbonding,
            ),
            position = assetLocal.position,
            favorite = assetLocal.displayAsset,
            visibility = assetLocal.visibility,
        )
    }

    suspend fun map(tokenLocal: TokenWithFiatLocal): Token =
        Token(
            id = tokenLocal.token.id,
            name = tokenLocal.token.name,
            symbol = tokenLocal.token.symbol,
            precision = tokenLocal.token.precision,
            isHidable = tokenLocal.token.isHidable,
            iconFile = whitelistTokensManager.getTokenIconUri(tokenLocal.token.id),
            fiatPrice = tokenLocal.price?.fiatPrice,
            fiatPriceChange = tokenLocal.price?.fiatChange,
            fiatSymbol = soraConfigManager.getSelectedCurrency().sign,
        )
}
