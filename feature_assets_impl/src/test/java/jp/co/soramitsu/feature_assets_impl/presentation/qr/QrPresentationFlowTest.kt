package jp.co.soramitsu.feature_assets_impl.presentation.qr

import android.graphics.Bitmap
import android.graphics.drawable.PictureDrawable
import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import java.math.BigDecimal
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.*
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus
import jp.co.soramitsu.common.util.*
import jp.co.soramitsu.feature_assets_api.domain.*
import jp.co.soramitsu.feature_assets_impl.presentation.screens.receiverequest.*
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.test_data.TestAssets
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Real QR presentation state; bitmaps, files, clipboard and navigation are inert boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class QrPresentationFlowTest {
    @get:Rule val main = MainCoroutineRule()
    @get:Rule val liveData = InstantTaskExecutorRule()
    private val assets = MutableStateFlow(listOf(TestAssets.xorAsset(BigDecimal.TEN), TestAssets.valAsset(BigDecimal.TEN)))
    private var account = SoraAccount("public-address-one", "Wallet one")
    private val interactor = mockk<AssetsInteractor>()
    private val qr = mockk<QrCodeInteractor>()
    private val generator = mockk<QrCodeGenerator>()
    private val avatar = mockk<AccountAvatarGenerator>()
    private val clipboard = mockk<BasicClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>()
    private val files = mockk<FileManager>()
    private val router = mockk<WalletRouter>(relaxed = true)
    private val bitmap = mockk<Bitmap>()
    private val picture = mockk<PictureDrawable>()
    private val uri = mockk<Uri>()
    private val store = ViewModelStore()
    private val dispatchers = mockk<CoroutineManager> { every { io } returns Dispatchers.Main; every { main } returns Dispatchers.Main }
    @Before fun setup() {
        mockkStatic(Uri::parse); every { Uri.parse(any()) } returns uri
        mockkObject(FirebaseWrapper); every { FirebaseWrapper.recordException(any()) } just Runs
        every { interactor.subscribeAssetsActiveOfCurAccount() } returns assets
        coEvery { interactor.getCurSoraAccount() } answers { account }
        coEvery { interactor.getPublicKeyHex(true) } returns "11".repeat(32)
        every { avatar.createAvatar(any(), any()) } returns picture
        coEvery { qr.createQrInput(any(), any(), any(), any(), any()) } returns "public QR fixture"
        every { generator.generateQrBitmap(any(), any()) } returns bitmap
        every { resources.getString(any()) } answers { "resource-${firstArg<Int>()}" }
        every { resources.getString(R.string.wallet_qr_share_message_empty_template_v2) } returns "%s on %s at %s"
        every { files.writeExternalCacheBitmap(any(), any(), any(), any()) } returns uri
    }
    @After fun cleanup() { store.clear(); unmockkStatic(Uri::parse); unmockkObject(FirebaseWrapper) }
    private fun vm() = QRCodeFlowViewModel(interactor, qr, dispatchers, generator, avatar, clipboard,
        NumbersFormatter(), resources, files, router).also { store.put("vm", it) }
    @Test fun receiveShareUsesRenderedBitmapAndExactAddress() = runTest {
        val vm = vm(); runCurrent(); vm.onShareQrCodeInReceiveScreen()
        assertEquals(uri, vm.shareQrCodeEvent.value!!.first)
        assertTrue(vm.shareQrCodeEvent.value!!.second.endsWith(account.substrateAddress))
        verify(exactly = 1) { files.writeExternalCacheBitmap(bitmap, "qrcodefile.png", Bitmap.CompressFormat.PNG, 100) }
    }
    @Test fun receiveShareBeforeLoadingCreatesNoFile() = runTest {
        coEvery { interactor.getCurSoraAccount() } coAnswers { awaitCancellation() }
        val vm = vm(); runCurrent(); vm.onShareQrCodeInReceiveScreen()
        verify(exactly = 0) { files.writeExternalCacheBitmap(any(), any(), any(), any()) }; assertNull(vm.shareQrCodeEvent.value)
    }
    @Test fun requestShareBeforeConfirmationCreatesNoFile() = runTest {
        val vm = vm(); runCurrent(); vm.onShareQrCodeInRequestConfirmScreen()
        verify(exactly = 0) { files.writeExternalCacheBitmap(any(), any(), any(), any()) }
    }
    @Test fun requestShareUsesConfirmedTokenAndBitmap() = runTest {
        val vm = vm(); runCurrent(); vm.onSelectToken(assets.value[1].token.id); vm.onRequestAmountChange(BigDecimal("1.25")); runCurrent()
        vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent(); vm.onShareQrCodeInRequestConfirmScreen()
        assertEquals(uri, vm.shareQrCodeEvent.value!!.first)
        assertTrue(vm.shareQrCodeEvent.value!!.second.startsWith(assets.value[1].token.symbol))
        assertTrue(vm.requestTokenConfirmScreenState.value.assetAmountInputState!!.readOnly)
        assertFalse(vm.requestTokenConfirmScreenState.value.assetAmountInputState!!.enabled)
    }
    @Test fun exactDecimalAmountIsPassedToQrEncoder() = runTest {
        val vm = vm(); runCurrent(); vm.onRequestAmountChange(BigDecimal("0.000000000000000001"))
        vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        coVerify { qr.createQrInput(account.substrateAddress, "11".repeat(32), account.accountName,
            assets.value.first().token.id, "0.000000000000000001") }
    }
    @Test fun searchTextSurvivesLeavingAndReturningToTokenPicker() = runTest {
        val vm = vm(); runCurrent(); vm.onToolbarSearch("VAL"); vm.onCurrentDestinationChanged(QRCodeFlowRoute.MainScreen.route)
        assertFalse(vm.toolbarState.value!!.basic.searchEnabled); assertEquals("", vm.toolbarState.value!!.basic.searchValue)
        vm.onCurrentDestinationChanged(QRCodeFlowRoute.SelectToken.route)
        assertTrue(vm.toolbarState.value!!.basic.searchEnabled); assertEquals("VAL", vm.toolbarState.value!!.basic.searchValue)
        assertEquals("VAL", vm.selectTokenScreenState.value.filter)
    }
    @Test fun confirmationToolbarUsesReceiveTitle() = runTest {
        val vm = vm(); vm.onCurrentDestinationChanged(QRCodeFlowRoute.ConfirmRequestByQRCode.route)
        assertEquals(R.string.receive_tokens, vm.toolbarState.value!!.basic.title)
        assertEquals(QRCodeFlowRoute.MainScreen.route, vm.startScreen())
    }
    @Test fun unknownRouteClearsSearchAndTitle() = runTest {
        val vm = vm(); vm.onToolbarSearch("VAL"); vm.onCurrentDestinationChanged("unknown")
        assertEquals("", vm.toolbarState.value!!.basic.title); assertFalse(vm.toolbarState.value!!.basic.searchEnabled)
    }
    @Test fun unreadyCopyDoesNotOverwriteClipboard() = runTest {
        coEvery { interactor.getCurSoraAccount() } coAnswers { awaitCancellation() }
        val vm = vm(); runCurrent(); vm.onUserAddressClickInReceiveScreen(); vm.onUserAddressClickInRequestConfirmScreen()
        verify(exactly = 0) { clipboard.addToClipboard(any(), any()) }
    }
    private suspend fun TestScope.scanError(error: QrException, resource: Int) {
        coEvery { qr.processQrResult("invalid") } throws error
        val vm = vm(); runCurrent(); vm.onReceiveQRCodeScanUriResult("invalid"); runCurrent()
        assertEquals("resource-${R.string.status_error}" to "resource-$resource", vm.alertDialogLiveData.value)
        verify(exactly = 0) { router.showValTransferAmount(any(), any(), any()) }
    }
    @Test fun unknownRecipientDisplaysSpecificScanError() = runTest { scanError(QrException.userNotFoundError(), R.string.invoice_scan_error_user_not_found) }
    @Test fun selfTransferDisplaysSpecificScanError() = runTest { scanError(QrException.sendingToMyselfError(), R.string.invoice_scan_error_match) }
    @Test fun malformedQrDisplaysSpecificScanError() = runTest { scanError(QrException.decodeError(), R.string.invoice_scan_error_no_info) }
    @Test fun scanCancellationNeverNavigates() = runTest {
        coEvery { qr.processQrResult("cancel") } throws CancellationException("fixture")
        val vm = vm(); runCurrent(); val job = vm.onReceiveQRCodeScanUriResult("cancel"); runCurrent()
        assertTrue(job.isCancelled); verify(exactly = 0) { router.showValTransferAmount(any(), any(), any()) }
    }
    @Test fun receiveReloadCanRecoverFromBitmapFailure() = runTest {
        every { generator.generateQrBitmap(any(), any()) } throws IllegalStateException("bitmap failed")
        val vm = vm(); runCurrent(); assertEquals(ScreenStatus.ERROR, vm.receiveTokenByQrScreenState.value.screenStatus)
        every { generator.generateQrBitmap(any(), any()) } returns bitmap
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent(); assertEquals(ScreenStatus.READY_TO_RENDER, vm.receiveTokenByQrScreenState.value.screenStatus)
    }
    @Test fun requestConfirmationCanRecoverFromEncoderFailure() = runTest {
        val vm = vm(); runCurrent(); coEvery { qr.createQrInput(any(), any(), any(), any(), any()) } throws IllegalStateException("encoder failed")
        vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent(); assertEquals(ScreenStatus.ERROR, vm.requestTokenConfirmScreenState.value.screenStatus)
        coEvery { qr.createQrInput(any(), any(), any(), any(), any()) } returns "recovered"
        vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent(); assertEquals(ScreenStatus.READY_TO_RENDER, vm.requestTokenConfirmScreenState.value.screenStatus)
    }
    @Test fun emptyAssetsCanRecoverWithoutRecreatingViewModel() = runTest {
        assets.value = emptyList(); val vm = vm(); runCurrent(); assertEquals(ScreenStatus.ERROR, vm.requestTokenScreenState.value.screenStatus)
        assets.value = listOf(TestAssets.valAsset(BigDecimal.TEN)); runCurrent()
        assertEquals(ScreenStatus.READY_TO_RENDER, vm.requestTokenScreenState.value.screenStatus)
        assertEquals(assets.value.single().token.id, vm.requestTokenScreenState.value.assetAmountInputState!!.token.id)
    }
    @Test fun missingSelectedTokenFallsBackToAvailableAsset() = runTest {
        val vm = vm(); runCurrent(); vm.onSelectToken("missing"); runCurrent()
        assertEquals(assets.value.first().token.id, vm.requestTokenScreenState.value.assetAmountInputState!!.token.id)
    }
    @Test fun accountLookupFailureDoesNotKillRequestReload() = runTest {
        coEvery { interactor.getCurSoraAccount() } throws IllegalStateException("locked")
        val vm = vm(); runCurrent(); assertEquals(ScreenStatus.ERROR, vm.requestTokenScreenState.value.screenStatus)
        coEvery { interactor.getCurSoraAccount() } answers { account }
        vm.onLoadRequestScreenDataClick(); runCurrent()
        assertEquals(ScreenStatus.READY_TO_RENDER, vm.requestTokenScreenState.value.screenStatus)
    }
    @Test fun failedReceiveReloadCannotSharePreviousAccountsQr() = runTest {
        val vm = vm(); runCurrent(); coEvery { interactor.getCurSoraAccount() } throws IllegalStateException("account changed")
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent(); vm.onShareQrCodeInReceiveScreen()
        verify(exactly = 0) { files.writeExternalCacheBitmap(any(), any(), any(), any()) }
    }
    @Test fun failedConfirmationReloadCannotSharePreviousAmountQr() = runTest {
        val vm = vm(); runCurrent(); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        coEvery { qr.createQrInput(any(), any(), any(), any(), any()) } throws IllegalStateException("encoder failed")
        vm.onRequestAmountChange(BigDecimal("7")); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent(); vm.onShareQrCodeInRequestConfirmScreen()
        verify(exactly = 0) { files.writeExternalCacheBitmap(any(), any(), any(), any()) }
    }
    @Test fun confirmationCopyUsesDisplayedAccountAfterRequestChanges() = runTest {
        val vm = vm(); runCurrent(); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        val displayed = account.substrateAddress; account = SoraAccount("public-address-two", "Wallet two")
        vm.onLoadRequestScreenDataClick(); runCurrent(); vm.onUserAddressClickInRequestConfirmScreen()
        verify(exactly = 1) { clipboard.addToClipboard(displayed, any()) }
    }
    @Test fun asynchronousConfirmationKeepsQrAndDisplayedAmountConsistent() = runTest {
        val vm = vm(); runCurrent(); vm.onRequestAmountChange(BigDecimal("1.25"))
        val release = CompletableDeferred<Unit>()
        coEvery { qr.createQrInput(any(), any(), any(), any(), any()) } coAnswers { release.await(); "first amount qr" }
        vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent(); vm.onRequestAmountChange(BigDecimal("9.5")); release.complete(Unit); runCurrent()
        assertEquals(BigDecimal("1.25"), vm.requestTokenConfirmScreenState.value.assetAmountInputState!!.amount)
    }
    @Test fun clearingViewModelStopsAssetCollection() = runTest {
        val vm = vm(); runCurrent(); val prior = vm.requestTokenScreenState.value; store.clear()
        assets.value = emptyList(); runCurrent(); assertEquals(prior, vm.requestTokenScreenState.value)
    }
    @Test fun receiveReloadCannotShareOldQrWhileNewAccountLoads() = runTest {
        val vm = vm(); runCurrent()
        coEvery { interactor.getCurSoraAccount() } coAnswers { awaitCancellation() }
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent(); vm.onShareQrCodeInReceiveScreen()
        verify(exactly = 0) { files.writeExternalCacheBitmap(any(), any(), any(), any()) }
    }
    @Test fun failedReceiveReloadCannotCopyPreviousAccountsAddress() = runTest {
        val vm = vm(); runCurrent(); coEvery { interactor.getCurSoraAccount() } throws IllegalStateException("account changed")
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent(); vm.onUserAddressClickInReceiveScreen()
        verify(exactly = 0) { clipboard.addToClipboard(any(), any()) }
    }
    @Test fun incompleteRequestCannotBecomeReadyConfirmation() = runTest {
        assets.value = emptyList(); val vm = vm(); runCurrent(); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        assertEquals(ScreenStatus.ERROR, vm.requestTokenConfirmScreenState.value.screenStatus)
        assertNull(vm.requestTokenConfirmScreenState.value.untransformedQrBitmap)
    }
    @Test fun olderReceiveRequestCannotReplaceNewAccountsQr() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { qr.createQrInput("public-address-one", any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { release.await() }; "old account qr"
        }
        val vm = vm(); runCurrent(); account = SoraAccount("public-address-two", "Wallet two")
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent(); release.complete(Unit); runCurrent()
        assertEquals("public-address-two", vm.receiveTokenByQrScreenState.value.untransformedUserAddress)
    }
    @Test fun olderConfirmationCannotReplaceLaterAmount() = runTest {
        val vm = vm(); runCurrent(); val release = CompletableDeferred<Unit>()
        val oldBitmap = mockk<Bitmap>()
        every { generator.generateQrBitmap("old amount qr", any()) } returns oldBitmap
        coEvery { qr.createQrInput(any(), any(), any(), any(), "1.25") } coAnswers {
            withContext(NonCancellable) { release.await() }; "old amount qr"
        }
        vm.onRequestAmountChange(BigDecimal("1.25")); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        vm.onRequestAmountChange(BigDecimal("9.5")); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        val newerBitmap = vm.requestTokenConfirmScreenState.value.untransformedQrBitmap
        release.complete(Unit); runCurrent()
        assertEquals(BigDecimal("9.5"), vm.requestTokenConfirmScreenState.value.assetAmountInputState!!.amount)
        assertSame(newerBitmap, vm.requestTokenConfirmScreenState.value.untransformedQrBitmap)
        coVerify(exactly = 1) { qr.createQrInput(any(), any(), any(), any(), "9.5") }
    }


    @Test fun failedSupersededReceiveCannotHideTheCurrentAccountsQr() = runTest {
        val release = CompletableDeferred<Unit>()
        coEvery { qr.createQrInput("public-address-one", any(), any(), any(), any()) } coAnswers {
            withContext(NonCancellable) { release.await(); throw IllegalStateException("old request failed") }
        }
        val vm = vm(); runCurrent(); account = SoraAccount("public-address-two", "Wallet two")
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent()
        val current = vm.receiveTokenByQrScreenState.value
        assertEquals(ScreenStatus.READY_TO_RENDER, current.screenStatus)
        release.complete(Unit); runCurrent()
        assertEquals(current, vm.receiveTokenByQrScreenState.value)
        assertNull(vm.errorLiveData.value); assertNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun failedSupersededConfirmationCannotHideTheCurrentAmountQr() = runTest {
        val vm = vm(); runCurrent(); val release = CompletableDeferred<Unit>()
        coEvery { qr.createQrInput(any(), any(), any(), any(), "1.25") } coAnswers {
            withContext(NonCancellable) { release.await(); throw IllegalStateException("old request failed") }
        }
        vm.onRequestAmountChange(BigDecimal("1.25")); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        vm.onRequestAmountChange(BigDecimal("9.5")); vm.onLoadRequestConfirmScreenDataAgainClick(); runCurrent()
        val current = vm.requestTokenConfirmScreenState.value
        assertEquals(ScreenStatus.READY_TO_RENDER, current.screenStatus)
        release.complete(Unit); runCurrent()
        assertEquals(current, vm.requestTokenConfirmScreenState.value)
        assertNull(vm.errorLiveData.value); assertNull(vm.errorFromResourceLiveData.value)
    }


    @Test fun reloadDuringAvatarGenerationCannotPublishTheSupersededAccount() = runTest {
        val vm = vm(); runCurrent(); val release = CompletableDeferred<Unit>(); var requested = false
        coEvery { qr.createQrInput("public-address-two", any(), any(), any(), any()) } coAnswers {
            release.await(); "new account qr"
        }
        every { avatar.createAvatar("public-address-one", any()) } answers {
            if (!requested) {
                requested = true
                account = SoraAccount("public-address-two", "Wallet two")
                vm.onLoadReceiveScreenDataAgainClick()
            }
            picture
        }
        vm.onLoadReceiveScreenDataAgainClick(); runCurrent()
        assertEquals(ScreenStatus.LOADING, vm.receiveTokenByQrScreenState.value.screenStatus)
        assertNull(vm.receiveTokenByQrScreenState.value.untransformedUserAddress)
        release.complete(Unit); runCurrent()
        assertEquals("public-address-two", vm.receiveTokenByQrScreenState.value.untransformedUserAddress)
    }

}
