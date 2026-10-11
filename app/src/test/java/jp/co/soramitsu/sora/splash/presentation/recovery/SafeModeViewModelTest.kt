package jp.co.soramitsu.sora.splash.presentation.recovery

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.nio.file.Files
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import io.mockk.verify
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStepState
import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.WalletUpgradeBackupException
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationProgress
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Show my recovery phrase" never reads a phrase before the person is confirmed. */
@OptIn(ExperimentalCoroutinesApi::class)
class SafeModeViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val noBackup = Files.createTempDirectory("no_backup").toFile()
    private val context = mockk<Context>(relaxed = true).also {
        every { it.noBackupFilesDir } returns noBackup
        every { it.applicationContext } returns it
    }
    private val interactor = mockk<SplashInteractor>(relaxed = true).also {
        every { it.migrationProgress } returns MutableStateFlow(MigrationProgress.Idle)
    }
    private val credentials = mockk<CredentialsRepository>()
    private val datasource = mockk<CredentialsDatasource>().also {
        // No key set on the phone needs its own recovery phrase, unless a test says so.
        coEvery { it.retainedKeySetsNeedingPhrase() } returns emptySet()
    }
    private val user = mockk<UserDatasource>().also {
        coEvery { it.retrievePin() } returns "123456"
        coEvery { it.isBiometryEnabled() } returns false
    }

    @After
    fun tearDown() {
        unmockkObject(WalletUpgradeBackup)
    }

    private fun TestScope.viewModel(facts: RecoveryFacts): SafeModeViewModel {
        mockkObject(WalletUpgradeBackup)
        every { WalletUpgradeBackup.recoveryFacts() } returns facts
        // No restore waits for a restart (android.system.Os is a stub on the JVM).
        every { WalletUpgradeBackup.pendingSetUpAgainScope(any()) } returns null
        val coroutineManager = mockk<CoroutineManager>()
        every { coroutineManager.io } returns coroutineContext[CoroutineDispatcher]!!
        return SafeModeViewModel(
            context = context,
            interactor = interactor,
            credentialsRepository = credentials,
            credentialsDatasource = datasource,
            userDatasource = user,
            cloudBackupProvider = CloudBackupProvider(false) { error("not used") },
            coroutineManager = coroutineManager,
            migrationSr25519Crypto = mockk(relaxed = true),
            sora2AddressCodec = Sora2AddressCodec(),
        )
    }

    private val recoveryFacts = RecoveryFacts(
        hasWalletFiles = true,
        hasRecoveryPhraseOrSeed = true,
        databaseAtCurrentVersion = true,
        accountsReadableReadOnly = true,
        phraseWalletIds = listOf("cnA"),
    )

    private fun options(code: String, transient: Boolean = false) =
        WalletRecoveryOptions.forState(code = code, transientFailure = transient, facts = recoveryFacts)

    @Test
    fun `a restore waiting for a restart is finished by a restart, never by trying again here`() = runTest {
        val vm = viewModel(recoveryFacts)
        every { WalletUpgradeBackup.pendingSetUpAgainScope(any()) } returns SetUpAgainScope.DATABASE_ONLY
        val events = mutableListOf<SafeModeEvent>()
        val collector = launch { vm.events.collect { events += it } }
        // Even a state that would retry by itself (a busy Keystore) waits for the restart.
        vm.showPanel("KEYSTORE_UNAVAILABLE", options("KEYSTORE_UNAVAILABLE", transient = true))
        val panel = requireNotNull(vm.state.value.panel)
        assertEquals(RecoveryPanelKind.RESTORE_PENDING, panel.kind)
        assertFalse(panel.retryAutomatically)
        assertFalse(RecoveryAction.RESTORE_PHRASE in panel.actions || RecoveryAction.READ_ONLY in panel.actions)
        assertTrue(RecoveryAction.CONTACT_SUPPORT in panel.actions)
        advanceTimeBy(60_000)
        advanceUntilIdle()
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        assertTrue(events.isEmpty())

        vm.onAction(RecoveryAction.TRY_AGAIN)
        advanceUntilIdle()
        assertEquals(listOf<SafeModeEvent>(SafeModeEvent.RestartForRestore), events)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        collector.cancel()
    }

    @Test
    fun `an unfinished set aside is shown as such and its try again restarts`() = runTest {
        val vm = viewModel(recoveryFacts)
        val events = mutableListOf<SafeModeEvent>()
        val collector = launch { vm.events.collect { events += it } }
        val incomplete = WalletRecoveryOptions.forState(
            code = "SET_UP_AGAIN_INCOMPLETE",
            transientFailure = false,
            facts = RecoveryFacts(hasWalletFiles = true),
        )
        vm.showPanel("SET_UP_AGAIN_INCOMPLETE", incomplete)
        assertEquals(RecoveryPanelKind.RESTORE_PENDING, vm.state.value.panel?.kind)
        advanceTimeBy(60_000)
        verify(exactly = 0) { WalletUpgradeBackup.retry(any()) }
        vm.onAction(RecoveryAction.TRY_AGAIN)
        advanceUntilIdle()
        assertEquals(listOf<SafeModeEvent>(SafeModeEvent.RestartForRestore), events)
        collector.cancel()
    }

    @Test
    fun `a verification that failed in this process offers only what works there`() = runTest {
        val vm = viewModel(recoveryFacts)
        WalletUpgradeBackup.noteMigrationVerified()
        try {
            // MigrationManager.recordFailure after the startup checks had passed.
            WalletUpgradeBackup.noteMigrationRecoveryRequired("VERIFIED_RECEIPT_INVALID")
            val splash = SplashInteractor(mockk(relaxed = true), mockk(relaxed = true), context)
            every { interactor.recoveryOptions(any()) } answers { splash.recoveryOptions(firstArg()) }
            every { WalletUpgradeBackup.requestSetUpAgain(any()) } returns Result.success(Unit)
            val events = mutableListOf<SafeModeEvent>()
            val collector = launch { vm.events.collect { events += it } }

            vm.showPanel("VERIFIED_RECEIPT_INVALID")
            val panel = requireNotNull(vm.state.value.panel)
            // The restore it offers is the one requestSetUpAgain accepts (it reads these options).
            assertEquals(SetUpAgainScope.DATABASE_ONLY, WalletUpgradeBackup.recoveryOptions()?.setUpAgainScope)
            assertEquals("VERIFIED_RECEIPT_INVALID", panel.code)
            assertTrue(RecoveryAction.RESTORE_PHRASE in panel.actions)
            // Show my recovery phrase and Export follow the startup facts (device test:
            // WalletUpgradeBackupTest.aVerificationThatFailsInTheRunningAppOffersTheRestoreItShows).

            vm.onAction(RecoveryAction.RESTORE_PHRASE)
            vm.confirmRestore()
            advanceUntilIdle()
            verify(exactly = 1) { WalletUpgradeBackup.requestSetUpAgain(any()) }
            assertEquals(listOf<SafeModeEvent>(SafeModeEvent.RestartForRestore), events)
            collector.cancel()
        } finally {
            WalletUpgradeBackup.noteMigrationVerified()
        }
    }

    @Test
    fun `without a recorded recovery state no restore is offered`() = runTest {
        val vm = viewModel(recoveryFacts)
        WalletUpgradeBackup.noteMigrationVerified()
        val splash = SplashInteractor(mockk(relaxed = true), mockk(relaxed = true), context)
        every { interactor.recoveryOptions(any()) } answers { splash.recoveryOptions(firstArg()) }
        vm.showPanel("WALLET_VERIFICATION_FAILED")
        val panel = requireNotNull(vm.state.value.panel)
        assertFalse(RecoveryAction.RESTORE_PHRASE in panel.actions || RecoveryAction.RESTORE_DRIVE in panel.actions)
        assertTrue(RecoveryAction.TRY_AGAIN in panel.actions && RecoveryAction.CONTACT_SUPPORT in panel.actions)
        // The facts gathered at startup still offer the phrase and the export.
        assertTrue(RecoveryAction.SHOW_PHRASE in panel.actions && RecoveryAction.EXPORT in panel.actions)
    }

    @Test
    fun `a refused restore keeps the panel and its code and says so in the sheet`() = runTest {
        val vm = viewModel(recoveryFacts)
        every { WalletUpgradeBackup.requestSetUpAgain(any()) } returns
            Result.failure(WalletUpgradeBackupException("SET_UP_AGAIN_NOT_AVAILABLE"))
        every { interactor.recoveryOptions(any()) } answers { options(firstArg()) }
        vm.showPanel("WALLET_MIGRATION_RECOVERY_REQUIRED", options("WALLET_MIGRATION_RECOVERY_REQUIRED"))
        vm.onAction(RecoveryAction.RESTORE_PHRASE)
        vm.confirmRestore()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(SafeSheetKind.RESTORE_PHRASE, state.sheet)
        assertEquals(SheetFailure.RESTORE_FAILED, state.sheetFailure)
        assertEquals("WALLET_MIGRATION_RECOVERY_REQUIRED", state.panel?.code)
        assertNull(state.running)
        val details = vm.technicalDetails().toMap()
        assertEquals("WALLET_MIGRATION_RECOVERY_REQUIRED", details[SafeModeViewModel.DETAIL_CODE])
        assertEquals("SET_UP_AGAIN_NOT_AVAILABLE", details[SafeModeViewModel.DETAIL_LAST_ATTEMPT])
    }

    @Test
    fun `a failed export stays on its sheet and says so`() = runTest {
        val vm = viewModel(recoveryFacts)
        every { WalletUpgradeBackup.createRecoveryArchive(any()) } returns
            Result.failure(WalletUpgradeBackupException("RECOVERY_EXPORT_FAILED"))
        every { interactor.recoveryOptions(any()) } answers { options(firstArg()) }
        vm.showPanel("WALLET_MIGRATION_RECOVERY_REQUIRED", options("WALLET_MIGRATION_RECOVERY_REQUIRED"))
        vm.onAction(RecoveryAction.EXPORT)
        vm.createExport()
        advanceUntilIdle()
        assertEquals(SafeSheetKind.EXPORT, vm.state.value.sheet)
        assertEquals(SheetFailure.EXPORT_FAILED, vm.state.value.sheetFailure)
        assertFalse(vm.state.value.exportCreated)
    }

    @Test
    fun `read only that cannot open the database says so and is not offered again`() = runTest {
        val vm = viewModel(recoveryFacts)
        every { WalletUpgradeBackup.authorizeSafeReadOnly(any()) } returns
            Result.success(WalletUpgradeBackup.SafeReadOnlyRoute.VAULT_ONLY)
        every { interactor.recoveryOptions(any()) } answers { options(firstArg()) }
        val events = mutableListOf<SafeModeEvent>()
        val collector = launch { vm.events.collect { events += it } }
        vm.showPanel("WALLET_MIGRATION_RECOVERY_REQUIRED", options("WALLET_MIGRATION_RECOVERY_REQUIRED"))
        assertTrue(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions)
        vm.onAction(RecoveryAction.READ_ONLY)
        vm.confirmReadOnly()
        advanceUntilIdle()
        assertEquals(SafeSheetKind.READ_ONLY, vm.state.value.sheet)
        assertEquals(SheetFailure.READ_ONLY_UNAVAILABLE, vm.state.value.sheetFailure)
        assertFalse(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions)
        assertTrue(events.none { it == SafeModeEvent.ReadOnlyReady })
        vm.closeSheet()
        assertNull(vm.state.value.sheetFailure)
        collector.cancel()
    }

    @Test
    fun `read only is not offered when the current account does not resolve`() = runTest {
        val vm = viewModel(recoveryFacts)
        every { interactor.recoveryOptions(any()) } answers { options(firstArg()) }
        vm.showPanel(
            "LEGACY_WALLET_SELECTION_UNAVAILABLE",
            options("WALLET_MIGRATION_RECOVERY_REQUIRED"),
            canContinueLegacyWallet = false,
        )
        assertFalse(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions)
        // A later redraw of the same screen (for example after a failed action) keeps it hidden.
        vm.showPanel("WALLET_MIGRATION_RECOVERY_REQUIRED")
        assertFalse(RecoveryAction.READ_ONLY in vm.state.value.panel!!.actions)
    }

    @Test
    fun `progress counts from when the verification started`() = runTest {
        val vm = viewModel(recoveryFacts)
        every { interactor.verificationStartedAtElapsed } returns 1_234L
        vm.showStartupProgress()
        assertEquals(1_234L, vm.state.value.progress?.startedAtElapsed)
    }

    private fun SafeModeViewModel.enter(pin: String) = pin.forEach { pinDigit(it) }

    @Test
    fun `no phrase is read before the pin is confirmed`() = runTest {
        coEvery { credentials.retrieveMnemonic(any()) } returns "alpha beta gamma"
        val vm = viewModel(RecoveryFacts(phraseWalletIds = listOf("cnA")))
        vm.startReveal()
        advanceUntilIdle()
        val auth = vm.state.value.reveal as RevealUi.Auth
        assertEquals(6, auth.pinLength)

        // A direct call before confirmation does nothing.
        vm.openEntry(PhraseEntry("cnA", "cnA", null, false))
        advanceUntilIdle()
        assertTrue(vm.state.value.reveal is RevealUi.Auth)

        vm.enter("000000")
        advanceUntilIdle()
        assertEquals(AuthError.WRONG, (vm.state.value.reveal as RevealUi.Auth).error)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }

        vm.enter("123456")
        advanceUntilIdle()
        val shown = vm.state.value.reveal as RevealUi.Shown
        assertEquals(RevealedSecret.Phrase(listOf("alpha", "beta", "gamma")), shown.secret)
        // The words stay veiled until tapped.
        assertEquals(false, shown.revealed)
        coVerify(exactly = 1) { credentials.retrieveMnemonic(any()) }
    }

    @Test
    fun `three wrong pins lock, and the lock outlives the flow`() = runTest {
        val vm = viewModel(RecoveryFacts(phraseWalletIds = listOf("cnA", "cnB")))
        vm.startReveal()
        advanceUntilIdle()
        repeat(3) {
            vm.enter("999999")
            advanceUntilIdle()
        }
        val locked = vm.state.value.reveal as RevealUi.Auth
        assertEquals(AuthError.LOCKED, locked.error)
        assertTrue(locked.lockedForMillis > 0)

        vm.closeReveal()
        vm.startReveal()
        advanceUntilIdle()
        assertEquals(AuthError.LOCKED, (vm.state.value.reveal as RevealUi.Auth).error)
        vm.enter("123456")
        advanceUntilIdle()
        assertTrue(vm.state.value.reveal is RevealUi.Auth)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }

    @Test
    fun `several wallets are listed after confirmation and closing forgets everything`() = runTest {
        val vm = viewModel(RecoveryFacts(phraseWalletIds = listOf("cnA", "cnB"), accountNames = mapOf("cnA" to "Main")))
        vm.startReveal()
        advanceUntilIdle()
        vm.enter("123456")
        advanceUntilIdle()
        val wallets = vm.state.value.reveal as RevealUi.Wallets
        assertEquals(listOf("Main", null), wallets.entries.map { it.name })

        vm.onStopped()
        assertNull(vm.state.value.reveal)
        vm.openEntry(wallets.entries.first())
        advanceUntilIdle()
        assertNull(vm.state.value.reveal)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }

    @Test
    fun `a key set left by Remove all accounts is not listed, whatever PIN is stored now`() = runTest {
        // The next person set their own PIN; a recovery state must not show them the phrase of
        // a wallet that was on the phone before that PIN.
        coEvery { datasource.retainedKeySetsNeedingPhrase() } returns setOf("cnLeftover")
        coEvery { credentials.retrieveMnemonic(any()) } returns "alpha beta gamma"
        val vm = viewModel(
            RecoveryFacts(
                phraseWalletIds = listOf("cnMine", "cnLeftover"),
                accountNames = mapOf("cnMine" to "Mine"),
            )
        )
        vm.startReveal()
        advanceUntilIdle()
        vm.enter("123456")
        advanceUntilIdle()

        // Only the account's own phrase is opened; the leftover key set is never read.
        val shown = vm.state.value.reveal as RevealUi.Shown
        assertEquals("cnMine", shown.entry.id)
        coVerify(exactly = 0) {
            credentials.retrieveMnemonic(match { it.substrateAddress == "cnLeftover" })
        }
    }

    @Test
    fun `a pin that cannot be read offers try again instead of a keypad`() = runTest {
        coEvery { user.retrievePin() } throws IllegalStateException("keystore busy")
        val vm = viewModel(RecoveryFacts(phraseWalletIds = listOf("cnA")))
        vm.startReveal()
        advanceUntilIdle()
        assertEquals(AuthError.UNAVAILABLE, (vm.state.value.reveal as RevealUi.Auth).error)
    }

    @Test
    fun `progress rows follow the real phases`() {
        fun map(phase: MigrationProgress, awaiting: Boolean = false) = SafeModeViewModel.progressFor(
            title = ProgressTitle.UPDATING,
            checkingData = SafeStepState.DONE,
            phase = phase,
            awaitingNewRun = awaiting,
            startedAtElapsed = 0,
        )
        map(MigrationProgress.OpeningStorage).let {
            assertEquals(SafeStepState.RUNNING, it.storage)
            assertEquals(SafeStepState.WAITING, it.accounts)
        }
        map(MigrationProgress.CheckingAccounts(1, 3)).let {
            assertEquals(SafeStepState.DONE, it.storage)
            assertEquals(SafeStepState.RUNNING, it.accounts)
            assertEquals(1, it.accountsChecked)
            assertEquals(3, it.accountsTotal)
        }
        map(MigrationProgress.Saving).let {
            assertEquals(SafeStepState.DONE, it.accounts)
            assertEquals(SafeStepState.RUNNING, it.finishing)
        }
        // A finished earlier run never shows ticks for a retry that has not started yet.
        map(MigrationProgress.Finished, awaiting = true).let {
            assertEquals(SafeStepState.WAITING, it.storage)
            assertEquals(SafeStepState.WAITING, it.accounts)
            assertEquals(SafeStepState.WAITING, it.finishing)
        }
        SafeModeViewModel.progressFor(
            ProgressTitle.FINISHING, SafeStepState.RUNNING, MigrationProgress.CheckingAccounts(1, 2), false, 0,
        ).let {
            assertEquals(SafeStepState.WAITING, it.storage)
        }
    }
}
