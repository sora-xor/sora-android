/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.util

import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import java.math.BigDecimal

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
        poolProvidersBalance.divideBy(totalIssuance).multiply(BigDecimal(100))

    fun calculateShareOfPoolFromAmount(
        amount: BigDecimal,
        amountPooled: BigDecimal
    ): BigDecimal =
        amount.divideBy(amountPooled).multiply(BigDecimal(100))

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
            .multiply(BigDecimal.valueOf(100))
    }

    fun estimateRemovingShareOfPool(
        amount: BigDecimal,
        pooled: BigDecimal,
        reserves: BigDecimal
    ): BigDecimal = pooled
        .minus(amount)
        .safeDivide(reserves.minus(amount))
        .multiply(BigDecimal.valueOf(100))

    fun calculateMinAmount(
        amount: BigDecimal,
        slippageTolerance: Double
    ): BigDecimal {
        return amount.minus(amount.multiply(BigDecimal.valueOf(slippageTolerance / 100)))
    }

    fun calculateTokenPerTokenRate(
        amount1: BigDecimal,
        amount2: BigDecimal
    ): BigDecimal {
        return amount1.safeDivide(amount2)
    }

    fun calculateMarkerAssetDesired(
        fromAmount: BigDecimal,
        firstReserves: BigDecimal,
        totalIssuance: BigDecimal
    ): BigDecimal = fromAmount.safeDivide(firstReserves).multiply(totalIssuance)

    fun calculateStrategicBonusAPY(
        strategicBonusApy: BigDecimal?
    ): BigDecimal? {
        return strategicBonusApy?.multiply(BigDecimal(100))
    }

    fun calculateAmountByPercentage(
        amount: BigDecimal,
        percentage: Int,
        precision: Int
    ): BigDecimal {
        return amount.safeDivide(BigDecimal(100), precision).multiply(percentage.toBigDecimal())
    }

    fun calculateOneAmountFromAnother(
        amount: BigDecimal,
        amountPooled: BigDecimal,
        otherPooled: BigDecimal
    ): BigDecimal = amount.multiply(otherPooled).safeDivide(amountPooled)
}
