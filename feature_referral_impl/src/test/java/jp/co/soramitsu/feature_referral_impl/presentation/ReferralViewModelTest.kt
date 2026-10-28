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

package jp.co.soramitsu.feature_referral_impl.presentation

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import java.io.IOException
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.androidfoundation.testing.getOrAwaitValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.DuplicateWaitingTransactionException
import jp.co.soramitsu.common.domain.NetworkFeeUnavailable
import jp.co.soramitsu.common.domain.TransactionChainFailure
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_api.presentation.TransactionScreen
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_referral_impl.domain.ReferralInteractor
import jp.co.soramitsu.feature_referral_impl.domain.model.Referral
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.test_data.TestAssets
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mockingDetails
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.eq
import org.mockito.kotlin.given
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.willSuspendableAnswer

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class ReferralViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: ReferralInteractor

    @Mock
    private lateinit var assetsInteractor: AssetsInteractor

    @Mock
    private lateinit var walletInteractor: WalletInteractor

    @Mock
    private lateinit var assetsRouter: AssetsRouter

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var referralViewModel: ReferralViewModel

    private val xorAsset = TestAssets.xorAsset()
    private val xorToken = TestAssets.xorAsset().token
    private val referrals = listOf(
        Referral("address", BigDecimal.ONE),
    )

    private val assetFlow = MutableSharedFlow<Asset>()
    private suspend fun assetFlowEmit(asset: Asset) = assetFlow.emit(asset)

    private val myReferrerFlow = MutableSharedFlow<String>()
    private suspend fun myReferrerFlowEmit(referrer: String) = myReferrerFlow.emit(referrer)

    private val referrerBalanceFlow = MutableSharedFlow<BigDecimal?>()
    private suspend fun referrerBalanceFlowEmit(b: BigDecimal) = referrerBalanceFlow.emit(b)

    private lateinit var expectedToolbarState: SoramitsuToolbarState

    @Before
    fun setUp() = runTest {
        whenever(walletInteractor.getFeeToken()).thenReturn(xorToken)
        whenever(interactor.getInvitationLink()).thenReturn("polkaswap/link")
        whenever(interactor.calcBondFee()).thenReturn(BigDecimal("0.07"))
        whenever(assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)).thenReturn(
            assetFlow
        )
        whenever(
            assetsInteractor.isNotEnoughXorLeftAfterTransaction(
                any(),
                any()
            )
        ).thenReturn(false)
        whenever(interactor.observeMyReferrer()).thenReturn(myReferrerFlow)
        whenever(interactor.observeReferrerBalance()).thenReturn(referrerBalanceFlow)
        whenever(interactor.getSetReferrerFee()).thenReturn(BigDecimal("0.07"))
        whenever(resourceManager.getString(R.string.referral_no_available_invitations)).thenReturn("No available invitations")
        whenever(resourceManager.getString(R.string.referral_referral_link)).thenReturn("Referrer’s link or address")
        whenever(resourceManager.getString(R.string.referral_invitation_link)).thenReturn("Referrer’s link or address")
        assetFlowEmit(xorAsset)
    }

    private fun setupViewModel() {
        referralViewModel = ReferralViewModel(
            assetsInteractor,
            assetsRouter,
            interactor,
            walletInteractor,
            NumbersFormatter(),
            router,
            resourceManager,
        )
        // The referral screen is the screen in front, as it is when a button is tapped, and
        // the router opens the transaction page when asked.
        pageOpens { true }
        referralViewModel.onScreenResumed()

        expectedToolbarState = SoramitsuToolbarState(
            type = SoramitsuToolbarType.Small(),
            basic = BasicToolbarState(
                title = "Referral program",
                navIcon = null,
            ),
        )
    }

    @Test
    fun `initial screen`() = runTest {
        whenever(interactor.getReferrals()).thenReturn(flow { emit(referrals) })
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(flow { emit("") })
        setupViewModel()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        myReferrerFlowEmit("my referrer")
        advanceUntilIdle()
        val actualToolbarState = referralViewModel.toolbarState.getOrAwaitValue()
        assertTrue(actualToolbarState.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.referral_toolbar_title, actualToolbarState.basic.title)
        val actualScreenState = referralViewModel.referralScreenState.value
        assertEquals("my referrer", actualScreenState.common.referrer)
        verify(interactor).updateReferrals()
    }

    @Test
    fun `no data welcome screen`() = runTest {
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        setupViewModel()
        advanceUntilIdle()
        val toolbar = referralViewModel.toolbarState.getOrAwaitValue()
        assertEquals(R.string.referral_toolbar_title, toolbar.basic.title)
        assertTrue(toolbar.type is SoramitsuToolbarType.Small)
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
        val state = referralViewModel.referralScreenState.value
        assertNull(state.common.referrer)
        var navEvent = referralViewModel.navToStart.getOrAwaitValue()
        assertEquals(ReferralFeatureRoutes.WELCOME_PAGE, navEvent)
        whenever(interactor.isLinkOrAddressOk("")).thenReturn(false to "")
        referralViewModel.openReferrerInput()
        advanceUntilIdle()
        assertEquals(false, referralViewModel.referralScreenState.value.common.activate)
        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)
        whenever(interactor.isLinkOrAddressOk("cnVko")).thenReturn(true to "cnVko")
        referralViewModel.onReferrerInputChange(TextFieldValue("cnVko"))
        advanceUntilIdle()
        assertEquals(true, referralViewModel.referralScreenState.value.common.activate)
        assertEquals(
            "cnVko",
            referralViewModel.referralScreenState.value.referrerInputState.value.text
        )
        whenever(interactor.observeSetReferrer("cnVko", false))
            .thenReturn(TransactionOutcome.Confirmed(txHash, "0xblock"))
        referralViewModel.onActivateLinkClick()
        advanceUntilIdle()
        navEvent = referralViewModel.navToStart.getOrAwaitValue()
        assertEquals(ReferralFeatureRoutes.WELCOME_PAGE, navEvent)
        verify(assetsRouter).showTxResult(txHash, TransactionScreen.REFERRAL)
        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)
    }

    private suspend fun TestScope.openReferrerInputWith(link: String) {
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        setupViewModel()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
        whenever(interactor.isLinkOrAddressOk("")).thenReturn(false to "")
        referralViewModel.openReferrerInput()
        advanceUntilIdle()
        whenever(interactor.isLinkOrAddressOk(link)).thenReturn(true to link)
        referralViewModel.onReferrerInputChange(TextFieldValue(link))
        advanceUntilIdle()
    }

    @Test
    fun `an Activate that the signer refuses says why and gives the button back`() = runTest {
        // The candidate showed nothing and the screen lost its button: the refusal is not a
        // SoraException, and the progress state was never reset.
        openReferrerInputWith("cnVko")
        stubOutcomeTexts()
        whenever(resourceManager.getString(R.string.tx_outcome_not_sent_account_unavailable))
            .thenReturn("account unavailable")
        whenever(interactor.observeSetReferrer("cnVko"))
            .thenThrow(IllegalStateException("SIGNING_WALLET_UNAVAILABLE"))

        referralViewModel.onActivateLinkClick()
        advanceUntilIdle()

        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)
        assertEquals(true, referralViewModel.referralScreenState.value.common.activate)
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to "account unavailable",
            referralViewModel.alertDialogLiveData.value,
        )
        verify(assetsRouter, never()).showTxDetails(any())
    }

    @Test
    fun `a Bond that fails for any other reason also says so and gives the button back`() = runTest {
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        setupViewModel()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
        stubOutcomeTexts()
        whenever(resourceManager.getString(R.string.tx_outcome_not_sent_generic)).thenReturn("nothing sent")
        whenever(interactor.observeBond(any(), eq(false))).thenThrow(IllegalStateException("anything"))

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to "nothing sent",
            referralViewModel.alertDialogLiveData.value,
        )
    }

    @Test
    fun `opening the screen without network reports it and does not end the app`() = runTest {
        stubOutcomeTexts()
        // Audit (SEED64-DYN): the candidate crashed here with BOUNDED_HTTP_IO. The fee is read
        // again when the XOR balance arrives, in a place no handler covered.
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        whenever(assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)).thenReturn(
            flow { emit(TestAssets.xorAsset()) }
        )
        whenever(interactor.calcBondFee()).thenAnswer {
            throw jp.co.soramitsu.common.network.BoundedHttpTextException("BOUNDED_HTTP_IO")
        }

        setupViewModel()
        advanceUntilIdle()

        assertEquals(
            R.string.common_error_general_title to R.string.common_error_network,
            referralViewModel.errorFromResourceLiveData.value,
        )
        // Changing the number of invitations while offline is reported the same way.
        referralViewModel.onBondPlus()
        advanceUntilIdle()
        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)
    }

    @Test
    fun `onDestinationChanged() called`() = runTest {
        whenever(interactor.getReferrals()).thenReturn(flow { emit(referrals) })
        whenever(interactor.observeReferrals()).thenReturn(flow { emit("") })
        setupViewModel()
        advanceUntilIdle()
        referralViewModel.onCurrentDestinationChanged(ReferralFeatureRoutes.WELCOME_PAGE)
        val actualToolbarState2 = referralViewModel.toolbarState.getOrAwaitValue()
        assertTrue(actualToolbarState2.type is SoramitsuToolbarType.Small)
        assertEquals(R.string.referral_toolbar_title, actualToolbarState2.basic.title)
    }

    @Test
    fun `WHEN invitations count is changed EXPECT transaction reminder is checked`() = runTest {
        whenever(interactor.getReferrals()).thenReturn(flow { emit(referrals) })
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        whenever(assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)).thenReturn(
            flow { emit(TestAssets.xorAsset()) }
        )
        setupViewModel()
        advanceUntilIdle()
        referralViewModel.onBondPlus()
        advanceUntilIdle()
        verify(assetsInteractor, atLeast(1)).isNotEnoughXorLeftAfterTransaction(any(), any())
        referralViewModel.onBondMinus()
        advanceUntilIdle()
        verify(assetsInteractor, atLeast(1)).isNotEnoughXorLeftAfterTransaction(any(), any())
    }

    // --- audit A5: one truthful result for every outcome, and no crash offline --------------

    private val txHash = "0x" + "ab".repeat(32)

    private suspend fun TestScope.programLoaded() {
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        setupViewModel()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()
    }

    private fun stubOutcomeTexts() {
        listOf(
            R.string.tx_outcome_refused_title,
            R.string.tx_outcome_note_refused_fee,
            R.string.tx_outcome_not_sent_title,
            R.string.tx_outcome_not_sent_network,
            R.string.tx_outcome_duplicate_title,
            R.string.tx_outcome_duplicate_message,
            R.string.tx_outcome_duplicate_send_again,
            R.string.tx_outcome_duplicate_wait,
            R.string.network_fee_unavailable_short,
            R.string.tx_outcome_screen_needs_network,
            R.string.common_try_again,
            R.string.common_close,
            R.string.network_fee_needs_app_update,
            R.string.common_update,
        ).forEach { id ->
            whenever(resourceManager.getString(id)).thenReturn("text-$id")
        }
    }

    /** What the router answers when asked to open the transaction page. */
    private fun pageOpens(answer: () -> Boolean) {
        org.mockito.Mockito.lenient()
            .`when`(assetsRouter.showTxResult(any(), any()))
            .thenAnswer { answer() }
    }

    private fun assertProgressCleared() =
        assertEquals(false, referralViewModel.referralScreenState.value.common.progress)

    @Test
    fun `a confirmed bond opens its transaction page`() = runTest {
        programLoaded()
        whenever(interactor.observeBond(any(), eq(false)))
            .thenReturn(TransactionOutcome.Confirmed(txHash, "0xblock"))

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        verify(assetsRouter).showTxResult(txHash, TransactionScreen.REFERRAL)
        assertEquals(
            ReferralFeatureRoutes.REFERRAL_PROGRAM,
            referralViewModel.navToStart.getOrAwaitValue(),
        )
        assertProgressCleared()
    }

    @Test
    fun `a bond that is still waiting opens its transaction page and shows no error`() = runTest {
        programLoaded()
        // What a failed status read or an unfinished finality wait gives after a broadcast.
        whenever(interactor.observeBond(any(), eq(false)))
            .thenReturn(TransactionOutcome.Waiting(txHash, acceptedByNode = true))

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        // The transaction page carries "waiting for confirmation" and follows the result.
        verify(assetsRouter).showTxResult(txHash, TransactionScreen.REFERRAL)
        assertNull(referralViewModel.alertDialogLiveData.value)
        assertNull(referralViewModel.errorFromResourceLiveData.value)
        // Before the fix nothing was shown at all and the progress mark stayed on.
        assertProgressCleared()
    }

    @Test
    fun `an unbond that failed on chain opens its transaction page`() = runTest {
        programLoaded()
        whenever(interactor.observeUnbond(any(), eq(false))).thenReturn(
            TransactionOutcome.FailedOnChain(
                txHash = txHash,
                blockHash = "0xblock",
                reason = TransactionChainFailure.EXECUTION_FAILED,
                detail = "Referrals.ReferrerInsufficientBalance",
            )
        )

        referralViewModel.onUnbondButtonClick()
        advanceUntilIdle()

        verify(assetsRouter).showTxResult(txHash, TransactionScreen.REFERRAL)
        assertProgressCleared()
    }

    @Test
    fun `a bond the node refused is a failure with its reason and is not submitted`() = runTest {
        programLoaded()
        stubOutcomeTexts()
        whenever(interactor.observeBond(any(), eq(false))).thenReturn(
            TransactionOutcome.RefusedByNode(
                txHash = txHash,
                reason = TransactionNodeRefusal.CANNOT_PAY_FEE,
                nodeMessage = "Inability to pay some fees",
            )
        )

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        assertEquals(
            "text-${R.string.tx_outcome_refused_title}" to
                "text-${R.string.tx_outcome_note_refused_fee}",
            referralViewModel.alertDialogLiveData.value,
        )
        verify(assetsRouter, never()).showTxResult(any(), any())
        assertProgressCleared()
    }

    @Test
    fun `a bond that could not be prepared says nothing was sent`() = runTest {
        programLoaded()
        stubOutcomeTexts()
        // The signing gate and the network raise plain exceptions, not SoraException.
        whenever(interactor.observeBond(any(), eq(false)))
            .thenAnswer { throw IOException("BOUNDED_HTTP_IO") }

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        // Before the fix: no message, and the button never came back.
        assertEquals(
            "text-${R.string.tx_outcome_not_sent_title}" to
                "text-${R.string.tx_outcome_not_sent_network}",
            referralViewModel.alertDialogLiveData.value,
        )
        verify(assetsRouter, never()).showTxResult(any(), any())
        assertProgressCleared()
    }

    @Test
    fun `the same bond still waiting needs a deliberate second confirmation`() = runTest {
        programLoaded()
        stubOutcomeTexts()
        whenever(interactor.observeBond(any(), eq(false)))
            .thenAnswer { throw DuplicateWaitingTransactionException(txHash, 1L) }
        whenever(interactor.observeBond(any(), eq(true)))
            .thenReturn(TransactionOutcome.Waiting("0x" + "cd".repeat(32), true))

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        val question = checkNotNull(referralViewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_duplicate_send_again}", question.confirmText)
        verify(assetsRouter, never()).showTxResult(any(), any())
        assertProgressCleared()

        question.onConfirm()
        advanceUntilIdle()
        verify(assetsRouter).showTxResult("0x" + "cd".repeat(32), TransactionScreen.REFERRAL)
    }

    @Test
    fun `a second tap while the bond is followed does nothing`() = runTest {
        programLoaded()
        stubOutcomeTexts()
        val result = CompletableDeferred<TransactionOutcome>()
        var started = 0
        given(interactor.observeBond(any(), eq(false))).willSuspendableAnswer {
            started += 1
            // What the duplicate guard answers a second request while the first is followed.
            if (started > 1) throw DuplicateWaitingTransactionException(txHash, 1L)
            result.await()
        }

        referralViewModel.onBondButtonClick()
        runCurrent()
        // The button is still clickable while its progress mark fades in.
        referralViewModel.onBondButtonClick()
        referralViewModel.onUnbondButtonClick()
        runCurrent()

        // Before: a second coroutine ran into the duplicate guard, cleared the progress mark
        // and asked "send again anyway?" about the bond sent a moment ago.
        assertEquals(1, started)
        verify(interactor, never()).observeUnbond(any(), any())
        assertEquals(true, referralViewModel.referralScreenState.value.common.progress)
        assertNull(referralViewModel.confirmDialogLiveData.value)

        // A balance update during the wait must not bring the button back either.
        assetFlowEmit(TestAssets.xorAsset(BigDecimal.TEN))
        runCurrent()
        assertEquals(true, referralViewModel.referralScreenState.value.common.progress)

        result.complete(TransactionOutcome.Confirmed(txHash, "0xblock"))
        advanceUntilIdle()

        assertProgressCleared()
        verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REFERRAL)
        assertEquals(1, started)
    }

    @Test
    fun `a bond can be sent again after one that did not reach the network`() = runTest {
        programLoaded()
        stubOutcomeTexts()
        var attempts = 0
        whenever(interactor.observeBond(any(), eq(false))).thenAnswer {
            attempts += 1
            if (attempts == 1) throw IOException("BOUNDED_HTTP_IO")
            TransactionOutcome.Confirmed(txHash, "0xblock")
        }

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()
        verify(assetsRouter, never()).showTxResult(any(), any())

        referralViewModel.onBondButtonClick()
        advanceUntilIdle()

        assertEquals(2, attempts)
        verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REFERRAL)
    }

    // --- the result arrives while the screen is not in front -------------------------------

    @Test
    fun `a result that arrives in the background opens its page on return and not before`() =
        runTest {
            programLoaded()
            whenever(interactor.observeBond(any(), eq(false))).thenAnswer {
                // Home was pressed (or the phone locked) while the bond was followed.
                referralViewModel.onScreenPaused()
                TransactionOutcome.Confirmed(txHash, "0xblock")
            }

            referralViewModel.onBondButtonClick()
            advanceUntilIdle()

            // Before the fix the page was opened from the coroutine at this moment. With the
            // activity stopped that navigation is dropped, and with the PIN lock on top the
            // page was put over the PIN lock.
            verify(assetsRouter, never()).showTxResult(any(), any())
            assertProgressCleared()

            // Nothing more is sent from here until the result has been shown.
            referralViewModel.onBondButtonClick()
            referralViewModel.onUnbondButtonClick()
            advanceUntilIdle()
            verify(interactor, times(1)).observeBond(any(), any())
            verify(interactor, never()).observeUnbond(any(), any())

            referralViewModel.onScreenResumed()

            verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REFERRAL)

            // Shown once: a later return to the front opens nothing more.
            referralViewModel.onScreenPaused()
            referralViewModel.onScreenResumed()
            verify(assetsRouter, times(1)).showTxResult(any(), any())
        }

    @Test
    fun `a result is kept while the PIN lock is on top and shown when the screen is back`() =
        runTest {
            programLoaded()
            // The router opens the page only when the referral screen is the current
            // destination.
            var pinLockOnTop = true
            pageOpens { !pinLockOnTop }
            whenever(interactor.observeUnbond(any(), eq(false))).thenAnswer {
                referralViewModel.onScreenPaused()
                TransactionOutcome.Waiting(txHash, acceptedByNode = true)
            }

            referralViewModel.onUnbondButtonClick()
            advanceUntilIdle()

            // The app returns after more than five minutes: the activity resumes this screen
            // for a moment while the PIN lock is already the current destination.
            referralViewModel.onScreenResumed()
            verify(assetsRouter, times(1)).showTxResult(txHash, TransactionScreen.REFERRAL)
            referralViewModel.onScreenPaused()

            // The PIN was entered and the PIN lock removed itself.
            pinLockOnTop = false
            referralViewModel.onScreenResumed()
            verify(assetsRouter, times(2)).showTxResult(txHash, TransactionScreen.REFERRAL)

            // Opened: nothing more is asked of the router, and the screen can be used again.
            referralViewModel.onScreenPaused()
            referralViewModel.onScreenResumed()
            verify(assetsRouter, times(2)).showTxResult(any(), any())
            referralViewModel.onUnbondButtonClick()
            advanceUntilIdle()
            verify(interactor, times(2)).observeUnbond(any(), any())
        }

    @Test
    fun `a network that needs a newer app says so and offers the update instead of another try`() =
        runTest {
            stubOutcomeTexts()
            whenever(interactor.getReferrals()).thenReturn(emptyFlow())
            whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
            // What the signing runtime raises for a live signed extension the app cannot write.
            whenever(interactor.calcBondFee()).thenAnswer {
                throw IllegalStateException(
                    "SORA2_FEE_QUOTE_FAILED",
                    IllegalStateException(NetworkFeeUnavailable.APP_UPDATE_REQUIRED_CODE),
                )
            }

            setupViewModel()
            advanceUntilIdle()
            assetFlowEmit(xorAsset)
            advanceUntilIdle()

            // Before the fix: "can't be checked right now, try again in a moment", for ever.
            val question = checkNotNull(referralViewModel.confirmDialogLiveData.value)
            assertEquals("text-${R.string.network_fee_needs_app_update}", question.message)
            assertEquals("text-${R.string.common_update}", question.confirmText)
            assertEquals("text-${R.string.common_close}", question.dismissText)
            assertNull(referralViewModel.openAppStoreLiveData.value)
            val feeQuestions = feeQuestionCount()

            question.onConfirm()
            advanceUntilIdle()

            // The store page of the app is asked for, the screen that cannot load is left, and
            // the fee is not asked again.
            assertNotNull(referralViewModel.openAppStoreLiveData.value)
            verify(router).popBackStack()
            assertEquals(feeQuestions, feeQuestionCount())
        }

    private fun feeQuestionCount(): Int =
        mockingDetails(interactor).invocations.count { it.method.name == "calcBondFee" }

    @Test
    fun `opening the screen offline explains and recovers instead of crashing`() = runTest {
        stubOutcomeTexts()
        whenever(interactor.getReferrals()).thenReturn(emptyFlow())
        whenever(interactor.observeReferrals()).thenReturn(emptyFlow())
        // RuntimeManager.getMutationRuntimeContext failing with BOUNDED_HTTP_IO, as in the
        // audit's crash log: the fee cannot be read while the XOR balance arrives.
        var offline = true
        whenever(interactor.calcBondFee()).thenAnswer {
            if (offline) throw IOException("BOUNDED_HTTP_IO")
            BigDecimal("0.07")
        }

        setupViewModel()
        advanceUntilIdle()
        assetFlowEmit(xorAsset)
        advanceUntilIdle()

        // Not crashed (an uncaught exception in viewModelScope would fail this test), and the
        // user is told what is wrong, with Try again and Close.
        val question = checkNotNull(referralViewModel.confirmDialogLiveData.value)
        assertEquals("text-${R.string.tx_outcome_screen_needs_network}", question.message)
        assertEquals("text-${R.string.common_try_again}", question.confirmText)
        assertEquals("text-${R.string.common_close}", question.dismissText)

        offline = false
        question.onConfirm()
        advanceUntilIdle()
        referrerBalanceFlowEmit(BigDecimal.ZERO)
        advanceUntilIdle()

        assertEquals(
            ReferralFeatureRoutes.WELCOME_PAGE,
            referralViewModel.navToStart.getOrAwaitValue(),
        )
    }

    @Test fun `bond count accepts zero and rejects negative input while stepper has a minimum of one`() = runTest {
        programLoaded()
        referralViewModel.openBond()
        advanceUntilIdle()
        assertEquals(1, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onBondMinus()
        advanceUntilIdle()
        assertEquals(1, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onBondValueChange(-2)
        advanceUntilIdle()
        assertEquals(1, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onBondValueChange(0)
        advanceUntilIdle()
        assertEquals(0, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onBondValueChange(20)
        advanceUntilIdle()
        assertEquals(2, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onBondPlus()
        advanceUntilIdle()
        assertEquals(3, referralViewModel.referralScreenState.value.bondState.invitationsCount)
    }

    @Test fun `unbond count is capped by invitations funded by the current balance`() = runTest {
        whenever(resourceManager.getString(R.string.referral_invitaion_link_title)).thenReturn("Invitation link")
        programLoaded()
        referrerBalanceFlowEmit(BigDecimal("0.21"))
        advanceUntilIdle()
        referralViewModel.openUnbond()
        advanceUntilIdle()
        assertEquals(1, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onUnbondPlus()
        advanceUntilIdle()
        assertEquals(2, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onUnbondPlus()
        advanceUntilIdle()
        referralViewModel.onUnbondPlus()
        advanceUntilIdle()
        assertEquals(3, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onUnbondValueChange(4)
        referralViewModel.onUnbondValueChange(-1)
        advanceUntilIdle()
        assertEquals(3, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onUnbondMinus()
        advanceUntilIdle()
        assertEquals(2, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        referralViewModel.onUnbondValueChange(1)
        advanceUntilIdle()
        referralViewModel.onUnbondMinus()
        advanceUntilIdle()
        assertEquals(1, referralViewModel.referralScreenState.value.bondState.invitationsCount)
        verify(assetsInteractor, atLeast(1)).isNotEnoughXorLeftAfterTransaction(networkFeeInXor = any(), xorChange = eq(BigDecimal("-0.21")))
    }

    @Test fun `sharing emits the displayed invitation and referral details expand reversibly`() = runTest {
        programLoaded()
        referralViewModel.onShareLink()
        assertEquals(referralViewModel.referralScreenState.value.referralInvitationsCardState.wrappedTextState.text, referralViewModel.shareLinkEvent.value)
        val initial = referralViewModel.referralScreenState.value.referralInvitationsCardState.referrals.isExpanded
        referralViewModel.toggleReferralsCard()
        assertEquals(!initial, referralViewModel.referralScreenState.value.referralInvitationsCardState.referrals.isExpanded)
        referralViewModel.toggleReferralsCard()
        assertEquals(initial, referralViewModel.referralScreenState.value.referralInvitationsCardState.referrals.isExpanded)
    }

    @Test fun `each referral subpage presents the correct toolbar title`() = runTest {
        programLoaded()
        mapOf(
            ReferralFeatureRoutes.BOND_XOR to R.string.referral_add_invitations_title,
            ReferralFeatureRoutes.UNBOND_XOR to R.string.wallet_unbonded,
            ReferralFeatureRoutes.REFERRER_INPUT to R.string.referral_enter_link_title,
            ReferralFeatureRoutes.REFERRER_FILLED to R.string.referral_your_referrer,
            ReferralFeatureRoutes.REFERRAL_PROGRAM to R.string.referral_toolbar_title,
        ).forEach { (route, expected) ->
            referralViewModel.onCurrentDestinationChanged(route)
            assertEquals(expected, referralViewModel.toolbarState.value?.basic?.title)
        }
        referralViewModel.setCurDestination(ReferralFeatureRoutes.REFERRAL_PROGRAM)
        referralViewModel.onBackPressed()
        verify(router).popBackStack()
    }

}
