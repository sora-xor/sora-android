package jp.co.soramitsu.sora.settings

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.xbackup.domain.exceptions.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavController
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.*
import jp.co.soramitsu.xbackup.BackupService
import jp.co.soramitsu.xbackup.domain.models.*
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.net.SocketException
import java.net.SocketTimeoutException

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.RunWith

/** Real Android Main/LiveData lifecycle; synthetic drafts and inert persistence/Drive services. */
@RunWith(AndroidJUnit4::class)
class OnboardingCloudLifecycleContractTest {
    private val interactor = mockk<MultiaccountInteractor>(relaxed = true)
    private val backup = mockk<BackupService>()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val starter = mockk<MainStarter>(relaxed = true)
    private val nav = mockk<NavController>(relaxed = true)
    private val activity = mockk<Activity>()
    private val store = ViewModelStore()
    private val account = SoraAccount("public-account", "Fixture wallet")
    private val password = "public-test-password"
    private val words = List(24) { "fixture$it" }
    private var wiped = false
    private val draft = WalletDraft(account.substrateAddress, account.accountName,
        words.joinToString(" "), "00".repeat(32), Sr25519Keypair(ByteArray(64), ByteArray(32), ByteArray(32)),
        WalletDraft.Source.GENERATED, { wiped = true })
    private val saved = mutableListOf<DecryptedBackupAccount>()
    private val savedPasswords = mutableListOf<String>()
    private val dispatcher = mockk<CoroutineManager> {
        every { io } returns Dispatchers.Main
        every { main } returns Dispatchers.Main
    }
    @Before fun setup() {
        coEvery { interactor.walletsRestorableWithPin() } returns emptyList()
        coEvery { interactor.createDraft(any()) } returns draft
        coEvery { interactor.generateDraftJson(draft, any()) } returns "encrypted-fixture-json"
        coEvery { interactor.convertPassphraseToSeed(any()) } returns "public-fixture-seed"
        coEvery { interactor.createUserFromDraft(draft) } returns account
        every { resources.getString(any()) } returns "fixture message %s"
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers {
            saved += firstArg<DecryptedBackupAccount>(); savedPasswords += secondArg<String>(); Unit
        }
        coEvery { backup.getBackupAccounts() } returns emptyList()
        coEvery { interactor.isAddressValid(any()) } returns true
        coEvery { interactor.isMnemonicValid(any()) } returns true
        coEvery { interactor.addressOfMnemonic(any()) } returns account.substrateAddress
        coEvery { interactor.importFromMnemonic(any(), any()) } returns account
        coEvery { backup.importBackupAccount(any(), any()) } returns cloudBackup()
    }
    @After fun cleanup() { act { store.clear(); draft.destroy() } }
    private fun cloudBackup() = DecryptedBackupAccount(name = account.accountName, address = account.substrateAddress,
        mnemonicPhrase = words.joinToString(" "), cryptoType = CryptoType.SR25519,
        backupAccountType = listOf(BackupAccountType.PASSPHRASE), seed = null, json = null)
    private fun createViewModel(cloud: Boolean = true) = OnboardingViewModel(mockk<InvitationHandler>(relaxed = true),
        interactor, starter, resources, CloudBackupProvider(cloud) { backup }, mockk<AccountAvatarGenerator>(relaxed = true), dispatcher)
        .also { store.put("vm-${System.identityHashCode(it)}", it) }
    private fun prepare(vm: OnboardingViewModel) = act {
        vm.onAccountNameChanged(TextFieldValue(account.accountName)); vm.onCreateAccountContinueClicked(nav)
        assertEquals(words, vm.passphraseCardState.value?.mnemonicWords)
    }
    private fun ready(vm: OnboardingViewModel) = act {
        vm.onBackupPasswordChanged(TextFieldValue(password)); vm.onBackupPasswordConfirmationChanged(TextFieldValue(password)); vm.onWarningToggle()
        assertTrue(vm.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
    }
    private fun select(vm: OnboardingViewModel, address: String = account.substrateAddress) = act {
        vm.onImportAccountSelected(nav, BackupAccountMetaWithIcon(BackupAccountMeta(account.accountName, address), null))
        vm.onImportPasswordChanged(TextFieldValue(password))
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun act(block: () -> Unit) { instrumentation.runOnMainSync(block); instrumentation.waitForIdleSync() }
    private fun viewModel(): OnboardingViewModel { lateinit var value: OnboardingViewModel; act { value = createViewModel() }; return value }
    private fun consent() = mockk<AuthConsentException>(relaxed = true) { every { intent } returns Intent("fixture.consent") }
    @Test fun cloudLoginFailuresSettleLoadingAndPermitTheSameScreenToRetry() {
        val vm = viewModel(); val launcher = mockk<ActivityResultLauncher<Intent>>(); val consent = consent()
        coEvery { backup.logout() } just Runs
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("unavailable"), CancellationException("closed"))) {
            coEvery { backup.authorize(launcher) } throws failure
            act { vm.onRecoveryDialogShow(); vm.onGoogleSignin(nav, launcher) }
            assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coEvery { backup.authorize(launcher) } returns false
        act { vm.onGoogleSignin(nav, launcher) }; assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
        coEvery { backup.authorize(launcher) } returns true
        act { vm.onGoogleSignin(nav, launcher) }
        verify { nav.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT) }
    }
    @Test fun loadingBackupListFailuresRetainTheCurrentDestination() {
        val vm = viewModel(); val consent = consent()
        for (failure in listOf(UnauthorizedException(), SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("failed"), CancellationException("closed"))) {
            coEvery { backup.getBackupAccounts() } throws failure
            act { vm.onSuccessfulGoogleSignin(nav) }; assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        verify(exactly = 0) { nav.navigate(any<String>()) }
    }
    @Test fun failedCloudSavesKeepTheDraftAndNeverCommitAnAccount() {
        val vm = viewModel(); prepare(vm); ready(vm); val consent = consent()
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, StorageQuotaExceeded(Exception("quota")), IllegalStateException("failed"), CancellationException("closed"))) {
            coEvery { backup.saveBackupAccount(any(), any()) } throws failure
            act { vm.onSetBackupPasswordClicked(activity) }
            assertFalse(vm.createBackupPasswordState.value!!.isLoading); assertFalse(wiped)
            assertEquals(words.joinToString(" "), draft.mnemonic)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
    }
    @Test fun successfulCloudSavePublishesTheDraftOnlyAfterUploadThenWipesIt() {
        val vm = viewModel(); prepare(vm); ready(vm)
        act { vm.onSetBackupPasswordClicked(activity) }
        assertEquals(account.substrateAddress, saved.single().address)
        assertEquals(words.joinToString(" "), saved.single().mnemonicPhrase)
        coVerifyOrder { backup.saveBackupAccount(any(), password); interactor.createUserFromDraft(draft); starter.start(activity) }
        assertTrue(wiped); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
    }
    @Test fun downloadFailuresKeepTheChosenAccountAndPasswordForRetry() {
        val vm = viewModel(); select(vm); val consent = consent()
        for (failure in listOf(DecryptionException(), DecodingException(), IllegalArgumentException("corrupt"), SocketException("offline"), SocketTimeoutException("timeout"), consent, CancellationException("closed"))) {
            coEvery { backup.importBackupAccount(any(), any()) } throws failure
            act { vm.onImportContinueClicked(nav) }
            assertFalse(vm.importAccountPasswordState.value!!.isLoading)
            assertEquals(account.substrateAddress, vm.importAccountPasswordState.value!!.selectedAccount!!.backupAccountMeta.address)
            assertEquals(password, vm.importAccountPasswordState.value!!.passwordInput.value.text)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coVerify(exactly = 0) { interactor.importFromMnemonic(any(), any()) }
    }
    @Test fun failedImportMorePreservesSelectionWhileSuccessfulRefreshClearsIt() {
        val vm = viewModel(); select(vm); val consent = consent()
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("failed"), CancellationException("closed"))) {
            coEvery { backup.getBackupAccounts() } throws failure
            act { vm.onImportMoreClicked(nav) }
            assertFalse(vm.importAccountPasswordState.value!!.isLoading)
            assertEquals(account.substrateAddress, vm.importAccountPasswordState.value!!.selectedAccount!!.backupAccountMeta.address)
        }
        coEvery { backup.getBackupAccounts() } returns emptyList()
        act { vm.onImportMoreClicked(nav) }
        assertNull(vm.importAccountPasswordState.value!!.selectedAccount)
        assertEquals("", vm.importAccountPasswordState.value!!.passwordInput.value.text)
    }
    @Test fun postImportListFailureDoesNotUndoTheAlreadyImportedWallet() {
        val vm = viewModel(); select(vm)
        coEvery { backup.getBackupAccounts() } throws IllegalStateException("list unavailable")
        act { vm.onImportContinueClicked(nav) }
        coVerify(exactly = 1) { interactor.importFromMnemonic(words.joinToString(" "), account.accountName) }
        assertFalse(vm.importAccountPasswordState.value!!.isImportMoreAvailable)
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
        act { vm.onImportFinished(activity) }; verify(exactly = 1) { starter.start(activity) }
    }
    @Test fun destroyingTheViewModelCancelsPendingUploadAndWipesUncommittedDraft() {
        val vm = viewModel(); prepare(vm); ready(vm); val release = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { release.await() }
        act { vm.onSetBackupPasswordClicked(activity) }; assertTrue(vm.createBackupPasswordState.value!!.isLoading)
        act { store.clear() }; assertTrue(wiped); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        release.complete(Unit); instrumentation.waitForIdleSync()
        coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
    }
}
