/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.exceptions.DecodingException
import jp.co.soramitsu.backup.domain.exceptions.DecryptionException
import jp.co.soramitsu.backup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CoroutineManager
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
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import kotlin.random.Random
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val invitationHandler: InvitationHandler,
    private val multiaccountInteractor: MultiaccountInteractor,
    private val mainStarter: MainStarter,
    private val resourceManager: ResourceManager,
    private val connectionManager: ConnectionManager,
    private val backupService: BackupService,
    private val avatarGenerator: AccountAvatarGenerator,
    private val coroutineManager: CoroutineManager,
) : BaseViewModel() {

    private val _createAccountCardState = MutableLiveData<CreateAccountState>()
    val createAccountCardState: LiveData<CreateAccountState> = _createAccountCardState

    private val _tutorialScreenState = MutableLiveData(TutorialScreenState())
    val tutorialScreenState: LiveData<TutorialScreenState> = _tutorialScreenState

    private val _createBackupPasswordState = MutableLiveData<CreateBackupPasswordState>()
    val createBackupPasswordState: LiveData<CreateBackupPasswordState> = _createBackupPasswordState

    private val _importAccountListState = MutableLiveData(ImportAccountListScreenState())
    val importAccountListState: LiveData<ImportAccountListScreenState> = _importAccountListState

    private val _importAccountPasswordState = MutableLiveData<ImportAccountPasswordState>()
    val importAccountPasswordState: LiveData<ImportAccountPasswordState> =
        _importAccountPasswordState

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

        resetBackupLiveData()

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

    fun onGoogleSignin(
        navController: NavController,
        launcher: ActivityResultLauncher<Intent>
    ) {
        _tutorialScreenState.value?.let {
            _tutorialScreenState.value = it.copy(isGoogleSigninLoading = true)
            viewModelScope.launch {
                if (backupService.authorize(launcher)) {
                    onSuccessfulGoogleSignin(navController)
                } else {
                    _tutorialScreenState.value = it.copy(isGoogleSigninLoading = false)
                }
            }
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
                            OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST -> R.string.select_account_import
                            OnboardingFeatureRoutes.IMPORT_ACCOUNT_PASSWORD -> R.string.enter_backup_password_title
                            OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD -> R.string.create_backup_password_title
                            OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS -> R.string.imported_account_title
                            OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION -> R.string.account_confirmation_title_v2
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
                    isViaGoogleDrive = isFromGoogleDrive
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
            1 -> {
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

            2 -> {
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

    fun onSuccessfulGoogleSignin(navController: NavController) {
        _tutorialScreenState.value =
            _tutorialScreenState.value?.copy(isGoogleSigninLoading = true)

        viewModelScope.launch {
            try {
                isFromGoogleDrive = true

                if (navController.currentDestination?.route == OnboardingFeatureRoutes.PASSPHRASE) {
                    navController.navigate(OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD)
                } else {
                    val result = backupService.getBackupAccounts()
                        .filter {
                            multiaccountInteractor.isAddressValid(it.address) && !multiaccountInteractor.accountExists(
                                it.address
                            )
                        }

                    _tutorialScreenState.value =
                        _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)

                    if (result.isEmpty()) {
                        navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
                    } else {
                        _importAccountListState.value = ImportAccountListScreenState(
                            accountList = result.map {
                                BackupAccountMetaWithIcon(
                                    it,
                                    getDrawableFromGoogleBackup(it.address),
                                )
                            }
                        )
                        navController.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
                    }
                }
            } catch (e: UnauthorizedException) {
                _tutorialScreenState.value =
                    _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)

                onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
            }
        }
    }

    fun onCreateAccountClicked(navController: NavController) {
        isFromGoogleDrive = false
        navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
    }

    fun onSetBackupPasswordClicked(
        activity: Activity
    ) {
        _createBackupPasswordState.value?.let { createBackupPasswordState ->
            _createBackupPasswordState.value = createBackupPasswordState.copy(isLoading = true)
            viewModelScope.launch(coroutineManager.io) {
                _passphraseCardState.value?.let { passphraseCardState ->
                    tempAccount?.let {
                        backupService.saveBackupAccount(
                            DecryptedBackupAccount(
                                it.accountName,
                                it.substrateAddress,
                                passphraseCardState.mnemonicWords.joinToString(" "),
                                CryptoType.SR25519,
                                "",
                                "",
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
        _createBackupPasswordState.value?.let { it ->
            val filteredValue =
                textFieldValue.copy(text = textFieldValue.text.filter { it.isLetter() })

            val isSecure = filteredValue.text.isPasswordSecure()
            val errorString =
                getPasswordConfirmationError(
                    filteredValue.text,
                    it.passwordConfirmation.value.text
                )
            _createBackupPasswordState.value = it.copy(
                password = it.password.copy(
                    value = filteredValue,
                    success = isSecure,
                    descriptionText = if (isSecure) resourceManager.getString(R.string.create_backup_password_is_secure) else "",
                ),
                passwordConfirmation = it.passwordConfirmation.copy(
                    error = errorString.isNotEmpty(),
                    descriptionText = if (filteredValue.text == it.passwordConfirmation.value.text)
                        resourceManager.getString(R.string.create_backup_password_matched)
                    else
                        errorString
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected &&
                    it.passwordConfirmation.value.text == filteredValue.text && isSecure
            )
        }
    }

    fun onImportAccountSelected(navController: NavController, account: BackupAccountMetaWithIcon) {
        _importAccountPasswordState.value?.let {
            _importAccountPasswordState.value = it.copy(
                selectedAccount = account
            )

            navController.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_PASSWORD)
        }
    }

    fun onImportPasswordChanged(textFieldValue: TextFieldValue) {
        _importAccountPasswordState.value?.let {
            _importAccountPasswordState.value = it.copy(
                passwordInput = it.passwordInput.copy(
                    value = textFieldValue,
                ),
                isContinueButtonEnabled = it.passwordInput.value.text.isNotEmpty()
            )
        }
    }

    fun onImportContinueClicked(navController: NavController) {
        _importAccountPasswordState.value?.let { state ->
            _importAccountPasswordState.value = state.copy(isLoading = true)
            viewModelScope.launch {
                state.selectedAccount?.let {
                    try {
                        val decryptedBackupAccount = withContext(coroutineManager.io) {
                            backupService.importBackupAccount(
                                it.backupAccountMeta.address,
                                state.passwordInput.value.text
                            )
                        }

                        val valid =
                            multiaccountInteractor.isMnemonicValid(decryptedBackupAccount.mnemonicPhrase)
                        if (valid) {
                            val soraAccount = multiaccountInteractor.recoverSoraAccountFromMnemonic(
                                decryptedBackupAccount.mnemonicPhrase,
                                decryptedBackupAccount.name
                            )

                            multiaccountInteractor.continueRecoverFlow(
                                soraAccount,
                                connectionManager.isConnected
                            )

                            navController.navigate(
                                route = OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS,
                                navOptions = NavOptions.Builder()
                                    .setPopUpTo(OnboardingFeatureRoutes.TUTORIAL, true)
                                    .build()
                            )
                        } else {
                            onError(SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID))
                        }
                    } catch (e: DecryptionException) {
                        onError(SoraException.businessError(ResponseCode.GOOGLE_BACKUP_DECRYPTION_FAILED))
                    } catch (e: DecodingException) {
                        onError(SoraException.businessError(ResponseCode.GENERAL_ERROR))
                    } catch (e: SoraException) {
                        onError(e)
                    }
                }
                _importAccountPasswordState.value = state.copy(isLoading = false)
            }
        }
    }

    fun onImportFinished(context: Context) {
        mainStarter.start(context)
    }

    fun onImportMoreClicked(navController: NavController) {
        viewModelScope.launch {
            _importAccountPasswordState.value =
                _importAccountPasswordState.value?.copy(isLoading = true)
            _importAccountListState.value = ImportAccountListScreenState(
                accountList = backupService.getBackupAccounts()
                    .filter {
                        multiaccountInteractor.isAddressValid(it.address) && !multiaccountInteractor.accountExists(
                            it.address
                        )
                    }
                    .map {
                        BackupAccountMetaWithIcon(
                            it,
                            getDrawableFromGoogleBackup(it.address),
                        )
                    }
            )

            resetBackupLiveData()
            navController.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
        }
    }

    private fun resetBackupLiveData() {
        _createBackupPasswordState.value = CreateBackupPasswordState(
            password = InputTextState(label = resourceManager.getString(R.string.create_backup_set_password)),
            passwordConfirmation = InputTextState(label = resourceManager.getString(R.string.export_json_input_confirmation_label))
        )

        _importAccountPasswordState.value = ImportAccountPasswordState(
            passwordInput = InputTextState(label = resourceManager.getString(R.string.enter_password_title))
        )
    }

    fun onBackupPasswordConfirmationChanged(textFieldValue: TextFieldValue) {
        _createBackupPasswordState.value?.let {
            val filteredValue =
                textFieldValue.copy(text = textFieldValue.text.filter { it.isLetter() })
            val isConfirmationRight =
                it.password.value.text == filteredValue.text
            val errorString =
                getPasswordConfirmationError(it.password.value.text, filteredValue.text)
            _createBackupPasswordState.value = it.copy(
                passwordConfirmation = it.passwordConfirmation.copy(
                    value = filteredValue,
                    success = isConfirmationRight,
                    descriptionText = if (isConfirmationRight) resourceManager.getString(R.string.create_backup_password_matched) else errorString,
                    error = errorString.isNotEmpty()
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected && isConfirmationRight && filteredValue.text.isPasswordSecure()
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

    private fun getDrawableFromGoogleBackup(address: String): Drawable? = runCatching {
        avatarGenerator.createAvatar(
            address,
            Dimens.x5.value.toInt(),
        )
    }.getOrNull()

    private fun getPasswordConfirmationError(
        password: String,
        passwordConfirmation: String
    ): String {
        return if (passwordConfirmation == "" || passwordConfirmation == password) {
            ""
        } else {
            resourceManager.getString(R.string.create_backup_password_not_matched)
        }
    }
}
