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

package jp.co.soramitsu.feature_polkaswap_impl.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
@ExperimentalCoroutinesApi
class SwapInteractorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var polkaswapRepository: PolkaswapRepository

    @Mock
    private lateinit var polkaswapSubscriptionRepository: PolkaswapSubscriptionRepository

    @Mock
    private lateinit var polkaswapExtrinsicRepository: PolkaswapExtrinsicRepository

    @Mock
    private lateinit var assetsRepository: AssetsRepository

    @Mock
    private lateinit var credentialsRepository: CredentialsRepository

    @Mock
    private lateinit var transactionHistoryRepository: TransactionHistoryRepository

    @Mock
    private lateinit var builder: TransactionBuilder

    @Mock
    private lateinit var userRepository: UserRepository

    private lateinit var interactor: SwapInteractorImpl

    private val xorDex = PoolDex(0, TestTokens.xorToken.id, TestTokens.xorToken.symbol)

    @Before
    fun setUp() {
        interactor = SwapInteractorImpl(
            assetsRepository,
            credentialsRepository,
            userRepository,
            transactionHistoryRepository,
            polkaswapRepository,
            polkaswapExtrinsicRepository,
            polkaswapSubscriptionRepository,
            builder,
        )
    }

    @Test
    fun `VAL to XOR sources recover when DEX synchronization finishes after first lookup`() = runTest {
        whenever(polkaswapRepository.getPoolBaseTokens())
            .thenReturn(emptyList(), listOf(xorDex))
        whenever(
            polkaswapSubscriptionRepository.isSwapAvailable(
                TestTokens.valToken.id, TestTokens.xorToken.id, xorDex.dexId,
            )
        ).thenReturn(true)
        whenever(
            polkaswapSubscriptionRepository.getAvailableSources(
                TestTokens.valToken.id, TestTokens.xorToken.id, xorDex.dexId,
            )
        ).thenReturn(listOf(Market.XYK))

        assertNull(interactor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
        assertEquals(
            setOf(Market.SMART, Market.XYK),
            interactor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id),
        )
        verify(polkaswapSubscriptionRepository).isSwapAvailable(
            TestTokens.valToken.id, TestTokens.xorToken.id, xorDex.dexId,
        )
    }

    @Test
    fun `DEX list reflects synchronized replacements`() = runTest {
        val replacement = PoolDex(1, TestTokens.valToken.id, TestTokens.valToken.symbol)
        whenever(polkaswapRepository.getPoolBaseTokens())
            .thenReturn(listOf(xorDex), listOf(replacement))

        assertEquals(listOf(xorDex), interactor.getPoolDexList())
        assertEquals(listOf(replacement), interactor.getPoolDexList())
    }

    @Test
    fun `pair with no path cannot quote using the previous pair markets`() = runTest {
        discoverXorToValSources()
        whenever(
            polkaswapSubscriptionRepository.isSwapAvailable(
                TestTokens.valToken.id, TestTokens.xorToken.id, xorDex.dexId,
            )
        ).thenReturn(false)

        assertNull(interactor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id))
        assertNoValToXorQuote()
    }

    @Test
    fun `failed discovery cannot quote using the previous pair markets`() = runTest {
        discoverXorToValSources()
        val failure = IllegalStateException("Path discovery failed")
        whenever(
            polkaswapSubscriptionRepository.isSwapAvailable(
                TestTokens.valToken.id, TestTokens.xorToken.id, xorDex.dexId,
            )
        ).thenThrow(failure)

        val result = runCatching {
            interactor.fetchAvailableSources(TestTokens.valToken.id, TestTokens.xorToken.id)
        }

        assertSame(failure, result.exceptionOrNull())
        assertNoValToXorQuote()
    }

    private suspend fun discoverXorToValSources() {
        whenever(polkaswapRepository.getPoolBaseTokens()).thenReturn(listOf(xorDex))
        whenever(
            polkaswapSubscriptionRepository.isSwapAvailable(
                TestTokens.xorToken.id, TestTokens.valToken.id, xorDex.dexId,
            )
        ).thenReturn(true)
        whenever(
            polkaswapSubscriptionRepository.getAvailableSources(
                TestTokens.xorToken.id, TestTokens.valToken.id, xorDex.dexId,
            )
        ).thenReturn(listOf(Market.XYK))

        assertEquals(
            setOf(Market.SMART, Market.XYK),
            interactor.fetchAvailableSources(TestTokens.xorToken.id, TestTokens.valToken.id),
        )
    }

    private suspend fun assertNoValToXorQuote() {
        assertNull(
            interactor.calcDetails(
                TestTokens.valToken,
                TestTokens.xorToken,
                TestTokens.xorToken,
                BigDecimal.ONE,
                WithDesired.INPUT,
                0.5,
            )
        )
        verify(polkaswapSubscriptionRepository, never()).getSwapQuote(
            any(), any(), any(), any(), any(), any(), any(),
        )
    }
}
