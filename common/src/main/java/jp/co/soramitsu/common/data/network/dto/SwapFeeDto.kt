/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network.dto

import androidx.annotation.Keep
import java.math.BigInteger

@Keep
data class SwapFeeDto(
    val amount: BigInteger,
    val fee: BigInteger,
)
