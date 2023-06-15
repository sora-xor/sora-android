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

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.backup.BackupService
import jp.co.soramitsu.backup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
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
import kotlinx.coroutines.Dispatchers
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
            null,
            ""
        )
    )
    val accountDetailsScreenState: LiveData<AccountDetailsScreenState> = _accountDetailsScreenState

    private val _createBackupPasswordState = MutableLiveData<CreateBackupPasswordState>()
    val createBackupPasswordState: LiveData<CreateBackupPasswordState> = _createBackupPasswordState

    private val changeNameFlow = MutableStateFlow("")

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.account_options,
        )
        viewModelScope.launch {
            val account = interactor.getSoraAccount(address)
            val isMnemonicAvailable = interactor.getMnemonic(account).isNotEmpty()
            _accountDetailsScreenState.value = AccountDetailsScreenState(
                InputTextState(
                    value = TextFieldValue(account.accountName),
                    label = resourceManager.getString(R.string.personal_info_username_v1),
                    leadingIcon = R.drawable.ic_input_pencil_24,
                ),
                isMnemonicAvailable,
                false,
                account.backupFileId,
                address,
            )
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
        _createBackupPasswordState.value?.let {
            val isSecure = textFieldValue.text.isPasswordSecure()
            _createBackupPasswordState.value = it.copy(
                password = it.password.copy(
                    value = textFieldValue,
                    success = isSecure,
                    descriptionText = if (isSecure) resourceManager.getString(R.string.create_backup_password_is_secure) else ""
                ),
                setPasswordButtonIsEnabled = it.warningIsSelected && it.passwordConfirmation.value.text == textFieldValue.text && it.password.value.text.isPasswordSecure()
            )
        }
    }

    fun onBackupPasswordConfirmationChanged(textFieldValue: TextFieldValue) {
        _createBackupPasswordState.value?.let {
            val isConfirmationRightAndSecure =
                it.password.value.text == textFieldValue.text && it.password.value.text.isPasswordSecure()
            _createBackupPasswordState.value = it.copy(
                passwordConfirmation = it.passwordConfirmation.copy(
                    value = textFieldValue,
                    success = isConfirmationRightAndSecure,
                    descriptionText = if (isConfirmationRightAndSecure) resourceManager.getString(R.string.create_backup_password_matched) else ""
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

    fun onBackupPasswordClicked(
        activity: Activity,
        navController: NavController
    ) {
        _createBackupPasswordState.value?.let { createBackupPasswordState ->
            _createBackupPasswordState.value = createBackupPasswordState.copy(isLoading = true)
            viewModelScope.launch(Dispatchers.IO) {
                _accountDetailsScreenState.value?.let { accountDetailsScreenState ->

                    val mnemonic = interactor.getMnemonic(accountDetailsScreenState.address)
                    val fileID = backupService.saveBackupAccount(
                        activity,
                        DecryptedBackupAccount(
                            accountDetailsScreenState.accountNameState.value.text,
                            accountDetailsScreenState.address,
                            mnemonic,
                            "",
                            CryptoType.SR25519
                        ),
                        createBackupPasswordState.password.value.text
                    )

                    interactor.updateBackupFileId(accountDetailsScreenState.address, fileID)

                    withContext(Dispatchers.Main) {
                        _accountDetailsScreenState.value =
                            accountDetailsScreenState.copy(fileId = fileID)
                        navController.popBackStack()
                    }
                }
            }
        }
    }

    fun onBackupClicked(
        navController: NavController,
        activity: Activity,
        launcher: ActivityResultLauncher<Intent>
    ) {
        viewModelScope.launch {
            _accountDetailsScreenState.value?.let {
                _accountDetailsScreenState.value = it.copy(isBackupLoading = true)
                if (backupService.authorize(activity, launcher)) {
                    if (it.fileId != null) {
                        backupService.deleteBackupAccount(activity, it.fileId)
                        interactor.removeBackupFileId(it.address)
                        _accountDetailsScreenState.value =
                            it.copy(fileId = null, isBackupLoading = false)
                    } else {
                        _createBackupPasswordState.value = CreateBackupPasswordState(
                            password = InputTextState(label = resourceManager.getString(R.string.create_backup_set_password)),
                            passwordConfirmation = InputTextState(
                                label = resourceManager.getString(
                                    R.string.export_json_input_confirmation_label
                                )
                            )
                        )

                        navController.navigate(AccountDetailsRoutes.BACKUP_ACCOUNT)
                        _accountDetailsScreenState.value = it.copy(isBackupLoading = false)
                    }
                }
            }
        }
    }

    fun onSuccessfulGoogleSignin(activity: Activity, navController: NavController) {
        viewModelScope.launch {
            _accountDetailsScreenState.value?.let {
                if (it.fileId != null) {
                    backupService.deleteBackupAccount(activity, it.fileId)
                    interactor.removeBackupFileId(it.address)
                    _accountDetailsScreenState.value =
                        it.copy(fileId = null, isBackupLoading = false)
                } else {
                    navController.navigate(AccountDetailsRoutes.BACKUP_ACCOUNT)
                    _accountDetailsScreenState.value = it.copy(isBackupLoading = false)
                }
            }
        }
    }
}
