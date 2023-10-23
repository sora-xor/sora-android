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

package jp.co.soramitsu.feature_wallet_impl.data.repository

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetBalance
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.GlobalCardsHubDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.TestData
import jp.co.soramitsu.feature_wallet_impl.data.repository.datasource.PrefsWalletDatasource
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.TestRuntimeProvider
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyBoolean
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import java.math.BigDecimal

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class WalletRepositoryTest {

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var datasource: PrefsWalletDatasource

    @Mock
    private lateinit var db: AppDatabase

    @Mock
    private lateinit var globalCardsHubDao: GlobalCardsHubDao

    @Mock
    private lateinit var extrinsicManager: ExtrinsicManager

    @Mock
    private lateinit var substrateCalls: SubstrateCalls

    @Mock
    private lateinit var runtimeManager: RuntimeManager

    @Mock
    private lateinit var soraConfigManager: SoraConfigManager

    private val mockedUri = DEFAULT_ICON_URI

    private lateinit var runtime: RuntimeSnapshot

    private lateinit var walletRepository: WalletRepository

    @Before
    fun setUp() = runTest {
        runtime = TestRuntimeProvider.buildRuntime("sora2")
        given(db.globalCardsHubDao()).willReturn(globalCardsHubDao)
        walletRepository = WalletRepositoryImpl(
            datasource,
            db,
            extrinsicManager,
            substrateCalls,
            runtimeManager,
            soraConfigManager,
        )
    }

    @Test
    fun `save migration status`() = runTest {
        val m = MigrationStatus.SUCCESS
        given(datasource.saveMigrationStatus(m)).willReturn(Unit)
        walletRepository.saveMigrationStatus(m)
        verify(datasource).saveMigrationStatus(m)
    }

    @Test
    fun `retrieve claim block hash`() = runTest {
        given(datasource.retrieveClaimBlockAndTxHash()).willReturn("block" to "hash")
        assertEquals("block" to "hash", walletRepository.retrieveClaimBlockAndTxHash())
    }

    @Test
    fun `observe migrate`() = runTest {
        val key = Sr25519Keypair(byteArrayOf(1, 2), byteArrayOf(3, 4), byteArrayOf(5, 6))
        given(
            extrinsicManager.submitAndWaitExtrinsic(
                anyString(),
                any(),
                anyBoolean(),
                anyString(),
                any(),
            )
        ).willReturn(
            ExtrinsicSubmitStatus(true, "txhash", "blockhash")
        )
        val test = walletRepository.migrate(
            "iroha address",
            "iroha public",
            "signature",
            key,
            "from",
        )

        assertEquals(true, test.success)
    }

    @Test
    fun `subscribe visible global cards hub list EXPECT get flow from db`() {
        given(globalCardsHubDao.getGlobalCardsHubVisible()).willReturn(
            flowOf(TestData.GLOBAL_CARD_HUB_LOCAL)
        )

        walletRepository.subscribeVisibleGlobalCardsHubList()

        verify(globalCardsHubDao).getGlobalCardsHubVisible()
    }

    @Test
    fun `update card visibility on card hub EXPECT update db`() = runTest {
        walletRepository.updateCardVisibilityOnGlobalCardsHub(cardId = "cardId", visible = true)

        verify(globalCardsHubDao).updateCardVisibility(cardId = "cardId", visibility = true)
    }

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
