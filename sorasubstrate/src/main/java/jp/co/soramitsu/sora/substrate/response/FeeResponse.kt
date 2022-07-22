/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

import androidx.annotation.Keep
import java.math.BigInteger

@Keep
data class FeeResponse(
    val partialFee: BigInteger,
    val weight: Long
)
