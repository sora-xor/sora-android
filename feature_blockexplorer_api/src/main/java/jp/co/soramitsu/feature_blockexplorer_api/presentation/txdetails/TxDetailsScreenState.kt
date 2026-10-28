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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus

val emptyTxDetailsState: TxDetailsScreenState = TxDetailsScreenState(
    basicTxDetailsState = BasicTxDetailsState(
        "",
        "",
        "",
        emptyList(),
        TransactionStatus.COMMITTED,
        "",
        "",
        "",
        R.drawable.ic_empty_state,
        ""
    ),
    "",
    "",
    "",
    DEFAULT_ICON_URI,
    DEFAULT_ICON_URI,
    DEFAULT_ICON_URI,
    true,
    txType = TxType.LIQUIDITY
)

data class TxDetailsScreenState(
    val basicTxDetailsState: BasicTxDetailsState,
    val amount1: String,
    val amount2: String? = null,
    val amountFiat: String,
    val icon1: String,
    val icon2: String? = null,
    val icon3: String? = null,
    val isAmountGreen: Boolean = false,
    val txType: TxType
)

enum class TxType {
    LIQUIDITY,
    REFERRAL_TRANSFER,
    SWAP,
    DEMETER,
}
