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

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.domain.AssetAmountInputState
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.SearchTokenFilter
import jp.co.soramitsu.feature_assets_api.presentation.selectsearchtoken.emptySearchTokenFilter

internal data class SendState(
    val address: String,
    val icon: Drawable,
    val inProgress: Boolean = false,
    val reviewEnabled: Boolean = false,
    val input: AssetAmountInputState? = null,
    val fee: String = "",
    val feeFiat: String = "",
    val feeLoading: Boolean = true,
    /** Set when the fee preview failed; the screen shows why and offers Try again. */
    val feeUnavailable: NetworkFeeUnavailable? = null,
    val searchFilter: SearchTokenFilter = emptySearchTokenFilter,
    val shouldTransactionReminderInsufficientWarningBeShown: Boolean = false,
    /**
     * Set while part of the selected token's balance is locked on chain (XOR held by staking,
     * voting or vesting): says how much, so a balance smaller than the total is understood.
     */
    val lockedNote: String? = null,
)
