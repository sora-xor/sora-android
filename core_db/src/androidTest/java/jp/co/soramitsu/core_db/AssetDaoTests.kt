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

package jp.co.soramitsu.core_db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AssetDaoTests {

    private lateinit var db: AppDatabase
    private lateinit var dao: AssetDao

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        dao = db.assetDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testdap() = runTest {
        insertFiat()
        val list = dao.getAssetsWhitelist("address", "usd", whitelist)
        assertEquals(3, list.size)
    }

    private suspend fun insertFiat() {
//        dao.insertFiatLocal(
//            FiatCurrencyLocal(
//                isoCode = "usd",
//                currencyName = "dollar",
//                currencySign = "$",
//                selected = true,
//            )
//        )
        dao.insertTokenList(
            listOf(
                map(TestTokens.xorToken),
                map(TestTokens.valToken),
                map(TestTokens.pswapToken),
            )
        )
        dao.insertFiatPrice(
            listOf(
                FiatTokenPriceLocal(
                    tokenIdFiat = TestTokens.xorToken.id,
                    "usd",
                    fiatPrice = 1.0,
                    fiatPriceTime = 123L,
                    fiatPricePrevH = 1.2,
                    fiatPricePrevHTime = 122L,
                    fiatPricePrevD = 1.1,
                    fiatPricePrevDTime = 120L,
                    fiatChange = 0.12,
                ),
                FiatTokenPriceLocal(
                    tokenIdFiat = TestTokens.valToken.id,
                    "usd",
                    fiatPrice = 1.0,
                    fiatPriceTime = 123L,
                    fiatPricePrevH = 1.2,
                    fiatPricePrevHTime = 122L,
                    fiatPricePrevD = 1.1,
                    fiatPricePrevDTime = 120L,
                    fiatChange = 0.12,
                ),
                FiatTokenPriceLocal(
                    tokenIdFiat = TestTokens.pswapToken.id,
                    "usd",
                    fiatPrice = 1.0,
                    fiatPriceTime = 123L,
                    fiatPricePrevH = 1.2,
                    fiatPricePrevHTime = 122L,
                    fiatPricePrevD = 1.1,
                    fiatPricePrevDTime = 120L,
                    fiatChange = 0.12,
                ),
            )
        )
    }

    private val whitelist = "whitelist"

    private fun map(t: Token): TokenLocal {
        return TokenLocal(
            id = t.id,
            name = t.name,
            symbol = t.symbol,
            precision = t.precision,
            isMintable = false,
            whitelistName = whitelist,
            isHidable = t.isHidable,
        )
    }
}
