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

import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge

data class LiquidityRemoveState(
    val btnState: ButtonState,
    val slippage: Double,
    val assetState1: AssetAmountInputState?,
    val assetState2: AssetAmountInputState?,
    val estimated: LiquidityRemoveEstimatedState,
    val prices: LiquidityRemovePricesState,
    val confirm: LiquidityRemoveConfirmState,
    val hintVisible: Boolean,
    val shouldTransactionReminderInsufficientWarningBeShown: Boolean,
    val poolInFarming: Boolean,
)

data class LiquidityRemoveConfirmState(
    val text: String,
    /** Result mark shown briefly after Confirm; taken from the transaction outcome. */
    val confirmResult: TransactionResultBadge?,
    val btnState: ButtonState,
)

data class LiquidityRemoveEstimatedState(
    val token1: String,
    val token1Value: String,
    val token2: String,
    val token2Value: String,
    val shareOfPool: String,
)

data class LiquidityRemovePricesState(
    val pair1: String,
    val pair1Value: String,
    val pair2: String,
    val pair2Value: String,
    val apy: String? = null,
    val fee: String,
)
