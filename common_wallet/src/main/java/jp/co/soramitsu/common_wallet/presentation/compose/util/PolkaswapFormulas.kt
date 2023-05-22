/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common_wallet.presentation.compose.util

import java.math.BigDecimal
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.sora.substrate.models.WithDesired

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
    ): Double =
        amount.divideBy(amountPooled).multiply(Big100).toDouble()

    fun calculateAddLiquidityAmount(
        baseAmount: BigDecimal,
        reservesFirst: BigDecimal,
        reservesSecond: BigDecimal,
        precisionFirst: Int,
        precisionSecond: Int,
        desired: WithDesired
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
            .safeDivide(amount.plus(reserves))
            .multiply(Big100)
    }

    fun estimateRemovingShareOfPool(
        amount: BigDecimal,
        pooled: BigDecimal,
        reserves: BigDecimal
    ): BigDecimal = pooled
        .minus(amount)
        .safeDivide(reserves.minus(amount))
        .multiply(Big100)

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
    ): BigDecimal {
        return if (percentage == 100.0) amount else amount.safeDivide(Big100, precision).multiply(percentage.toBigDecimal())
    }

    fun calculateOneAmountFromAnother(
        amount: BigDecimal,
        amountPooled: BigDecimal,
        otherPooled: BigDecimal,
    ): BigDecimal = amount.multiply(otherPooled).safeDivide(amountPooled)
}
