package jp.co.soramitsu.feature_multiaccount_impl

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import jp.co.soramitsu.xbackup.domain.exceptions.*
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavController
import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
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
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.net.SocketException
import java.net.SocketTimeoutException

/** Public synthetic material and inert account/cloud boundaries: no signing, storage or Drive. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingCloudFlowTest {
    @get:Rule val instant = InstantTaskExecutorRule()
    @get:Rule val main = MainCoroutineRule()
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
    @After fun cleanup() { store.clear(); draft.destroy() }
    private fun cloudBackup() = DecryptedBackupAccount(name = account.accountName, address = account.substrateAddress,
        mnemonicPhrase = words.joinToString(" "), cryptoType = CryptoType.SR25519,
        backupAccountType = listOf(BackupAccountType.PASSPHRASE), seed = null, json = null)
    private fun viewModel(cloud: Boolean = true) = OnboardingViewModel(mockk<InvitationHandler>(relaxed = true),
        interactor, starter, resources, CloudBackupProvider(cloud) { backup }, mockk<AccountAvatarGenerator>(relaxed = true), dispatcher)
        .also { store.put("vm-${System.identityHashCode(it)}", it) }
    private fun TestScope.prepare(vm: OnboardingViewModel) {
        vm.onAccountNameChanged(TextFieldValue(account.accountName)); vm.onCreateAccountContinueClicked(nav); runCurrent()
        assertEquals(words, vm.passphraseCardState.value?.mnemonicWords)
    }
    private fun ready(vm: OnboardingViewModel) {
        vm.onBackupPasswordChanged(TextFieldValue(password)); vm.onBackupPasswordConfirmationChanged(TextFieldValue(password)); vm.onWarningToggle()
        assertTrue(vm.createBackupPasswordState.value!!.setPasswordButtonIsEnabled)
    }
    private fun select(vm: OnboardingViewModel, address: String = account.substrateAddress) {
        vm.onImportAccountSelected(nav, BackupAccountMetaWithIcon(BackupAccountMeta(account.accountName, address), null))
        vm.onImportPasswordChanged(TextFieldValue(password))
    }
    @Test fun qualifiedBackupContainsExactDraftFormsAndCommitsAfterUpload() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm); vm.onSetBackupPasswordClicked(activity); runCurrent()
        val value = saved.single()
        assertEquals(account.substrateAddress, value.address); assertEquals(account.accountName, value.name)
        assertEquals(words.joinToString(" "), value.mnemonicPhrase); assertEquals("public-fixture-seed", value.seed?.substrateSeed)
        assertEquals("encrypted-fixture-json", value.json?.substrateJson); assertEquals(listOf(password), savedPasswords)
        assertEquals(setOf(BackupAccountType.JSON, BackupAccountType.PASSPHRASE, BackupAccountType.SEED), value.backupAccountType.toSet())
        coVerifyOrder { backup.saveBackupAccount(any(), password); interactor.createUserFromDraft(draft); starter.start(activity) }
        assertTrue(wiped); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
    }
    @Test fun noWarningAcknowledgmentCannotUpload() = runTest {
        val vm = viewModel(); prepare(vm)
        vm.onBackupPasswordChanged(TextFieldValue(password)); vm.onBackupPasswordConfirmationChanged(TextFieldValue(password))
        vm.onSetBackupPasswordClicked(activity); runCurrent(); assertTrue(saved.isEmpty()); assertFalse(wiped)
    }
    @Test fun weakPasswordCannotUploadEvenWithWarning() = runTest {
        val vm = viewModel(); prepare(vm)
        vm.onBackupPasswordChanged(TextFieldValue("short")); vm.onBackupPasswordConfirmationChanged(TextFieldValue("short")); vm.onWarningToggle()
        vm.onSetBackupPasswordClicked(activity); runCurrent(); assertTrue(saved.isEmpty())
    }
    @Test fun changedConfirmationCannotUpload() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm); vm.onBackupPasswordConfirmationChanged(TextFieldValue("different-password"))
        vm.onSetBackupPasswordClicked(activity); runCurrent(); assertTrue(saved.isEmpty())
    }
    @Test fun repeatedSaveTapStartsOneUploadAndOneCommit() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm); val release = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { release.await() }
        vm.onSetBackupPasswordClicked(activity); vm.onSetBackupPasswordClicked(activity); runCurrent()
        try { coVerify(exactly = 1) { backup.saveBackupAccount(any(), any()) } } finally { release.complete(Unit); runCurrent() }
        coVerify(exactly = 1) { interactor.createUserFromDraft(draft) }
    }
    @Test fun missingDraftReportsInterruptionAndClearsLoading() = runTest {
        val vm = viewModel(); ready(vm); vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertTrue(saved.isEmpty()); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertEquals(R.string.create_account_interrupted, vm.errorFromResourceLiveData.value?.second)
    }
    @Test fun disabledCloudBackupDoesNotTouchProvider() = runTest {
        val vm = viewModel(false); prepare(vm); ready(vm); vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertTrue(saved.isEmpty()); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertEquals(R.string.wallet_cloud_backup_unavailable, vm.errorFromResourceLiveData.value?.second)
    }
    @Test fun socketFailureClearsLoadingAndLeavesDraftAvailable() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws SocketException("fixture offline")
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading); assertFalse(wiped)
        assertEquals(words.joinToString(" "), draft.mnemonic); coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
    }
    @Test fun timeoutClearsLoadingAndDoesNotCommit() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws SocketTimeoutException("fixture timeout")
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading); coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
    }
    @Test fun unexpectedUploadFailureBecomesRecoverableFormError() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws IllegalStateException("fixture cloud failure")
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading); assertFalse(wiped)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value?.second)
    }
    @Test fun cancellationSettlesFormWithoutReportingCloudFailure() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws CancellationException("fixture cancelled")
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertFalse(vm.createBackupPasswordState.value!!.isLoading); assertNull(vm.errorFromResourceLiveData.value); assertFalse(wiped)
    }
    @Test fun editsDuringFailedUploadAreNotOverwrittenByCapturedForm() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm); val release = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { release.await(); throw SocketException("offline") }
        vm.onSetBackupPasswordClicked(activity); runCurrent(); vm.onBackupPasswordChanged(TextFieldValue("changed-password"))
        release.complete(Unit); runCurrent()
        assertEquals("changed-password", vm.createBackupPasswordState.value!!.password.value.text)
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
    }
    @Test fun draftJsonFailureDoesNotUploadOrCommit() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { interactor.generateDraftJson(any(), any()) } throws IllegalStateException("fixture encode failed")
        vm.onSetBackupPasswordClicked(activity); runCurrent(); assertTrue(saved.isEmpty()); assertFalse(wiped)
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
    }
    @Test fun failedUploadCanBeRetriedWithSameDraft() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { backup.saveBackupAccount(any(), any()) } throws SocketException("offline")
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        coEvery { backup.saveBackupAccount(any(), any()) } just Runs
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        coVerify(exactly = 1) { interactor.createUserFromDraft(draft) }; verify(exactly = 1) { starter.start(activity) }
    }
    @Test fun failedCommitPreservesDraftAndReturnsToPhrase() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        coEvery { interactor.createUserFromDraft(any()) } throws IllegalStateException("fixture disk full")
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertEquals(1, saved.size); assertFalse(wiped); assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        assertEquals(R.string.create_account_not_saved, vm.errorFromResourceLiveData.value?.second)
        verify(exactly = 0) { starter.start(any()) }
    }
    @Test fun changingCloudImportSelectionClearsPreviousPassword() = runTest {
        val vm = viewModel(); select(vm)
        vm.onImportAccountSelected(nav, BackupAccountMetaWithIcon(BackupAccountMeta("Second", "second-account"), null))
        assertEquals("", vm.importAccountPasswordState.value!!.passwordInput.value.text)
        assertFalse(vm.importAccountPasswordState.value!!.isContinueButtonEnabled)
    }
    @Test fun blankImportPasswordCannotStartCloudRequest() = runTest {
        val vm = viewModel(); select(vm); vm.onImportPasswordChanged(TextFieldValue("")); vm.onImportContinueClicked(nav); runCurrent()
        coVerify(exactly = 0) { backup.importBackupAccount(any(), any()) }
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
    }
    @Test fun noSelectedImportAccountDoesNotStartRequestOrRemainLoading() = runTest {
        val vm = viewModel(); vm.onImportPasswordChanged(TextFieldValue(password)); vm.onImportContinueClicked(nav); runCurrent()
        coVerify(exactly = 0) { backup.importBackupAccount(any(), any()) }; assertFalse(vm.importAccountPasswordState.value!!.isLoading)
    }
    @Test fun repeatedImportTapStartsOneRestore() = runTest {
        val vm = viewModel(); select(vm); val release = CompletableDeferred<DecryptedBackupAccount>()
        coEvery { backup.importBackupAccount(any(), any()) } coAnswers { release.await() }
        vm.onImportContinueClicked(nav); vm.onImportContinueClicked(nav); runCurrent()
        try { coVerify(exactly = 1) { backup.importBackupAccount(any(), any()) } } finally { release.complete(cloudBackup()); runCurrent() }
        coVerify(exactly = 1) { interactor.importFromMnemonic(any(), any()) }
    }
    @Test fun cloudImportCancellationClearsLoading() = runTest {
        val vm = viewModel(); select(vm)
        coEvery { backup.importBackupAccount(any(), any()) } throws CancellationException("fixture cancelled")
        vm.onImportContinueClicked(nav); runCurrent()
        assertFalse(vm.importAccountPasswordState.value!!.isLoading); assertNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun unexpectedImportFailureIsShownAndCanBeRetried() = runTest {
        val vm = viewModel(); select(vm)
        coEvery { backup.importBackupAccount(any(), any()) } throws IllegalStateException("fixture download failure")
        vm.onImportContinueClicked(nav); runCurrent()
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value?.second)
    }
    @Test fun importMoreSocketFailureDoesNotLeaveSpinner() = runTest {
        val vm = viewModel(); coEvery { backup.getBackupAccounts() } throws SocketException("fixture offline")
        vm.onImportMoreClicked(nav); runCurrent()
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
    }
    @Test fun importMoreTimeoutDoesNotLeaveSpinner() = runTest {
        val vm = viewModel(); coEvery { backup.getBackupAccounts() } throws SocketTimeoutException("fixture timeout")
        vm.onImportMoreClicked(nav); runCurrent()
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
    }
    @Test fun importMoreCancellationDoesNotLeaveSpinner() = runTest {
        val vm = viewModel(); coEvery { backup.getBackupAccounts() } throws CancellationException("fixture cancelled")
        vm.onImportMoreClicked(nav); runCurrent()
        assertFalse(vm.importAccountPasswordState.value!!.isLoading); assertNull(vm.errorFromResourceLiveData.value)
    }
    @Test fun importMoreUnexpectedFailureDoesNotCrash() = runTest {
        val vm = viewModel(); coEvery { backup.getBackupAccounts() } throws IllegalStateException("fixture unexpected")
        vm.onImportMoreClicked(nav); runCurrent()
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
        assertEquals(R.string.wallet_cloud_backup_failed, vm.errorFromResourceLiveData.value?.second)
    }
    @Test fun importMoreResetsPreviousCredentialsAfterSuccessfulListRead() = runTest {
        val vm = viewModel(); select(vm); vm.onImportMoreClicked(nav); runCurrent()
        val state = vm.importAccountPasswordState.value!!
        assertEquals("", state.passwordInput.value.text); assertNull(state.selectedAccount); assertFalse(state.isLoading)
        verify(exactly = 1) { nav.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST) }
    }
    @Test fun unavailableCloudCannotLoadMore() = runTest {
        val vm = viewModel(false); vm.onImportMoreClicked(nav); runCurrent()
        coVerify(exactly = 0) { backup.getBackupAccounts() }
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
    }
    @Test fun disposingUncommittedDraftWipesOnlyMemoryMaterial() = runTest {
        val vm = viewModel(); prepare(vm); store.clear()
        assertTrue(wiped); coVerify(exactly = 0) { interactor.createUserFromDraft(any()); backup.saveBackupAccount(any(), any()) }
    }
    @Test fun phraseConfirmationRequiresThreeDistinctPositionsBeforeSaving() = runTest {
        val vm = viewModel(); prepare(vm); vm.onPassphraseContinueClicked(nav)
        val positions = mutableSetOf<Int>()
        repeat(3) { step ->
            val state = vm.passphraseConfirmationState.value!!
            assertEquals(step + 1, state.confirmationStep); assertTrue(positions.add(state.currentWordIndex))
            assertEquals(3, state.buttonsList.toSet().size); assertTrue(words[state.currentWordIndex - 1] in state.buttonsList)
            coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
            vm.onConfirmationButtonPressed(activity, words[state.currentWordIndex - 1]); runCurrent()
        }
        coVerify(exactly = 1) { interactor.createUserFromDraft(draft) }; assertTrue(wiped)
    }
    @Test fun wrongPhraseAnswerRestartsConfirmationWithoutSaving() = runTest {
        val vm = viewModel(); prepare(vm); vm.onPassphraseContinueClicked(nav)
        val first = vm.passphraseConfirmationState.value!!
        vm.onConfirmationButtonPressed(activity, words[first.currentWordIndex - 1]); runCurrent()
        vm.onConfirmationButtonPressed(activity, "not-in-fixture-phrase"); runCurrent()
        val restarted = vm.passphraseConfirmationState.value!!
        assertEquals(1, restarted.confirmationStep); assertEquals(1, restarted.confirmedWordIndexes.size)
        assertNotNull(vm.alertDialogLiveData.value); coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
    }
    @Test fun repeatedFinalPhraseAnswerDoesNotStartAnotherCommit() = runTest {
        val vm = viewModel(); prepare(vm); vm.onPassphraseContinueClicked(nav)
        repeat(2) { vm.onConfirmationButtonPressed(activity, words[vm.passphraseConfirmationState.value!!.currentWordIndex - 1]); runCurrent() }
        val answer = words[vm.passphraseConfirmationState.value!!.currentWordIndex - 1]
        val release = CompletableDeferred<SoraAccount>()
        coEvery { interactor.createUserFromDraft(draft) } coAnswers { release.await() }
        vm.onConfirmationButtonPressed(activity, answer); vm.onConfirmationButtonPressed(activity, answer); runCurrent()
        release.complete(account); runCurrent()
        coVerify(exactly = 1) { interactor.createUserFromDraft(draft) }
        assertEquals(4, vm.passphraseConfirmationState.value!!.confirmationStep)
    }
    @Test fun phraseContinueBeforeDraftDoesNotOpenEmptyConfirmation() = runTest {
        val vm = viewModel(); vm.onPassphraseContinueClicked(nav); runCurrent()
        verify(exactly = 0) { nav.navigate(OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION) }
        assertNull(vm.passphraseConfirmationState.value)
    }
    @Test fun disclaimerRequiresEveryAcknowledgmentAndCanBeUnchecked() = runTest {
        val vm = viewModel(); prepare(vm)
        val items = vm.disclaimerCardState.value!!.selectableItemList
        assertFalse(vm.disclaimerCardState.value!!.isButtonEnabled)
        items.indices.forEach { vm.onItemClicked(it) }
        assertTrue(vm.disclaimerCardState.value!!.isButtonEnabled)
        vm.onItemClicked(0); assertFalse(vm.disclaimerCardState.value!!.isButtonEnabled)
        vm.onItemClicked(-1); assertFalse(vm.disclaimerCardState.value!!.isButtonEnabled)
    }
    @Test fun termsLoadingFinishesWithoutChangingSelectedDocument() = runTest {
        val vm = viewModel(); vm.onTermsClicked(nav)
        val before = vm.termsAndPrivacyState.value!!
        assertEquals(R.string.common_terms_title, before.title); assertTrue(before.webViewState.loading)
        vm.onTermsAndPrivacyLoadingFinished()
        assertEquals(before.webViewState.copy(loading = false), vm.termsAndPrivacyState.value!!.webViewState)
        verify(exactly = 1) { nav.navigate(OnboardingFeatureRoutes.TERMS_AND_PRIVACY) }
    }
    @Test fun privacyReplacesTermsAndStartsIndependentLoading() = runTest {
        val vm = viewModel(); vm.onTermsClicked(nav); vm.onTermsAndPrivacyLoadingFinished()
        val terms = vm.termsAndPrivacyState.value!!
        vm.onPrivacyClicked(nav)
        assertEquals(R.string.tutorial_privacy_policy, vm.termsAndPrivacyState.value!!.title)
        assertTrue(vm.termsAndPrivacyState.value!!.webViewState.loading)
        assertNotEquals(terms.webViewState, vm.termsAndPrivacyState.value!!.webViewState)
    }
    @Test fun skippingBackupStillCommitsOnlyOnceWhenTappedTwice() = runTest {
        val vm = viewModel(); prepare(vm); val release = CompletableDeferred<SoraAccount>()
        coEvery { interactor.createUserFromDraft(draft) } coAnswers { release.await() }
        vm.onAction(); assertEquals(true, vm.skipDialogState.value)
        vm.skipDialogConfirm(activity); vm.skipDialogConfirm(activity); runCurrent()
        try { coVerify(exactly = 1) { interactor.createUserFromDraft(draft) } } finally { release.complete(account); runCurrent() }
        assertEquals(false, vm.skipDialogState.value)
    }

    @Test fun completedUploadCannotCommitADifferentReplacementDraft() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm)
        val release = CompletableDeferred<Unit>()
        coEvery { backup.saveBackupAccount(any(), any()) } coAnswers { release.await() }
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        val replacement = WalletDraft("replacement", "Second wallet", words.joinToString(" "), "11".repeat(32),
            Sr25519Keypair(ByteArray(64), ByteArray(32), ByteArray(32)), WalletDraft.Source.GENERATED,
            { it.privateKey.fill(0); it.nonce.fill(0) })
        coEvery { interactor.createDraft(any()) } returns replacement
        vm.onCreateAccountContinueClicked(nav); runCurrent()
        release.complete(Unit); runCurrent()
        coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
        verify(exactly = 0) { starter.start(any()) }
    }
    @Test fun repeatedImportMoreTapStartsOneListRequest() = runTest {
        val vm = viewModel(); val release = CompletableDeferred<List<BackupAccountMeta>>()
        coEvery { backup.getBackupAccounts() } coAnswers { release.await() }
        vm.onImportMoreClicked(nav); vm.onImportMoreClicked(nav); runCurrent()
        try { coVerify(exactly = 1) { backup.getBackupAccounts() } }
        finally { release.complete(emptyList()); runCurrent() }
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
    }


    private fun consentFailure(): AuthConsentException = mockk<AuthConsentException>(relaxed = true) { every { intent } returns mockk() }
    @Test fun cloudLoginFailureMatrixSettlesSpinnerAndAllowsASecondLogin() = runTest {
        val vm = viewModel(); val launcher = mockk<ActivityResultLauncher<Intent>>()
        val consent = consentFailure()
        coEvery { backup.logout() } just Runs
        for (failure in listOf(SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("unavailable"), CancellationException("closed"))) {
            coEvery { backup.authorize(launcher) } throws failure
            vm.onRecoveryDialogShow(); vm.onGoogleSignin(nav, launcher); runCurrent()
            assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coEvery { backup.authorize(launcher) } returns false
        vm.onGoogleSignin(nav, launcher); runCurrent()
        assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
        coVerify(exactly = 0) { backup.getBackupAccounts() }
        coEvery { backup.authorize(launcher) } returns true
        vm.onGoogleSignin(nav, launcher); runCurrent()
        verify(exactly = 1) { nav.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT) }
        assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
    }
    @Test fun loadingBackupListFailureMatrixSettlesSpinnerWithoutNavigating() = runTest {
        val vm = viewModel(); val consent = consentFailure()
        for (failure in listOf(UnauthorizedException(), SocketException("offline"), SocketTimeoutException("timeout"), consent, IllegalStateException("unavailable"), CancellationException("closed"))) {
            coEvery { backup.getBackupAccounts() } throws failure
            vm.onSuccessfulGoogleSignin(nav); runCurrent()
            assertFalse(vm.tutorialScreenState.value!!.isGoogleSigninLoading)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        verify(exactly = 0) { nav.navigate(any<String>()) }
    }
    @Test fun savingNewWalletForwardsConsentAndQuotaFailuresWithoutCommitting() = runTest {
        val vm = viewModel(); prepare(vm); ready(vm); val consent = consentFailure()
        coEvery { backup.saveBackupAccount(any(), any()) } throws consent
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        assertFalse(vm.createBackupPasswordState.value!!.isLoading)
        coEvery { backup.saveBackupAccount(any(), any()) } throws StorageQuotaExceeded(Exception("quota"))
        vm.onSetBackupPasswordClicked(activity); runCurrent()
        assertEquals(R.string.backup_not_enough_space_error, vm.errorFromResourceLiveData.value!!.second)
        assertFalse(wiped); coVerify(exactly = 0) { interactor.createUserFromDraft(any()) }
    }
    @Test fun downloadFailureMatrixDoesNotImportOrDiscardSelectedAccount() = runTest {
        val vm = viewModel(); select(vm); val consent = consentFailure()
        for (failure in listOf(DecryptionException(), DecodingException(), IllegalArgumentException("corrupt"), SocketException("offline"), SocketTimeoutException("timeout"), consent)) {
            coEvery { backup.importBackupAccount(any(), any()) } throws failure
            vm.onImportContinueClicked(nav); runCurrent()
            assertFalse(vm.importAccountPasswordState.value!!.isLoading)
            assertEquals(account.substrateAddress, vm.importAccountPasswordState.value!!.selectedAccount!!.backupAccountMeta.address)
            assertEquals(password, vm.importAccountPasswordState.value!!.passwordInput.value.text)
        }
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        coVerify(exactly = 0) { interactor.importFromMnemonic(any(), any()) }
    }
    @Test fun importMoreForwardsConsentAndRetainsTheSelectedBackup() = runTest {
        val vm = viewModel(); select(vm); val consent = consentFailure()
        coEvery { backup.getBackupAccounts() } throws consent
        vm.onImportMoreClicked(nav); runCurrent()
        assertSame(consent.intent, vm.consentExceptionHandler.value)
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
        assertEquals(account.substrateAddress, vm.importAccountPasswordState.value!!.selectedAccount!!.backupAccountMeta.address)
    }
    @Test fun failedRemainingBackupLookupStillCompletesAnAlreadySuccessfulImport() = runTest {
        val vm = viewModel(); select(vm)
        coEvery { backup.getBackupAccounts() } throws IllegalStateException("list unavailable")
        vm.onImportContinueClicked(nav); runCurrent()
        coVerify(exactly = 1) { interactor.importFromMnemonic(words.joinToString(" "), account.accountName) }
        assertFalse(vm.importAccountPasswordState.value!!.isImportMoreAvailable)
        assertFalse(vm.importAccountPasswordState.value!!.isLoading)
        assertNull(vm.errorFromResourceLiveData.value)
        vm.onImportFinished(activity); verify(exactly = 1) { starter.start(activity) }
    }
    private fun TestScope.offerPhraseRepair(vm: OnboardingViewModel) {
        coEvery { interactor.importFromMnemonic(any(), any()) } throws jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException(account.substrateAddress, true, true)
        vm.onRecoveryClicked(nav, 1); vm.onRecoveryInputChanged(TextFieldValue(words.joinToString(" ")))
        vm.onRecoveryAccountChanged(TextFieldValue(account.accountName)); runCurrent()
        vm.recoveryNextClicked(nav, activity); runCurrent(); assertNotNull(vm.repairPrompt.value)
    }
    @Test fun repairFailureClosesPromptWithoutClaimingSuccessAndAllowsANewReview() = runTest {
        val vm = viewModel()
        for (failure in listOf(jp.co.soramitsu.common.domain.SoraException.businessError(jp.co.soramitsu.common.domain.ResponseCode.WALLET_KEYS_UNAVAILABLE), IllegalStateException("repair unavailable"))) {
            offerPhraseRepair(vm); coEvery { interactor.repairFromMnemonic(any(), any(), any(), any()) } throws failure
            vm.onRepairConfirmed("123456"); runCurrent()
            assertNull(vm.repairPrompt.value); assertFalse(vm.recoveryAccountNameCardState.value!!.loading)
        }
        verify(exactly = 0) { starter.start(any()) }
    }
    @Test fun repeatedRepairConfirmationAndDismissDuringWorkCannotStartAnotherRepair() = runTest {
        val vm = viewModel(); offerPhraseRepair(vm); val release = CompletableDeferred<SoraAccount>()
        coEvery { interactor.repairFromMnemonic(any(), any(), any(), any()) } coAnswers { release.await() }
        vm.onRepairConfirmed("123456"); runCurrent(); vm.onRepairConfirmed("654321"); vm.onRepairDismissed(); runCurrent()
        assertTrue(vm.repairPrompt.value!!.working)
        try { coVerify(exactly = 1) { interactor.repairFromMnemonic(any(), any(), any(), any()) } }
        finally { release.complete(account); runCurrent() }
        assertNull(vm.repairPrompt.value); verify(exactly = 1) { starter.start(activity) }
    }
    @Test fun retainedRestoreFailureResetsWorkingAndCanBeRetriedWithoutLeavingOnboarding() = runTest {
        coEvery { interactor.walletsRestorableWithPin() } returns listOf(account.substrateAddress)
        val vm = viewModel(); runCurrent()
        for (failure in listOf(jp.co.soramitsu.common.domain.SoraException.businessError(jp.co.soramitsu.common.domain.ResponseCode.WALLET_KEYS_UNAVAILABLE), IllegalStateException("storage unavailable"))) {
            coEvery { interactor.restoreRetainedWallets(any(), any()) } throws failure
            vm.onRestoreFoundWallets("123456", activity); runCurrent()
            assertFalse(vm.foundWallets.value!!.working); assertFalse(vm.foundWallets.value!!.wrongPin)
            assertEquals(listOf(account.substrateAddress), vm.foundWallets.value!!.addresses)
        }
        verify(exactly = 0) { starter.start(any()) }
    }
    @Test fun concurrentRetainedRestoreStartsOneMutationAndCompletesOnce() = runTest {
        coEvery { interactor.walletsRestorableWithPin() } returns listOf(account.substrateAddress)
        val vm = viewModel(); runCurrent(); val release = CompletableDeferred<MultiaccountInteractor.RetainedRestoreResult>()
        coEvery { interactor.restoreRetainedWallets(any(), any()) } coAnswers { release.await() }
        vm.onRestoreFoundWallets("123456", activity); vm.onRestoreFoundWallets("654321", activity); runCurrent()
        try { coVerify(exactly = 1) { interactor.restoreRetainedWallets(any(), any()) }; assertTrue(vm.foundWallets.value!!.working) }
        finally { release.complete(MultiaccountInteractor.RetainedRestoreResult(listOf(account), emptyList())); runCurrent() }
        verify(exactly = 1) { starter.start(activity) }
    }
    @Test fun toolbarTracksEveryOnboardingDestinationAndRestrictsSkipToPhraseStages() = runTest {
        every { resources.getString(any()) } answers { "resource:${firstArg<Int>()}" }; val vm = viewModel()
        val routes = listOf(OnboardingFeatureRoutes.TUTORIAL to R.string.tutorial_many_world,
            OnboardingFeatureRoutes.CREATE_ACCOUNT to R.string.onboarding_create_account_title,
            OnboardingFeatureRoutes.DISCLAIMER to R.string.common_pay_attention,
            OnboardingFeatureRoutes.PASSPHRASE to R.string.common_passphrase_title,
            OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST to R.string.select_account_import,
            OnboardingFeatureRoutes.IMPORT_ACCOUNT_PASSWORD to R.string.enter_backup_password_title,
            OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD to R.string.create_backup_password_title,
            OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS to R.string.imported_account_title,
            OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION to R.string.account_confirmation_title_v2,
            OnboardingFeatureRoutes.TERMS_AND_PRIVACY to R.string.common_terms_title,
            OnboardingFeatureRoutes.FOUND_WALLETS to R.string.safe_mode_retained_title,
            "unknown" to R.string.tutorial_many_world)
        for ((route, title) in routes) {
            vm.onDestinationChanged(route)
            assertEquals("resource:$title", vm.toolbarState.value!!.basic.title)
            assertEquals(route != OnboardingFeatureRoutes.TUTORIAL, vm.toolbarState.value!!.basic.visibility)
            assertEquals(if (route in listOf(OnboardingFeatureRoutes.PASSPHRASE, OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION)) "resource:${R.string.common_skip}" else null, vm.toolbarState.value!!.basic.actionLabel)
        }
        vm.onPrivacyClicked(nav); vm.onDestinationChanged(OnboardingFeatureRoutes.TERMS_AND_PRIVACY)
        assertEquals("resource:${R.string.tutorial_privacy_policy}", vm.toolbarState.value!!.basic.title)
    }
    @Test fun accountNamesAreTrimmedAtUtf8BoundaryForCreateAndRecovery() = runTest {
        val vm = viewModel(); val longName = "界".repeat(20)
        vm.onAccountNameChanged(TextFieldValue(longName)); vm.onRecoveryAccountChanged(TextFieldValue(longName))
        assertEquals("界".repeat(10), vm.createAccountCardState.value!!.accountNameInputState.value.text)
        assertEquals("界".repeat(10), vm.recoveryAccountNameCardState.value!!.accountNameInputState.value.text)
    }

}
