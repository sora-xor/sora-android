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

package jp.co.soramitsu.feature_assets_impl.data

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.shared_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.math.BigDecimal
import java.math.BigInteger

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AssetsRepositoryTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var assetDao: AssetDao

    @Mock
    private lateinit var extrinsicManager: ExtrinsicManager

    @Mock
    private lateinit var substrateCalls: SubstrateCalls

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var whitelistTokensManager: WhitelistTokensManager

    @Mock
    private lateinit var soraConfigManager: SoraConfigManager

    private val mockedUri = Mockito.mock(Uri::class.java)

    private lateinit var assetsRepository: AssetsRepository

    private val soraAccount = SoraAccount("a", "n")

    @Before
    fun setUp() = runTest {
        BDDMockito.given(db.assetDao()).willReturn(assetDao)
        BDDMockito.given(whitelistTokensManager.getTokenIconUri(BDDMockito.anyString()))
            .willReturn(mockedUri)
        BDDMockito.given(soraConfigManager.getSelectedCurrency()).willReturn(usdFiat)
        BDDMockito.given(coroutineManager.applicationScope).willReturn(this)
        BDDMockito.given(soraConfigManager.getSelectedCurrency()).willReturn(usdFiat)
        BDDMockito.given(assetDao.getTokensWithFiatOfCurrency(usdFiat.code)).willReturn(emptyList())
        BDDMockito.given(assetDao.insertTokenList(any())).willReturn(Unit)
        BDDMockito.given(assetDao.updateTokenList(any())).willReturn(Unit)
        BDDMockito.given(substrateCalls.fetchAssetsList()).willReturn(emptyList())
        assetsRepository = AssetsRepositoryImpl(
            db = db,
            assetLocalToAssetMapper = AssetLocalToAssetMapper(
                whitelistTokensManager,
                soraConfigManager
            ),
            extrinsicManager = extrinsicManager,
            substrateCalls = substrateCalls,
            soraConfigManager = soraConfigManager,
            coroutineManager = coroutineManager,
            whitelistTokensManager
        )
    }

    @Test
    fun `get assets`() = runTest {
        BDDMockito.given(
            assetDao.getAssetsFavorite(
                BDDMockito.anyString(),
                BDDMockito.anyString(),
                BDDMockito.anyString()
            )
        ).willReturn(
            assetTokenList()
        )
        val assets = assetsRepository.getAssetsFavorite("address")
        val expected = assetList().subList(0, 2)
        Assert.assertEquals(expected.size, assets.size)
        repeat(2) {
            Assert.assertEquals(expected[it], assets[it])
        }
    }

    @Test
    fun `calc fee`() = runTest {
        BDDMockito.given(
            extrinsicManager.calcFee(BDDMockito.anyString(), BDDMockito.anyBoolean(), any())
        ).willReturn(BigInteger("1000000000000000000"))
        val fee = assetsRepository.calcTransactionFee(
            "from",
            "to",
            TestTokens.xorToken,
            BigDecimal.ONE
        )
        Assert.assertEquals(BigDecimal("1"), fee)
    }

    @Test
    fun `observe transfer`() = runTest {
        val key = Sr25519Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4), byteArrayOf(5, 6))
        BDDMockito.given(
            extrinsicManager.submitAndWatchExtrinsic(
                BDDMockito.anyString(),
                any(),
                BDDMockito.anyBoolean(),
                any()
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "txhash", "blockhash")
        )
        val result = assetsRepository.observeTransfer(
            key,
            "from",
            "to",
            TestTokens.xorToken,
            BigDecimal.ONE,
            BigDecimal.ZERO
        )
        Assert.assertEquals(true, result.success)
    }

    @Test
    fun `display assets`() = runTest {
        assetsRepository.displayAssets(listOf(), soraAccount)
    }

    @Test
    fun `hide assets`() = runTest {
        assetsRepository.hideAssets(listOf(), soraAccount)
    }

    private fun assetTokenList() = listOf(
        AssetTokenWithFiatLocal(
            oneTokenLocal(), null, assetLocalList()[0]
        ),
        AssetTokenWithFiatLocal(
            oneTokenLocal2(), null, assetLocalList()[1]
        )
    )

    private val usdFiat = SoraCurrency(
        code = "USD",
        name = "Dollar",
        sign = "$",
    )

    private fun assetLocalList() = listOf(
        AssetLocal(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "someaddress",
            true,
            1,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
        ),
        AssetLocal(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "someaddress",
            true,
            2,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            true,
        ),
    )

    private fun assetList() = listOf(
        Asset(
            oneToken(),
            true,
            1,
            TestAssets.balance(BigDecimal.ZERO),
            true,
        ),
        Asset(
            oneToken2(),
            true,
            2,
            TestAssets.balance(BigDecimal.ZERO),
            true,
        ),
        Asset(
            oneToken3(),
            true,
            3,
            TestAssets.balance(BigDecimal.ZERO),
            true,
        ),
        Asset(
            oneToken4(),
            true,
            4,
            TestAssets.balance(BigDecimal.ZERO),
            true,
        )
    )

    private fun oneToken() = Token(
        "0x0200000000000000000000000000000000000000000000000000000000000000",
        "SORA",
        "XOR",
        18,
        false,
        mockedUri,
        null,
        null,
        "$",
    )

    private fun oneTokenLocal() =
        TokenLocal(
            "0x0200000000000000000000000000000000000000000000000000000000000000",
            "SORA",
            "XOR",
            18,
            true,
            "whitelist",
            false
        )

    private fun oneToken2() = Token(
        "0x0200040000000000000000000000000000000000000000000000000000000000",
        "SORA Validator Token",
        "VAL",
        18,
        true,
        mockedUri,
        null,
        null,
        "$",
    )

    private fun oneTokenLocal2() =
        TokenLocal(
            "0x0200040000000000000000000000000000000000000000000000000000000000",
            "SORA Validator Token",
            "VAL",
            18,
            true,
            "whitelist",
            true
        )

    private fun oneToken3() = Token(
        "0x0200050000000000000000000000000000000000000000000000000000000000",
        "Polkaswap",
        "PSWAP",
        18,
        true,
        mockedUri,
        null,
        null,
        "$",
    )

    private fun oneToken4() = Token(
        "0x0200080000000000000000000000000000000000000000000000000000000000",
        "SORA Synthetic USD",
        "XSTUSD",
        18,
        true,
        mockedUri,
        null,
        null,
        "$",
    )
}
