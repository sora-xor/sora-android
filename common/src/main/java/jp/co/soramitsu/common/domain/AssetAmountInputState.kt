/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import java.math.BigDecimal

data class AssetAmountInputState(
    val token: Token,
    val balance: String,
    val amount: BigDecimal,
    val initialAmount: BigDecimal? = null,
    val amountFiat: String,
    val enabled: Boolean,
    val readOnly: Boolean = false,
    val error: Boolean = false,
    val errorHint: String = "",
)
