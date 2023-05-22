/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.domain

import android.os.Parcelable
import java.math.BigDecimal
import kotlinx.parcelize.Parcelize

@Parcelize
data class LiquidityDetails(
    val baseAmount: BigDecimal,
    val targetAmount: BigDecimal,
    val perFirst: BigDecimal,
    val perSecond: BigDecimal,
    val networkFee: BigDecimal,
    val shareOfPool: BigDecimal,
    val pairPresented: Boolean,
    val pairEnabled: Boolean
) : Parcelable
