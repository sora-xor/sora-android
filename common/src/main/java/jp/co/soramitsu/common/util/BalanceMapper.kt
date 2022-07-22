/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow

fun mapBalance(
    bigInteger: BigInteger,
    precision: Int,
): BigDecimal =
    bigInteger.toBigDecimal().divide(BigDecimal(10.0.pow(precision)))

fun mapBalance(balance: BigDecimal, precision: Int): BigInteger =
    balance.multiply(BigDecimal(10.0.pow(precision))).toBigInteger()
