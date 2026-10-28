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

package jp.co.soramitsu.common_wallet.presentation

import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.format.Big100
import jp.co.soramitsu.androidfoundation.format.divideBy
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.format.safeDivide
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkaswapFormulasTest {

    private companion object {
        val BASE_AMOUNT = 437234.3428972311.toBigDecimal()
        val TARGET_AMOUNT = 8972.98129.toBigDecimal()

        val RESERVES_FIRST = 13234.3242311.toBigDecimal()
        val RESERVES_SECOND = 21334.231213.toBigDecimal()
        val SECOND_POOLED = 104563.322.toBigDecimal()

        val TOTAL_ISSUANCE = 32424.324976.toBigDecimal()
        val POOL_PROVIDERS_BALANCE = 324.23.toBigDecimal()

        const val SLIPPAGE_TOLERANCE = 0.5
        const val PRECISION_FIRST = 18
        const val PRECISION_SECOND = 18
    }

    @Test
    fun `test calculatePooledValue`() {
        val pooledValue =
            RESERVES_FIRST.multiply(POOL_PROVIDERS_BALANCE).divideBy(TOTAL_ISSUANCE)

        assertTrue(
            PolkaswapFormulas.calculatePooledValue(
                RESERVES_FIRST,
                POOL_PROVIDERS_BALANCE,
                TOTAL_ISSUANCE
            ).equalTo(BigDecimal("132.337834284157373294"))
        )
    }

    @Test
    fun `test calculateShareOfPool`() {
        val shareOfPool = POOL_PROVIDERS_BALANCE.divideBy(TOTAL_ISSUANCE).multiply(Big100)

        assertEquals(
            shareOfPool.toDouble(),
            PolkaswapFormulas.calculateShareOfPoolFromAmount(
                POOL_PROVIDERS_BALANCE,
                TOTAL_ISSUANCE
            ),
            0.001,
        )
    }

    @Test
    fun `test calculateAddLiquidityAmount with INPUT desired`() {
        val resultAmount =
            BASE_AMOUNT.multiply(RESERVES_SECOND).safeDivide(RESERVES_FIRST, PRECISION_SECOND)

        assertEquals(
            resultAmount,
            PolkaswapFormulas.calculateAddLiquidityAmount(
                BASE_AMOUNT,
                RESERVES_FIRST,
                RESERVES_SECOND,
                PRECISION_FIRST,
                PRECISION_SECOND,
                WithDesired.INPUT,
            )
        )
    }

    @Test
    fun `test calculateAddLiquidityAmount with OUPUT desired`() {
        val resultAmount =
            BASE_AMOUNT.multiply(RESERVES_FIRST).safeDivide(RESERVES_SECOND, PRECISION_FIRST)

        assertEquals(
            resultAmount,
            PolkaswapFormulas.calculateAddLiquidityAmount(
                BASE_AMOUNT,
                RESERVES_FIRST,
                RESERVES_SECOND,
                PRECISION_FIRST,
                PRECISION_SECOND,
                WithDesired.OUTPUT
            )
        )
    }

    @Test
    fun `test estimateAddingShareOfPool`() {
        val shareOfPool = SECOND_POOLED
            .plus(TARGET_AMOUNT)
            .multiply(Big100)
            .safeDivide(TARGET_AMOUNT.plus(RESERVES_SECOND))

        assertEquals(
            shareOfPool,
            PolkaswapFormulas.estimateAddingShareOfPool(
                TARGET_AMOUNT,
                SECOND_POOLED,
                RESERVES_SECOND,
            )
        )
    }

    @Test
    fun `test estimateRemovingShareOfPool`() {
        val shareOfPool = SECOND_POOLED
            .minus(TARGET_AMOUNT)
            .multiply(Big100)
            .safeDivide(RESERVES_SECOND.minus(TARGET_AMOUNT))

        assertEquals(
            shareOfPool,
            PolkaswapFormulas.estimateRemovingShareOfPool(
                TARGET_AMOUNT,
                SECOND_POOLED,
                RESERVES_SECOND,
            )
        )
    }

    @Test
    fun `test estimateRemovingShareOfPool if reserves equals pooled`() {
        val shareOfPool = BigDecimal.ZERO

        assertEquals(
            shareOfPool,
            PolkaswapFormulas.estimateRemovingShareOfPool(
                RESERVES_SECOND,
                RESERVES_SECOND,
                RESERVES_SECOND,
            )
        )
    }

    @Test
    fun `test calculateMinAmount`() {
        val amountMin = BASE_AMOUNT.minus(BASE_AMOUNT.multiply(BigDecimal.valueOf(SLIPPAGE_TOLERANCE / 100)))

        assertEquals(
            amountMin,
            PolkaswapFormulas.calculateMinAmount(
                BASE_AMOUNT,
                SLIPPAGE_TOLERANCE
            )
        )
    }

    @Test
    fun `test calculateTokenPerTokenRate`() {
        val firstPerSecondRate = BASE_AMOUNT.safeDivide(TARGET_AMOUNT)

        assertEquals(
            firstPerSecondRate,
            PolkaswapFormulas.calculateTokenPerTokenRate(
                BASE_AMOUNT,
                TARGET_AMOUNT
            )
        )
    }

    @Test
    fun `test calculateMarkerAssetDesired`() {
        val markerAssetDesired = BASE_AMOUNT.divideBy(RESERVES_FIRST).multiply(TOTAL_ISSUANCE)

        assertEquals(
            markerAssetDesired,
            PolkaswapFormulas.calculateMarkerAssetDesired(
                BASE_AMOUNT,
                RESERVES_FIRST,
                TOTAL_ISSUANCE
            )
        )
    }
}
