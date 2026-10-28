package jp.co.soramitsu.sora.settings

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsViewModel
import jp.co.soramitsu.xbackup.BackupService
import jp.co.soramitsu.xbackup.domain.exceptions.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.net.SocketException
import java.net.SocketTimeoutException

/** Android Main/LiveData lifecycle regression tests; all cloud and signing boundaries are inert. */
@RunWith(AndroidJUnit4::class)
class AccountCloudLifecycleContractTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val interactor = mockk<MultiaccountInteractor>()
    private val backup = mockk<BackupService>()
    private val router = mockk<MainRouter>(relaxed = true)
    private val clipboard = mockk<BasicClipboardManager>(relaxed = true)
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val dispatchers = mockk<CoroutineManager>()
    private val launcher = mockk<ActivityResultLauncher<Intent>>()
    private val account = SoraAccount("fixture-account", "Local wallet")
    private val store = ViewModelStore()
    @Before fun setup() {
        every { dispatchers.main } returns Dispatchers.Main
        every { dispatchers.io } returns Dispatchers.Main
        every { resources.getString(any()) } answers { compose.activity.getString(firstArg()) }
        every { interactor.flowUnavailableWalletIds() } returns MutableStateFlow(emptySet())
        coEvery { interactor.getSoraAccount(account.substrateAddress) } returns account
        coEvery { interactor.getMnemonic(account) } returns "public fixture phrase"
        coEvery { interactor.anyBackupStarted() } just Runs
        coEvery { backup.isAccountBackedUp(any()) } returns true
        coEvery { backup.authorize(any()) } returns true
        coEvery { backup.logout() } just Runs
        coEvery { backup.getBackupAccounts() } returns emptyList()
        coEvery { backup.deleteBackupAccount(any()) } just Runs
    }
    @After fun cleanup() { compose.runOnUiThread { store.clear() } }
    private fun screen(): AccountDetailsViewModel {
        lateinit var vm: AccountDetailsViewModel
        compose.runOnUiThread { vm = AccountDetailsViewModel(interactor, router, resources, clipboard, CloudBackupProvider(true) { backup }, dispatchers, account.substrateAddress); store.put("account", vm) }
        compose.waitForIdle(); return vm
    }
    private fun act(block: () -> Unit) { compose.runOnUiThread(block); compose.waitForIdle() }
    private fun consent() = mockk<AuthConsentException>(relaxed = true) { every { intent } returns Intent("fixture.consent") }
    private fun ready(): AccountDetailsViewModel {
        coEvery { backup.isAccountBackedUp(any()) } returns false
        coEvery { interactor.getBackupMaterial(account) } returns WalletBackupMaterial(true, listOf("fixture"), "01", false, false)
        coEvery { interactor.getMnemonic(account.substrateAddress) } returns "public fixture phrase"
        coEvery { interactor.getSeed(account.substrateAddress) } returns "01"
        coEvery { interactor.generateSubstrateJsonString(any(), any()) } returns "encrypted public fixture"
        coEvery { backup.saveBackupAccount(any(), any()) } just Runs
        val vm = screen(); act { vm.onSuccessfulGoogleSignin() }
        act { vm.onBackupPasswordChanged(TextFieldValue("PublicPassword1!")); vm.onBackupPasswordConfirmationChanged(TextFieldValue("PublicPassword1!")); vm.onWarningToggle() }
        assertTrue(vm.createBackupPasswordState.value!!.setPasswordButtonIsEnabled); return vm
    }
    @Test fun optionalNetworkLookupFailureStillLoadsLocalAccountAndExportActions() {
        coEvery { backup.isAccountBackedUp(any()) } throws SocketException("offline")
        val vm = screen()
        assertEquals(account.substrateAddress, vm.accountDetailsScreenState.value!!.address)
        assertNull(vm.accountDetailsScreenState.value!!.isBackupAvailable)
        act { vm.onShowPassphrase(); vm.onShowRawSeed(); vm.onExportJson(); vm.onAddressCopy(); vm.onLogout() }
        verify { router.showExportPassphraseProtection(account.substrateAddress); router.showExportSeedProtection(account.substrateAddress); router.showExportJSONProtection(mutableListOf(account.substrateAddress)); router.showPinForLogout(account.substrateAddress); clipboard.addToClipboard(account.substrateAddress) }
        coVerify(exactly = 3) { interactor.anyBackupStarted() }
    }
    @Test fun failedReadOnlyStreamDoesNotHideLocalDetailsOrInventRestoreOptions() {
        every { interactor.flowUnavailableWalletIds() } returns flow { throw IllegalStateException("read failed") }
        val vm = screen(); assertNull(vm.accountDetailsScreenState.value!!.readOnly)
        assertEquals("Local wallet", vm.accountDetailsScreenState.value!!.accountNameState.value.text)
        act { vm.onRestoreKeysWithPhrase(); vm.onRestoreKeysAnotherWay(); vm.onRestoreKeysFromGoogle(); vm.onShowSavedBackup() }
        assertNull(vm.restoreKeysEvent.value); verify(exactly = 0) { router.showExportPassphraseProtection(any()) }
    }
    @Test fun authorizationErrorsReleaseLoadingAndCanRetryOnTheSameScreen() {
        val vm = screen()
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), IllegalStateException("service unavailable"), CancellationException("closed"))) {
            coEvery { backup.authorize(any()) } throws failure
            act { vm.onBackupClicked(launcher) }
            assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading); assertFalse(vm.deleteDialogState.value!!)
        }
        coEvery { backup.authorize(any()) } returns true
        act { vm.onBackupClicked(launcher) }; assertTrue(vm.deleteDialogState.value!!)
    }
    @Test fun authorizationConsentIsForwardedAndRefreshesOnlyAfterConsent() {
        val vm = screen(); val error = consent()
        coEvery { backup.authorize(any()) } throws error
        act { vm.onBackupClicked(launcher) }; assertSame(error.intent, vm.consentExceptionHandler.value)
        coEvery { backup.isAccountBackedUp(any()) } returns false
        act { vm.onSuccessfulConsent() }; assertNotNull(vm.createBackupPasswordState.value)
        clearMocks(backup, answers = false)
        act { vm.onSuccessfulConsent() }; coVerify(exactly = 0) { backup.isAccountBackedUp(any()) }
    }
    @Test fun deleteErrorsRetainBackupAvailabilityAndAbsentFileClearsIt() {
        val vm = screen(); val consent = consent()
        for (error in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("failed"), CancellationException("closed"))) {
            coEvery { backup.deleteBackupAccount(any()) } throws error
            act { vm.deleteGoogleBackup() }
            assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading); assertTrue(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coEvery { backup.deleteBackupAccount(any()) } throws FileNotFoundException(Exception("already gone"))
        act { vm.deleteGoogleBackup() }; assertFalse(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
    }
    @Test fun signInFailuresLeaveTheExistingBackupAndCanRecover() {
        val vm = screen(); val consent = consent()
        for (error in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("failed"), CancellationException("closed"))) {
            coEvery { backup.isAccountBackedUp(any()) } throws error
            act { vm.onSuccessfulGoogleSignin() }
            assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading); assertTrue(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coEvery { backup.isAccountBackedUp(any()) } throws FileNotFoundException(Exception("gone"))
        act { vm.onSuccessfulGoogleSignin() }; assertFalse(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
        coEvery { backup.isAccountBackedUp(any()) } returns true
        act { vm.onSuccessfulGoogleSignin() }; assertTrue(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
    }
    @Test fun failedSaveClearsMainThreadProgressAndRetainsRetryablePasswordForm() {
        val vm = ready(); val consent = consent()
        for (error in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, StorageQuotaExceeded(Exception("quota")), IllegalStateException("failed"), CancellationException("closed"))) {
            coEvery { backup.saveBackupAccount(any(), any()) } throws error
            act { vm.onBackupPasswordClicked() }
            assertFalse(vm.createBackupPasswordState.value!!.isLoading); assertTrue(vm.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
            assertFalse(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coEvery { backup.saveBackupAccount(any(), any()) } just Runs
        coEvery { backup.isAccountBackedUp(any()) } returns true
        act { vm.onBackupPasswordClicked() }; assertTrue(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
    }
    @Test fun pendingSaveIsCancelledWhenTheScreenViewModelIsDestroyed() {
        val vm = ready(); val pending = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { pending.await() }
        act { vm.onBackupPasswordClicked() }; assertTrue(vm.createBackupPasswordState.value!!.isLoading)
        act { store.clear() }; assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        pending.complete(Unit); compose.waitForIdle(); assertFalse(vm.accountDetailsScreenState.value!!.isBackupAvailable!!)
    }
}
