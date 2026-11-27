/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
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

package jp.co.soramitsu.common_wallet.presentation.compose.states

import java.math.BigDecimal
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.UserPoolData
import jp.co.soramitsu.test_data.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PoolsListStateTest {

    private fun pool(base: Token, target: Token, basePooled: String, targetPooled: String) = CommonUserPoolData(
        basic = BasicPoolData(base, target, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, "reserve", null),
        user = UserPoolData(BigDecimal(basePooled), BigDecimal(targetPooled), 1.0, BigDecimal.ONE, false, 0),
    )

    private fun priced(token: Token, price: Double?) = token.copy(fiatPrice = price, fiatPriceChange = 0.0, fiatSymbol = "$")

    @Test
    fun `a pool worth less than a cent shows less than a cent under its amounts, never zero`() {
        // $0.004 + $0.001
        val dust = pool(priced(TestAssets.xorAsset().token, 0.004), priced(TestAssets.valAsset().token, 0.001), "1", "1")
        val (state, sum) = mapPoolsData(listOf(dust), NumbersFormatter())
        assertEquals("<$0.01", state.pools.single().fiat)
        assertEquals(0.005, sum!!, 1e-12)
    }

    @Test
    fun `pools none of which has a price sum to no value, never to zero`() {
        val unpriced = pool(priced(TestAssets.xorAsset().token, 2.0), priced(TestAssets.pswapAsset().token, null), "1", "3")
        val alsoUnpriced = pool(priced(TestAssets.valAsset().token, null), priced(TestAssets.pswapAsset().token, null), "2", "5")
        val (state, sum) = mapPoolsData(listOf(unpriced, alsoUnpriced), NumbersFormatter())
        assertNull("no pool has a value: the list's total is no value", sum)
        assertEquals(listOf("", ""), state.pools.map { it.fiat })
    }

    @Test
    fun `a priced pool beside unpriced ones sums to its own value, and no pools sum to zero`() {
        val pricedPool = pool(priced(TestAssets.xorAsset().token, 2.0), priced(TestAssets.valAsset().token, 1.0), "1", "3")
        val unpricedPool = pool(priced(TestAssets.xorAsset().token, 2.0), priced(TestAssets.pswapAsset().token, null), "1", "3")
        assertEquals(5.0, mapPoolsData(listOf(unpricedPool, pricedPool), NumbersFormatter()).second!!, 1e-12)
        assertEquals(0.0, mapPoolsData(emptyList(), NumbersFormatter()).second!!, 0.0)
    }

    @Test
    fun `a priced pool shows its value and an unpriced one shows none`() {
        val pricedPool = pool(priced(TestAssets.xorAsset().token, 2.0), priced(TestAssets.valAsset().token, 1.0), "1", "3")
        val unpricedPool = pool(priced(TestAssets.xorAsset().token, 2.0), priced(TestAssets.pswapAsset().token, null), "1", "3")
        val (state, _) = mapPoolsData(listOf(pricedPool, unpricedPool), NumbersFormatter())
        assertEquals("$5", state.pools[0].fiat)
        assertEquals("", state.pools[1].fiat)
    }
}
