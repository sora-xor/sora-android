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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import jp.co.soramitsu.common_wallet.presentation.compose.BasicFarmListItemState
import jp.co.soramitsu.common_wallet.presentation.compose.BasicUserFarmListItemState

internal data class PoolDetailsState(
    val token1Icon: String,
    val token2Icon: String,
    val rewardsUri: String,
    val rewardsTokenSymbol: String,
    val symbol1: String,
    val symbol2: String,
    val apy: String,
    val pooled1: String?,
    val pooled2: String?,
    val tvl: String,
    val addEnabled: Boolean,
    val removeEnabled: Boolean,
    val userPoolSharePercent: String?,
    val demeterPools: List<BasicUserFarmListItemState>?,
    val availableDemeterFarms: List<BasicFarmListItemState>? = emptyList(),
    val demeter100Percent: Boolean,
)
