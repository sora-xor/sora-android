/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.states

import android.net.Uri
import androidx.annotation.StringRes
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.common.domain.iconUri
import jp.co.soramitsu.common.domain.printFiat
import jp.co.soramitsu.common.domain.printFiatChange
import jp.co.soramitsu.common.util.NumbersFormatter

data class CardsState(
    val curAccount: String,
    val loading: Boolean = false,
    val cards: List<CardState> = emptyList(),
)

sealed interface CardState

sealed interface AssetCardState

data class TitledAmountCardState(
    val amount: String,
    @StringRes val title: Int,
    val state: AssetCardState,
    val collapsedState: Boolean = false,
    val onCollapseClick: () -> Unit,
    val onExpandClick: (() -> Unit)? = null,
) : CardState

class FavoriteAssetsCardState(
    val assets: List<AssetItemCardState>
) : AssetCardState

data class AssetItemCardState(
    val tokenIcon: Uri,
    val tokenId: String,
    val tokenName: String,
    val tokenSymbol: String,
    val assetAmount: String,
    val assetFiatAmount: String,
    val fiatChange: String,
)

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
    val cardInfo: SoraCardInformation? = null,
    val kycStatus: String? = null,
    val visible: Boolean = false
) : CardState

data class BuyXorState(
    val visible: Boolean = false
) : CardState

class FavoritePoolsCardState(
    val state: PoolsListState,
) : AssetCardState
