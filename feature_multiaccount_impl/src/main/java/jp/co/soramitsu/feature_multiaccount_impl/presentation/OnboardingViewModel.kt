/**
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0
 */

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.compose.webview.WebViewState
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const.SORA_PRIVACY_PAGE
import jp.co.soramitsu.common.util.Const.SORA_TERMS_PAGE
import jp.co.soramitsu.common.util.ext.isAccountNameLongerThen32Bytes
import jp.co.soramitsu.common.util.ext.isPasswordSecure
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlin.random.Random
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val invitationHandler: InvitationHandler,
    private val multiaccountInteractor: MultiaccountInteractor,
    private val mainStarter: MainStarter,
    private val resourceManager: ResourceManager,
    private val connectionManager: ConnectionManager,
    private val backupService: BackupService,
) : BaseViewModel() {

    private val _createAccountCardState = MutableLiveData<CreateAccountState>()
    val createAccountCardState: LiveData<CreateAccountState> = _createAccountCardState

    private val _createBackupPasswordState = MutableLiveData(CreateBackupPasswordState(password = InputTextState(label = "Set password"), passwordConfirmation = InputTextState(label = "Confirm password")))
    val createBackupPasswordState: LiveData<CreateBackupPasswordState> = _createBackupPasswordState

    private val _recoveryAccountNameCardState = MutableLiveData<CreateAccountState>()
    val recoveryAccountNameCardState: LiveData<CreateAccountState> = _recoveryAccountNameCardState

    private val _disclaimerCardState = MutableLiveData<ExportProtectionScreenState>()
    val disclaimerCardState: LiveData<ExportProtectionScreenState> = _disclaimerCardState

    private val _passphraseCardState = MutableLiveData<BackupScreenState>()
    val passphraseCardState: LiveData<BackupScreenState> = _passphraseCardState

    private val _passphraseConfirmationState = MutableLiveData<MnemonicConfirmationState>()
    val passphraseConfirmationState: LiveData<MnemonicConfirmationState> =
        _passphraseConfirmationState

    private val _termsAndPrivacyState = MutableLiveData<TermsAndPrivacyState>()
    val termsAndPrivacyState: LiveData<TermsAndPrivacyState> = _termsAndPrivacyState

    private val _recoveryState = MutableLiveData<RecoveryState>()
    val recoveryState: LiveData<RecoveryState> = _recoveryState

    private var tempAccount: SoraAccount? = null

    private var isFromGoogleDrive = false

    private var recoverSoraAccountMethod = multiaccountInteractor::recoverSoraAccountFromMnemonic
    private var isValidMethod = multiaccountInteractor::isMnemonicValid
    private var errorMessageCode = ResponseCode.MNEMONIC_IS_NOT_VALID

    init {
        _toolbarState.value = initSmallTitle2("")
        _createAccountCardState.value = CreateAccountState(
            accountNameInputState = InputTextState(
                label = resourceManager.getString(R.string.personal_info_username_v1)
            )
        )

        _disclaimerCardState.value = ExportProtectionScreenState(
            titleResource = R.string.common_passphrase_title,
            descriptionResource = R.string.export_protection_passphrase_description,
            selectableItemList = listOf(
                ExportProtectionSelectableModel(
                    textString = R.string.export_protection_passphrase_1
                ),
                ExportProtectionSelectableModel(
                    textString = R.string.export_protection_passphrase_2
                ),
                ExportProtectionSelectableModel(
                    textString = R.string.export_protection_passphrase_3
                )
            )
        )

        _createAccountCardState.value = CreateAccountState(
            accountNameInputState = InputTextState(
                label = resourceManager.getString(R.string.personal_info_username_v1)
            )
        )

        _recoveryAccountNameCardState.value = CreateAccountState(
            accountNameInputState = InputTextState(
                label = resourceManager.getString(R.string.personal_info_username_v1)
            )
        )

    }

    fun startedWithInviteAction() {
        invitationHandler.invitationApplied()
    }

    fun onTermsClicked(navController: NavController) {
        _termsAndPrivacyState.value = TermsAndPrivacyState(
            R.string.common_terms_title,
            WebViewState(SORA_TERMS_PAGE)
        )

        navController.navigate(OnboardingFeatureRoutes.TERMS_AND_PRIVACY)
    }

    fun onPrivacyClicked(navController: NavController) {
        _termsAndPrivacyState.value = TermsAndPrivacyState(
            R.string.tutorial_privacy_policy,
            WebViewState(SORA_PRIVACY_PAGE)
        )

        navController.navigate(OnboardingFeatureRoutes.TERMS_AND_PRIVACY)
    }

    fun onConfirmationButtonPressed(context: Context, buttonString: String) {
        _passphraseCardState.value?.let { passphraseCardState ->
            _passphraseConfirmationState.value?.let { passphraseConfirmationState ->
                if (buttonString == passphraseCardState.mnemonicWords[passphraseConfirmationState.currentWordIndex - 1]) {
                    if (passphraseConfirmationState.confirmationStep == 3) {
                        _passphraseConfirmationState.value = passphraseConfirmationState.copy(
                            confirmationStep = 4
                        )

                        finishCreateAccountProcess(context)
                    } else {
                        initiateConfirmationStep(passphraseConfirmationState.confirmationStep + 1)
                    }
                } else {
                    alertDialogLiveData.value = Pair(
                        resourceManager.getString(R.string.passphrase_confirmation_error_title),
                        resourceManager.getString(R.string.passphrase_confirmation_error_message),
                    )
                    initiateConfirmationStep(1)
                }
            }
        }
    }

    fun onItemClicked(index: Int) {
        _disclaimerCardState.value?.let {
            val newList = it.selectableItemList
                .mapIndexed { i, exportProtectionSelectableModel ->
                    if (i == index) {
                        ExportProtectionSelectableModel(
                            !exportProtectionSelectableModel.isSelected,
                            exportProtectionSelectableModel.textString
                        )
                    } else {
                        exportProtectionSelectableModel
                    }
                }

            val isButtonEnabled = newList.all { it.isSelected }

            _disclaimerCardState.value = it.copy(
                selectableItemList = newList,
                isButtonEnabled = isButtonEnabled
            )
        }
    }

    fun onDestinationChanged(route: String) {
        currentDestination = route
        toggleToolbarTitle(route)
    }

    fun onAccountNameChanged(textFieldValue: TextFieldValue) {
        _createAccountCardState.value?.let {
            val newAccountName = if (textFieldValue.text.isAccountNameLongerThen32Bytes()) {
                it.accountNameInputState.value
            } else {
                textFieldValue
            }

            _createAccountCardState.value = it.copy(
                accountNameInputState = it.accountNameInputState.copy(
                    value = newAccountName,
                ),
            )
        }
    }

    fun onRecoveryAccountChanged(textFieldValue: TextFieldValue) {
        _recoveryAccountNameCardState.value?.let {
            val newAccountName = if (textFieldValue.text.isAccountNameLongerThen32Bytes()) {
                it.accountNameInputState.value
            } else {
                textFieldValue
            }

            _recoveryAccountNameCardState.value = it.copy(
                accountNameInputState = it.accountNameInputState.copy(
                    value = newAccountName,
                ),
            )
        }
    }

    fun onRecoveryInputChanged(textFieldValue: TextFieldValue) {
        viewModelScope.launch {
            _recoveryState.value?.let {
                _recoveryState.value = it.copy(
                    recoveryInputState = it.recoveryInputState.copy(
                        value = textFieldValue,
                    ),
                    isButtonEnabled = isValidMethod(textFieldValue.text)
                )
            }
        }
    }

    fun recoveryNextClicked(navController: NavController, context: Context) {
        _recoveryState.value?.let { recoveryCardState ->
            _recoveryAccountNameCardState.value?.let { recoverAccountNameState ->
                viewModelScope.launch {
                    _recoveryAccountNameCardState.value = recoverAccountNameState.copy(
                        btnEnabled = false,
                    )
                    try {
                        val valid = isValidMethod(recoveryCardState.recoveryInputState.value.text)

                        if (valid) {
                            val soraAccount = recoverSoraAccountMethod(
                                recoveryCardState.recoveryInputState.value.text,
                                recoverAccountNameState.accountNameInputState.value.text
                            )
                            multiaccountInteractor.continueRecoverFlow(
                                soraAccount,
                                connectionManager.isConnected
                            )
                            mainStarter.start(context)
                        } else {
                            throw SoraException.businessError(errorMessageCode)
                        }
                    } catch (t: Throwable) {
                        navController.popBackStack()
                        onError(t)
                    } finally {
                        _recoveryAccountNameCardState.value = recoverAccountNameState.copy(
                            btnEnabled = true,
                        )
                    }
                }
            }
        }
    }

    fun onGoogleSignin(navController: NavController, activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (backupService.isAuthorized(activity)) {
            isFromGoogleDrive = true
            navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
        } else {
            backupService.authorize(activity, launcher)
        }
    }

    private fun toggleToolbarTitle(route: String) {
        _toolbarState.value?.let {
            _toolbarState.value = it.copy(
                basic = it.basic.copy(
                    title = resourceManager.getString(
                        when (route) {
                            OnboardingFeatureRoutes.TUTORIAL -> R.string.tutorial_many_world
                            OnboardingFeatureRoutes.CREATE_ACCOUNT, OnboardingFeatureRoutes.RECOVERY_ACCOUNT_NAME -> R.string.onboarding_create_account_title
                            OnboardingFeatureRoutes.DISCLAIMER -> R.string.common_pay_attention
                            OnboardingFeatureRoutes.PASSPHRASE -> R.string.common_passphrase_title
                            OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION -> R.string.account_confirmation_title_v2
                            OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD -> R.string.account_confirmation_title_v2
                            OnboardingFeatureRoutes.RECOVERY -> when (_recoveryState.value?.recoveryType) {
                                RecoveryType.PASSPHRASE -> R.string.onboarding_enter_passphrase
                                RecoveryType.SEED -> R.string.onboarding_enter_seed
                                else -> R.string.onboarding_enter_passphrase
                            }
                            OnboardingFeatureRoutes.TERMS_AND_PRIVACY ->
                                _termsAndPrivacyState.value?.title
                                    ?: R.string.common_terms_title
                            else -> R.string.tutorial_many_world
                        }
                    ),
                    visibility = route != OnboardingFeatureRoutes.TUTORIAL,
                )
            )
        }
    }

    fun onCreateAccountContinueClicked(navController: NavController) {
        navController.navigate(OnboardingFeatureRoutes.DISCLAIMER)

        viewModelScope.launch {
            _createAccountCardState.value?.let {
                val accountName = it.accountNameInputState.value.text
                val soraAccount = multiaccountInteractor.generateUserCredentials(accountName)
                val mnemonic = multiaccountInteractor.getMnemonic(soraAccount)
                tempAccount = soraAccount
                _passphraseCardState.value = BackupScreenState(
                    mnemonicWords = mnemonic.split(" "),
                    isCreatingFlow = true,
                )
            }
        }
    }

    fun onPassphraseContinueClicked(navController: NavController) {
        if (isFromGoogleDrive) {
            navController.navigate(OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD)
        } else {
            initiateConfirmationStep(1)
            navController.navigate(OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION)
        }
    }

    private fun initiateConfirmationStep(step: Int) {
        _passphraseCardState.value?.let {
            if (step == 1) {
                _passphraseConfirmationState.value?.confirmedWordIndexes?.clear()
            }

            var wordIndex = Random.nextInt(0, it.mnemonicWords.size - 1)
            var wordIndex2 = wordIndex
            var wordIndex3 = wordIndex
            while (_passphraseConfirmationState.value?.confirmedWordIndexes?.contains(wordIndex) == true || wordIndex2 == wordIndex || wordIndex3 == wordIndex2 || wordIndex == wordIndex3) {
                wordIndex = Random.nextInt(0, it.mnemonicWords.size - 1)
                wordIndex2 = Random.nextInt(0, it.mnemonicWords.size - 1)
                wordIndex3 = Random.nextInt(0, it.mnemonicWords.size - 1)
            }

            val newList =
                _passphraseConfirmationState.value?.confirmedWordIndexes ?: mutableListOf()
            newList.add(wordIndex)

            _passphraseConfirmationState.value = MnemonicConfirmationState(
                currentWordIndex = wordIndex + 1,
                buttonsList = listOf(
                    it.mnemonicWords[wordIndex],
                    it.mnemonicWords[wordIndex2],
                    it.mnemonicWords[wordIndex3],
                ).shuffled(),
                confirmationStep = step,
                confirmedWordIndexes = newList
            )
        }
    }

    fun onTermsAndPrivacyLoadingFinished() {
        _termsAndPrivacyState.value?.let {
            _termsAndPrivacyState.value = it.copy(
                webViewState = it.webViewState.copy(
                    loading = false
                )
            )
        }
    }

    fun onRecoveryClicked(navController: NavController, index: Int) {
        _recoveryState.value = when (index) {
            0 -> {
                isValidMethod = multiaccountInteractor::isMnemonicValid
                recoverSoraAccountMethod = multiaccountInteractor::recoverSoraAccountFromMnemonic
                errorMessageCode = ResponseCode.MNEMONIC_IS_NOT_VALID

                RecoveryState(
                    title = R.string.recovery_enter_passphrase_title,
                    recoveryType = RecoveryType.PASSPHRASE,
                    recoveryInputState = InputTextState(
                        label = resourceManager.getString(R.string.recovery_mnemonic_passphrase)
                    )
                )
            }
            1 -> {
                isValidMethod = multiaccountInteractor::isRawSeedValid
                recoverSoraAccountMethod = multiaccountInteractor::recoverSoraAccountFromRawSeed
                errorMessageCode = ResponseCode.RAW_SEED_IS_NOT_VALID

                RecoveryState(
                    title = R.string.recovery_enter_seed_title,
                    recoveryType = RecoveryType.SEED,
                    recoveryInputState = InputTextState(
                        label = resourceManager.getString(R.string.recovery_input_raw_seed_hint)
                    )
                )
            }
            else -> RecoveryState(
                title = R.string.recovery_enter_passphrase_title,
                recoveryType = RecoveryType.PASSPHRASE,
                recoveryInputState = InputTextState(
                    label = resourceManager.getString(R.string.recovery_mnemonic_passphrase)
                )
            )
        }

        navController.navigate(OnboardingFeatureRoutes.RECOVERY)
    }

    fun onSkipButtonPressed(context: Context) {
        finishCreateAccountProcess(context)
    }

    private fun finishCreateAccountProcess(context: Context) {
        viewModelScope.launch {
            multiaccountInteractor.createUser(
                soraAccount = requireNotNull(
                    tempAccount
                ),
                update = connectionManager.isConnected,
            )
            multiaccountInteractor.saveRegistrationStateFinished()

            mainStarter.start(context)
        }
    }

    fun onSuccessfulGoogleSignin(activity: Activity, navController: NavController) {
        viewModelScope.launch {
            try {
                backupService.deleteAllAccounts(activity)
                val result = backupService.getBackupAccounts(activity)
                if (result.isEmpty()) {
                    isFromGoogleDrive = true
                    onCreateAccountClicked(navController)
                } else {
//                    _googleImportListCardState.value = GoogleImportListState(backupService.getBackupAccounts(activity))
                    // TODO: show list
                }
            } catch (e: UnauthorizedException) {
                // TODO: handle error
                e.printStackTrace()
            }
        }
    }

    fun onCreateAccountClicked(navController: NavController) {
        navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
    }

    fun onSetBackupPasswordClicked(
        activity: Activity
    ) {
        viewModelScope.launch {
            _createBackupPasswordState.value?.let { createBackupPasswordState ->
                _passphraseCardState.value?.let { passphraseCardState ->
                    tempAccount?.let {
                        backupService.saveBackupAccount(
                            activity,
                            DecryptedBackupAccount(
                                it.accountName,
                                it.substrateAddress,
                                passphraseCardState.mnemonicWords.joinToString(" ")
                            ),
                            createBackupPasswordState.password.value.text
                        )

                        finishCreateAccountProcess(activity)
                    }
                }
            }
        }
    }

    fun onBackupPasswordChanged(textFieldValue: TextFieldValue) {
        _createBackupPasswordState.value?.let {
            val isSecure = textFieldValue.text.isPasswordSecure()
            _createBackupPasswordState.value = it.copy(
                password = it.password.copy(
                    value = textFieldValue,
                    success = isSecure,
                    descriptionText = if (isSecure) "Is secure" else ""
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected && it.passwordConfirmation.value.text == textFieldValue.text && it.password.value.text.isPasswordSecure()
            )
        }
    }

    fun onBackupPasswordConfirmationChanged(textFieldValue: TextFieldValue) {
        _createBackupPasswordState.value?.let {
            val isConfirmationRightAndSecure = it.password.value.text == textFieldValue.text && it.password.value.text.isPasswordSecure()
            _createBackupPasswordState.value = it.copy(
                passwordConfirmation = it.password.copy(
                    value = textFieldValue,
                    success = isConfirmationRightAndSecure,
                    descriptionText = if (isConfirmationRightAndSecure) "Password matched" else ""
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected && isConfirmationRightAndSecure
            )
        }
    }

    fun onWarningToggle() {
        _createBackupPasswordState.value?.let {
            val newWarningState = !it.warningIsSelected
            _createBackupPasswordState.value = it.copy(
                warningIsSelected = newWarningState,
                setPasswordButtonIsEnabled = newWarningState && it.password.value.text == it.passwordConfirmation.value.text && it.password.value.text.isPasswordSecure()
            )
        }
    }
}
