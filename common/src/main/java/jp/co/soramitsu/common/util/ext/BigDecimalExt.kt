/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.util.ext

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import jp.co.soramitsu.common.domain.OptionsProvider
import kotlin.math.max

val Big100 = BigDecimal.valueOf(100)

fun compareNullDesc(o1: BigDecimal?, o2: BigDecimal?): Int =
    when {
        o1 == null && o2 == null -> 0
        o1 != null && o2 != null -> o2.compareTo(o1)
        o1 == null -> 1
        else -> -1
    }

fun compareNullDesc(o1: Double?, o2: Double?): Int =
    when {
        o1 == null && o2 == null -> 0
        o1 != null && o2 != null -> o2.compareTo(o1)
        o1 == null -> 1
        else -> -1
    }

fun BigInteger.isZero(): Boolean = this.compareTo(BigInteger.ZERO) == 0
fun BigDecimal.isZero(): Boolean = this.compareTo(BigDecimal.ZERO) == 0

fun BigDecimal?.multiplyNullable(decimal: BigDecimal?): BigDecimal? =
    if (this != null && decimal != null) this.multiply(decimal) else null

fun BigDecimal.toDoubleInfinite() = this.toDouble().takeIf { converted ->
    converted != Double.NEGATIVE_INFINITY && converted != Double.POSITIVE_INFINITY
} ?: 0.0

fun BigDecimal.equalTo(a: BigDecimal) = this.compareTo(a) == 0

fun BigDecimal.greaterThan(a: BigDecimal) = this.compareTo(a) == 1

fun BigDecimal?.orZero(): BigDecimal = this ?: BigDecimal.ZERO

fun BigDecimal.nullZero(): BigDecimal? = if (this.isZero()) null else this

fun BigDecimal.divideBy(
    divisor: BigDecimal,
    scale: Int? = null,
): BigDecimal {
    return if (scale == null) {
        val maxScale = max(this.scale(), divisor.scale()).coerceAtMost(OptionsProvider.defaultScale)

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
    scale: Int? = null,
): BigDecimal {
    return if (divisor.isZero()) {
        BigDecimal.ZERO
    } else {
        divideBy(divisor, scale)
    }
}
