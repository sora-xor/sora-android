package jp.co.soramitsu.feature_multiaccount_impl.export.account_details

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsViewModel
import jp.co.soramitsu.xbackup.BackupService
import jp.co.soramitsu.xbackup.domain.models.BackupAccountType
import jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.net.SocketException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class AccountBackupFlowContractTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()
    private val interactor = mockk<MultiaccountInteractor>()
    private val router = mockk<MainRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val clipboard = mockk<BasicClipboardManager>(relaxed = true)
    private val backup = mockk<BackupService>()
    private val dispatcher = mockk<CoroutineManager>()
    private val launcher = mockk<ActivityResultLauncher<Intent>>()
    private val store = ViewModelStore()
    private val account = SoraAccount("fixture-account", "My wallet")
    private val unavailable = MutableStateFlow<Set<String>>(emptySet())
    private val password = "test-password"
    private val saved = mutableListOf<DecryptedBackupAccount>()
    private val savedPasswords = mutableListOf<String>()
    @Before fun setup() {
        every { dispatcher.io } returns Dispatchers.Main
        every { dispatcher.main } returns Dispatchers.Main
        every { interactor.flowUnavailableWalletIds() } returns unavailable
        coEvery { interactor.getSoraAccount(account.substrateAddress) } returns account
        coEvery { interactor.getMnemonic(account) } returns "public fixture phrase"
        coEvery { interactor.getMnemonic(account.substrateAddress) } returns "public fixture phrase"
        coEvery { interactor.getSeed(account.substrateAddress) } returns "public-fixture-seed"
        coEvery { interactor.getBackupMaterial(account) } returns WalletBackupMaterial(true, listOf("public", "fixture", "phrase"), "public-fixture-seed", false, false)
        coEvery { interactor.generateSubstrateJsonString(listOf(account), any()) } returns "encrypted-fixture-json"
        coEvery { interactor.anyBackupStarted() } just Runs
        coEvery { interactor.updateName(any(), any()) } just Runs
        coEvery { backup.isAccountBackedUp(account.substrateAddress) } returns true
        coEvery { backup.authorize(launcher) } returns true
        coEvery { backup.logout() } just Runs
        coEvery { backup.getBackupAccounts() } returns emptyList()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers {
            saved.add(firstArg()); savedPasswords.add(secondArg()); Unit
        }
        coEvery { backup.deleteBackupAccount(account.substrateAddress) } just Runs
    }
    @After fun cleanup() { store.clear() }
    private fun viewModel(cloud: Boolean = true): AccountDetailsViewModel {
        val vm = AccountDetailsViewModel(interactor, router, resources, clipboard, CloudBackupProvider(cloud) { backup }, dispatcher, account.substrateAddress)
        store.put("vm-${System.identityHashCode(vm)}", vm)
        return vm
    }
    private fun TestScope.openForm(vm: AccountDetailsViewModel) {
        runCurrent()
        coEvery { backup.isAccountBackedUp(account.substrateAddress) } returns false
        vm.onBackupClicked(launcher); runCurrent()
        assertNotNull(vm.createBackupPasswordState.value)
    }
    private fun ready(vm: AccountDetailsViewModel) {
        vm.onBackupPasswordChanged(TextFieldValue(password))
        vm.onBackupPasswordConfirmationChanged(TextFieldValue(password))
        vm.onWarningToggle()
        assertTrue(vm.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
    }
    @Test fun `successful backup retains only qualified forms and exact password`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { backup.isAccountBackedUp(account.substrateAddress) } returns true
        vm.onBackupPasswordClicked(); runCurrent()
        val value = saved.single()
        assertEquals(account.substrateAddress, value.address); assertEquals(account.accountName, value.name)
        assertEquals("public fixture phrase", value.mnemonicPhrase)
        assertEquals("public-fixture-seed", value.seed?.substrateSeed); assertEquals("encrypted-fixture-json", value.json?.substrateJson)
        assertEquals(listOf(BackupAccountType.PASSPHRASE, BackupAccountType.SEED, BackupAccountType.JSON), value.backupAccountType)
        assertEquals(listOf(password), savedPasswords); assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        assertNotNull(vm.navigationPop.value); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
    }
    @Test fun `weak password cannot invoke backup even if the click handler is called directly`() = runTest {
        val vm = viewModel(); openForm(vm)
        vm.onBackupPasswordChanged(TextFieldValue("short")); vm.onBackupPasswordConfirmationChanged(TextFieldValue("short")); vm.onWarningToggle()
        vm.onBackupPasswordClicked(); runCurrent(); assertTrue(saved.isEmpty())
        coVerify(exactly = 0) { interactor.getBackupMaterial(any<SoraAccount>()) }
    }
    @Test fun `mismatched confirmation cannot invoke backup`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        vm.onBackupPasswordConfirmationChanged(TextFieldValue("other-password"))
        vm.onBackupPasswordClicked(); runCurrent(); assertTrue(saved.isEmpty())
    }
    @Test fun `backup requires the acknowledged warning`() = runTest {
        val vm = viewModel(); openForm(vm)
        vm.onBackupPasswordChanged(TextFieldValue(password)); vm.onBackupPasswordConfirmationChanged(TextFieldValue(password))
        vm.onBackupPasswordClicked(); runCurrent(); assertTrue(saved.isEmpty())
    }
    @Test fun `password editing strips spaces and validates the same new value`() = runTest {
        val vm = viewModel(); openForm(vm)
        vm.onBackupPasswordConfirmationChanged(TextFieldValue("test pass word")); vm.onWarningToggle()
        vm.onBackupPasswordChanged(TextFieldValue("test pass word"))
        val state = vm.createBackupPasswordState.value!!
        assertEquals("testpassword", state.password.value.text); assertEquals("testpassword", state.passwordConfirmation.value.text)
        assertTrue(state.password.success); assertTrue(state.passwordConfirmation.success); assertTrue(state.setPasswordButtonIsEnabled)
        vm.onBackupPasswordChanged(TextFieldValue("short"))
        assertFalse(vm.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
        assertTrue(vm.createBackupPasswordState.value!!.passwordConfirmation.error)
    }
    @Test fun `clearing confirmation removes stale success and readiness`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        vm.onBackupPasswordConfirmationChanged(TextFieldValue(""))
        val state = vm.createBackupPasswordState.value!!
        assertFalse(state.passwordConfirmation.success); assertFalse(state.passwordConfirmation.error)
        assertFalse(state.setPasswordButtonIsEnabled)
        assertEquals(R.string.common_empty_string, state.passwordConfirmation.descriptionText)
    }
    @Test fun `repeated save taps cannot create simultaneous cloud writes`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        val pending = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { pending.await() }
        vm.onBackupPasswordClicked(); vm.onBackupPasswordClicked(); runCurrent()
        coVerify(exactly = 1) { backup.saveBackupAccount(any(), any()) }
        pending.complete(Unit); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
    }
    @Test fun `socket failure restores save readiness without losing password edits`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws SocketException("offline")
        vm.onBackupPasswordClicked(); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertEquals(password, vm.createBackupPasswordState.value!!.password.value.text)
        assertNull(vm.navigationPop.value)
    }
    @Test fun `timeout restores save readiness`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws SocketTimeoutException("timeout")
        vm.onBackupPasswordClicked(); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading); assertNull(vm.navigationPop.value)
    }
    @Test fun `unexpected cloud failure is reported without crashing the view model`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws IllegalStateException("cloud unavailable")
        vm.onBackupPasswordClicked(); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value!!.second)
        assertNull(vm.navigationPop.value)
    }
    @Test fun `cancelled backup clears progress without reporting a failure`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws CancellationException("screen closed")
        vm.onBackupPasswordClicked(); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertNull(vm.errorFromResourceLiveData.value); assertNull(vm.navigationPop.value)
    }
    @Test fun `read only account without qualified material never uploads an empty backup`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { interactor.getBackupMaterial(account) } returns WalletBackupMaterial(false, emptyList(), "", true, true)
        vm.onBackupPasswordClicked(); runCurrent()
        assertTrue(saved.isEmpty()); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertEquals(R.string.backup_no_matching_secret_read_only, vm.errorFromResourceLiveData.value!!.second)
        coVerify(exactly = 0) { interactor.generateSubstrateJsonString(any(), any()) }
    }
    @Test fun `read only account with a proven phrase can back up without signing JSON`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { interactor.getBackupMaterial(account) } returns WalletBackupMaterial(false, listOf("fixture"), "", false, false)
        coEvery { interactor.getSeed(account.substrateAddress) } returns ""
        vm.onBackupPasswordClicked(); runCurrent()
        assertEquals(listOf(BackupAccountType.PASSPHRASE), saved.single().backupAccountType)
        assertTrue(saved.single().json?.substrateJson.isNullOrEmpty())
        coVerify(exactly = 0) { interactor.generateSubstrateJsonString(any(), any()) }
    }
    @Test fun `JSON only backup does not invent a recovery phrase or seed`() = runTest {
        val vm = viewModel(); openForm(vm); ready(vm)
        coEvery { interactor.getBackupMaterial(account) } returns WalletBackupMaterial(true, emptyList(), "", true, true)
        coEvery { interactor.getMnemonic(account.substrateAddress) } returns ""
        coEvery { interactor.getSeed(account.substrateAddress) } returns ""
        vm.onBackupPasswordClicked(); runCurrent()
        assertEquals(listOf(BackupAccountType.JSON), saved.single().backupAccountType)
        assertTrue(saved.single().mnemonicPhrase.isNullOrEmpty()); assertTrue(saved.single().seed?.substrateSeed.isNullOrEmpty())
    }
    @Test fun `cloud disabled actions do not create a service or authorize`() = runTest {
        val vm = viewModel(false); runCurrent()
        vm.onBackupClicked(launcher); vm.onBackupPasswordClicked(); vm.deleteGoogleBackup(); vm.onSuccessfulGoogleSignin(); runCurrent()
        coVerify(exactly = 0) { backup.authorize(any()); backup.isAccountBackedUp(any()); backup.deleteBackupAccount(any()) }
        assertEquals(R.string.wallet_cloud_backup_unavailable, vm.errorFromResourceLiveData.value!!.second)
        assertFalse(vm.accountDetailsScreenState.value!!.isCloudBackupAvailable)
    }
    @Test fun `missing activity launcher does not start backup`() = runTest {
        val vm = viewModel(); runCurrent(); vm.onBackupClicked(null); runCurrent()
        coVerify(exactly = 0) { interactor.anyBackupStarted(); backup.authorize(any()) }
    }
    @Test fun `failed authorization clears progress and does not open a form`() = runTest {
        val vm = viewModel(); runCurrent(); coEvery { backup.authorize(launcher) } returns false
        vm.onBackupClicked(launcher); runCurrent()
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading); assertNull(vm.createBackupPasswordState.value)
    }
    @Test fun `authorization exception clears progress and reports failure`() = runTest {
        val vm = viewModel(); runCurrent(); coEvery { backup.authorize(launcher) } throws IllegalStateException("unavailable")
        vm.onBackupClicked(launcher); runCurrent()
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value!!.second)
    }
    @Test fun `existing cloud backup requires an explicit delete choice`() = runTest {
        val vm = viewModel(); runCurrent(); vm.onBackupClicked(launcher); runCurrent()
        assertEquals(true, vm.deleteDialogState.value); coVerify(exactly = 0) { backup.deleteBackupAccount(any()) }
        vm.deleteDialogDismiss(); assertEquals(false, vm.deleteDialogState.value)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun `delete cloud backup updates availability and preserves local account`() = runTest {
        val vm = viewModel(); runCurrent(); vm.deleteGoogleBackup(); runCurrent()
        coVerify(exactly = 1) { backup.deleteBackupAccount(account.substrateAddress) }
        assertEquals(false, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
        assertEquals(account.substrateAddress, vm.accountDetailsScreenState.value!!.address)
    }
    @Test fun `failed delete leaves backup availability intact and ends progress`() = runTest {
        val vm = viewModel(); runCurrent(); coEvery { backup.deleteBackupAccount(any()) } throws SocketException("offline")
        vm.deleteGoogleBackup(); runCurrent()
        assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun `sign in result refreshes existing backup status`() = runTest {
        val vm = viewModel(); runCurrent(); vm.onSuccessfulGoogleSignin(); runCurrent()
        assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable); assertNull(vm.createBackupPasswordState.value)
    }
    @Test fun `sign in without a backup opens a clean password form`() = runTest {
        val vm = viewModel(); runCurrent(); coEvery { backup.isAccountBackedUp(any()) } returns false
        vm.onSuccessfulGoogleSignin(); runCurrent()
        val state = vm.createBackupPasswordState.value!!
        assertEquals("", state.password.value.text); assertEquals("", state.passwordConfirmation.value.text)
        assertFalse(state.setPasswordButtonIsEnabled); assertFalse(state.warningIsSelected)
    }
    @Test fun `export actions retain the account identity and require protected routes`() = runTest {
        val vm = viewModel(); runCurrent()
        vm.onShowPassphrase(); vm.onShowRawSeed(); vm.onExportJson(); vm.onLogout(); vm.onAddressCopy(); runCurrent()
        verify { router.showExportPassphraseProtection(account.substrateAddress); router.showExportSeedProtection(account.substrateAddress)
            router.showExportJSONProtection(listOf(account.substrateAddress)); router.showPinForLogout(account.substrateAddress)
            clipboard.addToClipboard(account.substrateAddress) }
        coVerify(exactly = 3) { interactor.anyBackupStarted() }
        assertNotNull(vm.copiedToast.value)
    }
    @Test fun `account name byte limit rejects multibyte overflow but keeps user edit visible`() = runTest {
        val vm = viewModel(); runCurrent()
        val longName = "あ".repeat(OptionsProvider.nameByteLimit)
        vm.onNameChange(TextFieldValue(longName)); advanceTimeBy(501); runCurrent()
        assertEquals(longName, vm.accountDetailsScreenState.value!!.accountNameState.value.text)
        coVerify(exactly = 0) { interactor.updateName(any(), any()) }
        vm.onNameChange(TextFieldValue("Valid")); advanceTimeBy(501); runCurrent()
        coVerify { interactor.updateName(account.substrateAddress, "Valid") }
    }
}
