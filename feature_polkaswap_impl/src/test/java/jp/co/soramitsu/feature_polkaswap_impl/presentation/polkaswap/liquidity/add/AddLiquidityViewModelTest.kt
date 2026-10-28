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

package jp.co.soramitsu.feature_polkaswap_impl.presentation.polkaswap.liquidity.add

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.mockkObject
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.equalTo
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.transaction.TransactionResultBadge
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_polkaswap_impl.presentation.screens.liquidityadd.LiquidityAddViewModel
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.PolkaswapTestData.LIQUIDITY_DATA
import jp.co.soramitsu.test_data.PolkaswapTestData.LIQUIDITY_DETAILS
import jp.co.soramitsu.test_data.PolkaswapTestData.VAL_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XOR_ASSET
import jp.co.soramitsu.test_data.PolkaswapTestData.XSTXAU_ASSET
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.test_data.TestTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import org.mockito.kotlin.verify as kVerify
import org.mockito.kotlin.willSuspendableAnswer

@FlowPreview
@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AddLiquidityViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var poolsInteractor: PoolsInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var coroutineManager: CoroutineManager

    @Mock
    private lateinit var router: WalletRouter

    private lateinit var viewModel: LiquidityAddViewModel

    private fun setUpViewModel(
        secondTokenId: String?,
        firstTokenId: String? = null,
    ) {
        viewModel = LiquidityAddViewModel(
            assetsInteractor,
            assetsRouter,
            router,
            walletInteractor,
            poolsInteractor,
            NumbersFormatter(),
            resourceManager,
            coroutineManager,
            firstTokenId ?: TestTokens.xorToken.id,
            secondTokenId,
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Before
    fun setUp() = runTest {
        mockkObject(FirebaseWrapper)
        given(
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                networkFeeInXor = any(),
                xorChange = any(),
            )
        ).willReturn(false)
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(LIQUIDITY_DATA))
        given(
            poolsInteractor.calcLiquidityDetails(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).willReturn(LIQUIDITY_DETAILS)
        given(assetsInteractor.subscribeAssetsActiveOfCurAccount()).willReturn(
            flowOf(
                listOf(
                    XOR_ASSET,
                    VAL_ASSET,
                    XSTXAU_ASSET,
                    TestAssets.xstusdAsset(BigDecimal.ONE),
                    TestAssets.pswapAsset(BigDecimal.TEN),
                    TestAssets.xstAsset(BigDecimal.TEN),
                )
            )
        )
        given(walletInteractor.getFeeToken()).willReturn(TestTokens.xorToken)
        given(coroutineManager.io).willReturn(this.coroutineContext[CoroutineDispatcher])

        given(resourceManager.getString(R.string.common_supply)).willReturn("Supply")
        given(resourceManager.getString(R.string.common_confirm)).willReturn("Confirm")
        given(resourceManager.getString(R.string.choose_tokens)).willReturn("Choose tokens")
        given(resourceManager.getString(R.string.common_enter_amount)).willReturn("Enter amount")
        given(resourceManager.getString(R.string.common_insufficient_balance)).willReturn("Insufficient balance")
    }

    /** Errors a test expects the screen to report; every other test must report none. */
    private var expectedReportedErrors = 0

    @After
    fun tearDown() {
        io.mockk.verify(exactly = expectedReportedErrors) {
            FirebaseWrapper.recordException(any())
        }
    }

    @Test
    fun `init viewModel EXPECT initial button state text`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        assertEquals("Choose tokens", viewModel.addState.value.btnState.text)
    }

    @Test
    fun `maximum XOR amount reserves the quoted network fee`() = runTest {
        val quotedFee = BigDecimal("0.1")
        given(poolsInteractor.calcLiquidityDetails(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(LIQUIDITY_DETAILS.copy(networkFee = quotedFee))
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("0.1"))
        advanceUntilIdle()

        viewModel.optionSelected(100)
        advanceUntilIdle()

        assertEquals(
            0,
            (BigDecimal.ONE - quotedFee)
                .compareTo(viewModel.addState.value.assetState1!!.amount),
        )
        assertTrue(viewModel.addState.value.btnState.enabled)
    }

    @Test
    fun `maximum XOR amount is zero and disabled when the fee exceeds the balance`() = runTest {
        given(poolsInteractor.calcLiquidityDetails(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .willReturn(LIQUIDITY_DETAILS.copy(networkFee = BigDecimal.TEN))
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("0.1"))
        advanceUntilIdle()

        viewModel.optionSelected(100)
        advanceUntilIdle()

        assertEquals(0, viewModel.addState.value.assetState1!!.amount!!.signum())
        assertFalse(viewModel.addState.value.btnState.enabled)
    }

    @Test
    fun `scaled zero and negative amounts keep supply disabled`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("0.1"))
        advanceUntilIdle()
        assertTrue(viewModel.addState.value.btnState.enabled)

        for (amount in listOf(BigDecimal("0.000"), BigDecimal("-0.1"))) {
            viewModel.onAmount1Change(amount)
            advanceUntilIdle()
            assertFalse(viewModel.addState.value.btnState.enabled)
        }
    }

    @Test
    fun `repeated confirmation submits liquidity only once while pending`() = runTest {
        val submitted = CompletableDeferred<TransactionOutcome>()
        doSuspendableAnswer { submitted.await() }.whenever(poolsInteractor)
            .observeAddLiquidity(any(), any(), any(), any(), any(), any(), any(), any())
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("0.1"))
        advanceUntilIdle()
        // The screen is in front, so the transaction page opens when the result arrives.
        pageOpens { true }
        viewModel.onScreenResumed()

        viewModel.onConfirmClick()
        viewModel.onConfirmClick()
        advanceUntilIdle()
        assertTrue(viewModel.addState.value.confirm.btnState.loading)
        submitted.complete(TransactionOutcome.Confirmed("liquidity-hash", "0xblock"))
        advanceUntilIdle()

        kVerify(poolsInteractor, times(1))
            .observeAddLiquidity(any(), any(), any(), any(), any(), any(), any(), any())
        kVerify(assetsRouter, times(1))
            .showTxResult("liquidity-hash", TransactionScreen.ADD_LIQUIDITY)
        assertFalse(viewModel.addState.value.confirm.btnState.loading)
    }

    @Test
    fun `init viewModel with both tokens EXPECT initial button state text`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TestTokens.valToken.id)
        advanceUntilIdle()
        assertEquals("Enter amount", viewModel.addState.value.btnState.text)
    }

    @Test
    fun `slippage tolerance changed EXPECT update slippageTolerance`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        viewModel.slippageChanged(1.0)
        advanceUntilIdle()
        assertEquals(1.0, viewModel.stateSlippage.value, 0.01)
    }

    @Test
    fun `amount from changed EXPECT update fromAssetAmount`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals(BigDecimal("110.34"), viewModel.addState.value.assetState1?.amount)
    }

    @Test
    fun `amount to changed EXPECT update toAssetAmount`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount2Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals(BigDecimal("110.34"), viewModel.addState.value.assetState2?.amount)
    }

    @Test
    fun `set tokens from args EXPECT update visible assets`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        verify(poolsInteractor).isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id)
        verify(poolsInteractor).isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id)
        verify(poolsInteractor).subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id)
    }

    @Test
    fun `set tokens from args EXPECT update toToken`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        assertEquals(VAL_ASSET.token, viewModel.addState.value.assetState2?.token)
    }

    @Test
    fun `set tokens from args EXPECT update fromToken`() = runTest {
        setUpViewModel(null)
        advanceUntilIdle()

        assertEquals(XOR_ASSET.token, viewModel.addState.value.assetState1?.token)
    }

    @Test
    fun `option selected and INPUT desired EXPECT update amountFrom value`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Focused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertTrue(viewModel.addState.value.assetState1?.amount?.equalTo(BigDecimal(0.5)) == true)
    }

    @Test
    fun `option selected and OUTPUT desired  EXPECT update amountTo value`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount2Focused()
        advanceUntilIdle()
        viewModel.optionSelected(50)
        advanceUntilIdle()

        assertTrue(viewModel.addState.value.assetState2?.amount?.equalTo(BigDecimal(0.5)) == true)
    }

    @Test
    fun `pair is not exists EXPECT pairNotExists is true`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(false))
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(null))
        given(
            poolsInteractor.getLiquidityData(
                XOR_ASSET.token,
                VAL_ASSET.token,
                enabled = false,
                presented = false
            )
        )
            .willReturn(LiquidityData())
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        val pair = viewModel.addState.value.pairNotExist
        assertTrue(pair == true)
    }

    @Test
    fun `reserves that cannot be read keep Supply off and Try again reads them`() = runTest {
        // The pair is not in the local cache, so its reserves are read from the network, and
        // that read fails. First fix: the exception no longer leaves viewModelScope uncaught
        // (which ends the app). But the screen then went on with reserves of zero, as for a new
        // pool: both amounts could be typed freely and confirmed, and the deposit failed on
        // chain with the fee charged.
        expectedReportedErrors = 1
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(null))
        var reads = 0
        given(
            poolsInteractor.getLiquidityData(
                eq(XOR_ASSET.token),
                eq(VAL_ASSET.token),
                any(),
                any(),
            )
        ).willAnswer {
            reads += 1
            if (reads == 1) throw java.io.IOException("socket closed")
            LIQUIDITY_DATA
        }
        listOf(
            R.string.tx_outcome_pool_unavailable_title,
            R.string.tx_outcome_pool_unavailable,
            R.string.common_try_again,
            R.string.common_close,
        ).forEach { id ->
            given(resourceManager.getString(id)).willReturn("text-$id")
        }

        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal.ONE)
        viewModel.onAmount2Change(BigDecimal.ONE)
        advanceUntilIdle()

        // Both tokens and both amounts are there, and Supply is off because of the reserves.
        assertEquals(VAL_ASSET.token.id, viewModel.addState.value.assetState2?.token?.id)
        assertEquals(
            "text-${R.string.tx_outcome_pool_unavailable_title}",
            viewModel.addState.value.btnState.text,
        )
        assertFalse(viewModel.addState.value.btnState.enabled)
        // Nothing was estimated against reserves that were never read ...
        verify(poolsInteractor, never()).calcLiquidityDetails(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        )
        // ... and nothing can be signed.
        viewModel.onConfirmClick()
        advanceUntilIdle()
        verify(poolsInteractor, never()).observeAddLiquidity(
            any(), any(), any(), any(), any(), any(), any(), any(),
        )

        // The person is told, with Try again; the cache flow would never ask a second time.
        val notice = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_pool_unavailable_title}", notice.title)
        assertEquals("text-${R.string.tx_outcome_pool_unavailable}", notice.message)
        assertEquals("text-${R.string.common_try_again}", notice.confirmText)

        notice.onConfirm()
        advanceUntilIdle()

        assertEquals(2, reads)
        assertEquals("Supply", viewModel.addState.value.btnState.text)
        assertTrue(viewModel.addState.value.btnState.enabled)
        // The amounts are now checked against the reserves that were read.
        verify(poolsInteractor, org.mockito.Mockito.atLeastOnce()).calcLiquidityDetails(
            any(),
            any(),
            eq(LIQUIDITY_DATA.firstReserves),
            eq(LIQUIDITY_DATA.secondReserves),
            any(), any(), any(), any(), any(), any(), any(),
        )
    }

    @Test
    fun `nothing can be supplied while the reserves are still being read`() = runTest {
        // Seen on the emulator: offline, the read of the reserves does not fail, it waits for
        // the connection. Until it answers the reserves are not known, and the screen must not
        // treat them as zero (a new pool) either.
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.subscribeReservesCache(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(null))
        val answer = kotlinx.coroutines.CompletableDeferred<LiquidityData>()
        given(
            poolsInteractor.getLiquidityData(
                eq(XOR_ASSET.token),
                eq(VAL_ASSET.token),
                any(),
                any(),
            )
        ).willSuspendableAnswer { answer.await() }

        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        // Both tokens are chosen; the button no longer says "Choose tokens".
        assertEquals("Enter amount", viewModel.addState.value.btnState.text)

        viewModel.onAmount1Change(BigDecimal.ONE)
        viewModel.onAmount2Change(BigDecimal.ONE)
        advanceUntilIdle()

        assertEquals("Supply", viewModel.addState.value.btnState.text)
        assertFalse(viewModel.addState.value.btnState.enabled)
        verify(poolsInteractor, never()).calcLiquidityDetails(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
        )
        viewModel.onConfirmClick()
        advanceUntilIdle()
        verify(poolsInteractor, never()).observeAddLiquidity(
            any(), any(), any(), any(), any(), any(), any(), any(),
        )

        // The connection is back and the read answers.
        answer.complete(LIQUIDITY_DATA)
        advanceUntilIdle()

        assertEquals("Supply", viewModel.addState.value.btnState.text)
        assertTrue(viewModel.addState.value.btnState.enabled)
    }

    @Test
    fun `pair is exists EXPECT pairNotExists is false`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        val pair = viewModel.addState.value.pairNotExist
        assertTrue(pair == false)
    }

    @Test
    fun `assets balance are zero EXPECT clean up liquidity details`() = runTest {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()

        viewModel.onAmount1Change(BigDecimal("110.34"))
        viewModel.onAmount2Change(BigDecimal("110.34"))
        advanceUntilIdle()
        assertEquals("1", viewModel.addState.value.prices.pair1Value)
    }

    @Test
    fun `WHEN user enters amount including one XOR EXPECT transaction reminder is checked`() =
        runTest {
            given(
                poolsInteractor.isPairEnabled(
                    inputAssetId = XOR_ASSET.token.id,
                    outputAssetId = VAL_ASSET.token.id
                )
            ).willReturn(
                flowOf(
                    true
                )
            )

            given(
                poolsInteractor.isPairPresentedInNetwork(
                    baseTokenId = XOR_ASSET.token.id,
                    tokenId = VAL_ASSET.token.id
                )
            ).willReturn(
                flowOf(
                    true
                )
            )

            setUpViewModel(
                firstTokenId = XOR_ASSET.token.id,
                secondTokenId = VAL_ASSET.token.id
            )

            advanceUntilIdle()

            viewModel.onAmount1Change(BigDecimal.TEN)

            advanceUntilIdle()

            kVerify(
                assetsInteractor,
                times(1)
            ).isNotEnoughXorLeftAfterTransaction(
                xorChange = BigDecimal.TEN,
                networkFeeInXor = LIQUIDITY_DETAILS.networkFee,
            )
        }

    // --- an estimate that cannot be made ----------------------------------------------------

    private var failedEstimates = 0

    /** The estimate reads the network fee; [reachable] says whether that read answers. */
    private suspend fun estimateAnswers(reachable: () -> Boolean) {
        given(
            poolsInteractor.calcLiquidityDetails(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        ).willAnswer {
            if (!reachable()) {
                failedEstimates += 1
                // RuntimeManager.getMutationRuntimeContext while the endpoint is unreachable.
                throw java.io.IOException("BOUNDED_HTTP_IO")
            }
            LIQUIDITY_DETAILS
        }
    }

    private fun stubEstimateTexts() {
        listOf(
            R.string.network_fee_unavailable_short,
            R.string.tx_outcome_not_sent_title,
            R.string.tx_outcome_not_sent_estimate,
            R.string.remove_pool_confirmation_description,
        ).forEach { id ->
            org.mockito.Mockito.lenient()
                .`when`(resourceManager.getString(id))
                .thenReturn("text-$id")
        }
    }

    private suspend fun verifyNothingSigned() {
        verify(poolsInteractor, never()).observeAddLiquidity(
            any(), any(), any(), any(), any(), any(), any(), any(),
        )
    }

    @Test
    fun `an estimate that cannot be made keeps Supply and Confirm off and Try again recovers`() =
        runTest {
            // The first Add liquidity of the session on a pair whose reserves are cached, while
            // the SORA endpoint cannot be reached: the fee read inside the estimate fails.
            given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
                .willReturn(flowOf(true))
            given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
                .willReturn(flowOf(true))
            var reachable = false
            estimateAnswers { reachable }
            stubEstimateTexts()
            setUpViewModel(VAL_ASSET.token.id)
            advanceUntilIdle()
            pageOpens { true }
            viewModel.onScreenResumed()

            viewModel.onAmount1Change(BigDecimal.ONE)
            advanceUntilIdle()

            // Before: the button spun briefly, the second amount stayed empty and nothing said
            // why. Now the same notice as on Send and Swap is shown, with Try again.
            assertEquals(
                NetworkFeeUnavailable.NETWORK_UNREACHABLE,
                viewModel.addState.value.estimateUnavailable,
            )
            assertFalse(viewModel.addState.value.btnState.loading)

            // The second amount is typed by hand, at a ratio the pool was never asked about.
            viewModel.onAmount2Change(BigDecimal("0.5"))
            advanceUntilIdle()

            // Before: "Supply", enabled, with a blank fee.
            assertEquals(
                "text-${R.string.network_fee_unavailable_short}",
                viewModel.addState.value.btnState.text,
            )
            assertFalse(viewModel.addState.value.btnState.enabled)
            assertEquals("", viewModel.addState.value.prices.fee)

            // Whatever is tapped, nothing is signed, and Confirm says why.
            viewModel.onReviewClick()
            confirm()
            advanceUntilIdle()
            verifyNothingSigned()
            assertEquals(
                "text-${R.string.tx_outcome_not_sent_title}" to
                    "text-${R.string.tx_outcome_not_sent_estimate}",
                viewModel.alertDialogLiveData.value,
            )
            assertFalse(viewModel.addState.value.confirm.btnState.loading)

            // The connection is back.
            reachable = true
            viewModel.onRetryEstimate()
            advanceUntilIdle()

            assertEquals(null, viewModel.addState.value.estimateUnavailable)
            assertEquals("Supply", viewModel.addState.value.btnState.text)
            assertTrue(viewModel.addState.value.btnState.enabled)
            assertTrue(viewModel.addState.value.prices.fee.isNotEmpty())

            // The amounts on screen are the estimated ones and can be confirmed now.
            answers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }
            viewModel.onReviewClick()
            confirm()
            advanceUntilIdle()
            kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)

            assertTrue(failedEstimates >= 2)
            expectedReportedErrors = failedEstimates
        }

    @Test
    fun `an estimate that fails after the review does not let Confirm sign`() = runTest {
        var reachable = true
        estimateAnswers { reachable }
        stubEstimateTexts()
        readyToConfirm()
        viewModel.onReviewClick()
        assertTrue(viewModel.addState.value.btnState.enabled)

        // On the confirm screen: the pool changed (every block does that) and the amounts
        // could not be checked against it.
        reachable = false
        viewModel.slippageChanged(1.0)
        advanceUntilIdle()
        assertEquals(
            NetworkFeeUnavailable.NETWORK_UNREACHABLE,
            viewModel.addState.value.estimateUnavailable,
        )

        confirm()
        advanceUntilIdle()

        verifyNothingSigned()
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_estimate}",
            viewModel.alertDialogLiveData.value,
        )
        assertEquals(null, shownBadge())

        // "Try again": a blocked Confirm asks for the estimate once more, so the next one
        // succeeds as soon as the network answers.
        reachable = true
        confirm()
        advanceUntilIdle()
        verifyNothingSigned()
        answers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }
        confirm()
        advanceUntilIdle()
        kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)

        expectedReportedErrors = failedEstimates
    }

    @Test
    fun `an edited amount cannot be supplied before its estimate is there`() = runTest {
        stubEstimateTexts()
        readyToConfirm()
        assertTrue(viewModel.addState.value.btnState.enabled)
        // The estimate for the new amount is on its way (a fee read can take seconds).
        val estimate = kotlinx.coroutines.CompletableDeferred<Unit>()
        given(
            poolsInteractor.calcLiquidityDetails(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
            )
        ).willSuspendableAnswer {
            estimate.await()
            LIQUIDITY_DETAILS
        }

        viewModel.onAmount1Change(BigDecimal("0.5"))
        advanceTimeBy(1_000)
        runCurrent()

        // Before: Supply stayed on with the second amount of the earlier estimate, and
        // Confirm signed the new first amount with the old second one.
        assertFalse(viewModel.addState.value.btnState.enabled)
        confirm()
        runCurrent()
        verifyNothingSigned()

        estimate.complete(Unit)
        advanceUntilIdle()

        assertEquals("Supply", viewModel.addState.value.btnState.text)
        assertTrue(viewModel.addState.value.btnState.enabled)
    }

    @Test
    fun `an estimate made for another pair does not carry over`() = runTest {
        given(poolsInteractor.isPairEnabled(any(), any())).willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(any(), any())).willReturn(flowOf(true))
        given(poolsInteractor.subscribeReservesCache(any(), any()))
            .willReturn(flowOf(LIQUIDITY_DATA))
        var reachable = true
        estimateAnswers { reachable }
        stubEstimateTexts()
        setUpViewModel(VAL_ASSET.token.id)
        advanceUntilIdle()
        viewModel.onAmount1Change(BigDecimal.ONE)
        advanceUntilIdle()
        assertTrue(viewModel.addState.value.btnState.enabled)

        // Another second token is chosen while the network cannot be reached.
        reachable = false
        viewModel.onToken2Change(XSTXAU_ASSET.token.id)
        advanceUntilIdle()

        assertFalse(viewModel.addState.value.btnState.enabled)
        confirm()
        advanceUntilIdle()
        verifyNothingSigned()

        expectedReportedErrors = failedEstimates
    }

    // --- audit A5: one truthful result for every outcome of Confirm -------------------------

    private val txHash = "0x" + "ab".repeat(32)

    private suspend fun TestScope.readyToConfirm() {
        given(poolsInteractor.isPairEnabled(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        given(poolsInteractor.isPairPresentedInNetwork(XOR_ASSET.token.id, VAL_ASSET.token.id))
            .willReturn(flowOf(true))
        setUpViewModel(TestTokens.valToken.id)
        advanceUntilIdle()
        // An amount was entered and estimated against the pool, as it is before Review.
        viewModel.onAmount1Change(BigDecimal.ONE)
        advanceUntilIdle()
        // The screen is the screen in front, as it is when Confirm is tapped, and the router
        // opens the transaction page when asked.
        pageOpens { true }
        viewModel.onScreenResumed()
    }

    /** What the router answers when asked to open the transaction page. */
    private fun pageOpens(answer: () -> Boolean) {
        org.mockito.Mockito.lenient()
            .`when`(assetsRouter.showTxResult(any(), any()))
            .thenAnswer { answer() }
    }

    private suspend fun answers(confirmedDuplicate: Boolean, answer: () -> TransactionOutcome) {
        given(
            poolsInteractor.observeAddLiquidity(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(confirmedDuplicate),
            )
        ).willAnswer { answer() }
    }

    private fun confirm() = viewModel.onConfirmClick()

    private fun shownBadge() = viewModel.addState.value.confirm.confirmResult

    private fun confirmButton() = viewModel.addState.value.confirm.btnState

    private fun stubOutcomeTexts() {
        listOf(
            R.string.tx_outcome_refused_title,
            R.string.tx_outcome_note_refused_busy,
            R.string.tx_outcome_duplicate_title,
            R.string.tx_outcome_duplicate_message,
            R.string.tx_outcome_duplicate_send_again,
            R.string.tx_outcome_duplicate_wait,
        ).forEach { id ->
            given(resourceManager.getString(id)).willReturn("text-$id")
        }
    }

    @Test
    fun `a confirmed transaction shows confirmed and opens its transaction page`() = runTest {
        readyToConfirm()
        answers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

        confirm()
        runCurrent()

        assertEquals(TransactionResultBadge.CONFIRMED, shownBadge())
        advanceUntilIdle()
        kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)
    }

    @Test
    fun `a transaction that is still waiting is shown as submitted and never as rejected`() =
        runTest {
            readyToConfirm()
            // What a failed status read or an unfinished finality wait gives after a broadcast.
            answers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

            confirm()
            runCurrent()

            assertEquals(TransactionResultBadge.SUBMITTED, shownBadge())
            assertEquals(R.string.wallet_transaction_submitted_1, shownBadge()?.text)
            advanceUntilIdle()
            // The transaction page carries "waiting for confirmation" and follows the result.
            kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)
            kVerify(router, never()).returnToHubFragment()
            assertEquals(null, viewModel.alertDialogLiveData.value)
        }

    @Test
    fun `a transaction that failed on chain shows failed and opens its transaction page`() =
        runTest {
            readyToConfirm()
            answers(false) {
                TransactionOutcome.FailedOnChain(
                    txHash = txHash,
                    blockHash = "0xblock",
                    reason = TransactionChainFailure.EXECUTION_FAILED,
                    detail = "PoolXYK.InsufficientLiquidity",
                )
            }

            confirm()
            runCurrent()

            assertEquals(TransactionResultBadge.FAILED, shownBadge())
            advanceUntilIdle()
            kVerify(assetsRouter).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)
        }

    @Test
    fun `a transaction the node refused is a failure with its reason and is not submitted`() =
        runTest {
            readyToConfirm()
            stubOutcomeTexts()
            answers(false) {
                TransactionOutcome.RefusedByNode(
                    txHash = txHash,
                    reason = TransactionNodeRefusal.NETWORK_BUSY,
                    nodeMessage = "Immediately Dropped",
                )
            }

            confirm()
            runCurrent()

            assertEquals(TransactionResultBadge.NOT_SENT, shownBadge())
            advanceUntilIdle()
            assertEquals(
                "text-${R.string.tx_outcome_refused_title}" to
                    "text-${R.string.tx_outcome_note_refused_busy}",
                viewModel.alertDialogLiveData.value,
            )
            kVerify(assetsRouter, never()).showTxResult(any(), any())
            // The dialog is on this screen, so the screen stays and offers Confirm again.
            kVerify(router, never()).returnToHubFragment()
            assertTrue(confirmButton().enabled)
            assertFalse(confirmButton().loading)
        }

    @Test
    fun `the same action still waiting needs a deliberate second confirmation`() = runTest {
        readyToConfirm()
        stubOutcomeTexts()
        answers(false) { throw DuplicateWaitingTransactionException(txHash, 1L) }
        answers(true) { TransactionOutcome.Waiting("0x" + "cd".repeat(32), true) }

        confirm()
        advanceUntilIdle()

        val question = checkNotNull(viewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_title}", question.title)
        assertEquals(null, shownBadge())
        kVerify(assetsRouter, never()).showTxResult(any(), any())
        assertTrue(confirmButton().enabled)

        question.onConfirm()
        advanceUntilIdle()
        kVerify(assetsRouter).showTxResult("0x" + "cd".repeat(32), TransactionScreen.ADD_LIQUIDITY)
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            readyToConfirm()
            answers(false) { TransactionOutcome.Confirmed(txHash, "0xblock") }

            confirm()
            runCurrent()
            // Home was pressed (or the phone locked) while the result mark was shown.
            viewModel.onScreenPaused()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top it
            // removes the PIN lock.
            kVerify(assetsRouter, never()).showTxResult(any(), any())
            // Until the page opens the confirm screen says what happened and Confirm is off.
            assertEquals(TransactionResultBadge.CONFIRMED, shownBadge())
            assertFalse(confirmButton().enabled)
            assertFalse(confirmButton().loading)

            viewModel.onScreenResumed()

            kVerify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)

            // Shown once: a later return to the front opens nothing more.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(1)).showTxResult(any(), any())
        }

    @Test
    fun `a sent transaction cannot be confirmed a second time while its page has not opened`() =
        runTest {
            readyToConfirm()
            var sent = 0
            answers(false) {
                sent += 1
                TransactionOutcome.Waiting(txHash, acceptedByNode = true)
            }

            confirm()
            runCurrent()
            viewModel.onScreenPaused()
            advanceUntilIdle()

            confirm()
            viewModel.onReviewClick()
            confirm()
            advanceUntilIdle()

            assertEquals(1, sent)
            assertFalse(confirmButton().enabled)
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            readyToConfirm()
            // The router opens the page only when this screen is the current destination.
            var pinLockOnTop = true
            pageOpens { !pinLockOnTop }
            answers(false) { TransactionOutcome.Waiting(txHash, acceptedByNode = true) }

            confirm()
            runCurrent()
            viewModel.onScreenPaused()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)
            assertEquals(TransactionResultBadge.SUBMITTED, shownBadge())
            assertFalse(confirmButton().enabled)
            viewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(2)).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)

            // Opened: nothing more is asked of the router.
            viewModel.onScreenPaused()
            viewModel.onScreenResumed()
            kVerify(assetsRouter, times(2)).showTxResult(any(), any())
        }

    @Test
    fun `a failure that arrives in the background is explained and opens nothing`() = runTest {
        readyToConfirm()
        stubOutcomeTexts()
        answers(false) {
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.NETWORK_BUSY,
                nodeMessage = "Immediately Dropped",
            )
        }

        confirm()
        runCurrent()
        viewModel.onScreenPaused()
        advanceUntilIdle()
        viewModel.onScreenResumed()

        // The dialog is held by the view model and delivered when the screen is shown again.
        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_busy}",
            viewModel.alertDialogLiveData.value,
        )
        kVerify(assetsRouter, never()).showTxResult(any(), any())
        assertEquals(null, shownBadge())
        assertTrue(confirmButton().enabled)
    }

    @Test
    fun `the same deposit cannot be reviewed and confirmed again during the wait`() = runTest {
        readyToConfirm()
        val result = kotlinx.coroutines.CompletableDeferred<TransactionOutcome>()
        var sent = 0
        given(
            poolsInteractor.observeAddLiquidity(
                any(), any(), any(), any(), any(), any(), any(), eq(false),
            )
        ).willSuspendableAnswer {
            sent += 1
            result.await()
        }

        confirm()
        runCurrent()
        // Back to the amounts, Review and Confirm again while the first deposit is followed.
        viewModel.onReviewClick()
        confirm()
        runCurrent()

        assertEquals(1, sent)
        assertFalse(confirmButton().enabled)
        assertTrue(confirmButton().loading)

        result.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
        advanceUntilIdle()
        kVerify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.ADD_LIQUIDITY)
    }
}
