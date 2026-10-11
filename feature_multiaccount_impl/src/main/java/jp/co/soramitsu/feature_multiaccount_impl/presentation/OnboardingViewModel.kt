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
import java.net.SocketException
import java.net.SocketTimeoutException
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.compose.webview.WebViewState
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Const.SORA_PRIVACY_PAGE
import jp.co.soramitsu.common.util.Const.SORA_TERMS_PAGE
import jp.co.soramitsu.common.util.ext.isAccountNameLongerThen32Bytes
import jp.co.soramitsu.common.util.ext.isPasswordSecure
import jp.co.soramitsu.common.util.ext.trimAccountNameTo32Bytes
import jp.co.soramitsu.feature_account_api.domain.model.RepairPinLockedException
import jp.co.soramitsu.feature_account_api.domain.model.WalletDraft
import jp.co.soramitsu.feature_account_api.domain.model.WalletRepairAvailableException
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import jp.co.soramitsu.ui_core.component.input.InputTextState
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.xbackup.domain.exceptions.AuthConsentException
import jp.co.soramitsu.xbackup.domain.exceptions.DecodingException
import jp.co.soramitsu.xbackup.domain.exceptions.DecryptionException
import jp.co.soramitsu.xbackup.domain.exceptions.StorageQuotaExceeded
import jp.co.soramitsu.xbackup.domain.exceptions.UnauthorizedException
import jp.co.soramitsu.xbackup.domain.models.BackupAccountType
import jp.co.soramitsu.xbackup.domain.models.CryptoType
import jp.co.soramitsu.xbackup.domain.models.DecryptedBackupAccount
import jp.co.soramitsu.xbackup.domain.models.Json
import jp.co.soramitsu.xbackup.domain.models.Seed
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MINUTE_MILLIS = 60_000L

/** Characters kept from each end of an address in the list of accounts to repair. */
private const val SHORT_ADDRESS_CHARS = 8

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val invitationHandler: InvitationHandler,
    private val multiaccountInteractor: MultiaccountInteractor,
    private val mainStarter: MainStarter,
    private val resourceManager: ResourceManager,
    private val cloudBackupProvider: CloudBackupProvider,
    private val avatarGenerator: AccountAvatarGenerator,
    private val coroutineManager: CoroutineManager,
) : BaseViewModel() {

    private val backupService get() = requireNotNull(cloudBackupProvider.serviceOrNull())

    private fun requireCloudBackup(): Boolean {
        if (cloudBackupProvider.isAvailable) return true
        onError(jp.co.soramitsu.common.R.string.wallet_cloud_backup_unavailable)
        return false
    }

    private val _createAccountCardState = MutableLiveData<CreateAccountState>()
    val createAccountCardState: LiveData<CreateAccountState> = _createAccountCardState

    private val _tutorialScreenState = MutableLiveData(TutorialScreenState(isGoogleBackupAvailable = cloudBackupProvider.isAvailable))
    val tutorialScreenState: LiveData<TutorialScreenState> = _tutorialScreenState

    private val _recoveryDialog = MutableStateFlow(false)
    val recoveryDialog = _recoveryDialog.asStateFlow()

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

    private val _skipDialogState = MutableLiveData(false)
    val skipDialogState = _skipDialogState

    private val _consentExceptionHandler = SingleLiveEvent<Intent>()
    val consentExceptionHandler: LiveData<Intent> = _consentExceptionHandler

    /**
     * "Repair this account with its recovery phrase": shown when an import found keys stored for
     * the same address that cannot be used. Nothing was written; confirming (with the PIN when
     * one is set, or the phone's screen lock when the stored PIN cannot be read) keeps a copy of
     * the old values and writes the ones the phrase derives.
     */
    data class RepairPrompt(
        val accountExists: Boolean,
        val pinRequired: Boolean,
        /** What was entered is a recovery phrase (otherwise a raw seed or a backup file). */
        val fromPhrase: Boolean = true,
        val wrongPin: Boolean = false,
        val working: Boolean = false,
        /**
         * A PIN is stored on this phone but cannot be read: the repair is confirmed with the
         * phone's screen lock instead ([onRepairConfirmed] with `screenLockConfirmed`).
         */
        val screenLockRequired: Boolean = false,
        /**
         * The accounts of a batch backup file that will be repaired, one line each (name and
         * short address). Empty when one account is repaired.
         */
        val batchAccounts: List<String> = emptyList(),
    )

    /** What the person entered to import: decides which repair call a confirmation makes. */
    private enum class ImportKind { PHRASE, SEED, JSON }

    /**
     * Some accounts of a batch backup file were not added. Shown once, then the app continues
     * to the wallet when at least one account was added.
     */
    data class BatchImportNotice(val message: String, val anyImported: Boolean)

    private val _batchImportNotice = MutableStateFlow<BatchImportNotice?>(null)
    val batchImportNotice = _batchImportNotice.asStateFlow()

    fun onBatchImportNoticeDismissed(context: Context) {
        val notice = _batchImportNotice.value ?: return
        _batchImportNotice.value = null
        if (notice.anyImported) mainStarter.start(context)
    }

    /** A chosen backup file could not be read as text. Nothing was changed. */
    fun onRecoveryFileUnreadable() {
        onError(jp.co.soramitsu.common.R.string.recovery_json_file_unreadable)
    }

    /**
     * This phone has no app that can pick a file. Nothing was changed; the text of the backup
     * file can be pasted into the field.
     */
    fun onRecoveryFilePickerUnavailable() {
        onError(jp.co.soramitsu.common.R.string.recovery_json_no_file_picker)
    }

    private val _repairPrompt = MutableStateFlow<RepairPrompt?>(null)
    val repairPrompt = _repairPrompt.asStateFlow()

    /** What the person entered, kept in memory only while the repair prompt is open. */
    private class PendingRepair(
        val input: String,
        val accountName: String,
        val kind: ImportKind,
        /** The password of the backup file, for [ImportKind.JSON]. */
        val password: String,
        /** The address a Google backup names, when the import came from one. */
        val expectedAddress: String?,
        /** The accounts of a batch backup file to repair; empty for one account. */
        val batchAddresses: List<String>,
        val onRepaired: suspend () -> Unit,
        /** Runs when the prompt closes without a repair (cancelled, or PIN entry locked). */
        val onClosedWithoutRepair: () -> Unit,
    )

    private var pendingRepair: PendingRepair? = null

    private fun offerRepair(
        offer: WalletRepairAvailableException,
        input: String,
        accountName: String,
        kind: ImportKind,
        password: String = "",
        expectedAddress: String? = null,
        onRepaired: suspend () -> Unit,
    ) {
        pendingRepair = PendingRepair(
            input = input,
            accountName = accountName,
            kind = kind,
            password = password,
            expectedAddress = expectedAddress,
            batchAddresses = emptyList(),
            onRepaired = onRepaired,
            onClosedWithoutRepair = {},
        )
        _repairPrompt.value = RepairPrompt(
            accountExists = offer.accountExists,
            pinRequired = offer.pinRequired,
            fromPhrase = kind == ImportKind.PHRASE,
            screenLockRequired = offer.screenLockRequired,
        )
    }

    /**
     * A batch backup file holds accounts whose stored keys on this phone cannot be used. The
     * same file repairs them after one confirmation that names them. Closing the prompt without
     * confirming changes nothing for them and says how many accounts were added.
     */
    private fun offerBatchRepair(
        result: MultiaccountInteractor.JsonImportResult,
        json: String,
        password: String,
        accountName: String,
        context: Context,
    ) {
        pendingRepair = PendingRepair(
            input = json,
            accountName = accountName,
            kind = ImportKind.JSON,
            password = password,
            expectedAddress = null,
            batchAddresses = result.needRepair.map { it.address },
            onRepaired = { mainStarter.start(context) },
            onClosedWithoutRepair = {
                _batchImportNotice.value = BatchImportNotice(
                    message = resourceManager.getString(
                        jp.co.soramitsu.common.R.string.json_backup_batch_skipped
                    ).format(
                        result.imported.size.toString(),
                        result.needRepair.size.toString(),
                    ),
                    anyImported = result.imported.isNotEmpty(),
                )
            },
        )
        _repairPrompt.value = RepairPrompt(
            accountExists = result.needRepair.all { it.accountExists },
            pinRequired = result.repairPinRequired,
            fromPhrase = false,
            screenLockRequired = result.repairScreenLockRequired,
            batchAccounts = result.needRepair.map { candidate ->
                val address = candidate.address
                val short = if (address.length <= SHORT_ADDRESS_CHARS * 2) {
                    address
                } else {
                    "${address.take(SHORT_ADDRESS_CHARS)}…${address.takeLast(SHORT_ADDRESS_CHARS)}"
                }
                if (candidate.name.isBlank()) short else "${candidate.name} ($short)"
            },
        )
    }

    private fun closeRepairPrompt(): PendingRepair? {
        val pending = pendingRepair
        pendingRepair = null
        _repairPrompt.value = null
        return pending
    }

    fun onRepairDismissed() {
        if (_repairPrompt.value?.working == true) return
        closeRepairPrompt()?.onClosedWithoutRepair?.invoke()
    }

    /**
     * The person confirmed the repair. [pin] is what they typed when a PIN is asked for.
     * [screenLockConfirmed] is true only when the phone's screen lock was just confirmed (the
     * way a repair is confirmed when the stored PIN cannot be read).
     */
    fun onRepairConfirmed(pin: String, screenLockConfirmed: Boolean = false) {
        val prompt = _repairPrompt.value ?: return
        val pending = pendingRepair ?: return
        if (prompt.working) return
        _repairPrompt.value = prompt.copy(working = true, wrongPin = false)
        viewModelScope.launch {
            try {
                when {
                    pending.batchAddresses.isNotEmpty() -> multiaccountInteractor.repairFromJsonBatch(
                        json = pending.input,
                        password = pending.password,
                        accountName = pending.accountName,
                        pin = pin,
                        addresses = pending.batchAddresses,
                        screenLockConfirmed = screenLockConfirmed,
                    )
                    pending.kind == ImportKind.PHRASE -> multiaccountInteractor.repairFromMnemonic(
                        pending.input,
                        pending.accountName,
                        pin,
                        screenLockConfirmed,
                    )
                    pending.kind == ImportKind.SEED -> multiaccountInteractor.repairFromRawSeed(
                        pending.input,
                        pending.accountName,
                        pin,
                        screenLockConfirmed,
                    )
                    else -> multiaccountInteractor.repairFromJson(
                        json = pending.input,
                        password = pending.password,
                        accountName = pending.accountName,
                        pin = pin,
                        expectedAddress = pending.expectedAddress,
                        screenLockConfirmed = screenLockConfirmed,
                    )
                }
                closeRepairPrompt()
                pending.onRepaired()
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (locked: RepairPinLockedException) {
                // Too many wrong PINs: PIN entry is locked here and on the PIN screen. Nothing
                // was changed, and the import can be started again once the lock is over.
                closeRepairPrompt()
                val minutes = ((locked.remainingMillis + MINUTE_MILLIS - 1) / MINUTE_MILLIS)
                    .coerceAtLeast(1)
                alertDialogLiveData.value = Pair(
                    resourceManager.getString(
                        jp.co.soramitsu.common.R.string.account_repair_pin_locked_title
                    ),
                    resourceManager.getString(
                        jp.co.soramitsu.common.R.string.account_repair_pin_locked
                    ).format(minutes.toString()),
                )
                pending.onClosedWithoutRepair()
            } catch (error: SoraException) {
                if (error.errorResponseCode == ResponseCode.WRONG_PIN_CODE) {
                    _repairPrompt.value = prompt.copy(working = false, wrongPin = true)
                } else {
                    // The error says what happened. Nothing more is claimed here: the import
                    // can be started again, and it reports each account as it then is.
                    closeRepairPrompt()
                    onError(error)
                }
            } catch (error: Throwable) {
                closeRepairPrompt()
                onError(SoraException.unexpectedError(error))
            }
        }
    }

    /**
     * The wallet being created. It lives in memory only until the person finishes the flow, so
     * backing out at any step leaves no key material on the device.
     */
    private var tempDraft: WalletDraft? = null

    private var isFromGoogleDrive = false

    private var recoverSoraAccountMethod = multiaccountInteractor::importFromMnemonic
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
        toggleToolbarActionButton(route)
    }

    fun onAccountNameChanged(textFieldValue: TextFieldValue) {
        _createAccountCardState.value?.let {
            val newAccountName = if (textFieldValue.text.isAccountNameLongerThen32Bytes()) {
                textFieldValue.copy(text = textFieldValue.text.trimAccountNameTo32Bytes())
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
                textFieldValue.copy(text = textFieldValue.text.trimAccountNameTo32Bytes())
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

    /**
     * The text of a chosen backup file. It is kept here and not in the text field: a file can be
     * a hundred kilobytes, which a text field cannot lay out, and it would push the password
     * field and the button off the screen.
     */
    private var chosenFileText: String? = null

    /** Whether the entered text (or the chosen file) is valid input; checked when it changes. */
    private var recoveryInputValid = false

    /** What will be imported: the chosen file, or what is typed in the field. */
    private fun recoveryInput(state: RecoveryState): String =
        chosenFileText ?: state.recoveryInputState.value.text

    fun onRecoveryInputChanged(textFieldValue: TextFieldValue) {
        viewModelScope.launch {
            _recoveryState.value?.let {
                val textChanged = textFieldValue.text != it.recoveryInputState.value.text
                // Typing or pasting replaces a file that was chosen before.
                if (textChanged) chosenFileText = null
                if (textChanged || chosenFileText == null) {
                    recoveryInputValid = isValidMethod(textFieldValue.text)
                }
                _recoveryState.value = it.copy(
                    recoveryInputState = it.recoveryInputState.copy(
                        value = textFieldValue,
                    ),
                    chosenFileName = it.chosenFileName.takeIf { chosenFileText != null },
                    isButtonEnabled = recoveryInputValid &&
                        (it.recoveryType != RecoveryType.JSON || it.passwordInputState.value.text.isNotEmpty())
                )
            }
        }
    }

    /** The password of the backup file ([RecoveryType.JSON]). */
    fun onRecoveryPasswordChanged(textFieldValue: TextFieldValue) {
        _recoveryState.value?.let {
            // The file text is not parsed again for every character of the password.
            _recoveryState.value = it.copy(
                passwordInputState = it.passwordInputState.copy(value = textFieldValue),
                isButtonEnabled = textFieldValue.text.isNotEmpty() && recoveryInputValid
            )
        }
    }

    /**
     * A backup file the person chose. It is accepted only when it has the shape of an encrypted
     * account backup; anything else (a wrong file) is refused here and never shown or parsed
     * further. The screen shows the file's name, not its text.
     */
    fun onRecoveryFileChosen(text: String, fileName: String = "") {
        viewModelScope.launch {
            val state = _recoveryState.value ?: return@launch
            if (state.recoveryType != RecoveryType.JSON) return@launch
            if (!multiaccountInteractor.isJsonBackupValid(text)) {
                onRecoveryFileUnreadable()
                return@launch
            }
            chosenFileText = text
            recoveryInputValid = true
            _recoveryState.value = state.copy(
                recoveryInputState = state.recoveryInputState.copy(value = TextFieldValue("")),
                chosenFileName = fileName.ifBlank {
                    resourceManager.getString(
                        jp.co.soramitsu.common.R.string.recovery_json_file_unnamed
                    )
                },
                isButtonEnabled = state.passwordInputState.value.text.isNotEmpty(),
            )
        }
    }

    fun recoveryNextClicked(navController: NavController, context: Context) {
        _recoveryState.value?.let { recoveryCardState ->
            _recoveryAccountNameCardState.value?.let { recoverAccountNameState ->
                if (recoverAccountNameState.loading) return
                val input = recoveryInput(recoveryCardState)
                val accountName = recoverAccountNameState.accountNameInputState.value.text
                val password = recoveryCardState.passwordInputState.value.text
                viewModelScope.launch {
                    // Deriving keys and opening a backup file take a moment (they run off the
                    // main thread): the button shows that the app is working.
                    _recoveryAccountNameCardState.value = recoverAccountNameState.copy(
                        btnEnabled = false,
                        loading = true,
                    )
                    try {
                        val valid = recoveryInputValid || isValidMethod(input)

                        if (!valid) {
                            throw SoraException.businessError(errorMessageCode)
                        }
                        if (recoveryCardState.recoveryType == RecoveryType.JSON) {
                            // Stores the keys of the file's account (or accounts) and adds them.
                            val result = multiaccountInteractor.importFromJson(
                                json = input,
                                password = password,
                                accountName = accountName,
                            )
                            if (result.needRepair.isNotEmpty()) {
                                offerBatchRepair(result, input, password, accountName, context)
                            } else {
                                mainStarter.start(context)
                            }
                        } else {
                            // Stores the keys and adds the account in one step.
                            recoverSoraAccountMethod(input, accountName)
                            mainStarter.start(context)
                        }
                    } catch (offer: WalletRepairAvailableException) {
                        offerRepair(
                            offer = offer,
                            input = input,
                            accountName = accountName,
                            kind = when (recoveryCardState.recoveryType) {
                                RecoveryType.PASSPHRASE -> ImportKind.PHRASE
                                RecoveryType.SEED -> ImportKind.SEED
                                RecoveryType.JSON -> ImportKind.JSON
                            },
                            password = password,
                            onRepaired = { mainStarter.start(context) },
                        )
                    } catch (t: Throwable) {
                        navController.popBackStack()
                        onError(t)
                    } finally {
                        _recoveryAccountNameCardState.value =
                            (_recoveryAccountNameCardState.value ?: recoverAccountNameState).copy(
                                btnEnabled = true,
                                loading = false,
                            )
                    }
                }
            }
        }
    }

    fun onRecoverySourceDismiss() {
        _recoveryDialog.value = false
    }

    fun onRecoveryDialogShow() {
        _recoveryDialog.value = true
    }

    fun onGoogleSignin(
        navController: NavController,
        launcher: ActivityResultLauncher<Intent>
    ) {
        _recoveryDialog.value = false
        if (!requireCloudBackup()) return
        _tutorialScreenState.value?.let {
            _tutorialScreenState.value = it.copy(isGoogleSigninLoading = true)
            viewModelScope.launch {
                try {
                    backupService.logout()
                    if (backupService.authorize(launcher)) {
                        loadGoogleBackups(navController)
                    } else {
                        _tutorialScreenState.value = it.copy(isGoogleSigninLoading = false)
                    }
                } catch (e: SocketException) {
                    _tutorialScreenState.value = it.copy(isGoogleSigninLoading = false)
                    onError(SoraException.networkError(resourceManager, e))
                } catch (e: SocketTimeoutException) {
                    _tutorialScreenState.value = it.copy(isGoogleSigninLoading = false)
                    onError(SoraException.networkError(resourceManager, e))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: AuthConsentException) {
                    _consentExceptionHandler.value = e.intent
                } catch (e: Exception) {
                    onError(R.string.wallet_cloud_backup_failed)
                } finally {
                    _tutorialScreenState.value = _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)
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
                                RecoveryType.JSON ->
                                    jp.co.soramitsu.common.R.string.onboarding_enter_json
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

    private fun toggleToolbarActionButton(route: String) {
        _toolbarState.value?.let {
            _toolbarState.value = it.copy(
                basic = it.basic.copy(
                    actionLabel = if ((route == OnboardingFeatureRoutes.PASSPHRASE || route == OnboardingFeatureRoutes.PASSPHRASE_CONFIRMATION) && !isFromGoogleDrive) {
                        resourceManager.getString(R.string.common_skip)
                    } else {
                        null
                    }
                )
            )
        }
    }

    fun onCreateAccountContinueClicked(navController: NavController) {
        navController.navigate(OnboardingFeatureRoutes.DISCLAIMER)

        viewModelScope.launch {
            _createAccountCardState.value?.let {
                val accountName = it.accountNameInputState.value.text
                // Nothing is stored yet: the keys are written only when the flow finishes.
                val draft = multiaccountInteractor.createDraft(accountName)
                tempDraft?.destroy()
                tempDraft = draft
                _passphraseCardState.value = BackupScreenState(
                    mnemonicWords = draft.mnemonicWords,
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
        _recoveryDialog.value = false
        chosenFileText = null
        recoveryInputValid = false
        _recoveryState.value = when (index) {
            1 -> {
                isValidMethod = multiaccountInteractor::isMnemonicValid
                recoverSoraAccountMethod = multiaccountInteractor::importFromMnemonic
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
                recoverSoraAccountMethod = multiaccountInteractor::importFromRawSeed
                errorMessageCode = ResponseCode.RAW_SEED_IS_NOT_VALID

                RecoveryState(
                    title = R.string.recovery_enter_seed_title,
                    recoveryType = RecoveryType.SEED,
                    recoveryInputState = InputTextState(
                        label = resourceManager.getString(R.string.recovery_input_raw_seed_hint)
                    )
                )
            }

            3 -> {
                isValidMethod = multiaccountInteractor::isJsonBackupValid
                errorMessageCode = ResponseCode.JSON_BACKUP_INVALID

                RecoveryState(
                    title = jp.co.soramitsu.common.R.string.recovery_enter_json_title,
                    recoveryType = RecoveryType.JSON,
                    recoveryInputState = InputTextState(
                        label = resourceManager.getString(
                            jp.co.soramitsu.common.R.string.recovery_json_input_label
                        )
                    ),
                    passwordInputState = InputTextState(
                        label = resourceManager.getString(
                            jp.co.soramitsu.common.R.string.recovery_json_password_label
                        )
                    ),
                )
            }

            else -> null
        }

        navController.navigate(OnboardingFeatureRoutes.RECOVERY)
    }

    private fun finishCreateAccountProcess(context: Context) {
        viewModelScope.launch {
            try {
                // Stores the keys and adds the account in one step, only now that the person
                // has finished the flow.
                multiaccountInteractor.createUserFromDraft(requireNotNull(tempDraft))
                tempDraft?.destroy()
                tempDraft = null
                mainStarter.start(context)
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                onError(error)
            }
        }
    }

    fun onSuccessfulGoogleSignin(navController: NavController) {
        if (!requireCloudBackup()) return
        viewModelScope.launch { loadGoogleBackups(navController) }
    }

    private suspend fun loadGoogleBackups(navController: NavController) {
        _tutorialScreenState.value = _tutorialScreenState.value?.copy(isGoogleSigninLoading = true)
        try {
            isFromGoogleDrive = true

            if (navController.currentDestination?.route == OnboardingFeatureRoutes.PASSPHRASE) {
                navController.navigate(OnboardingFeatureRoutes.CREATE_BACKUP_PASSWORD)
            } else {
                val backups = googleBackups()

                _tutorialScreenState.value =
                    _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)

                when {
                    backups.restorable.isNotEmpty() -> {
                        _importAccountListState.value =
                            ImportAccountListScreenState(backups.restorable)
                        navController.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
                    }

                    backups.allUsableHere -> {
                        // The Drive holds backups, and each one is of an account that already
                        // works on this phone. Opening Create account here would look as if no
                        // backup had been found, so the screen stays and says what was found.
                        isFromGoogleDrive = false
                        errorFromResourceLiveData.setValue(
                            jp.co.soramitsu.common.R.string.google_backups_all_here_title to
                                jp.co.soramitsu.common.R.string.google_backups_all_here
                        )
                    }

                    // No backup in this Google account: a new wallet that is backed up there.
                    else -> navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
                }
            }
        } catch (e: UnauthorizedException) {
            _tutorialScreenState.value =
                _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)

            onError(SoraException.businessError(ResponseCode.GOOGLE_LOGIN_FAILED))
        } catch (e: SocketException) {
            onError(SoraException.networkError(resourceManager, e))
        } catch (e: SocketTimeoutException) {
            onError(SoraException.networkError(resourceManager, e))
        } catch (e: AuthConsentException) {
            _tutorialScreenState.value =
                _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)
            _consentExceptionHandler.value = e.intent
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            onError(R.string.wallet_cloud_backup_failed)
        } finally {
            _tutorialScreenState.value = _tutorialScreenState.value?.copy(isGoogleSigninLoading = false)
        }
    }

    fun onCreateAccountClicked(navController: NavController) {
        isFromGoogleDrive = false
        navController.navigate(OnboardingFeatureRoutes.CREATE_ACCOUNT)
    }

    fun onSetBackupPasswordClicked(
        activity: Activity
    ) {
        if (!requireCloudBackup()) return
        _createBackupPasswordState.value?.let { createBackupPasswordState ->
            _createBackupPasswordState.value = createBackupPasswordState.copy(isLoading = true)
            viewModelScope.launch(coroutineManager.io) {
                try {
                    _passphraseCardState.value?.let { passphraseCardState ->
                        tempDraft?.let { draft ->
                            val jsonString = multiaccountInteractor.generateDraftJson(
                                draft,
                                createBackupPasswordState.password.value.text
                            )

                            val passphrase = passphraseCardState.mnemonicWords.joinToString(" ")

                            val seed = Seed(
                                substrateSeed = multiaccountInteractor.convertPassphraseToSeed(
                                    passphrase
                                )
                            )

                            backupService.saveBackupAccount(
                                DecryptedBackupAccount(
                                    name = draft.accountName,
                                    address = draft.address,
                                    mnemonicPhrase = passphrase,
                                    cryptoType = CryptoType.SR25519,
                                    backupAccountType = listOf(
                                        BackupAccountType.JSON,
                                        BackupAccountType.PASSPHRASE,
                                        BackupAccountType.SEED
                                    ),
                                    seed = seed,
                                    json = Json(substrateJson = jsonString)
                                ),
                                createBackupPasswordState.password.value.text
                            )

                            finishCreateAccountProcess(activity)
                        }
                    }
                } catch (e: SocketException) {
                    withContext(coroutineManager.main) {
                        _createBackupPasswordState.value =
                            createBackupPasswordState.copy(isLoading = false)
                    }
                } catch (e: SocketTimeoutException) {
                    withContext(coroutineManager.main) {
                        _createBackupPasswordState.value =
                            createBackupPasswordState.copy(isLoading = false)
                    }
                } catch (e: AuthConsentException) {
                    withContext(coroutineManager.main) {
                        _createBackupPasswordState.value =
                            createBackupPasswordState.copy(isLoading = false)
                        _consentExceptionHandler.value = e.intent
                    }
                } catch (e: StorageQuotaExceeded) {
                    withContext(coroutineManager.main) {
                        _createBackupPasswordState.value =
                            createBackupPasswordState.copy(isLoading = false)
                        onError(R.string.backup_not_enough_space_error)
                    }
                }
            }
        }
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
        if (!requireCloudBackup()) return
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

                        val showImported: suspend () -> Unit = {
                            // Whether other backups remain is known before the success screen
                            // opens, also when it opens after a confirmed repair.
                            val more = try {
                                getBackupedAccountsFiltered().isNotEmpty()
                            } catch (error: kotlinx.coroutines.CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                // The list could not be loaded just now; the account itself
                                // was restored.
                                false
                            }
                            _importAccountPasswordState.value =
                                (_importAccountPasswordState.value ?: state).copy(
                                    isImportMoreAvailable = more,
                                    isLoading = false,
                                )
                            navController.navigate(
                                route = OnboardingFeatureRoutes.IMPORT_ACCOUNT_SUCCESS,
                                navOptions = NavOptions.Builder()
                                    .setPopUpTo(OnboardingFeatureRoutes.TUTORIAL, true)
                                    .build()
                            )
                        }
                        // Stores the keys and adds the account in one step.
                        val backupPassword = state.passwordInput.value.text
                        try {
                            recoverSoraAccountFromDecryptedBackupAccount(
                                decryptedBackupAccount,
                                backupPassword,
                            )
                        } catch (offer: WalletRepairAvailableException) {
                            offerRepairFromBackup(
                                offer,
                                decryptedBackupAccount,
                                backupPassword,
                                showImported,
                            )
                            return@let
                        }

                        showImported()
                    } catch (e: DecryptionException) {
                        onError(SoraException.businessError(ResponseCode.GOOGLE_BACKUP_DECRYPTION_FAILED))
                    } catch (e: DecodingException) {
                        onError(SoraException.businessError(ResponseCode.GENERAL_ERROR))
                    } catch (e: SoraException) {
                        onError(asGoogleBackupError(e))
                    } catch (e: IllegalArgumentException) {
                        onError(SoraException.businessError(ResponseCode.GOOGLE_BACKUP_DECRYPTION_FAILED))
                    } catch (e: SocketException) {
                        onError(SoraException.networkError(resourceManager, e))
                    } catch (e: SocketTimeoutException) {
                        onError(SoraException.networkError(resourceManager, e))
                    } catch (e: AuthConsentException) {
                        _consentExceptionHandler.value = e.intent
                    }
                }
                // The success screen's state (import more) was set by showImported; only the
                // loading mark is cleared here.
                _importAccountPasswordState.value =
                    (_importAccountPasswordState.value ?: state).copy(isLoading = false)
            }
        }
    }

    /**
     * A Google backup that holds only the encrypted JSON is opened with the backup password by
     * the JSON import itself, so a wrong password or an unusable backup arrives with the codes
     * of a backup file. Their texts speak of a file the person chose and exported; on this
     * screen the person typed the password of a Google backup, and the message says that.
     */
    private fun asGoogleBackupError(error: SoraException): SoraException =
        when (error.errorResponseCode) {
            ResponseCode.JSON_BACKUP_WRONG_PASSWORD ->
                SoraException.businessError(ResponseCode.GOOGLE_BACKUP_DECRYPTION_FAILED)
            ResponseCode.JSON_BACKUP_INVALID ->
                SoraException.businessError(ResponseCode.GOOGLE_BACKUP_UNREADABLE)
            else -> error
        }

    /**
     * The form of the wallet in a Google backup that is restored: the one that derives the
     * account the backup names. A backup holds up to three forms (phrase, raw seed, encrypted
     * JSON); an old backup can hold a phrase or seed that is not this account's, and a wallet
     * that is only a key pair is backed up as JSON alone.
     */
    private class BackupSecret(val kind: ImportKind, val input: String)

    private suspend fun backupSecret(backup: DecryptedBackupAccount): BackupSecret? {
        val phrase = backup.mnemonicPhrase
            ?.takeIf { backup.backupAccountType.contains(BackupAccountType.PASSPHRASE) }
            ?.takeIf { multiaccountInteractor.isMnemonicValid(it) }
        val seed = backup.seed?.substrateSeed
            ?.takeIf { backup.backupAccountType.contains(BackupAccountType.SEED) }
            ?.takeIf { multiaccountInteractor.isRawSeedValid(it) }
        val json = backup.json?.substrateJson
            ?.takeIf { backup.backupAccountType.contains(BackupAccountType.JSON) }
            ?.takeIf { multiaccountInteractor.isJsonBackupValid(it) }
        // First choice: the form that restores exactly the account the backup names.
        if (phrase != null && multiaccountInteractor.addressOfMnemonic(phrase) == backup.address) {
            return BackupSecret(ImportKind.PHRASE, phrase)
        }
        if (seed != null && multiaccountInteractor.addressOfRawSeed(seed) == backup.address) {
            return BackupSecret(ImportKind.SEED, seed)
        }
        if (json != null) return BackupSecret(ImportKind.JSON, json)
        // A backup whose address is written another way: the order every release used.
        return when {
            phrase != null -> BackupSecret(ImportKind.PHRASE, phrase)
            seed != null -> BackupSecret(ImportKind.SEED, seed)
            else -> null
        }
    }

    /** The JSON in a backup names its own account; anything else in the file is not imported. */
    private fun expectedJsonAddress(backup: DecryptedBackupAccount): String? =
        backup.address.takeIf { multiaccountInteractor.isAddressValid(it) }

    /** The same choice of secret as [recoverSoraAccountFromDecryptedBackupAccount]. */
    private suspend fun offerRepairFromBackup(
        offer: WalletRepairAvailableException,
        decryptedBackupAccount: DecryptedBackupAccount,
        backupPassword: String,
        onRepaired: suspend () -> Unit,
    ) {
        val secret = backupSecret(decryptedBackupAccount)
        offerRepair(
            offer = offer,
            input = secret?.input.orEmpty(),
            accountName = decryptedBackupAccount.name,
            kind = secret?.kind ?: ImportKind.PHRASE,
            password = backupPassword,
            expectedAddress = expectedJsonAddress(decryptedBackupAccount),
            onRepaired = onRepaired,
        )
    }

    private suspend fun recoverSoraAccountFromDecryptedBackupAccount(
        decryptedBackupAccount: DecryptedBackupAccount,
        backupPassword: String,
    ): SoraAccount {
        // A backup that holds no valid phrase, seed or JSON cannot restore anything: said in
        // Google backup words, with the way forward, not as "Something went wrong".
        val secret = backupSecret(decryptedBackupAccount)
            ?: throw SoraException.businessError(ResponseCode.GOOGLE_BACKUP_UNREADABLE)
        return when (secret.kind) {
            ImportKind.PHRASE ->
                multiaccountInteractor.importFromMnemonic(secret.input, decryptedBackupAccount.name)
            ImportKind.SEED ->
                multiaccountInteractor.importFromRawSeed(secret.input, decryptedBackupAccount.name)
            // The JSON of a Google backup is encrypted with the backup password itself.
            ImportKind.JSON -> multiaccountInteractor.importFromJson(
                json = secret.input,
                password = backupPassword,
                accountName = decryptedBackupAccount.name,
                expectedAddress = expectedJsonAddress(decryptedBackupAccount),
            ).imported.single()
        }
    }

    fun onImportFinished(context: Context) {
        mainStarter.start(context)
    }

    fun onImportMoreClicked(navController: NavController) {
        viewModelScope.launch {
            try {
                _importAccountPasswordState.value =
                    _importAccountPasswordState.value?.copy(isLoading = true)
                _importAccountListState.value =
                    ImportAccountListScreenState(getBackupedAccountsFiltered())

                resetBackupLiveData()
                navController.navigate(OnboardingFeatureRoutes.IMPORT_ACCOUNT_LIST)
            } catch (e: SocketException) {
                onError(SoraException.networkError(resourceManager, e))
            } catch (e: SocketTimeoutException) {
                onError(SoraException.networkError(resourceManager, e))
            }
        }
    }

    fun onConsentSuccess(navController: NavController) {
        if (currentDestination == OnboardingFeatureRoutes.TUTORIAL || currentDestination == OnboardingFeatureRoutes.PASSPHRASE) {
            onSuccessfulGoogleSignin(navController)
        }
    }

    private fun resetBackupLiveData() {
        _createBackupPasswordState.value = CreateBackupPasswordState(
            password = InputTextState(
                label = resourceManager.getString(R.string.create_backup_set_password),
                descriptionText = resourceManager.getString(R.string.backup_password_requirments)
            ),
            passwordConfirmation = InputTextState(label = resourceManager.getString(R.string.export_json_input_confirmation_label))
        )

        _importAccountPasswordState.value = ImportAccountPasswordState(
            passwordInput = InputTextState(label = resourceManager.getString(R.string.enter_password_title))
        )
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

    private fun getDrawableFromGoogleBackup(address: String): Drawable? = runCatching {
        avatarGenerator.createAvatar(
            address,
            Dimens.x5.value.toInt(),
        )
    }.getOrNull()

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

    /**
     * The Google backups that still have something to do on this phone: of an account that is
     * not here yet (restoring adds it), and of an account that is here but read-only (restoring
     * completes or repairs its keys in place; the entry is marked). Only the backup of an account
     * that can already sign is left out.
     *
     * A read-only account must stay in this list: its Google backup can be the only backup it
     * has, and hiding it would leave removing the account as the only way to use that backup.
     */
    private suspend fun getBackupedAccountsFiltered(): List<BackupAccountMetaWithIcon> =
        googleBackups().restorable

    /** What the Google account holds for this phone. */
    private class GoogleBackups(
        /** The backups that still have something to do here ([getBackupedAccountsFiltered]). */
        val restorable: List<BackupAccountMetaWithIcon>,
        /**
         * The Google account holds backups, and every one is of an account that already works
         * on this phone, so there is nothing to restore.
         */
        val allUsableHere: Boolean,
    )

    private suspend fun googleBackups(): GoogleBackups {
        val backups = backupService.getBackupAccounts()
            .filter { multiaccountInteractor.isAddressValid(it.address) }
        if (backups.isEmpty()) return GoogleBackups(emptyList(), allUsableHere = false)
        val restores = multiaccountInteractor.backupRestores(backups.map { it.address })
        val restorable = backups.mapNotNull { backup ->
            val restore = restores[backup.address] ?: return@mapNotNull null
            val readOnly = restore == MultiaccountInteractor.BackupRestore.REPAIRS_READ_ONLY_ACCOUNT
            BackupAccountMetaWithIcon(
                backupAccountMeta = backup,
                icon = getDrawableFromGoogleBackup(backup.address),
                readOnlyOnThisPhone = readOnly,
            )
        }
        return GoogleBackups(restorable, allUsableHere = restorable.isEmpty())
    }

    override fun onAction() {
        _skipDialogState.value = true
    }

    override fun onCleared() {
        tempDraft?.destroy()
        tempDraft = null
        super.onCleared()
    }

    fun skipDialogConfirm(context: Context) {
        finishCreateAccountProcess(context)
        _skipDialogState.value = false
    }

    fun skipDialogDismiss() {
        _skipDialogState.value = false
    }
}
