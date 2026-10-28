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

package jp.co.soramitsu.feature_assets_impl.presentation.states

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
    val tokenIcon: String,
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
    /** Names the XOR that is free on the chain and locked; null when nothing is locked. */
    val lockedNote: String? = null,
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
)
