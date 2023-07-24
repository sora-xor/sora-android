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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.account_details

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.net.SocketException
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.BackupAccountType
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.backup.domain.models.Json
import jp.co.soramitsu.backup.domain.models.Seed
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.ext.isPasswordSecure
import jp.co.soramitsu.core.models.CryptoType
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.CreateBackupPasswordState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.AccountDetailsScreenState
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountDetailsViewModel @AssistedInject constructor(
    private val interactor: MultiaccountInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager,
    private val clipboardManager: ClipboardManager,
    private val backupService: BackupService,
    private val coroutineManager: CoroutineManager,
    @Assisted("address") private val address: String,
) : BaseViewModel() {

    @AssistedFactory
    interface AccountDetailsViewModelFactory {
        fun create(
            @Assisted("address") address: String
        ): AccountDetailsViewModel
    }

    private val _copyEvent = SingleLiveEvent<Unit>()
    val copyEvent: LiveData<Unit> = _copyEvent

    private val _accountDetailsScreenState = MutableLiveData(
        AccountDetailsScreenState(
            InputTextState(value = TextFieldValue("")),
            false,
            false,
            false,
            "",
        )
    )
    val accountDetailsScreenState: LiveData<AccountDetailsScreenState> = _accountDetailsScreenState

    private val _createBackupPasswordState = MutableLiveData<CreateBackupPasswordState>()
    val createBackupPasswordState: LiveData<CreateBackupPasswordState> = _createBackupPasswordState

    private val _deleteDialogState = MutableLiveData<Boolean>(false)
    val deleteDialogState: LiveData<Boolean> = _deleteDialogState

    private val changeNameFlow = MutableStateFlow("")
    private var account: SoraAccount? = null

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.account_options,
        )
        viewModelScope.launch {
            val isAccountBackedUp = try {
                backupService.isAccountBackedUp(address)
            } catch (e: SocketException) {
                onError(SoraException.networkError(resourceManager, e))
                null
            }

            account = interactor.getSoraAccount(address)
            account?.let { account ->
                val isMnemonicAvailable = interactor.getMnemonic(account).isNotEmpty()
                _accountDetailsScreenState.value = AccountDetailsScreenState(
                    InputTextState(
                        value = TextFieldValue(account.accountName),
                        label = resourceManager.getString(R.string.personal_info_username_v1),
                        leadingIcon = R.drawable.ic_input_pencil_24,
                    ),
                    isMnemonicAvailable,
                    false,
                    isBackupAvailable = isAccountBackedUp,
                    address,
                )
            }
        }

        viewModelScope.launch {
            changeNameFlow
                .debounce(500)
                .drop(1)
                .collectLatest {
                    if (it.toByteArray().size <= OptionsProvider.nameByteLimit) {
                        interactor.updateName(address, it)
                    }
                }
        }
    }

    override fun startScreen(): String = AccountDetailsRoutes.ACCOUNT_DETAILS

    fun onNameChange(textValue: TextFieldValue) {
        _accountDetailsScreenState.value?.let {
            _accountDetailsScreenState.value = it.copy(
                accountNameState = it.accountNameState.copy(value = textValue),
            )
            changeNameFlow.value = textValue.text
        }
    }

    fun onShowPassphrase() {
        _accountDetailsScreenState.value?.let {
            router.showExportPassphraseProtection(address)
        }
    }

    fun onShowRawSeed() {
        _accountDetailsScreenState.value?.let {
            router.showExportSeedProtection(address)
        }
    }

    fun onExportJson() {
        _accountDetailsScreenState.value?.let {
            router.showExportJSONProtection(mutableListOf(address))
        }
    }

    fun onLogout() {
        _accountDetailsScreenState.value?.let {
            router.showPinForLogout(address)
        }
    }

    fun onAddressCopy() {
        clipboardManager.addToClipboard("address", address)
        _copyEvent.trigger()
    }

    fun onBackupPasswordChanged(textFieldValue: TextFieldValue) {
        _createBackupPasswordState.value?.let { it ->
            val filteredValue =
                textFieldValue.copy(text = textFieldValue.text.filter { it != ' ' })

            val isSecure = filteredValue.text.isPasswordSecure()
            val descriptionText = if (isSecure) {
                R.string.backup_password_mandatory_reqs_fulfilled
            } else {
                R.string.backup_password_requirments
            }
            val (confirmationDescriptionText, isError) = getPasswordConfirmationDescriptionAndErrorStatus(
                filteredValue.text,
                it.passwordConfirmation.value.text
            )
            _createBackupPasswordState.value = it.copy(
                password = it.password.copy(
                    value = filteredValue,
                    success = isSecure,
                    descriptionText = descriptionText,
                    error = !isSecure && filteredValue.text.isNotEmpty()
                ),
                passwordConfirmation = it.passwordConfirmation.copy(
                    error = isError,
                    descriptionText = confirmationDescriptionText,
                    success = !isError && confirmationDescriptionText != R.string.common_empty_string
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected &&
                    it.passwordConfirmation.value.text == filteredValue.text && isSecure
            )
        }
    }

    fun onBackupPasswordConfirmationChanged(textFieldValue: TextFieldValue) {
        _createBackupPasswordState.value?.let {
            val filteredValue =
                textFieldValue.copy(text = textFieldValue.text.filter { it != ' ' })
            val (confirmationDescText, isError) = getPasswordConfirmationDescriptionAndErrorStatus(
                it.password.value.text,
                filteredValue.text
            )
            _createBackupPasswordState.value = it.copy(
                passwordConfirmation = it.passwordConfirmation.copy(
                    value = filteredValue,
                    success = !isError && filteredValue.text.isNotEmpty(),
                    descriptionText = confirmationDescText,
                    error = isError
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected && !isError && filteredValue.text.isPasswordSecure()
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

    fun onBackupPasswordClicked() {
        _createBackupPasswordState.value?.let { createBackupPasswordState ->
            _createBackupPasswordState.value = createBackupPasswordState.copy(isLoading = true)
            try {
                viewModelScope.launch(coroutineManager.io) {
                    _accountDetailsScreenState.value?.let { accountDetailsScreenState ->
                        account?.let { account ->
                            val passphrase =
                                interactor.getMnemonic(accountDetailsScreenState.address)
                            val jsonString = interactor.generateSubstrateJsonString(
                                listOf(account),
                                createBackupPasswordState.password.value.text
                            )

                            val seed = Seed(
                                substrateSeed = interactor.getSeed(accountDetailsScreenState.address)
                            )

                            val backupAccounts = mutableListOf<BackupAccountType>()
                            if (passphrase.isNotEmpty()) {
                                backupAccounts.add(BackupAccountType.PASSHRASE)
                            }
                            if (!seed.substrateSeed.isNullOrEmpty()) {
                                backupAccounts.add(BackupAccountType.SEED)
                            }
                            if (jsonString.isNotEmpty()) {
                                backupAccounts.add(BackupAccountType.JSON)
                            }

                            backupService.saveBackupAccount(
                                DecryptedBackupAccount(
                                    name = accountDetailsScreenState.accountNameState.value.text,
                                    address = accountDetailsScreenState.address,
                                    mnemonicPhrase = passphrase,
                                    cryptoType = CryptoType.SR25519,
                                    backupAccountType = backupAccounts,
                                    seed = seed,
                                    json = Json(substrateJson = jsonString)
                                ),
                                createBackupPasswordState.password.value.text
                            )

                            withContext(coroutineManager.main) {
                                _accountDetailsScreenState.value = _accountDetailsScreenState
                                    .value?.copy(
                                        isBackupAvailable = backupService.isAccountBackedUp(
                                            address
                                        )
                                    )
                                _navigationPop.trigger()
                            }
                        }
                    }
                }
            } catch (e: SocketException) {
                onError(SoraException.networkError(resourceManager, e))
            }
        }
    }

    fun onBackupClicked(
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            try {
                _accountDetailsScreenState.value?.let {
                    _accountDetailsScreenState.value = it.copy(isBackupLoading = true)
                    if (backupService.authorize(launcher)) {
                        if (backupService.isAccountBackedUp(address)) {
                            _deleteDialogState.value = true
                        } else {
                            openCreateBackupScreen()
                        }
                    } else {
                        _accountDetailsScreenState.value = it.copy(isBackupLoading = false)
                    }
                }
            } catch (e: SocketException) {
                onError(SoraException.networkError(resourceManager, e))
            }
        }
    }

    fun deleteGoogleBackup() {
        _deleteDialogState.value = false

        viewModelScope.launch {
            try {
                _accountDetailsScreenState.value = accountDetailsScreenState.value?.copy(
                    isBackupLoading = true
                )
                backupService.deleteBackupAccount(address)
                _accountDetailsScreenState.value = accountDetailsScreenState.value?.copy(
                    isBackupLoading = false,
                    isBackupAvailable = backupService.isAccountBackedUp(address)
                )
            } catch (e: SocketException) {
                onError(SoraException.networkError(resourceManager, e))
            }
        }
    }

    private fun openCreateBackupScreen() {
        _createBackupPasswordState.value = CreateBackupPasswordState(
            password = InputTextState(
                label = resourceManager.getString(R.string.create_backup_set_password),
                descriptionText = resourceManager.getString(R.string.backup_password_requirments)
            ),
            passwordConfirmation = InputTextState(label = resourceManager.getString(R.string.export_json_input_confirmation_label))
        )

        _navEvent.value = AccountDetailsRoutes.BACKUP_ACCOUNT to {}
        _accountDetailsScreenState.value =
            accountDetailsScreenState.value?.copy(isBackupLoading = false)
    }

    fun onSuccessfulGoogleSignin() {
        viewModelScope.launch {
            try {
                _accountDetailsScreenState.value?.let {
                    if (backupService.isAccountBackedUp(address)) {
                        backupService.deleteBackupAccount(address)
                        _accountDetailsScreenState.value =
                            it.copy(isBackupLoading = false)
                    } else {
                        _createBackupPasswordState.value = CreateBackupPasswordState(
                            password = InputTextState(label = resourceManager.getString(R.string.create_backup_set_password)),
                            passwordConfirmation = InputTextState(
                                label = resourceManager.getString(
                                    R.string.export_json_input_confirmation_label
                                )
                            )
                        )
                        _navEvent.value = AccountDetailsRoutes.BACKUP_ACCOUNT to {}
                        _accountDetailsScreenState.value = it.copy(isBackupLoading = false)
                    }
                }
            } catch (e: SocketException) {
                onError(SoraException.networkError(resourceManager, e))
            }
        }
    }

    fun deleteDialogDismiss() {
        _deleteDialogState.value = false
        _accountDetailsScreenState.value?.let {
            _accountDetailsScreenState.value = it.copy(
                isBackupLoading = false
            )
        }
    }

    private fun getPasswordConfirmationDescriptionAndErrorStatus(
        password: String,
        passwordConfirmation: String
    ): Pair<Int, Boolean> {
        return when (passwordConfirmation) {
            "" -> R.string.common_empty_string to false
            password -> R.string.create_backup_password_matched to false
            else -> R.string.create_backup_password_not_matched to true
        }
    }
}
