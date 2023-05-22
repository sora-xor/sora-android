/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import java.math.BigDecimal
import java.math.RoundingMode
import jp.co.soramitsu.common.domain.OptionsProvider
import kotlin.math.max

val Big100 = BigDecimal.valueOf(100)

fun BigDecimal.isZero(): Boolean = this.compareTo(BigDecimal.ZERO) == 0

fun BigDecimal.equalTo(a: BigDecimal) = this.compareTo(a) == 0

fun BigDecimal.greaterThan(a: BigDecimal) = this.compareTo(a) == 1

fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO

fun BigDecimal.nullZero(): BigDecimal? = if (this.isZero()) null else this

fun BigDecimal.divideBy(
    divisor: BigDecimal,
    scale: Int? = null
): BigDecimal {
    return if (scale == null) {
        val maxScale = max(this.scale(), divisor.scale())

        if (maxScale != 0) {
            this.divide(divisor, maxScale, RoundingMode.HALF_EVEN)
        } else {
            this.divide(divisor, OptionsProvider.defaultScale, RoundingMode.HALF_EVEN)
        }
    } else {
        this.divide(divisor, scale, RoundingMode.HALF_EVEN)
    }
}

fun BigDecimal.safeDivide(
    divisor: BigDecimal,
    scale: Int? = null
): BigDecimal {
    return if (divisor.isZero()) {
        BigDecimal.ZERO
    } else {
        divideBy(divisor, scale)
    }
}
