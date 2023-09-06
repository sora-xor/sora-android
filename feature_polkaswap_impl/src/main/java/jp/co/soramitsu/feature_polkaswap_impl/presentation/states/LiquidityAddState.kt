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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.states

import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.presentation.compose.states.ButtonState

data class LiquidityAddState(
    val btnState: ButtonState,
    val assetState1: AssetAmountInputState?,
    val assetState2: AssetAmountInputState?,
    val estimated: LiquidityAddEstimatedState,
    val prices: LiquidityAddPricesState,
    val confirm: LiquidityAddConfirmState,
    val pairNotExist: Boolean? = null,
    val hintVisible: Boolean,
    val shouldTransactionReminderInsufficientWarningBeShown: Boolean,
)

data class LiquidityAddConfirmState(
    val text: String,
    val confirmResult: Boolean?,
    val btnState: ButtonState,
)

data class LiquidityAddEstimatedState(
    val token1: String,
    val token1Value: String,
    val token2: String,
    val token2Value: String,
    val shareOfPool: String,
)

data class LiquidityAddPricesState(
    val pair1: String,
    val pair1Value: String,
    val pair2: String,
    val pair2Value: String,
    val apy: String? = null,
    val fee: String,
)
