/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation

import jp.co.soramitsu.common.R

data class AssetBalanceData(
    val amount: String,
    val ticker: String? = null,
    val style: AssetBalanceStyle
)

data class AssetBalanceStyle(
    val intStyle: Int,
    val decStyle: Int,
    val color: Int = R.attr.balanceColorDefault,
    val tickerStyle: Int? = null,
    val tickerColor: Int? = null,
)
