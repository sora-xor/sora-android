/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common_wallet.presentation.compose.util

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.Big100
import jp.co.soramitsu.androidfoundation.format.divideBy
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.format.safeDivide
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common_wallet.domain.model.WithDesired

object PolkaswapFormulas {

    fun calculatePooledValue(
        reserves: BigDecimal,
        poolProvidersBalance: BigDecimal,
        totalIssuance: BigDecimal,
        precision: Int? = OptionsProvider.defaultScale,
    ): BigDecimal =
        reserves.multiply(poolProvidersBalance).divideBy(totalIssuance, precision)

    private fun calculateShareOfPool(
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
