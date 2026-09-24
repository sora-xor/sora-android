@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package jp.co.soramitsu.sora.ux

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.android.Intents
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkClass
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QRCodeScannerActivity
import jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrCodeScannerViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import jp.co.soramitsu.feature_wallet_impl.data.nexus.*
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.feature_wallet_impl.presentation.cardshub.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Test-only acceptance checks. No production activity/manifest, signer or transport changes. */
@RunWith(AndroidJUnit4::class)
class WalletUxAcceptanceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val screenshotDirectory get() = File(context.getExternalFilesDir(null), "ux-acceptance").apply { mkdirs() }
    private val sampleRecipient = "SampleRecipientForUxOnly1234567890123456789012345678901234567890"

    @Before fun enableTestProcessDrawing() {
        instrumentation.runOnMainSync { HardwareRenderer.setDrawingEnabled(true) }
    }

    @Test fun realQrBitmapAndPhotoUriDecodeAndInvalidImage() {
        val decoder = QrCodeDecoder(context.contentResolver)
        val matrix = MultiFormatWriter().encode(sampleRecipient, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        val photo = File(context.cacheDir, "ux-recipient-qr.png")
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        assertEquals(sampleRecipient, decoder.decodeQrFromUri(Uri.fromFile(photo)))
        val invalid = File(context.cacheDir, "ux-invalid-photo.png")
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { blank ->
            blank.eraseColor(Color.WHITE)
            invalid.outputStream().use { blank.compress(Bitmap.CompressFormat.PNG, 100, it) }
            blank.recycle()
        }
        assertThrows(Exception::class.java) { decoder.decodeQrFromUri(Uri.fromFile(invalid)) }
        photo.delete(); invalid.delete()
    }

    @Test fun narrowSendPasteKeyboardAndExactReview() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            val state = mutableStateOf(NexusSendUiState(visible = true, network = sampleNetwork()))
            var confirmed = false
            scenario.onActivity { activity ->
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("sample recipient", sampleRecipient))
                activity.setContent {
                    SoraAppTheme {
                        NexusSendDialog(state.value, { state.value = state.value.copy(visible = false) },
                            { state.value = state.value.copy(recipient = it) },
                            { state.value = state.value.copy(amount = it) },
                            { state.value = state.value.copy(prepared = prepared(state.value.recipient, state.value.amount)) },
                            { confirmed = true }, { state.value = state.value.copy(prepared = null) }, {}, {})
                    }
                }
            }
            waitUntil { findText("Paste address") != null }
            clickText("Paste address")
            waitUntil { state.value.recipient == sampleRecipient }
            revealText("Amount in XOR")
            val input = findText("Amount in XOR") ?: error("Amount input missing")
            val editable = findEditable(input) ?: editableNodes().lastOrNull() ?: error("Amount input not editable")
            editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "12.5")
            })
            waitUntil { state.value.amount == "12.5" }
            waitUntil {
                val visible = java.util.concurrent.atomic.AtomicBoolean(false)
                instrumentation.runOnMainSync {
                    visible.set(android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                        it.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) == true
                    })
                }
                visible.get()
            }
            val titleBounds = Rect().also { requireNotNull(findText("Send XOR")).getBoundsInScreen(it) }
            assertTrue("Keyboard must keep send header on screen", titleBounds.top >= 0 && titleBounds.bottom > 0)
            capture("send-paste-keyboard")
            clickText("Review")
            waitUntil { state.value.prepared != null }
            capture("send-review-top")
            revealText("Total: 12.51 XOR")
            assertNotNull(findText("Total: 12.51 XOR"))
            assertEquals(sampleRecipient, state.value.recipient)
            capture("send-exact-review")
            assertFalse(confirmed)
            clickText("Edit transfer")
            waitUntil { state.value.prepared == null }
            assertEquals(sampleRecipient, state.value.recipient)
            assertEquals("12.5", state.value.amount)
        }
    }

    @Test fun photoPickerResultDecodesIntoActualScannerResultAndCancelStaysOpen() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)
        val photo = qrPhoto(sampleRecipient)
        val contract = jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl()
            .create(jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose.RECIPIENT)
        val intent = contract.createIntent(context, Unit)
        val monitor = instrumentation.addMonitor(android.content.IntentFilter("android.provider.action.PICK_IMAGES").apply {
                addAction(Intent.ACTION_OPEN_DOCUMENT); addAction(Intent.ACTION_GET_CONTENT); addDataType("image/*")
            },
            Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.soraFileProvider", photo))), true)
        try {
            ActivityScenario.launchActivityForResult<QRCodeScannerActivity>(intent).use { scenario ->
                waitUntil { findText("Upload from library") != null }
                clickText("Upload from library")
                waitUntil { scenario.state == Lifecycle.State.DESTROYED }
                assertEquals(1, monitor.hits)
                assertEquals(sampleRecipient, contract.parseResult(scenario.result.resultCode, scenario.result.resultData))
            }
        } finally { instrumentation.removeMonitor(monitor); photo.delete() }
        val cancelled = instrumentation.addMonitor(android.content.IntentFilter("android.provider.action.PICK_IMAGES").apply {
                addAction(Intent.ACTION_OPEN_DOCUMENT); addAction(Intent.ACTION_GET_CONTENT); addDataType("image/*")
            },
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            ActivityScenario.launchActivityForResult<QRCodeScannerActivity>(intent).use { scenario ->
                waitUntil { findText("Upload from library") != null }
                clickText("Upload from library")
                instrumentation.waitForIdleSync()
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                assertEquals(1, cancelled.hits)
                scenario.onActivity { it.finish() }
                assertNull(contract.parseResult(scenario.result.resultCode, scenario.result.resultData))
            }
        } finally { instrumentation.removeMonitor(cancelled) }
    }

    @Test fun invalidPhotoKeepsActualScannerAvailableForAnotherAttempt() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)
        val photo = File(context.cacheDir, "ux-invalid-picker.png")
        Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.soraFileProvider", photo)
        val contract = jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl()
            .create(jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose.RECIPIENT)
        val monitor = instrumentation.addMonitor(android.content.IntentFilter("android.provider.action.PICK_IMAGES").apply {
            addAction(Intent.ACTION_OPEN_DOCUMENT); addAction(Intent.ACTION_GET_CONTENT); addDataType("image/*")
        }, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
        try {
            ActivityScenario.launchActivityForResult<QRCodeScannerActivity>(contract.createIntent(context, Unit)).use { scenario ->
                waitUntil { findText("Upload from library") != null }
                clickText("Upload from library")
                val failed = java.util.concurrent.atomic.AtomicBoolean(false)
                waitUntil {
                    scenario.onActivity { activity ->
                        val delegate = QRCodeScannerActivity::class.java.getDeclaredField("viewModel\$delegate")
                            .apply { isAccessible = true }.get(activity) as Lazy<*>
                        failed.set((delegate.value as QrCodeScannerViewModel).qrCodeScannerScreenState.screenStatus ==
                            jp.co.soramitsu.common.presentation.compose.uikit.tokens.ScreenStatus.ERROR)
                    }
                    failed.get()
                }
                assertEquals(1, monitor.hits)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                assertNotNull(findText("Upload from library"))
                capture("scanner-invalid-photo-recovery")
                scenario.onActivity { it.finish() }
                assertNull(contract.parseResult(scenario.result.resultCode, scenario.result.resultData))
            }
        } finally { instrumentation.removeMonitor(monitor); photo.delete() }
    }

    @Test fun activityResultReachesRealFragmentAndViewModelAndRejectsEditedDraft() {
        val vm = realSendViewModel()
        val network = sampleNetwork()
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            lateinit var fragment: CardsHubFragment
            scenario.onActivity { activity ->
                fragment = CardsHubFragment()
                CardsHubFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
                    .set(fragment, lazyOf(vm))
                activity.supportFragmentManager.beginTransaction().add(fragment, "ux-scanner-contract")
                    .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow()
                // Only activate its result callback. No mocked fragment view or wallet navigation is installed.
                (fragment.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_START)
                bindSampleWallet(vm, network)
                vm.openNexusSend(network); vm.setNexusAmount("12.5")
            }
            val result = Intent().putExtra(Intents.Scan.RESULT, sampleRecipient)
            val monitor = instrumentation.addMonitor(QRCodeScannerActivity::class.java.name,
                Instrumentation.ActivityResult(Activity.RESULT_OK, result), true)
            try {
                scenario.onActivity {
                    val action = CardsHubFragment::class.java.getDeclaredMethod("scanNexusRecipient").apply { isAccessible = true }
                    action.invoke(fragment); action.invoke(fragment)
                }
                waitUntil { vm.nexusSend.value.recipient == sampleRecipient }
                assertEquals(1, monitor.hits)
                assertEquals("12.5", vm.nexusSend.value.amount)
                scenario.onActivity {
                    CardsHubFragment::class.java.getDeclaredMethod("scanNexusRecipient").apply { isAccessible = true }.invoke(fragment)
                    vm.setNexusRecipient("edited-while-scanning")
                }
                instrumentation.waitForIdleSync()
                assertEquals("edited-while-scanning", vm.nexusSend.value.recipient)
                assertEquals(2, monitor.hits)
            } finally { instrumentation.removeMonitor(monitor) }
            scenario.onActivity {
                (fragment.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                it.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            }
        }
    }

    @Test fun decodedCameraResultUsesRecipientContractWithoutOpeningConnect() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)
        val text = "sora://iroha-connect?sample-only=1"
        val contract = jp.co.soramitsu.feature_assets_impl.presentation.screens.scan.QrScannerContractFactoryImpl()
            .create(jp.co.soramitsu.feature_assets_api.presentation.QrScanPurpose.RECIPIENT)
        ActivityScenario.launchActivityForResult<QRCodeScannerActivity>(contract.createIntent(context, Unit)).use { scenario ->
            scenario.onActivity { activity ->
                val manager = QRCodeScannerActivity::class.java.getDeclaredField("capture").apply { isAccessible = true }.get(activity)
                val decoded = com.journeyapps.barcodescanner.BarcodeResult(
                    com.google.zxing.Result(text, null, null, BarcodeFormat.QR_CODE), null)
                manager.javaClass.getDeclaredMethod("returnResult", com.journeyapps.barcodescanner.BarcodeResult::class.java)
                    .apply { isAccessible = true }.invoke(manager, decoded)
            }
            waitUntil { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(text, contract.parseResult(scenario.result.resultCode, scenario.result.resultData))
        }
    }

    @Test fun deniedNullActivityResultPreservesDraftAndShowsPasteRecovery() {
        // Run separately after revoking CAMERA from the shell; revoking it inside the test kills its host.
        org.junit.Assume.assumeTrue(context.checkSelfPermission(android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED)
        val vm = realSendViewModel()
        val network = sampleNetwork()
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            lateinit var fragment: CardsHubFragment
            scenario.onActivity { activity ->
                fragment = CardsHubFragment()
                CardsHubFragment::class.java.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }
                    .set(fragment, lazyOf(vm))
                activity.supportFragmentManager.beginTransaction().add(fragment, "ux-denied-scanner")
                    .setMaxLifecycle(fragment, Lifecycle.State.CREATED).commitNow()
                (fragment.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_START)
                bindSampleWallet(vm, network)
                vm.openNexusSend(network); vm.setNexusAmount("12.5"); vm.setNexusRecipient(sampleRecipient)
            }
            val monitor = instrumentation.addMonitor(QRCodeScannerActivity::class.java.name,
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            try {
                scenario.onActivity {
                    CardsHubFragment::class.java.getDeclaredMethod("scanNexusRecipient").apply { isAccessible = true }.invoke(fragment)
                }
                waitUntil { vm.nexusSend.value.scannerPermissionDenied }
                assertEquals(sampleRecipient, vm.nexusSend.value.recipient)
                assertEquals("12.5", vm.nexusSend.value.amount)
                scenario.onActivity { activity ->
                    activity.setContent {
                        val state by vm.nexusSend.collectAsState()
                        SoraAppTheme {
                            NexusSendDialog(state, {}, vm::setNexusRecipient, vm::setNexusAmount,
                                {}, {}, {}, {}, {})
                        }
                    }
                }
                waitUntil { findText("Camera access is unavailable") != null }
                assertNotNull(findText("Paste address"))
                capture("send-denied-preserved-draft")
                // Changing wallet while a result is queued must not apply the previous wallet's scan.
                scenario.onActivity {
                    CardsHubFragment::class.java.getDeclaredMethod("scanNexusRecipient").apply { isAccessible = true }.invoke(fragment)
                    val replacement = network.copy(walletId = "replacement-wallet")
                    vm.closeNexusSend(); bindSampleWallet(vm, replacement); vm.openNexusSend(replacement)
                    vm.setNexusRecipient("new-wallet-draft")
                }
                instrumentation.waitForIdleSync()
                assertEquals("new-wallet-draft", vm.nexusSend.value.recipient)
                assertFalse(vm.nexusSend.value.scannerPermissionDenied)
            } finally { instrumentation.removeMonitor(monitor) }
            scenario.onActivity {
                (fragment.lifecycle as LifecycleRegistry).handleLifecycleEvent(Lifecycle.Event.ON_STOP)
                it.supportFragmentManager.beginTransaction().remove(fragment).commitNow()
            }
        }
    }

    @Test fun networkPortfolioRoutesActivityReceiveAndSendForSelectedWallet() {
        val network = sampleNetwork()
        val send = mutableStateOf(NexusSendUiState())
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    SoraAppTheme {
                        NexusPortfolioCard(Sora2PortfolioBalance(network.walletId, "125.25"), listOf(network),
                            {}, {}, { true }, { selected -> send.value = NexusSendUiState(visible = true, network = selected) },
                            { it.walletId == network.walletId }, {})
                        NexusSendDialog(send.value, { send.value = NexusSendUiState() }, {}, {}, {}, {}, {}, {}, {})
                    }
                }
            }
            waitUntil { findText("Taira") != null }
            clickText("Taira")
            waitUntil { findText(context.getString(jp.co.soramitsu.common.R.string.wallet_no_activity)) != null }
            capture("network-empty-activity")
            clickText("Receive")
            waitUntil { findText("Receive XOR") != null }
            assertNotNull(findText(network.address))
            capture("network-receive")
            clickText("Close")
            waitUntil { findText(context.getString(jp.co.soramitsu.common.R.string.wallet_no_activity)) != null }
            clickText("Send")
            waitUntil { findText("Send XOR") != null }
            assertEquals(network, send.value.network)
            capture("network-to-send")
        }
    }

    @Test fun normalLocalSampleWalletReachesHomeAndCenteredPolkaswap() {
        // A disposable local account is created through the production onboarding model.
        // No phrase is read/exported, and no transport or signing action is invoked.
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            waitUntil { findText("Create account") != null }
            clickText("Create account")
            scenario.onActivity { activity ->
                activity.viewModel.onAccountNameChanged(androidx.compose.ui.text.input.TextFieldValue("UX sample wallet"))
                val nav = OnboardingActivity::class.java.getDeclaredField("navController").apply { isAccessible = true }
                    .get(activity) as androidx.navigation.NavController
                activity.viewModel.onCreateAccountContinueClicked(nav)
            }
            val generated = java.util.concurrent.atomic.AtomicBoolean(false)
            waitUntil {
                scenario.onActivity { activity ->
                    generated.set(activity.viewModel.javaClass.getDeclaredField("tempAccount").apply { isAccessible = true }
                        .get(activity.viewModel) != null)
                }
                generated.get()
            }
            scenario.onActivity { it.viewModel.skipDialogConfirm(it) }
            lateinit var main: jp.co.soramitsu.feature_main_impl.presentation.MainActivity
            waitUntil {
                var found = false
                instrumentation.runOnMainSync {
                    val resumed = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    val candidate = resumed.filterIsInstance<jp.co.soramitsu.feature_main_impl.presentation.MainActivity>().firstOrNull()
                    if (candidate != null) { main = candidate; found = true }
                }
                found
            }
            lateinit var pin: jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeFragment
            waitUntil {
                var found = false
                instrumentation.runOnMainSync {
                    fun visit(fragments: List<androidx.fragment.app.Fragment>) {
                        fragments.forEach { fragment ->
                            if (fragment is jp.co.soramitsu.feature_main_impl.presentation.pincode.PincodeFragment) {
                                pin = fragment; found = true
                            } else visit(fragment.childFragmentManager.fragments)
                        }
                    }
                    visit(main.supportFragmentManager.fragments)
                }
                found
            }
            // The isolated device's selected RPC is unavailable. Supply only the connection
            // observation to the actual PIN model; its local migration admission still runs.
            instrumentation.runOnMainSync {
                val delegate = pin.viewModel.javaClass.getDeclaredField("state\$delegate")
                    .apply { isAccessible = true }.get(pin.viewModel) as MutableState<jp.co.soramitsu.feature_main_impl.presentation.pincode.PinCodeScreenState>
                delegate.value = delegate.value.copy(isConnected = true)
            }
            repeat(2) {
                instrumentation.runOnMainSync {
                    repeat(pin.viewModel.state.maxDotsCount) { pin.viewModel.pinCodeNumberClicked("1") }
                }
                instrumentation.waitForIdleSync()
            }
            waitUntil { findText("Wallet") != null }
            capture("normal-sample-wallet-home")
            instrumentation.runOnMainSync {
                val fab = main.findViewById<android.view.View>(jp.co.soramitsu.feature_main_impl.R.id.fabMain)
                val location = IntArray(2); fab.getLocationOnScreen(location)
                val displayWidth = main.resources.displayMetrics.widthPixels
                assertTrue(kotlin.math.abs(location[0] + fab.width / 2 - displayWidth / 2) <= 2)
                assertTrue(fab.performClick())
            }
            // This walkthrough starts after pm clear, so wait for the delayed first-run
            // disclaimer before checking the final route (avoid capturing during navigation).
            waitUntil { findText("Disclaimer") != null }
            capture("normal-polkaswap-disclaimer")
            revealText("Close")
            clickText("Close")
            waitUntil { findText("Disclaimer") == null && findText(context.getString(jp.co.soramitsu.common.R.string.wallet_assets_unavailable_details)) != null }
            capture("normal-centered-polkaswap")
            instrumentation.runOnMainSync { main.finish() }
        }
    }

    private fun realSendViewModel(): CardsHubViewModel {
        val assets = mockk<AssetsInteractor>(relaxed = true)
        every { assets.flowCurSoraAccount() } returns emptyFlow()
        val cards = mockk<CardsHubInteractorImpl>(relaxed = true)
        every { cards.subscribeVisibleCardsHubList() } returns emptyFlow()
        val portfolio = mockk<NexusPortfolioRepository>(relaxed = true)
        every { portfolio.observeCurrentWallet() } returns emptyFlow()
        val result = AtomicReference<CardsHubViewModel>()
        instrumentation.runOnMainSync {
            val constructor = CardsHubViewModel::class.java.constructors.single()
            val arguments = constructor.parameterTypes.map { type ->
                when (type) {
                    AssetsInteractor::class.java -> assets
                    CardsHubInteractorImpl::class.java -> cards
                    NexusPortfolioRepository::class.java -> portfolio
                    else -> mockkClass(type.kotlin, relaxed = true)
                }
            }.toTypedArray()
            result.set(constructor.newInstance(*arguments) as CardsHubViewModel)
        }
        return result.get()
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindSampleWallet(vm: CardsHubViewModel, network: NexusPortfolioBalance) {
        fun field(name: String) = CardsHubViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
        field("selectedPortfolioWalletId").set(vm, network.walletId)
        (field("_sora2Portfolio").get(vm) as MutableStateFlow<Sora2PortfolioBalance?>).value = Sora2PortfolioBalance(network.walletId, "125.25")
        (field("_nexusPortfolio").get(vm) as MutableStateFlow<List<NexusPortfolioBalance>>).value = listOf(network)
    }

    private fun qrPhoto(value: String): File {
        val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        for (y in 0 until 512) for (x in 0 until 512) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        return File(context.cacheDir, "ux-picker-qr.png").also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }

    private fun sampleNetwork() = NexusPortfolioBalance("sample", WalletNetworkId.TAIRA, "Taira", true,
        "SampleSenderForLayoutOnly123456789012345678901234567890", "125.25", "xor", "https://example.com", true,
        emptyList(), emptyList(), null, null)

    private fun prepared(recipient: String, amount: String): NexusPreparedSend {
        val network = sampleNetwork()
        val account = jp.co.soramitsu.core_db.model.NetworkAccountLocal("sample", "taira", "00", network.address, "", 1, true)
        val request = NexusTransferSigningRequest(NexusNetworks.taira, account, recipient, "xor", amount)
        val quote = NexusTransferFeeQuote("taira", network.address, recipient, "xor", amount, "0.01", "fixture", 123)
        return NexusPreparedSend(NexusSendRequest("sample", WalletNetworkId.TAIRA, recipient, amount),
            recipient, amount, "xor", "0.01", "125.25", "fixture", request, quote)
    }

    private fun waitUntil(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            if (System.nanoTime() >= deadline) {
                capture("failure")
                error("UI condition timed out")
            }
            Thread.sleep(100)
        }
        instrumentation.waitForIdleSync()
    }

    private fun findText(text: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.toString()?.contains(text) == true || node.contentDescription?.toString()?.contains(text) == true) return node
            for (index in 0 until node.childCount) node.getChild(index)?.let { visit(it)?.let { match -> return match } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findEditable(it)?.let { result -> return result } }
        return node.parent?.takeIf { it.isEditable }
    }

    private fun editableNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun visit(node: AccessibilityNodeInfo) {
            if (node.isEditable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT }) result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(::visit)
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
        return result
    }

    private fun revealText(text: String) {
        repeat(24) {
            val node = findText(text)
            if (node != null) {
                node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                instrumentation.waitForIdleSync(); Thread.sleep(250)
                if (findText(text)?.isVisibleToUser == true) return
            }
            fun scroll(node: AccessibilityNodeInfo): Boolean {
                if (node.isScrollable && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true
                for (i in 0 until node.childCount) node.getChild(i)?.let { if (scroll(it)) return true }
                return false
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::scroll)
            instrumentation.waitForIdleSync(); Thread.sleep(250)
        }
        capture("failure-reveal")
        error("Cannot reveal $text")
    }

    private fun clickText(text: String) {
        val node = findText(text) ?: error("Missing action: $text")
        var click: AccessibilityNodeInfo? = node
        while (click != null && !click.isClickable) click = click.parent
        check(click?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "Cannot click $text" }
    }

    private fun capture(name: String) {
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach { it.invalidate() }
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(1200)
        val suffix = InstrumentationRegistry.getArguments().getString("captureSuffix", "normal")
        val tree = StringBuilder()
        instrumentation.runOnMainSync {
            android.view.inspector.WindowInspector.getGlobalWindowViews().forEach {
                tree.append("Window IME visible=").append(it.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()))
                    .append(" IME insets=").append(it.rootWindowInsets?.getInsets(android.view.WindowInsets.Type.ime())).append("\n")
            }
        }
        fun describe(node: AccessibilityNodeInfo, depth: Int) {
            tree.append(" ".repeat(depth)).append(node.className).append(" text=").append(node.text)
                .append(" description=").append(node.contentDescription).append(" editable=").append(node.isEditable)
                .append(" actions=").append(node.actionList.map { it.id })
                .append(" bounds=").append(Rect().also { node.getBoundsInScreen(it) }).append("\n")
            for (index in 0 until node.childCount) node.getChild(index)?.let { describe(it, depth + 1) }
        }
        instrumentation.uiAutomation.rootInActiveWindow?.let { describe(it, 0) }
        File(screenshotDirectory, "$name-$suffix.txt").writeText(tree.toString())
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(screenshotDirectory, "$name-$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
