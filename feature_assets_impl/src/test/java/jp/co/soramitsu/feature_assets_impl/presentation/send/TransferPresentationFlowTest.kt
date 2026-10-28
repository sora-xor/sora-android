@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.feature_assets_impl.presentation.send

import android.graphics.drawable.PictureDrawable
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.*
import jp.co.soramitsu.common.domain.*
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_assets_impl.presentation.screens.send.*
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Presentation and stale-data admission, with no signer, repository mutation or transport. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferPresentationFlowTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val liveData = InstantTaskExecutorRule()
    private val assets = MutableStateFlow(listOf(TestAssets.xorAsset(BigDecimal.TEN), TestAssets.valAsset(BigDecimal.TEN)))
    private val account = MutableStateFlow(SoraAccount("public-wallet", "Wallet"))
    private val interactor = mockk<AssetsInteractor>()
    private val resources = mockk<ResourceManager> { every { getString(any()) } answers { "resource-${firstArg<Int>()}" } }
    private val clipboard = mockk<BasicClipboardManager>(relaxed = true)
    private val walletRouter = mockk<WalletRouter>(relaxed = true)
    private val assetsRouter = mockk<AssetsRouter>(relaxed = true)
    private val avatar = mockk<AccountAvatarGenerator> { every { createAvatar(any(), any()) } returns mockk<PictureDrawable>() }
    private val store = ViewModelStore()
    @Before fun setup() {
        mockkObject(FirebaseWrapper); every { FirebaseWrapper.recordException(any()) } just Runs
        every { interactor.subscribeAssetsActiveOfCurAccount() } returns assets
        every { interactor.flowCurSoraAccount() } returns account
        coEvery { interactor.getCurSoraAccount() } answers { account.value }
        coEvery { interactor.calcTransactionFee(any(), any(), any(), any()) } returns BigDecimal("0.07")
        coEvery { interactor.isNotEnoughXorLeftAfterTransaction(any(), any()) } returns false
    }
    @After fun cleanup() { store.clear(); unmockkObject(FirebaseWrapper) }
    private fun vm(amount: String? = "1", asset: String = assets.value.first().token.id) = TransferAmountViewModel(
        interactor, walletRouter, assetsRouter, NumbersFormatter(), clipboard, resources, avatar, "public-recipient", asset, amount
    ).also { store.put("vm", it) }
    @Test fun selectedDestinationRestoresSearchQuery() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.onToolbarSearch("VAL"); vm.setCurDestination(SendRoutes.selectToken)
        assertEquals("VAL", vm.toolbarState.value!!.basic.searchValue); assertEquals("VAL", vm.sendState.value.searchFilter.filter)
        assertTrue(vm.toolbarState.value!!.basic.searchEnabled); assertEquals(R.string.choose_token, vm.toolbarState.value!!.basic.title)
    }
    @Test fun confirmationMakesAmountReadOnlyAndUsesConfirmationTitle() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.setCurDestination(SendRoutes.confirm)
        assertTrue(vm.sendState.value.input!!.readOnly); assertFalse(vm.toolbarState.value!!.basic.searchEnabled)
        assertEquals(R.string.confirm_sending, vm.toolbarState.value!!.basic.title)
    }
    @Test fun returningToAmountEntryRestoresEditingAndClearsZero() = runTest {
        val vm = vm("0"); advanceUntilIdle(); vm.setCurDestination(SendRoutes.confirm); vm.setCurDestination(SendRoutes.start)
        assertFalse(vm.sendState.value.input!!.readOnly); assertNull(vm.sendState.value.input!!.amount)
        assertEquals(R.string.common_enter_amount, vm.toolbarState.value!!.basic.title)
    }
    @Test fun copyingRecipientProducesCopyEventOnly() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.copyAddress()
        verify(exactly = 1) { clipboard.addToClipboard("public-recipient", any()) }; assertNotNull(vm.copiedToast.value)
    }
    @Test fun reviewNormalizesMissingAmountWithoutSending() = runTest {
        val vm = vm(null); advanceUntilIdle(); vm.onReviewClick()
        assertEquals(BigDecimal.ZERO, vm.sendState.value.input!!.amount)
        coVerify(exactly = 0) { interactor.observeTransfer(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun zeroAmountDisablesReviewWithoutShowingInsufficientFunds() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.amountChanged(BigDecimal.ZERO); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); assertFalse(vm.sendState.value.input!!.error)
    }
    @Test fun negativeAmountCannotBeReviewedOrSubmitted() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.amountChanged(BigDecimal("-1")); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); vm.onConfirmClick(); advanceUntilIdle()
        coVerify(exactly = 0) { interactor.observeTransfer(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun amountAndFeeTogetherMustFitXorBalance() = runTest {
        val vm = vm("9.95"); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); assertEquals("resource-${R.string.amount_error_no_funds}", vm.sendState.value.input!!.errorHint)
    }
    @Test fun nonFeeTokenCanUseItsFullBalanceWhenXorPaysFee() = runTest {
        val vm = vm("10", assets.value[1].token.id); advanceUntilIdle(); assertTrue(vm.sendState.value.reviewEnabled)
    }
    @Test fun nonFeeTokenStillRequiresEnoughXorForFee() = runTest {
        assets.value = listOf(TestAssets.xorAsset(BigDecimal("0.01")), TestAssets.valAsset(BigDecimal.TEN))
        val vm = vm("1", assets.value[1].token.id); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); assertEquals("resource-${R.string.error_transaction_fee_title}", vm.sendState.value.input!!.errorHint)
    }
    @Test fun missingSelectedAssetInvalidatesReadyReview() = runTest {
        val vm = vm(); advanceUntilIdle(); assertTrue(vm.sendState.value.reviewEnabled)
        assets.value = listOf(TestAssets.valAsset(BigDecimal.TEN)); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); vm.onConfirmClick(); advanceUntilIdle()
        coVerify(exactly = 0) { interactor.observeTransfer(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun missingFeeAssetInvalidatesOtherwiseReadyTokenReview() = runTest {
        val vm = vm("1", assets.value[1].token.id); advanceUntilIdle(); assertTrue(vm.sendState.value.reviewEnabled)
        assets.value = listOf(TestAssets.valAsset(BigDecimal.TEN)); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); vm.onConfirmClick(); advanceUntilIdle()
        coVerify(exactly = 0) { interactor.observeTransfer(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun unknownTokenChoiceCannotRetainPreviousReadyInput() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.onTokenChange("missing"); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); assertNull(vm.sendState.value.input)
    }
    @Test fun removedSelectedAssetCanRecoverWhenRestored() = runTest {
        val original = assets.value; val vm = vm(); advanceUntilIdle()
        assets.value = emptyList(); advanceUntilIdle(); assets.value = original; advanceUntilIdle()
        vm.amountChanged(BigDecimal.ONE); advanceUntilIdle(); assertTrue(vm.sendState.value.reviewEnabled)
    }
    @Test fun walletSwitchInvalidatesPreviouslyReviewedFee() = runTest {
        val vm = vm(); advanceUntilIdle(); account.value = SoraAccount("replacement-wallet", "Replacement"); advanceUntilIdle()
        assertFalse(vm.sendState.value.reviewEnabled); vm.onConfirmClick(); advanceUntilIdle()
        coVerify(exactly = 0) { interactor.observeTransfer(any(), any(), any(), any(), any(), any()) }
    }
    @Test fun walletSwitchRetryBindsFeeToNewAccount() = runTest {
        val vm = vm(); advanceUntilIdle(); account.value = SoraAccount("replacement-wallet", "Replacement"); advanceUntilIdle()
        vm.onRetryFee(); advanceUntilIdle(); assertTrue(vm.sendState.value.reviewEnabled)
        coVerify { interactor.calcTransactionFee("public-recipient", any(), BigDecimal.ONE, "replacement-wallet") }
    }
    @Test fun balanceUpdatesCannotReviveAnUnfinishedNewFee() = runTest {
        val vm = vm(); advanceUntilIdle(); val pending = CompletableDeferred<BigDecimal?>()
        coEvery { interactor.calcTransactionFee(any(), any(), any(), any()) } coAnswers { pending.await() }
        vm.amountChanged(BigDecimal("2")); advanceUntilIdle()
        assets.value = listOf(TestAssets.xorAsset(BigDecimal("20")), TestAssets.valAsset(BigDecimal.TEN)); runCurrent()
        assertFalse(vm.sendState.value.reviewEnabled); pending.complete(BigDecimal("0.08")); advanceUntilIdle()
        assertTrue(vm.sendState.value.reviewEnabled)
    }
    @Test fun staleFeeForPreviousTokenCannotReplaceCurrentFee() = runTest {
        val vm = vm(); advanceUntilIdle(); val pending = CompletableDeferred<BigDecimal?>()
        coEvery { interactor.calcTransactionFee(any(), match { it.id == assets.value.first().token.id }, any(), any()) } coAnswers { withContext(NonCancellable) { pending.await() } }
        vm.amountChanged(BigDecimal("2")); advanceUntilIdle(); vm.onTokenChange(assets.value[1].token.id); advanceUntilIdle()
        pending.complete(BigDecimal("9")); advanceUntilIdle()
        assertEquals(assets.value[1].token.id, vm.sendState.value.input!!.token.id); assertTrue(vm.sendState.value.reviewEnabled)
        assertFalse(vm.sendState.value.fee.startsWith("9"))
    }
    @Test fun changingTokenResetsReminderCheck() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.amountChanged(BigDecimal("2")); advanceUntilIdle()
        vm.onTokenChange(assets.value[1].token.id); advanceUntilIdle(); vm.amountChanged(BigDecimal("3")); advanceUntilIdle()
        coVerify { interactor.isNotEnoughXorLeftAfterTransaction(BigDecimal("0.07"), null) }
    }
    @Test fun cancellationOfSubmissionClearsBusyState() = runTest {
        coEvery { interactor.observeTransfer(any(), any(), any(), any(), any(), any()) } throws CancellationException("fixture operation cancelled")
        val vm = vm(); advanceUntilIdle(); vm.onConfirmClick(); advanceUntilIdle()
        assertFalse(vm.sendState.value.inProgress)
        verify(exactly = 0) { assetsRouter.showTxResult(any(), any()) }
    }
    @Test fun clearingViewModelStopsBalanceAndAccountObservers() = runTest {
        val vm = vm(); advanceUntilIdle(); val previous = vm.sendState.value; store.clear()
        assets.value = emptyList(); account.value = SoraAccount("changed", "Changed"); advanceUntilIdle()
        assertEquals(previous, vm.sendState.value)
    }
}
