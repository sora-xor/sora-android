/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common_wallet.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common_wallet.domain.BalanceWrapper
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.TokenFiatLocal
import jp.co.soramitsu.core_db.model.TokenWithFiatLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager

@Singleton
class AssetLocalToAssetMapper @Inject constructor(
    private val whitelistTokensManager: WhitelistTokensManager,
    private val soraConfigManager: SoraConfigManager,
) {

    suspend fun map(token: TokenFiatLocal): Token {
        return Token(
            id = token.token.id,
            name = AssetHolder.displayName(token.token.id, token.token.name),
            symbol = AssetHolder.displaySymbol(token.token.id, token.token.symbol),
            precision = token.token.precision,
            isHidable = token.token.isHidable,
            iconFile = whitelistTokensManager.getTokenIconUri(token.token.id),
            fiatPrice = token.fiat?.fiatPrice,
            fiatPriceChange = token.fiat?.fiatChange,
            fiatSymbol = soraConfigManager.getSelectedCurrency().sign,
        )
    }

    @Throws(IllegalArgumentException::class)
    suspend fun mapNullable(asset: AssetTokenWithFiatLocal?): Asset? {
        if (asset == null) return null
        return map(asset)
    }

    @Throws(IllegalArgumentException::class)
    suspend fun map(asset: AssetTokenWithFiatLocal): Asset? {
        val assetLocal = asset.assetLocal ?: return null
        return Asset(
            token = map(TokenWithFiatLocal(asset.token, asset.price)),
            balance = BalanceWrapper.mapLocalBalance(assetLocal),
            position = assetLocal.position,
            favorite = assetLocal.displayAsset,
            visibility = assetLocal.visibility,
        )
    }

    suspend fun map(tokenLocal: TokenWithFiatLocal): Token =
        Token(
            id = tokenLocal.token.id,
            name = AssetHolder.displayName(tokenLocal.token.id, tokenLocal.token.name),
            symbol = AssetHolder.displaySymbol(tokenLocal.token.id, tokenLocal.token.symbol),
            precision = tokenLocal.token.precision,
            isHidable = tokenLocal.token.isHidable,
            iconFile = whitelistTokensManager.getTokenIconUri(tokenLocal.token.id),
            fiatPrice = tokenLocal.price?.fiatPrice,
            fiatPriceChange = tokenLocal.price?.fiatChange,
            fiatSymbol = soraConfigManager.getSelectedCurrency().sign,
        )
}
