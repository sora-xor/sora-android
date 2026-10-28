package jp.co.soramitsu.feature_multiaccount_impl.export.account_details

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details.AccountDetailsViewModel
import jp.co.soramitsu.xbackup.BackupService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import jp.co.soramitsu.feature_account_api.domain.model.WalletBackupMaterial
import jp.co.soramitsu.xbackup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.xbackup.domain.exceptions.FileNotFoundException
import jp.co.soramitsu.xbackup.domain.exceptions.StorageQuotaExceeded
import java.net.SocketException
import java.net.SocketTimeoutException

/** Asynchronous account editing and cloud operations through inert service boundaries. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountManagementFlowTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()
    private val interactor = mockk<MultiaccountInteractor>()
    private val resources = mockk<ResourceManager>(relaxed = true)
    private val backup = mockk<BackupService>()
    private val dispatcher = mockk<CoroutineManager>()
    private val launcher = mockk<ActivityResultLauncher<Intent>>()
    private val store = ViewModelStore()
    private val account = SoraAccount("fixture-account", "My wallet")
    @Before fun setup() {
        every { dispatcher.io } returns Dispatchers.Main; every { dispatcher.main } returns Dispatchers.Main
        every { interactor.flowUnavailableWalletIds() } returns MutableStateFlow(emptySet())
        coEvery { interactor.getSoraAccount(account.substrateAddress) } returns account
        coEvery { interactor.getMnemonic(account) } returns "public fixture phrase"
        coEvery { interactor.anyBackupStarted() } just Runs
        coEvery { interactor.updateName(any(), any()) } just Runs
        coEvery { backup.isAccountBackedUp(any()) } returns true
        coEvery { backup.authorize(any()) } returns true
        coEvery { backup.logout() } just Runs
        coEvery { backup.getBackupAccounts() } returns emptyList()
        coEvery { backup.deleteBackupAccount(any()) } just Runs
    }
    @After fun cleanup() { store.clear() }
    private fun TestScope.vm(): AccountDetailsViewModel {
        val value = AccountDetailsViewModel(interactor, mockk<MainRouter>(relaxed = true), resources,
            mockk<BasicClipboardManager>(relaxed = true), CloudBackupProvider(true) { backup }, dispatcher, account.substrateAddress)
        store.put("vm", value); runCurrent(); return value
    }
    @Test fun duplicateAuthorizationTapsStartOneRequest() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Boolean>()
        coEvery { backup.authorize(any()) } coAnswers { pending.await() }
        vm.onBackupClicked(launcher); vm.onBackupClicked(launcher); runCurrent()
        coVerify(exactly = 1) { backup.authorize(launcher) }; coVerify(exactly = 1) { interactor.anyBackupStarted() }
        pending.complete(false); runCurrent(); assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun declinedAuthorizationDoesNotRevertANameEditedWhileWaiting() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Boolean>()
        coEvery { backup.authorize(any()) } coAnswers { pending.await() }
        vm.onBackupClicked(launcher); runCurrent(); vm.onNameChange(TextFieldValue("Edited while signing in"))
        pending.complete(false); runCurrent(); assertEquals("Edited while signing in", vm.accountDetailsScreenState.value!!.accountNameState.value.text)
    }
    @Test fun authorizationCanRetryAfterCancellation() = runTest {
        val vm = vm(); coEvery { backup.authorize(any()) } throws CancellationException("closed")
        vm.onBackupClicked(launcher); runCurrent(); assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
        assertNull(vm.errorFromResourceLiveData.value)
        coEvery { backup.authorize(any()) } returns true
        vm.onBackupClicked(launcher); runCurrent(); assertEquals(true, vm.deleteDialogState.value)
    }
    @Test fun deletingWhileAuthorizationIsPendingDoesNotStartAnotherCloudOperation() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Boolean>(); coEvery { backup.authorize(any()) } coAnswers { pending.await() }
        vm.onBackupClicked(launcher); runCurrent(); vm.deleteGoogleBackup(); runCurrent()
        coVerify(exactly = 0) { backup.deleteBackupAccount(any()) }; pending.complete(false); runCurrent()
    }
    @Test fun duplicateDeleteTapsStartOneDeletion() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Unit>(); coEvery { backup.deleteBackupAccount(any()) } coAnswers { pending.await() }
        vm.deleteGoogleBackup(); vm.deleteGoogleBackup(); runCurrent()
        coVerify(exactly = 1) { backup.deleteBackupAccount(account.substrateAddress) }
        pending.complete(Unit); runCurrent(); assertEquals(false, vm.accountDetailsScreenState.value!!.isBackupAvailable)
    }
    @Test fun unexpectedDeleteFailureIsReportedAndLeavesBackupAvailable() = runTest {
        val vm = vm(); coEvery { backup.deleteBackupAccount(any()) } throws IllegalStateException("service unavailable")
        vm.deleteGoogleBackup(); runCurrent(); assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value!!.second)
    }
    @Test fun cancelledDeleteNeverClaimsBackupWasRemoved() = runTest {
        val vm = vm(); coEvery { backup.deleteBackupAccount(any()) } throws CancellationException("closed")
        vm.deleteGoogleBackup(); runCurrent(); assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading); assertNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun timedOutDeleteCanBeRetried() = runTest {
        val vm = vm(); coEvery { backup.deleteBackupAccount(any()) } throws SocketTimeoutException("timeout")
        vm.deleteGoogleBackup(); runCurrent(); assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        coEvery { backup.deleteBackupAccount(any()) } just Runs
        vm.deleteGoogleBackup(); runCurrent(); assertEquals(false, vm.accountDetailsScreenState.value!!.isBackupAvailable)
    }
    @Test fun successfulDeletePreservesConcurrentNameEdit() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Unit>(); coEvery { backup.deleteBackupAccount(any()) } coAnswers { pending.await() }
        vm.deleteGoogleBackup(); runCurrent(); vm.onNameChange(TextFieldValue("New name")); pending.complete(Unit); runCurrent()
        assertEquals("New name", vm.accountDetailsScreenState.value!!.accountNameState.value.text)
    }
    @Test fun dismissingDialogDoesNotReleaseAnInFlightDeleteGuard() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Unit>(); coEvery { backup.deleteBackupAccount(any()) } coAnswers { pending.await() }
        vm.deleteGoogleBackup(); runCurrent(); vm.deleteDialogDismiss(); vm.deleteGoogleBackup(); runCurrent()
        coVerify(exactly = 1) { backup.deleteBackupAccount(any()) }; pending.complete(Unit); runCurrent()
    }
    @Test fun signInRefreshShowsProgressAndBlocksDuplicateCallbacks() = runTest {
        val vm = vm(); clearMocks(backup, answers = false); val pending = CompletableDeferred<Boolean>()
        coEvery { backup.isAccountBackedUp(any()) } coAnswers { pending.await() }
        vm.onSuccessfulGoogleSignin(); vm.onSuccessfulGoogleSignin(); runCurrent()
        assertTrue(vm.accountDetailsScreenState.value!!.isBackupLoading)
        coVerify(exactly = 1) { backup.isAccountBackedUp(account.substrateAddress) }
        pending.complete(true); runCurrent(); assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun signInRefreshPreservesANameEditedWhileWaiting() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Boolean>(); coEvery { backup.isAccountBackedUp(any()) } coAnswers { pending.await() }
        vm.onSuccessfulGoogleSignin(); runCurrent(); vm.onNameChange(TextFieldValue("Edited")); pending.complete(true); runCurrent()
        assertEquals("Edited", vm.accountDetailsScreenState.value!!.accountNameState.value.text)
    }
    @Test fun unexpectedSignInRefreshFailureIsReportedWithoutCrashing() = runTest {
        val vm = vm(); coEvery { backup.isAccountBackedUp(any()) } throws IllegalStateException("metadata unavailable")
        vm.onSuccessfulGoogleSignin(); runCurrent(); assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value!!.second)
        assertNull(vm.createBackupPasswordState.value)
    }
    @Test fun cancelledSignInRefreshRetainsExistingAvailability() = runTest {
        val vm = vm(); coEvery { backup.isAccountBackedUp(any()) } throws CancellationException("closed")
        vm.onSuccessfulGoogleSignin(); runCurrent(); assertEquals(true, vm.accountDetailsScreenState.value!!.isBackupAvailable)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading); assertNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun signInNetworkFailureDoesNotOpenPasswordForm() = runTest {
        val vm = vm(); coEvery { backup.isAccountBackedUp(any()) } throws SocketException("offline")
        vm.onSuccessfulGoogleSignin(); runCurrent(); assertNull(vm.createBackupPasswordState.value)
        assertFalse(vm.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun signInTimeoutCanBeRetried() = runTest {
        val vm = vm(); coEvery { backup.isAccountBackedUp(any()) } throws SocketTimeoutException("timeout")
        vm.onSuccessfulGoogleSignin(); runCurrent()
        coEvery { backup.isAccountBackedUp(any()) } returns false
        vm.onSuccessfulGoogleSignin(); runCurrent(); assertNotNull(vm.createBackupPasswordState.value)
    }
    @Test fun newNameIsSavedOnlyAfterDebounceSettles() = runTest {
        val vm = vm(); vm.onNameChange(TextFieldValue("First")); advanceTimeBy(300)
        vm.onNameChange(TextFieldValue("Final")); advanceTimeBy(499); runCurrent()
        coVerify(exactly = 0) { interactor.updateName(any(), any()) }
        advanceTimeBy(1); runCurrent(); coVerify(exactly = 1) { interactor.updateName(account.substrateAddress, "Final") }
    }
    @Test fun failedNameWriteDoesNotKillLaterEdits() = runTest {
        val vm = vm(); coEvery { interactor.updateName(any(), "First") } throws IllegalStateException("database unavailable")
        vm.onNameChange(TextFieldValue("First")); advanceTimeBy(501); runCurrent()
        vm.onNameChange(TextFieldValue("Second")); advanceTimeBy(501); runCurrent()
        coVerify(exactly = 1) { interactor.updateName(account.substrateAddress, "Second") }
    }
    @Test fun clearingScreenCancelsPendingNameSave() = runTest {
        val vm = vm(); vm.onNameChange(TextFieldValue("Do not persist")); store.clear(); advanceTimeBy(501); runCurrent()
        coVerify(exactly = 0) { interactor.updateName(any(), any()) }
    }
    @Test fun clearingScreenCancelsPendingCloudAuthorization() = runTest {
        val vm = vm(); val pending = CompletableDeferred<Boolean>(); coEvery { backup.authorize(any()) } coAnswers { pending.await() }
        vm.onBackupClicked(launcher); runCurrent(); store.clear(); pending.complete(true); runCurrent()
        assertNull(vm.createBackupPasswordState.value); assertEquals(false, vm.deleteDialogState.value)
    }

    private fun consentFailure(): AuthConsentException = mockk<AuthConsentException>(relaxed = true) { every { intent } returns mockk() }
    private fun TestScope.readyToSave(): AccountDetailsViewModel {
        coEvery { backup.isAccountBackedUp(any()) } returns false
        coEvery { interactor.getBackupMaterial(account) } returns WalletBackupMaterial(true, listOf("fixture"), "01", false, false)
        coEvery { interactor.getMnemonic(account.substrateAddress) } returns "public fixture phrase"
        coEvery { interactor.getSeed(account.substrateAddress) } returns "01"
        coEvery { interactor.generateSubstrateJsonString(any(), any()) } returns "encrypted fixture"
        coEvery { backup.saveBackupAccount(any(), any()) } just Runs
        val value = vm()
        value.onSuccessfulGoogleSignin(); runCurrent()
        value.onBackupPasswordChanged(TextFieldValue("ValidPublicPassword1!"))
        value.onBackupPasswordConfirmationChanged(TextFieldValue("ValidPublicPassword1!"))
        value.onWarningToggle()
        assertTrue(value.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
        return value
    }
    @Test fun authorizationNetworkFailuresReleaseProgressAndPermitRetry() = runTest {
        val value = vm()
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), IllegalStateException("failed"))) {
            coEvery { backup.authorize(any()) } throws failure
            value.onBackupClicked(launcher); runCurrent()
            assertFalse(value.accountDetailsScreenState.value!!.isBackupLoading)
            assertFalse(value.deleteDialogState.value!!)
        }
        assertEquals(R.string.wallet_cloud_backup_failed, value.errorFromResourceLiveData.value!!.second)
        coEvery { backup.authorize(any()) } returns true
        value.onBackupClicked(launcher); runCurrent()
        assertTrue(value.deleteDialogState.value!!)
    }
    @Test fun authorizationConsentRefreshesMetadataOnlyOnceAfterSuccessfulConsent() = runTest {
        val value = vm(); val failure = consentFailure()
        coEvery { backup.authorize(any()) } throws failure
        value.onBackupClicked(launcher); runCurrent()
        assertSame(failure.intent, value.consentExceptionHandler.value)
        assertFalse(value.accountDetailsScreenState.value!!.isBackupLoading)
        clearMocks(backup, answers = false)
        coEvery { backup.isAccountBackedUp(any()) } returns false
        value.onSuccessfulConsent(); value.onSuccessfulConsent(); runCurrent()
        coVerify(exactly = 1) { backup.isAccountBackedUp(account.substrateAddress) }
        assertNotNull(value.createBackupPasswordState.value)
    }
    @Test fun failedCloudDeletionForwardsConsentWithoutClaimingRemoval() = runTest {
        val value = vm(); val failure = consentFailure()
        coEvery { backup.deleteBackupAccount(any()) } throws failure
        value.deleteGoogleBackup(); runCurrent()
        assertSame(failure.intent, value.consentExceptionHandler.value)
        assertTrue(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
        assertFalse(value.accountDetailsScreenState.value!!.isBackupLoading)
        clearMocks(backup, answers = false)
        value.onSuccessfulConsent(); runCurrent()
        coVerify(exactly = 0) { backup.isAccountBackedUp(any()) }
    }
    @Test fun alreadyDeletedCloudFileIsTreatedAsAbsent() = runTest {
        val value = vm()
        coEvery { backup.deleteBackupAccount(any()) } throws FileNotFoundException(Exception("gone"))
        value.deleteGoogleBackup(); runCurrent()
        assertFalse(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
        assertFalse(value.accountDetailsScreenState.value!!.isBackupLoading)
        assertNull(value.errorFromResourceLiveData.value)
    }
    @Test fun offlineDeletionLeavesTheBackupAvailableForRetry() = runTest {
        val value = vm()
        coEvery { backup.deleteBackupAccount(any()) } throws SocketException("offline")
        value.deleteGoogleBackup(); runCurrent()
        assertTrue(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
        assertFalse(value.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun signInConsentIsForwardedAndAbsentFileClearsAvailability() = runTest {
        val value = vm(); val failure = consentFailure()
        coEvery { backup.isAccountBackedUp(any()) } throws failure
        value.onSuccessfulGoogleSignin(); runCurrent()
        assertSame(failure.intent, value.consentExceptionHandler.value)
        assertTrue(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
        coEvery { backup.isAccountBackedUp(any()) } throws FileNotFoundException(Exception("gone"))
        value.onSuccessfulGoogleSignin(); runCurrent()
        assertFalse(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
        assertFalse(value.accountDetailsScreenState.value!!.isBackupLoading)
    }
    @Test fun allSaveFailuresClearProgressAndKeepThePasswordFormForRetry() = runTest {
        val value = readyToSave()
        val consent = consentFailure()
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent,
            StorageQuotaExceeded(Exception("quota")), IllegalStateException("failed"), CancellationException("cancelled"))) {
            coEvery { backup.saveBackupAccount(any(), any()) } throws failure
            value.onBackupPasswordClicked(); runCurrent()
            assertFalse(value.createBackupPasswordState.value!!.isLoading)
            assertTrue(value.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
            assertFalse(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
            if (failure is StorageQuotaExceeded) assertEquals(R.string.backup_not_enough_space_error, value.errorFromResourceLiveData.value!!.second)
            if (failure is IllegalStateException) assertEquals(R.string.wallet_cloud_backup_failed, value.errorFromResourceLiveData.value!!.second)
        }
        assertSame(consent.intent, value.consentExceptionHandler.value)
        coEvery { backup.saveBackupAccount(any(), any()) } just Runs
        coEvery { backup.isAccountBackedUp(any()) } returns true
        value.onBackupPasswordClicked(); runCurrent()
        assertTrue(value.accountDetailsScreenState.value!!.isBackupAvailable!!)
        coVerify(exactly = 7) { backup.saveBackupAccount(any(), "ValidPublicPassword1!") }
    }
    @Test fun pendingSaveRejectsDuplicateSaveAuthorizationAndDeletion() = runTest {
        val value = readyToSave(); val pending = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { pending.await() }
        value.onBackupPasswordClicked(); runCurrent()
        assertTrue(value.createBackupPasswordState.value!!.isLoading)
        value.onBackupPasswordClicked(); value.onBackupClicked(launcher); value.deleteGoogleBackup(); runCurrent()
        coVerify(exactly = 1) { backup.saveBackupAccount(any(), any()) }
        coVerify(exactly = 0) { backup.authorize(any()) }
        coVerify(exactly = 0) { backup.deleteBackupAccount(any()) }
        pending.complete(Unit); runCurrent()
        assertFalse(value.createBackupPasswordState.value!!.isLoading)
    }
}
