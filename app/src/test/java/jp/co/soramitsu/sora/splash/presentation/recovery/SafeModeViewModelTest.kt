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
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStepState
import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationProgress
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    private val datasource = mockk<CredentialsDatasource>()
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
        )
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
