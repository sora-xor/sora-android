package jp.co.soramitsu.sora.splash.presentation.recovery

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStepState
import jp.co.soramitsu.core_db.*
import jp.co.soramitsu.feature_account_api.domain.interfaces.*
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationProgress
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

/** Recovery UI lifecycle with all recovery disk operations intercepted and synthetic PIN only. */
@OptIn(ExperimentalCoroutinesApi::class)
class SafeModeLifecycleFlowTest {
    @get:Rule val main = MainCoroutineRule()
    private val directory = Files.createTempDirectory("sora-safe-mode-lifecycle").toFile()
    private val context = mockk<Context>(relaxed = true) {
        every { noBackupFilesDir } returns directory
        every { applicationContext } returns this@mockk
    }
    private val phases = MutableStateFlow<MigrationProgress>(MigrationProgress.Idle)
    private val interactor = mockk<SplashInteractor>(relaxed = true) {
        every { migrationProgress } returns phases
    }
    private val credentials = mockk<CredentialsRepository>()
    private val datasource = mockk<CredentialsDatasource>()
    private val user = mockk<UserDatasource>()
    private val crypto = mockk<jp.co.soramitsu.feature_account_impl.domain.migration.MigrationSr25519Crypto>(relaxed = true)
    private val store = ViewModelStore()
    private val facts = RecoveryFacts(hasWalletFiles = true, hasRecoveryPhraseOrSeed = true,
        databaseAtCurrentVersion = true, accountsReadableReadOnly = true, phraseWalletIds = listOf("public-fixture-wallet"))
    private val events = mutableListOf<SafeModeEvent>()
    private val SCREEN_LOCK = androidx.biometric.BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL
    private val BIOMETRIC = androidx.biometric.BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC
    private val KEYGUARD_PRE_R = jp.co.soramitsu.feature_main_impl.presentation.pincode.ScreenLockPrompt.SCREEN_LOCK_OR_SYSTEM_BIOMETRIC_PRE_R
    private val ANDROID_10 = 29
    private val ANDROID_11 = 30
    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.addLast(block) }
        fun runCurrent() { while (queue.isNotEmpty()) queue.removeFirst().run() }
    }
    @Before fun setup() {
        mockkObject(WalletUpgradeBackup)
        every { WalletUpgradeBackup.recoveryFacts() } returns facts
        every { WalletUpgradeBackup.pendingSetUpAgainScope(any()) } returns null
        every { WalletUpgradeBackup.retry(any()) } returns Result.success(Unit)
        every { WalletUpgradeBackup.cancelSetUpAgain(any()) } returns Result.success(Unit)
        every { WalletUpgradeBackup.requestSetUpAgain(any()) } returns Result.success(Unit)
        every { WalletUpgradeBackup.authorizeSafeReadOnly(any()) } returns Result.success(WalletUpgradeBackup.SafeReadOnlyRoute.ROOM_READ_ONLY)
        every { interactor.recoveryOptions(any()) } answers { options(firstArg()) }
        coEvery { user.retrievePin() } returns "123456"
        coEvery { user.isBiometryEnabled() } returns false
        coEvery { datasource.retainedKeySetsNeedingPhrase() } returns emptySet()
    }
    @After fun cleanup() { unmockkStatic(BiometricManager::class); store.clear(); unmockkObject(WalletUpgradeBackup); directory.deleteRecursively() }
    private fun deviceCredentialAvailable() {
        mockkStatic(BiometricManager::class)
        val manager = mockk<BiometricManager> {
            every { canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS
        }
        every { BiometricManager.from(any()) } returns manager
    }
    private fun options(code: String, transient: Boolean = code == "KEYSTORE_UNAVAILABLE") =
        WalletRecoveryOptions.forState(code, transient, facts)
    private fun TestScope.vm(io: CoroutineDispatcher = StandardTestDispatcher(testScheduler)): SafeModeViewModel {
        val manager = mockk<CoroutineManager> { every { this@mockk.io } returns io }
        return SafeModeViewModel(context, interactor, credentials, datasource, user,
            CloudBackupProvider(false) { error("cloud unused") }, manager, crypto, Sora2AddressCodec()).also {
            store.put("vm", it)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.events.collect { event -> events += event } }
        }
    }
    private fun SafeModeViewModel.panel(code: String = "VERIFIED_RECEIPT_INVALID") = showPanel(code, options(code))
    /**
     * A transient state is retried for as long as its screen exists, so a test that leaves one
     * running closes the screen (its view model scope) before runTest drains the scheduler.
     */
    private fun endScreen() = store.clear()

    // Update rule of 2026-11-09: a transient state never stops at "paused". The progress screen
    // stays, and the same check runs again at 1, 3, 10 s and then every 30 s, without limit.
    @Test fun transientFailureKeepsRetryingBehindTheProgressScreenAndNeverStops() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("KEYSTORE_UNAVAILABLE"))
        vm.panel("KEYSTORE_UNAVAILABLE"); runCurrent()
        assertNull(vm.state.value.panel); assertNotNull(vm.state.value.progress); assertTrue(vm.state.value.visible)
        advanceTimeBy(999); runCurrent(); verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(1); runCurrent(); verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(2999); runCurrent(); verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(1); runCurrent(); verify(exactly = 2) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(9999); runCurrent(); verify(exactly = 2) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(1); runCurrent(); verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(29_999); runCurrent(); verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(1); runCurrent(); verify(exactly = 4) { WalletUpgradeBackup.retry(any()) }
        advanceTimeBy(10 * 30_000L); runCurrent()
        verify(exactly = 14) { WalletUpgradeBackup.retry(any()) }
        // Still the progress screen, never the paused panel; the support code is kept.
        assertNull(vm.state.value.panel); assertNotNull(vm.state.value.progress)
        assertEquals("KEYSTORE_UNAVAILABLE", vm.technicalDetails().first { it.first == SafeModeViewModel.DETAIL_CODE }.second)
        endScreen()
    }
    @Test fun transientFailureThatPassesRunsTheVerificationAgain() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.retry(any()) } returnsMany listOf(
            Result.failure(WalletUpgradeBackupException("DATABASE_LOCKED")), Result.success(Unit),
        )
        vm.panel("DATABASE_LOCKED"); advanceTimeBy(1_000); runCurrent()
        assertNull(vm.state.value.panel); assertTrue(events.isEmpty())
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf(SafeModeEvent.RetryReady), events); assertNull(vm.state.value.panel)
        assertEquals(SafeStepState.DONE, vm.state.value.progress?.checkingData)
    }
    @Test fun getHelpWhileRetryingShowsTheOptionsAndTheRetriesGoOn() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("KEYSTORE_UNAVAILABLE"))
        vm.panel("KEYSTORE_UNAVAILABLE"); runCurrent(); assertNull(vm.state.value.panel)
        vm.onGetHelp(); runCurrent()
        assertEquals(RecoveryPanelKind.TRANSIENT, vm.state.value.panel?.kind); assertNull(vm.state.value.progress)
        assertNull(vm.state.value.sheet)
        advanceTimeBy(5 * 30_000L); runCurrent()
        verify(atLeast = 5) { WalletUpgradeBackup.retry(any()) }
        // The person asked for the options: they stay on screen while the retries go on.
        assertEquals(RecoveryPanelKind.TRANSIENT, vm.state.value.panel?.kind)
        endScreen()
    }
    @Test fun getHelpWithNothingRetryingOpensSupport() = runTest {
        val vm = vm(); vm.showProgress(ProgressTitle.UPDATING, true); vm.onGetHelp()
        assertEquals(SafeSheetKind.SUPPORT, vm.state.value.sheet)
    }
    @Test fun resumableFailureIsRetriedThreeTimesPerStartThenShowsItsOptions() = runTest {
        val vm = vm()
        val code = "VERIFIED_RECEIPT_INVALID"
        // Where the wallet cannot open read-only either, the options are what is left.
        val resumable = options(code).copy(retryAutomatically = true, canReadOnly = false)
        every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException(code))
        every { interactor.recoveryOptions(code) } returns resumable
        vm.showPanel(code, resumable); runCurrent()
        assertNull(vm.state.value.panel); assertNotNull(vm.state.value.progress)
        advanceTimeBy(1_000 + 3_000 + 10_000); runCurrent()
        verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
        assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind); assertNull(vm.state.value.progress)
        advanceTimeBy(10 * 30_000L); runCurrent(); verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
    }
    // Audit item 13, update rule of 2026-11-09: a check that cannot finish does not stop a start
    // at its options when the wallet can open read-only behind its PIN. "Continue in read-only
    // mode" and its confirmation remain for a person who is using the screen.
    private fun SafeModeViewModel.unattendedPanel(code: String = "DATABASE_CORRUPT") =
        showPanel(code, options(code), unattended = true)

    @Test fun aCheckThatCannotFinishOpensTheWalletReadOnlyWithoutAsking() = runTest {
        val vm = vm(); vm.unattendedPanel(); runCurrent()
        assertEquals(listOf(SafeModeEvent.ReadOnlyReady), events)
        assertNull(vm.state.value.panel); assertNull(vm.state.value.sheet); assertNull(vm.state.value.running)
        assertNotNull(vm.state.value.progress)
        // The same guarded authorization the confirmation ran, after any running verification,
        // and only with a PIN that the PIN screen can check.
        coVerifyOrder {
            interactor.awaitRunningVerification()
            user.retrievePin()
            WalletUpgradeBackup.authorizeSafeReadOnly(any())
        }
        verify(exactly = 1) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
    }
    @Test fun aResumableCheckIsRetriedFirstAndOpensReadOnlyOnlyAfterItsRetries() = runTest {
        val vm = vm()
        val code = "VERIFIED_RECEIPT_INVALID"
        val resumable = options(code).copy(retryAutomatically = true)
        every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException(code))
        every { interactor.recoveryOptions(code) } returns resumable
        vm.showPanel(code, resumable, unattended = true); runCurrent()
        assertTrue(events.isEmpty()); verify(exactly = 0) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        advanceTimeBy(1_000 + 3_000); runCurrent()
        assertTrue(events.isEmpty()); verify(exactly = 0) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        advanceTimeBy(10_000); runCurrent()
        verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
        assertEquals(listOf(SafeModeEvent.ReadOnlyReady), events); assertNull(vm.state.value.panel)
        advanceTimeBy(10 * 30_000L); runCurrent(); verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
    }
    @Test fun withoutAStoredPinNothingOpensByItself() = runTest {
        // No PIN to prove the owner at the PIN screen: the options are shown instead.
        coEvery { user.retrievePin() } returns ""
        val vm = vm(); vm.unattendedPanel(); runCurrent()
        assertTrue(events.isEmpty()); verify(exactly = 0) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind); assertNull(vm.state.value.progress)
        assertTrue(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions); assertNull(vm.state.value.running)
    }
    @Test fun aStoredPinThatCannotBeReadOpensNothingByItself() = runTest {
        coEvery { user.retrievePin() } throws IllegalStateException("PIN_UNREADABLE")
        val vm = vm(); vm.unattendedPanel(); runCurrent()
        assertTrue(events.isEmpty()); verify(exactly = 0) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind)
    }
    @Test fun readOnlyThatCannotOpenHereShowsTheOptionsWithoutIt() = runTest {
        every { WalletUpgradeBackup.authorizeSafeReadOnly(any()) } returns Result.success(WalletUpgradeBackup.SafeReadOnlyRoute.VAULT_ONLY)
        val vm = vm(); vm.unattendedPanel(); runCurrent()
        assertTrue(events.isEmpty()); assertNull(vm.state.value.sheet); assertNull(vm.state.value.sheetFailure)
        assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind)
        assertFalse(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions)
    }
    @Test fun aRefusedReadOnlyAuthorizationShowsTheOptionsAndIsNotTriedAgain() = runTest {
        every { WalletUpgradeBackup.authorizeSafeReadOnly(any()) } returns Result.failure(WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED"))
        val vm = vm(); vm.unattendedPanel(); runCurrent()
        assertTrue(events.isEmpty()); assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind)
        vm.unattendedPanel(); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind)
    }
    @Test fun readOnlyOpensByItselfOncePerStart() = runTest {
        val vm = vm(); vm.unattendedPanel(); runCurrent()
        assertEquals(listOf(SafeModeEvent.ReadOnlyReady), events)
        // It came back (the selected account did not resolve): now the options.
        vm.showPanel("LEGACY_WALLET_SELECTION_UNAVAILABLE", options("LEGACY_WALLET_SELECTION_UNAVAILABLE"), canContinueLegacyWallet = false, unattended = true); runCurrent()
        assertEquals(1, events.size); verify(exactly = 1) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        assertFalse(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions)
    }
    @Test fun onceThePersonUsesTheScreenNothingIsDecidedForThem() = runTest {
        // Try again that the person tapped and that failed: its answer is the options.
        every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("DATABASE_CORRUPT"))
        val tapped = vm(); tapped.panel("DATABASE_CORRUPT"); tapped.onAction(RecoveryAction.TRY_AGAIN); runCurrent()
        assertEquals(RecoveryPanelKind.RECOVERY, tapped.state.value.panel?.kind)
        tapped.unattendedPanel(); runCurrent()
        assertEquals(RecoveryPanelKind.RECOVERY, tapped.state.value.panel?.kind)
        // "Get help" during the progress, then the check fails.
        val asked = vm(); asked.showProgress(ProgressTitle.UPDATING, true); asked.onGetHelp(); asked.closeSheet()
        asked.unattendedPanel(); runCurrent()
        assertEquals(RecoveryPanelKind.RECOVERY, asked.state.value.panel?.kind)
        // "Try upgrade again" from the read-only home that fails.
        val fromHome = vm(); fromHome.startRetryFromHome(); runCurrent()
        assertEquals(RecoveryPanelKind.RECOVERY, fromHome.state.value.panel?.kind)
        fromHome.unattendedPanel(); runCurrent()
        assertEquals(RecoveryPanelKind.RECOVERY, fromHome.state.value.panel?.kind)
        assertTrue(events.isEmpty()); verify(exactly = 0) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
    }
    @Test fun storageKeysAndPendingRestoreNeverOpenReadOnlyByThemselves() = runTest {
        // Storage first gives back cache on a worker (Codex review 4); here that is not enough.
        every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("INSUFFICIENT_BACKUP_STORAGE"))
        val storage = vm(); storage.unattendedPanel("INSUFFICIENT_BACKUP_STORAGE"); runCurrent()
        assertEquals(RecoveryPanelKind.STORAGE, storage.state.value.panel?.kind)
        val keys = vm(); keys.unattendedPanel("KEYSTORE_KEY_UNAVAILABLE"); runCurrent()
        assertEquals(RecoveryPanelKind.KEYS_UNAVAILABLE, keys.state.value.panel?.kind)
        every { WalletUpgradeBackup.pendingSetUpAgainScope(any()) } returns SetUpAgainScope.ALL_WALLET_FILES
        val restore = vm(); restore.unattendedPanel(); runCurrent()
        assertEquals(RecoveryPanelKind.RESTORE_PENDING, restore.state.value.panel?.kind)
        assertTrue(events.isEmpty()); verify(exactly = 0) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
    }
    @Test fun integrityFailureThatCannotPassByItselfShowsItsOptionsAtOnce() = runTest {
        val vm = vm(); vm.panel("DATABASE_CORRUPT"); runCurrent()
        assertEquals(RecoveryPanelKind.RECOVERY, vm.state.value.panel?.kind)
        advanceTimeBy(120_000); runCurrent(); verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
    }
    @Test fun comingBackToTheAppRetriesAtOnce() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("STORAGE_IO"))
        vm.panel("STORAGE_IO"); advanceTimeBy(1_000); runCurrent(); advanceTimeBy(3_000); runCurrent()
        verify(exactly = 2) { WalletUpgradeBackup.retry(any()) }
        vm.onPaused(); vm.onResumed(); runCurrent()
        verify(exactly = 3) { WalletUpgradeBackup.retry(any()) }
        endScreen()
    }
    @Test fun closingThePhraseFlowResumesRetrying() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("KEYSTORE_UNAVAILABLE"))
        vm.panel("KEYSTORE_UNAVAILABLE"); vm.onGetHelp(); vm.startReveal(); runCurrent()
        advanceTimeBy(60_000); runCurrent(); verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        vm.closeReveal(); advanceTimeBy(1_000); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        endScreen()
    }
    @Test fun openSupportPausesAutomaticRetryAndCloseResumesIt() = runTest {
        val vm = vm(); vm.panel("KEYSTORE_UNAVAILABLE"); vm.openSheet(SafeSheetKind.SUPPORT)
        advanceTimeBy(5000); runCurrent(); verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        vm.closeSheet(); advanceTimeBy(1000); runCurrent(); verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        assertEquals(listOf(SafeModeEvent.RetryReady), events)
    }
    @Test fun revealAuthenticationPausesAutomaticRetry() = runTest {
        val vm = vm(); vm.panel("KEYSTORE_UNAVAILABLE"); vm.startReveal(); runCurrent()
        advanceTimeBy(20_000); runCurrent(); verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
    }
    @Test fun manualRetryEmitsReadyThenShowsDelayedProgress() = runTest {
        val vm = vm(); vm.panel(); vm.onAction(RecoveryAction.TRY_AGAIN); runCurrent()
        assertEquals(listOf(SafeModeEvent.RetryReady), events); assertNull(vm.state.value.progress)
        advanceTimeBy(1499); runCurrent(); assertNull(vm.state.value.progress)
        advanceTimeBy(1); runCurrent()
        assertEquals(ProgressTitle.FINISHING, vm.state.value.progress?.title)
        assertEquals(SafeStepState.DONE, vm.state.value.progress?.checkingData)
    }
    @Test fun verificationRoutingCancelsDelayedProgressAndClearsBusy() = runTest {
        val vm = vm(); vm.panel(); vm.onAction(RecoveryAction.TRY_AGAIN); runCurrent(); vm.onVerificationRouted()
        advanceTimeBy(2000); runCurrent(); assertNull(vm.state.value.progress); assertNull(vm.state.value.running)
        assertFalse(vm.state.value.autoRetrying)
    }
    @Test fun retryFailureShowsCurrentFailureCodeInsteadOfPreviousPanel() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("FIXTURE_RETRY_FAILURE"))
        vm.panel(); vm.onAction(RecoveryAction.TRY_AGAIN); runCurrent()
        assertEquals("FIXTURE_RETRY_FAILURE", vm.state.value.panel?.code); assertNull(vm.state.value.running)
        assertTrue(events.isEmpty())
    }
    @Test fun retryFromHomeShowsProgressBeforeWorkAndPreservesOneRun() = runTest {
        val vm = vm(); vm.startRetryFromHome(); assertNotNull(vm.state.value.progress); runCurrent()
        assertEquals(ProgressTitle.FINISHING, vm.state.value.progress?.title)
        assertEquals(SafeStepState.DONE, vm.state.value.progress?.checkingData)
        assertEquals(listOf(SafeModeEvent.RetryReady), events)
    }
    @Test fun pendingRestoreFromHomeOffersRestartWithoutCallingRetry() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.pendingSetUpAgainScope(any()) } returns SetUpAgainScope.DATABASE_ONLY
        vm.startRetryFromHome(); runCurrent()
        assertEquals(RecoveryPanelKind.RESTORE_PENDING, vm.state.value.panel?.kind)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }; assertTrue(events.isEmpty())
    }
    @Test fun progressIsIdempotentAndCompletionTicksEveryPhase() = runTest {
        val vm = vm(); assertFalse(vm.markProgressFinished())
        vm.showProgress(ProgressTitle.UPDATING, false, 42L); vm.showProgress(ProgressTitle.FINISHING, true, 99L); runCurrent()
        assertEquals(42L, vm.state.value.progress?.startedAtElapsed); assertEquals(ProgressTitle.UPDATING, vm.state.value.progress?.title)
        assertTrue(vm.markProgressFinished())
        val p = vm.state.value.progress!!
        assertEquals(listOf(SafeStepState.DONE, SafeStepState.DONE, SafeStepState.DONE, SafeStepState.DONE),
            listOf(p.checkingData, p.storage, p.accounts, p.finishing))
    }
    @Test fun startupProgressUsesInterruptedRunTitleAndOriginalStart() = runTest {
        every { interactor.resumingInterruptedUpgrade } returns true
        every { interactor.verificationStartedAtElapsed } returns 17L
        val vm = vm(); vm.showStartupProgress(); runCurrent()
        assertEquals(17L, vm.state.value.progress?.startedAtElapsed); assertEquals(ProgressTitle.FINISHING, vm.state.value.progress?.title)
    }
    @Test fun newRecoveryPanelStopsOldProgressSubscription() = runTest {
        val vm = vm(); vm.showProgress(ProgressTitle.UPDATING, true); runCurrent(); vm.panel()
        phases.value = MigrationProgress.OpeningStorage; runCurrent()
        assertNull(vm.state.value.progress); assertNotNull(vm.state.value.panel)
    }
    @Test fun successfulReadOnlyAuthorizationClosesSheetAndRoutesOnce() = runTest {
        val vm = vm(); vm.panel(); vm.openSheet(SafeSheetKind.READ_ONLY); vm.confirmReadOnly(); runCurrent()
        assertNull(vm.state.value.sheet); assertEquals(listOf(SafeModeEvent.ReadOnlyReady), events)
    }
    @Test fun normalAccessAuthorizationAlsoRoutesToReadOnlyEntry() = runTest {
        every { WalletUpgradeBackup.authorizeSafeReadOnly(any()) } returns Result.success(WalletUpgradeBackup.SafeReadOnlyRoute.NORMAL_ACCESS)
        val vm = vm(); vm.panel(); vm.confirmReadOnly(); runCurrent()
        assertEquals(listOf(SafeModeEvent.ReadOnlyReady), events)
    }
    @Test fun successfulExportSharesExactArchiveAndLeavesSheetAvailable() = runTest {
        val file = directory.resolve("fixture-recovery.zip")
        every { WalletUpgradeBackup.createRecoveryArchive(any()) } returns Result.success(file)
        val vm = vm(); vm.panel(); vm.openSheet(SafeSheetKind.EXPORT); vm.createExport(); runCurrent()
        assertEquals(listOf(SafeModeEvent.ShareExport(file)), events); assertTrue(vm.state.value.exportCreated)
        assertNull(vm.state.value.running); assertEquals(SafeSheetKind.EXPORT, vm.state.value.sheet)
    }
    @Test fun openingAnotherSheetClearsPreviousExportAndEmailErrors() = runTest {
        val vm = vm(); vm.panel(); vm.onNoEmailApp(); assertTrue(vm.state.value.supportNoEmail)
        vm.openSheet(SafeSheetKind.EXPORT); assertFalse(vm.state.value.supportNoEmail); assertFalse(vm.state.value.exportCreated)
    }
    @Test fun successfulDriveRestoreEmitsRestartAndRetainsSelectedSheet() = runTest {
        val vm = vm(); vm.panel(); vm.openSheet(SafeSheetKind.RESTORE_DRIVE); vm.confirmRestore(); runCurrent()
        assertEquals(listOf(SafeModeEvent.RestartForRestore), events); assertNull(vm.state.value.running)
        assertEquals(SafeSheetKind.RESTORE_DRIVE, vm.state.value.sheet)
    }
    @Test fun cancellingPendingRestoreRetriesOnlyAfterCancellationSucceeds() = runTest {
        val vm = vm(); vm.panel(); vm.onAction(RecoveryAction.CANCEL_RESTORE); runCurrent()
        verifyOrder { WalletUpgradeBackup.cancelSetUpAgain(context); WalletUpgradeBackup.retry(context) }
        assertEquals(listOf(SafeModeEvent.RetryReady), events)
    }
    @Test fun refusedCancellationKeepsPanelAndDoesNotRetry() = runTest {
        val vm = vm(); every { WalletUpgradeBackup.cancelSetUpAgain(any()) } returns Result.failure(IllegalStateException("fixture started"))
        vm.panel(); vm.onAction(RecoveryAction.CANCEL_RESTORE); runCurrent()
        assertEquals("SET_UP_AGAIN_ALREADY_STARTED", vm.state.value.lastAttemptCode)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }; assertNotNull(vm.state.value.panel)
    }
    @Test fun supportPayloadContainsTechnicalCodesWithoutWalletIdentifiers() = runTest {
        val vm = vm(); vm.panel(); vm.emailSupport(mapOf(SafeModeViewModel.DETAIL_CODE to "Issue")); runCurrent()
        val email = events.single() as SafeModeEvent.Email
        assertTrue(email.body.contains("Issue: VERIFIED_RECEIPT_INVALID"))
        assertFalse(email.body.contains("public-fixture-wallet")); assertFalse(email.body.contains("123456"))
    }
    @Test fun openingStorageEmitsSettingsIntentOnly() = runTest {
        val vm = vm(); vm.panel(); vm.onAction(RecoveryAction.OPEN_STORAGE); runCurrent()
        assertEquals(listOf(SafeModeEvent.OpenStorageSettings), events)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
    }
    @Test fun doubleConfirmationCannotAuthorizeTwoReadOnlyOperations() = runTest {
        val ioScheduler = QueuedDispatcher(); val vm = vm(ioScheduler)
        vm.panel(); vm.openSheet(SafeSheetKind.READ_ONLY); vm.confirmReadOnly(); vm.confirmReadOnly(); runCurrent()
        ioScheduler.runCurrent(); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.authorizeSafeReadOnly(any()) }
        assertEquals(listOf(SafeModeEvent.ReadOnlyReady), events)
    }
    @Test fun supportCanOpenWhileWorkRunsButOtherActionsCannot() = runTest {
        val ioScheduler = QueuedDispatcher(); val vm = vm(ioScheduler)
        vm.panel(); vm.onAction(RecoveryAction.TRY_AGAIN); runCurrent()
        vm.onAction(RecoveryAction.EXPORT); assertNull(vm.state.value.sheet)
        vm.onAction(RecoveryAction.CONTACT_SUPPORT); assertEquals(SafeSheetKind.SUPPORT, vm.state.value.sheet)
        vm.closeSheet(); assertNull(vm.state.value.sheet)
        ioScheduler.runCurrent(); runCurrent()
    }
    @Test fun exportSheetCannotCloseWhileArchiveWorkRuns() = runTest {
        val ioScheduler = QueuedDispatcher(); val vm = vm(ioScheduler)
        every { WalletUpgradeBackup.createRecoveryArchive(any()) } returns Result.success(directory.resolve("fixture.zip"))
        vm.panel(); vm.openSheet(SafeSheetKind.EXPORT); vm.createExport(); runCurrent(); vm.closeSheet()
        assertEquals(SafeSheetKind.EXPORT, vm.state.value.sheet)
        advanceTimeBy(10_000); runCurrent(); assertTrue(vm.state.value.runningLong)
        ioScheduler.runCurrent(); runCurrent(); assertFalse(vm.state.value.runningLong)
    }
    @Test fun stoppedAuthenticationDropsPartialPinAndRequiresFreshEntry() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent(); vm.pinDigit('1'); vm.pinDigit('2')
        assertEquals(2, (vm.state.value.reveal as RevealUi.Auth).entered)
        vm.onStopped(); assertNull(vm.state.value.reveal); vm.startReveal(); runCurrent()
        assertEquals(0, (vm.state.value.reveal as RevealUi.Auth).entered)
    }
    @Test fun deletingPinDigitsDoesNotReadWalletSecrets() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent(); vm.pinDigit('x'); vm.pinDelete()
        assertEquals(0, (vm.state.value.reveal as RevealUi.Auth).entered)
        vm.pinDigit('1'); vm.pinDigit('2'); vm.pinDelete()
        assertEquals(1, (vm.state.value.reveal as RevealUi.Auth).entered); confirmVerified(credentials, datasource)
    }
    @Test fun closingRevealWhilePinLookupRunsCannotReopenIt() = runTest {
        val release = CompletableDeferred<String>(); coEvery { user.retrievePin() } coAnswers { release.await() }
        val vm = vm(); vm.startReveal(); runCurrent(); vm.closeReveal()
        release.complete("123456"); runCurrent(); assertNull(vm.state.value.reveal)
    }
    @Test fun olderPinLookupCannotReplaceNewRevealRequest() = runTest {
        val release = CompletableDeferred<String>(); var calls = 0
        coEvery { user.retrievePin() } coAnswers { if (calls++ == 0) release.await() else "1234" }
        val vm = vm(); vm.startReveal(); runCurrent(); vm.closeReveal(); vm.startReveal(); runCurrent()
        assertEquals(4, (vm.state.value.reveal as RevealUi.Auth).pinLength)
        release.complete("123456"); runCurrent(); assertEquals(4, (vm.state.value.reveal as RevealUi.Auth).pinLength)
    }
    @Test fun pinLookupCancellationDoesNotLeaveHiddenAuthenticationVisible() = runTest {
        val release = CompletableDeferred<String>(); coEvery { user.retrievePin() } coAnswers { release.await() }
        val vm = vm(); vm.startReveal(); runCurrent(); vm.onStopped()
        release.complete("123456"); runCurrent(); assertNull(vm.state.value.reveal)
    }
    @Test fun delayedPinVerificationCannotReopenClosedReveal() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent()
        val release = CompletableDeferred<String>()
        coEvery { user.retrievePin() } coAnswers { release.await() }
        "123456".forEach(vm::pinDigit); runCurrent(); vm.closeReveal()
        release.complete("123456"); runCurrent()
        assertNull(vm.state.value.reveal)
        coVerify(exactly = 0) { datasource.retainedKeySetsNeedingPhrase() }
        confirmVerified(credentials)
    }
    @Test fun delayedCorrectPinCannotAuthenticateReplacementReveal() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent()
        val release = CompletableDeferred<String>()
        coEvery { user.retrievePin() } coAnswers { release.await() }
        "123456".forEach(vm::pinDigit); runCurrent(); vm.closeReveal()
        coEvery { user.retrievePin() } returns "654321"
        vm.startReveal(); runCurrent(); vm.pinDigit('6')
        release.complete("123456"); runCurrent()
        assertEquals(1, (vm.state.value.reveal as RevealUi.Auth).entered)
        coVerify(exactly = 0) { datasource.retainedKeySetsNeedingPhrase() }; confirmVerified(credentials)
    }
    @Test fun delayedWrongPinCannotEraseNewAuthenticationEntry() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent()
        val release = CompletableDeferred<String>()
        coEvery { user.retrievePin() } coAnswers { release.await() }
        "000000".forEach(vm::pinDigit); runCurrent(); vm.closeReveal()
        coEvery { user.retrievePin() } returns "654321"
        vm.startReveal(); runCurrent(); vm.pinDigit('6')
        release.complete("123456"); runCurrent()
        val auth = vm.state.value.reveal as RevealUi.Auth
        assertEquals(1, auth.entered); assertNull(auth.error)
    }
    @Test fun oldAuthorizedWalletListCannotBypassNewAuthentication() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent()
        val release = CompletableDeferred<Set<String>>()
        coEvery { datasource.retainedKeySetsNeedingPhrase() } coAnswers { release.await() }
        "123456".forEach(vm::pinDigit); runCurrent(); vm.closeReveal(); vm.startReveal(); runCurrent()
        release.complete(emptySet()); runCurrent()
        assertTrue(vm.state.value.reveal is RevealUi.Auth); confirmVerified(credentials)
    }
    @Test fun repeatedExportConfirmationCreatesAndSharesOneArchive() = runTest {
        val io = QueuedDispatcher(); val vm = vm(io)
        val archive = directory.resolve("fixture.zip")
        every { WalletUpgradeBackup.createRecoveryArchive(any()) } returns Result.success(archive)
        vm.panel(); vm.openSheet(SafeSheetKind.EXPORT); vm.createExport(); vm.createExport(); runCurrent()
        io.runCurrent(); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.createRecoveryArchive(any()) }
        assertEquals(listOf(SafeModeEvent.ShareExport(archive)), events)
    }
    @Test fun repeatedRestoreConfirmationRecordsOneRestart() = runTest {
        val io = QueuedDispatcher(); val vm = vm(io)
        vm.panel(); vm.openSheet(SafeSheetKind.RESTORE_PHRASE); vm.confirmRestore(); vm.confirmRestore(); runCurrent()
        io.runCurrent(); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.requestSetUpAgain(any()) }
        assertEquals(listOf(SafeModeEvent.RestartForRestore), events)
    }
    @Test fun queuedExportConfirmationCannotRunDuringRetry() = runTest {
        val io = QueuedDispatcher(); val vm = vm(io)
        vm.panel(); vm.onAction(RecoveryAction.TRY_AGAIN); vm.createExport(); runCurrent()
        io.runCurrent(); runCurrent()
        verify(exactly = 0) { WalletUpgradeBackup.createRecoveryArchive(any()) }
        assertEquals(listOf(SafeModeEvent.RetryReady), events)
    }

    @Test fun returningFromStorageSettingsRetriesOnceAndOnlyAfterPause() = runTest {
        val vm = vm(); vm.panel("STORAGE_FULL"); assertEquals(RecoveryPanelKind.STORAGE, vm.state.value.panel!!.kind)
        vm.onResumed(); runCurrent(); verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        vm.onPaused(); vm.onResumed(); vm.onResumed(); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }; assertEquals(listOf(SafeModeEvent.RetryReady), events)
    }
    @Test fun storageReturnDoesNotRetryWhilePhraseAuthenticationIsOpen() = runTest {
        val vm = vm(); vm.panel("STORAGE_FULL"); vm.startReveal(); runCurrent(); vm.onPaused(); vm.onResumed(); runCurrent()
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
    }
    @Test fun unavailableRecoveryOptionsNeverOfferDestructiveSetup() = runTest {
        every { interactor.recoveryOptions(any()) } throws IllegalStateException("unreadable options")
        val vm = vm(); vm.showPanel("FIXTURE_UNKNOWN")
        val actions = vm.state.value.panel!!.actions
        assertFalse(RecoveryAction.RESTORE_PHRASE in actions); assertFalse(RecoveryAction.RESTORE_DRIVE in actions)
        assertTrue(RecoveryAction.TRY_AGAIN in actions)
    }
    @Test fun deniedReadOnlyAuthorizationRetainsSheetAndFailureCode() = runTest {
        every { WalletUpgradeBackup.authorizeSafeReadOnly(any()) } returns Result.failure(WalletUpgradeBackupException("FIXTURE_READ_DENIED"))
        val vm = vm(); vm.panel(); vm.confirmReadOnly(); runCurrent()
        assertEquals(SheetFailure.READ_ONLY_FAILED, vm.state.value.sheetFailure)
        assertEquals("FIXTURE_READ_DENIED", vm.state.value.lastAttemptCode); assertTrue(events.isEmpty())
    }
    @Test fun noPinDevicePromptSurvivesExpectedStopAndCancelledPromptClearsOnNextStop() = runTest {
        deviceCredentialAvailable()
        coEvery { user.retrievePin() } returns ""
        val vm = vm(); vm.startReveal(); runCurrent(); vm.requestBiometric(); runCurrent()
        assertEquals(listOf(SafeModeEvent.Authenticate(true)), events)
        vm.onStopped(); assertTrue(vm.state.value.reveal is RevealUi.Auth)
        vm.onAuthenticationEnded(); vm.onStopped(); assertNull(vm.state.value.reveal)
    }
    @Test fun biometricSuccessCannotRevealSecretsProtectedByWalletPin() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent()
        assertFalse((vm.state.value.reveal as RevealUi.Auth).biometric)
        vm.requestBiometric(); runCurrent()
        assertTrue(events.isEmpty())
        vm.onAuthenticated(SCREEN_LOCK, ANDROID_11); runCurrent()
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }

    @Test fun noStoredPinUsesDeviceCredentialAndCanRetryAfterPinBecomesAvailable() = runTest {
        deviceCredentialAvailable()
        coEvery { user.retrievePin() } returns ""
        val vm = vm(); vm.startReveal(); runCurrent()
        assertTrue((vm.state.value.reveal as RevealUi.Auth).noPin)
        vm.requestBiometric(); runCurrent(); assertEquals(listOf(SafeModeEvent.Authenticate(true)), events)
        coEvery { user.retrievePin() } returns "123456"; vm.retryAuthCheck(); runCurrent()
        assertFalse((vm.state.value.reveal as RevealUi.Auth).noPin)
    }
    @Test fun pinReadFailureDuringSubmissionReturnsUnavailableWithoutReadingSecrets() = runTest {
        val vm = vm(); vm.startReveal(); runCurrent()
        coEvery { user.retrievePin() } throws java.io.IOException("fixture key unavailable")
        "123456".forEach(vm::pinDigit); runCurrent()
        assertEquals(AuthError.UNAVAILABLE, (vm.state.value.reveal as RevealUi.Auth).error)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
    // Codex review 1: with no app PIN stored, the phone's screen lock is the proof that opens
    // the recovery phrase; a fingerprint or face that the prompt might accept reveals nothing.
    @Test fun noStoredPinRevealsOnlyAfterTheScreenLockItselfNeverABiometric() = runTest {
        deviceCredentialAvailable()
        coEvery { user.retrievePin() } returns ""
        coEvery { credentials.retrieveMnemonic(any()) } returns "public fixture words"
        every { credentials.convertRetainedSoraPassphraseToSeed(any()) } returns "01"
        val vm = vm(); vm.startReveal(); runCurrent()
        vm.requestBiometric(); runCurrent()
        vm.onAuthenticated(BIOMETRIC, ANDROID_11); runCurrent()
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
        vm.requestBiometric(); runCurrent()
        vm.onAuthenticated(SCREEN_LOCK, ANDROID_11); runCurrent()
        assertFalse(vm.state.value.reveal is RevealUi.Auth)
        coVerify(exactly = 1) { credentials.retrieveMnemonic(any()) }
    }
    // Codex re-review 1: on Android 11 and later Keyguard's result reveals nothing; on Android 8
    // to 10 it is the system's confirmation there (platform limit) and reveals.
    @Test fun keyguardsResultRevealsOnlyOnAndroid8To10() = runTest {
        deviceCredentialAvailable()
        coEvery { user.retrievePin() } returns ""
        coEvery { credentials.retrieveMnemonic(any()) } returns "public fixture words"
        every { credentials.convertRetainedSoraPassphraseToSeed(any()) } returns "01"
        val vm = vm(); vm.startReveal(); runCurrent()
        vm.requestBiometric(); runCurrent()
        vm.onAuthenticated(KEYGUARD_PRE_R, ANDROID_11); runCurrent()
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
        vm.requestBiometric(); runCurrent()
        vm.onAuthenticated(BIOMETRIC, ANDROID_10); runCurrent()
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
        vm.requestBiometric(); runCurrent()
        vm.onAuthenticated(KEYGUARD_PRE_R, ANDROID_10); runCurrent()
        assertFalse(vm.state.value.reveal is RevealUi.Auth)
        coVerify(exactly = 1) { credentials.retrieveMnemonic(any()) }
    }
    @Test fun theSafeModePromptAllowsTheScreenLockAloneNeverABiometric() {
        assertEquals(BiometricManager.Authenticators.DEVICE_CREDENTIAL, SafeModeViewModel.DEVICE_CREDENTIAL_AUTHENTICATORS)
    }

    // Codex review 4: the app start may not walk the cache or ask Android for space (main
    // thread). A storage state nobody asked for runs the check again on a worker first, which
    // gives back cache; the progress screen stays, and the storage screen comes only if the
    // space is still too little. Never a question while that runs, never a loop.
    @Test fun lowStorageAtTheStartIsGivenBackOnAWorkerBeforeAnyStorageScreen() = runTest {
        val vm = vm(); vm.showPanel("INSUFFICIENT_BACKUP_STORAGE", options("INSUFFICIENT_BACKUP_STORAGE"), unattended = true)
        // Before the worker ran: the progress screen, no panel, no question.
        assertNull(vm.state.value.panel); assertNotNull(vm.state.value.progress); assertNull(vm.state.value.sheet)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        assertEquals(listOf(SafeModeEvent.RetryReady), events)
        assertNull(vm.state.value.panel)
    }
    @Test fun storageStillTooLittleAfterGivingBackShowsTheStorageScreenOnceAndNoLoop() = runTest {
        every { WalletUpgradeBackup.retry(any()) } returns Result.failure(WalletUpgradeBackupException("INSUFFICIENT_BACKUP_STORAGE"))
        val vm = vm(); vm.showPanel("INSUFFICIENT_BACKUP_STORAGE", options("INSUFFICIENT_BACKUP_STORAGE"), unattended = true); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        assertEquals(RecoveryPanelKind.STORAGE, vm.state.value.panel?.kind); assertNull(vm.state.value.progress)
        advanceTimeBy(10 * 60_000L); runCurrent()
        verify(exactly = 1) { WalletUpgradeBackup.retry(any()) }
        assertTrue(events.isEmpty())
    }
    @Test fun aStorageScreenThePersonAskedForIsShownAtOnce() = runTest {
        val vm = vm(); vm.showPanel("INSUFFICIENT_BACKUP_STORAGE", options("INSUFFICIENT_BACKUP_STORAGE")); runCurrent()
        assertEquals(RecoveryPanelKind.STORAGE, vm.state.value.panel?.kind)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
    }

    @Test fun revealedWalletCanBeHiddenAndReturnToListThenClose() = runTest {
        every { WalletUpgradeBackup.recoveryFacts() } returns facts.copy(phraseWalletIds = listOf("fixture-a", "fixture-b"))
        coEvery { credentials.retrieveMnemonic(any()) } returns "public fixture words"
        every { credentials.convertRetainedSoraPassphraseToSeed(any()) } returns "01"
        val vm = vm(); vm.startReveal(); runCurrent(); "123456".forEach(vm::pinDigit); runCurrent()
        val list = vm.state.value.reveal as RevealUi.Wallets
        vm.openEntry(list.entries.first()); runCurrent(); vm.setRevealed(true)
        assertTrue((vm.state.value.reveal as RevealUi.Shown).revealed)
        vm.setRevealed(false); assertFalse((vm.state.value.reveal as RevealUi.Shown).revealed)
        vm.backFromReveal(); runCurrent(); assertEquals(list.entries, (vm.state.value.reveal as RevealUi.Wallets).entries)
        vm.backFromReveal(); assertNull(vm.state.value.reveal)
    }
    @Test fun singleWalletRevealBackClosesAndDoesNotRetainAuthentication() = runTest {
        coEvery { credentials.retrieveMnemonic(any()) } returns "public fixture words"
        every { credentials.convertRetainedSoraPassphraseToSeed(any()) } returns "01"
        val vm = vm(); vm.onAction(RecoveryAction.SHOW_PHRASE); runCurrent(); "123456".forEach(vm::pinDigit); runCurrent()
        assertFalse((vm.state.value.reveal as RevealUi.Shown).canGoBack)
        vm.backFromReveal(); assertNull(vm.state.value.reveal)
        vm.openEntry(PhraseEntry("fixture-wallet", "fixture-wallet", null, false)); runCurrent()
        assertNull(vm.state.value.reveal)
    }
    @Test fun seedProofUsesDerivedAddressAndErasesTemporaryKeyMaterial() = runTest {
        val publicKey = ByteArray(32) { 1 }
        val address = Sora2AddressCodec().toSoraAddressOrNull(publicKey)!!
        every { WalletUpgradeBackup.recoveryFacts() } returns facts.copy(phraseWalletIds = listOf(address))
        coEvery { credentials.retrieveMnemonic(any()) } returns ""
        coEvery { credentials.retrieveStoredSeed(any()) } returns "02".repeat(32)
        val privateKey = ByteArray(32) { 3 }; val nonce = ByteArray(32) { 4 }; var seedInput: ByteArray? = null
        every { crypto.generateKeypair(any()) } answers {
            seedInput = firstArg()
            jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair(privateKey, publicKey, nonce)
        }
        val vm = vm(); vm.startReveal(); runCurrent(); "123456".forEach(vm::pinDigit); runCurrent()
        val secret = (vm.state.value.reveal as RevealUi.Shown).secret as RevealedSecret.Seed
        assertTrue(secret.checked); assertFalse(secret.otherWallet)
        assertTrue(privateKey.all { it == 0.toByte() }); assertTrue(nonce.all { it == 0.toByte() })
        assertTrue(requireNotNull(seedInput).all { it == 0.toByte() })
    }
    @Test fun malformedDerivedSeedDoesNotAskCryptoToInterpretIt() = runTest {
        coEvery { credentials.retrieveMnemonic(any()) } returns "public fixture phrase"
        every { credentials.convertRetainedSoraPassphraseToSeed(any()) } returns "01"
        val vm = vm(); vm.startReveal(); runCurrent(); "123456".forEach(vm::pinDigit); runCurrent()
        assertTrue((vm.state.value.reveal as RevealUi.Shown).secret is RevealedSecret.Phrase)
        verify(exactly = 0) { crypto.generateKeypair(any()) }
    }
    @Test fun retryProgressSwitchesFromAwaitingRunToActiveStorageAndAccountPhases() = runTest {
        val vm = vm(); vm.startRetryFromHome(); runCurrent()
        phases.value = MigrationProgress.OpeningStorage; runCurrent()
        assertEquals(SafeStepState.RUNNING, vm.state.value.progress!!.storage)
        phases.value = MigrationProgress.CheckingAccounts(1, 2); runCurrent()
        assertEquals(SafeStepState.DONE, vm.state.value.progress!!.storage)
        assertEquals(SafeStepState.RUNNING, vm.state.value.progress!!.accounts)
    }
    @Test fun copyingDiagnosticTextAcknowledgesEvenIfClipboardIsUnavailable() = runTest {
        every { context.getSystemService(android.content.ClipboardManager::class.java) } throws SecurityException("clipboard blocked")
        val vm = vm(); vm.copyToClipboard("Public diagnostic", "FIXTURE_CODE"); runCurrent()
        assertEquals(listOf(SafeModeEvent.Copied), events)
    }
}
