/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common_wallet.data

import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.TokenFiatLocal
import jp.co.soramitsu.core_db.model.TokenWithFiatLocal
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager

@Singleton
class AssetLocalToAssetMapper @Inject constructor(
    private val whitelistTokensManager: WhitelistTokensManager,
    private val soraConfigManager: SoraConfigManager,
) {

    suspend fun map(token: TokenFiatLocal): Token {
        return Token(
            id = token.token.id,
            name = token.token.name,
            symbol = token.token.symbol,
            precision = token.token.precision,
            isHidable = token.token.isHidable,
            iconFile = whitelistTokensManager.getTokenIconUri(token.token.id),
            fiatPrice = token.fiat.fiatPrice,
            fiatPriceChange = token.fiat.fiatChange,
            fiatSymbol = soraConfigManager.getSelectedCurrency().sign,
        )
    }

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
