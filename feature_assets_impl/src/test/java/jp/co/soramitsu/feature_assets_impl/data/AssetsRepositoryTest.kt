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

package jp.co.soramitsu.feature_assets_impl.data

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.WhitelistTokensManager
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetTokenWithFiatLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraCurrency
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
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
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any

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

    private val mockedUri = DEFAULT_ICON_URI

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
            assetDao.getAssetsActive(
                BDDMockito.anyString(),
                BDDMockito.anyString(),
                BDDMockito.anyString()
            )
        ).willReturn(
            assetTokenList()
        )
        val assets = assetsRepository.getAssetsActive("address")
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
            extrinsicManager.submitFeeQualifiedAndWatchExtrinsic(
                BDDMockito.anyString(),
                any(),
                any(),
                BDDMockito.anyBoolean(),
                any(),
                any(),
                any(),
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
            BigDecimal.ONE,
            validateSelectedWallet = {},
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

    // Stored as "SORA" by earlier versions (oneTokenLocal); the mapper shows native XOR by its
    // symbol (AssetHolder.displayName, modernize 2e49467a).
    private fun oneToken() = Token(
        "0x0200000000000000000000000000000000000000000000000000000000000000",
        "XOR",
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
