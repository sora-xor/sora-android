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

package jp.co.soramitsu.common_wallet.presentation.compose.states

import androidx.annotation.StringRes
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.formatFiatSuffix
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.formatFiatOrEmpty
import jp.co.soramitsu.common.domain.frozenTotal
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.printFiatChange
import jp.co.soramitsu.common.util.NumbersFormatter

data class CardsState(
    val accountAddress: String,
    val cards: List<CardState> = emptyList(),
    /** Accounts listed in the app, and how many of them can sign on this phone (0: not read yet). */
    val accountCount: Int = 0,
    val signingAccountCount: Int = 0,
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

data class AssetItemCardState(
    val tokenIcon: String,
    val tokenId: String,
    val tokenName: String,
    val tokenSymbol: String,
    val assetAmount: String,
    val assetFiatAmount: String,
    val fiatChange: String,
    /**
     * What the account holds of this asset and cannot send now (reserved, bonded and locked),
     * formatted with its symbol; null when there is nothing. [assetAmount] is the liquid part
     * only, so an account whose XOR is all locked shows "0 XOR" there, and this says where the
     * rest is.
     */
    val frozenAmount: String? = null,
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
            frozenAmount = it.balance.frozenTotal
                .takeIf { frozen -> frozen > BigDecimal.ZERO }
                ?.let { frozen -> it.token.printBalance(frozen, nf, precision) },
        )
    }
}

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
