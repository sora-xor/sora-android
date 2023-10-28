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

package jp.co.soramitsu.common_wallet.presentation.compose.states

import androidx.annotation.StringRes
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.formatFiatOrEmpty
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.printFiatChange
import jp.co.soramitsu.common.util.NumbersFormatter

data class CardsState(
    val accountAddress: String,
    val curAccount: String,
    val loading: Boolean = false,
    val cards: List<CardState> = emptyList(),
)

sealed class CardState(
    open val loading: Boolean,
)

sealed class BasicBannerCardState(
    override val loading: Boolean,
) : CardState(loading)

sealed interface AssetCardState

data class TitledAmountCardState(
    val amount: String,
    @StringRes val title: Int,
    val state: AssetCardState,
    val collapsedState: Boolean = false,
    val onCollapseClick: () -> Unit,
    override val loading: Boolean,
) : CardState(loading)

class FavoriteAssetsCardState(
    val assets: List<AssetItemCardState>
) : AssetCardState

data class AssetItemCardState(
    val tokenIcon: String,
    val tokenId: String,
    val tokenName: String,
    val tokenSymbol: String,
    val assetAmount: String,
    val assetFiatAmount: String,
    val fiatChange: String,
)

fun mapTokensToCardState(
    tokens: List<Pair<Token, BigDecimal?>>,
    nf: NumbersFormatter,
): List<AssetItemCardState> {
    return tokens.mapIndexed { index, pair ->
        AssetItemCardState(
            tokenIcon = pair.first.iconUri(),
            tokenName = pair.first.name,
            tokenId = pair.first.id,
            assetAmount = pair.first.printFiat(pair.second?.formatFiatSuffix()).orEmpty(),
            tokenSymbol = "",
            assetFiatAmount = pair.first.formatFiatOrEmpty(pair.first.fiatPrice, nf, true),
            fiatChange = pair.first.printFiatChange(nf),
        )
    }
}

fun mapAssetsToCardState(
    assets: List<Asset>,
    nf: NumbersFormatter,
    precision: Int = AssetHolder.ROUNDING
): List<AssetItemCardState> {
    return assets.map {
        AssetItemCardState(
            tokenIcon = it.token.iconUri(),
            tokenName = it.token.name,
            tokenId = it.token.id,
            assetAmount = it.token.printBalance(
                it.balance.transferable,
                nf,
                precision
            ),
            tokenSymbol = it.token.symbol,
            assetFiatAmount = it.printFiat(nf),
            fiatChange = it.token.printFiatChange(nf),
        )
    }
}

data class SoraCardState(
    val success: Boolean,
    val needUpdate: Boolean,
    val ibanBalance: String?,
    val kycStatus: String?,
    override val loading: Boolean,
) : BasicBannerCardState(loading)

data object BuyXorState : BasicBannerCardState(false)

data object ReferralState : BasicBannerCardState(false)

data object BackupWalletState : BasicBannerCardState(false)

class FavoritePoolsCardState(
    val state: PoolsListState,
) : AssetCardState

val assetItemCardStateEmpty =
    AssetItemCardState(
        tokenName = "",
        tokenId = "",
        tokenIcon = DEFAULT_ICON_URI,
        assetAmount = "",
        tokenSymbol = "",
        assetFiatAmount = "",
        fiatChange = "",
    )

val previewAssetItemCardStateList = listOf(
    AssetItemCardState(
        tokenName = "some qwe",
        tokenId = "id 01",
        tokenIcon = DEFAULT_ICON_URI,
        assetAmount = "13.3 XOR",
        tokenSymbol = "XOR",
        assetFiatAmount = "$45.9",
        fiatChange = "+34%",
    ),
    AssetItemCardState(
        tokenName = "some asd",
        tokenId = "id 01",
        tokenIcon = DEFAULT_ICON_URI,
        assetAmount = "1238.3 VAL",
        tokenSymbol = "VAL",
        assetFiatAmount = "$0.09",
        fiatChange = "-0.12%",
    ),
)
