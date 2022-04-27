/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation

import jp.co.soramitsu.common.R

data class FiatBalanceData(
    val amount: String,
    val symbol: String? = null,
    val style: FiatBalanceStyle
)

data class FiatBalanceStyle(
    val intStyle: Int,
    val decStyle: Int,
    val color: Int = R.attr.fiatBalanceColorDefault,
    val symbolColor: Int? = null
)
