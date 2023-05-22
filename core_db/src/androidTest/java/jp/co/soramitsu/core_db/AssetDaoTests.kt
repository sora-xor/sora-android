/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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