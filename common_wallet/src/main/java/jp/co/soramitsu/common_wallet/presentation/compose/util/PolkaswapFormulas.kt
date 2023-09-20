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

package jp.co.soramitsu.common_wallet.presentation.compose.util

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.equalTo
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.common_wallet.domain.model.WithDesired

object PolkaswapFormulas {

    fun calculatePooledValue(
        reserves: BigDecimal,
        poolProvidersBalance: BigDecimal,
        totalIssuance: BigDecimal
    ): BigDecimal =
        reserves.multiply(poolProvidersBalance).divideBy(totalIssuance)

    fun calculateShareOfPool(
        poolProvidersBalance: BigDecimal,
        totalIssuance: BigDecimal
    ): BigDecimal =
        poolProvidersBalance.divideBy(totalIssuance).multiply(Big100)

    fun calculateShareOfPoolFromAmount(
        amount: BigDecimal,
        amountPooled: BigDecimal,
    ): Double = if (amount.equalTo(amountPooled)) 100.0 else
        calculateShareOfPool(amount, amountPooled).toDouble()

    fun calculateAddLiquidityAmount(
        baseAmount: BigDecimal,
        reservesFirst: BigDecimal,
        reservesSecond: BigDecimal,
        precisionFirst: Int,
        precisionSecond: Int,
        desired: WithDesired,
    ): BigDecimal {
        return if (desired == WithDesired.INPUT) {
            baseAmount.multiply(reservesSecond).safeDivide(reservesFirst, precisionSecond)
        } else {
            baseAmount.multiply(reservesFirst).safeDivide(reservesSecond, precisionFirst)
        }
    }

    fun estimateAddingShareOfPool(
        amount: BigDecimal,
        pooled: BigDecimal,
        reserves: BigDecimal
    ): BigDecimal {
        return pooled
            .plus(amount)
            .multiply(Big100)
            .safeDivide(amount.plus(reserves))
    }

    fun estimateRemovingShareOfPool(
        amount: BigDecimal,
        pooled: BigDecimal,
        reserves: BigDecimal
    ): BigDecimal = pooled
        .minus(amount)
        .multiply(Big100)
        .safeDivide(reserves.minus(amount))

    fun calculateMinAmount(
        amount: BigDecimal,
        slippageTolerance: Double,
    ): BigDecimal {
        return amount.minus(amount.multiply(BigDecimal.valueOf(slippageTolerance / 100)))
    }

    fun calculateTokenPerTokenRate(
        amount1: BigDecimal,
        amount2: BigDecimal,
    ): BigDecimal {
        return amount1.safeDivide(amount2)
    }

    fun calculateMarkerAssetDesired(
        fromAmount: BigDecimal,
        firstReserves: BigDecimal,
        totalIssuance: BigDecimal,
    ): BigDecimal = fromAmount.safeDivide(firstReserves).multiply(totalIssuance)

    fun calculateStrategicBonusAPY(
        strategicBonusApy: Double?
    ): Double? {
        return strategicBonusApy?.times(100)
    }

    fun calculateAmountByPercentage(
        amount: BigDecimal,
        percentage: Double,
        precision: Int,
    ): BigDecimal = if (percentage == 100.0) amount else
        amount.multiply(percentage.toBigDecimal())
            .safeDivide(Big100, precision)

    fun calculateOneAmountFromAnother(
        amount: BigDecimal,
        amountPooled: BigDecimal,
        otherPooled: BigDecimal,
        precision: Int? = OptionsProvider.defaultScale,
    ): BigDecimal = amount.multiply(otherPooled).safeDivide(amountPooled, precision)
}
