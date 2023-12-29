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

package jp.co.soramitsu.common_wallet.presentation

import java.math.BigDecimal
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.equalTo
import jp.co.soramitsu.common.util.ext.safeDivide
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
