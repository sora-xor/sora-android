/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_assets_impl.presentation.states

import android.net.Uri
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common_wallet.presentation.compose.states.PoolsListState
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel

internal data class AssetCardState(
    val loading: Boolean,
    val state: AssetCardStateData,
)

internal data class AssetCardStateData(
    val tokenId: String,
    val tokenName: String,
    val tokenIcon: Uri,
    val tokenSymbol: String,
    val price: String,
    val priceChange: String,
    val transferableBalance: String,
    val transferableBalanceFiat: String,
    val frozenBalance: String? = null,
    val frozenBalanceFiat: String? = null,
    val xorBalance: FrozenXorDetailsModel? = null,
    val poolsCardTitle: String,
    val poolsState: PoolsListState,
    val poolsSum: String,
    val isTransferableBalanceAvailable: Boolean = false,
    val hasTokens: Boolean = false,
    val events: List<EventUiModel>,
    val buyCryptoAvailable: Boolean,
)

internal val emptyAssetCardState = AssetCardStateData(
    tokenId = "",
    tokenName = "",
    tokenIcon = DEFAULT_ICON_URI,
    tokenSymbol = "",
    price = "",
    priceChange = "",
    transferableBalance = "",
    transferableBalanceFiat = "",
    xorBalance = null,
    poolsCardTitle = "",
    poolsState = PoolsListState(emptyList()),
    poolsSum = "",
    events = emptyList(),
    buyCryptoAvailable = false,
)
