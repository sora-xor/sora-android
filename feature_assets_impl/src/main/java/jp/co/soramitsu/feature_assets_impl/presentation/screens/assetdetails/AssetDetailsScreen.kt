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

package jp.co.soramitsu.feature_assets_impl.presentation.screens.assetdetails

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsBalanceCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsPooledCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsRecentActivityCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetDetailsTokenPriceCard
import jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails.AssetIdCard
import jp.co.soramitsu.feature_assets_impl.presentation.states.AssetCardStateData
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun AssetDetailsScreen(
    stateData: AssetCardStateData,
    scrollState: ScrollState,
    onBalanceClick: () -> Unit,
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSwapClick: () -> Unit,
    onBuyCrypto: () -> Unit,
    onPoolClick: (StringPair) -> Unit,
    onRecentClick: () -> Unit,
    onHistoryItemClick: (String) -> Unit,
    onAssetIdClick: () -> Unit,
) {
    if (stateData.xorBalance != null) {
        XorBalancesDialog(
            state = stateData.xorBalance,
            onClick = onBalanceClick,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        AssetDetailsTokenPriceCard(
            tokenName = stateData.tokenName,
            tokenSymbol = stateData.tokenSymbol,
            tokenPrice = stateData.price,
            tokenPriceChange = stateData.priceChange,
            iconUri = stateData.tokenIcon,
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
        AssetDetailsBalanceCard(
            amount = stateData.transferableBalance,
            amountFiat = stateData.transferableBalanceFiat,
            frozenAmount = stateData.frozenBalance,
            frozenAmountFiat = stateData.frozenBalanceFiat,
            buyCryptoAvailable = stateData.buyCryptoAvailable,
            isTransferableAmountAvailable = stateData.isTransferableBalanceAvailable,
            onSendClick = onSendClick,
            onReceiveClick = onReceiveClick,
            onSwapClick = onSwapClick,
            onBalanceClick = onBalanceClick,
            onBuyCryptoClick = onBuyCrypto,
        )
        if (stateData.poolsState.pools.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            AssetDetailsPooledCard(
                title = stateData.poolsCardTitle,
                state = stateData.poolsState,
                onPoolClick = onPoolClick,
            )
        }
        if (stateData.events.isNotEmpty()) {
            Spacer(modifier = Modifier.size(Dimens.x2))
            AssetDetailsRecentActivityCard(
                events = stateData.events,
                onShowMoreActivity = onRecentClick,
                onHistoryItemClick = onHistoryItemClick,
            )
        }
        Spacer(modifier = Modifier.size(Dimens.x2))
        AssetIdCard(
            id = stateData.tokenId,
            onClick = onAssetIdClick,
        )
        Spacer(modifier = Modifier.size(Dimens.x2))
    }
}

@Composable
@Preview
private fun PreviewAssetDetailsScreen01() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AssetDetailsScreen(
            AssetCardStateData(
                "tokenId",
                "tokenName",
                DEFAULT_ICON_URI,
                "XOR",
                "$12.3",
                "+4%",
                "12314324.13",
                "$879.12",
                "123.123",
                "$99.9",
                null,
                "title",
                PoolsListState(
                    listOf(
                        PoolsListItemState(
                            token1Icon = DEFAULT_ICON_URI,
                            token2Icon = DEFAULT_ICON_URI,
                            poolAmounts = "123.456",
                            poolToken1Symbol = "XOR",
                            poolToken2Symbol = "VAL",
                            fiat = "$7908",
                            fiatChange = "+23.1 %",
                            tokenIds = "" to "",
                            rewardTokenIconsList = emptyList()
                        ),
                    )
                ),
                "456",
                true,
                true,
                emptyList(),
                true,
            ),
            rememberScrollState(),
            {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}

@Composable
@Preview
private fun PreviewAssetDetailsScreen02() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        AssetDetailsScreen(
            AssetCardStateData(
                "tokenId",
                "tokenName",
                DEFAULT_ICON_URI,
                "XOR",
                "$12.3",
                "+4%",
                "12314324.13",
                "$879.12",
                "123.123",
                "$99.9",
                null,
                "title",
                PoolsListState(
                    listOf(
                        PoolsListItemState(
                            token1Icon = DEFAULT_ICON_URI,
                            token2Icon = DEFAULT_ICON_URI,
                            poolAmounts = "123.456",
                            poolToken1Symbol = "XOR",
                            poolToken2Symbol = "VAL",
                            fiat = "$7908",
                            fiatChange = "+23.1 %",
                            tokenIds = "" to "",
                            rewardTokenIconsList = emptyList()
                        ),
                    )
                ),
                "456",
                true,
                true,
                listOf(
                    EventUiModel.EventTxUiModel.EventTransferInUiModel(
                        "hash",
                        123123123,
                        TransactionStatus.COMMITTED,
                        DEFAULT_ICON_URI,
                        "address",
                        "12.12.1970",
                        "123.456",
                        "$78.23",
                    )
                ),
                true,
            ),
            rememberScrollState(),
            {}, {}, {}, {}, {}, {}, {}, {}, {}
        )
    }
}
