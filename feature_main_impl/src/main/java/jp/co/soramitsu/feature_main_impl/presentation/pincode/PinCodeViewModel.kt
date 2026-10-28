/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.os.CountDownTimer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.compose.SnackBarState
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.presentation.viewmodel.FailureNotice
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.common.presentation.compose.neon.safemode.WalletSessionState
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_account_api.domain.model.WalletDeletionScope
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class PinCodeViewModel @Inject constructor(
    private val interactor: PinCodeInteractor,
    private val mainInteractor: MainInteractor,
    private val mainRouter: MainRouter,
    private val resourceManager: ResourceManager,
    private val selectNodeRouter: SelectNodeRouter,
    private val progress: WithProgress,
    private val deviceVibrator: DeviceVibrator,
    connectionManager: ConnectionManager,
) : BaseViewModel(), WithProgress by progress {

    internal var state by mutableStateOf(
        PinCodeScreenState(
            maxDotsCount = PinCodeInteractor.PINCODE_LENGTH,
            toolbarTitleString = "",
        )
    )
        private set

    private lateinit var action: PinCodeAction
    private var tempCode = ""
    private var isBiometryEnabled = false
    private var isPincodeUpdateNeeded: Boolean = false
    private var isChanging = false
    private var triesUsed = 0
    private var buttonsDisabled = false
    private var timerStartedTimeStamp = 0L

    private var inputedCode = ""

    val showFingerPrintEventLiveData = SingleLiveEvent<Boolean>()
    val startFingerprintScannerEventLiveData = SingleLiveEvent<Boolean>()
    val fingerPrintDialogVisibilityLiveData = MutableLiveData<Boolean>()
    val fingerPrintErrorLiveData = SingleLiveEvent<String>()

    private val _closeAppLiveData = SingleLiveEvent<Unit>()
    val closeAppLiveData: LiveData<Unit> = _closeAppLiveData

    private val _fingerPrintCanceledFromPromptEvent = SingleLiveEvent<Unit>()
    val fingerPrintCanceledFromPromptEvent: LiveData<Unit> = _fingerPrintCanceledFromPromptEvent

    private val _checkInviteLiveData = SingleLiveEvent<Unit>()
    val checkInviteLiveData: LiveData<Unit> = _checkInviteLiveData

    private val _pincodeChangedEvent = SingleLiveEvent<Unit>()
    val pincodeChangedEvent: LiveData<Unit> = _pincodeChangedEvent

    /**
     * Set once the PIN or biometrics were confirmed for "Forget account": the removal screen
     * replaces the keypad. Nothing is removed before [onRemovalConfirmed].
     */
    internal var removal by mutableStateOf<AccountRemovalState?>(null)
        private set

    private val _biometryInitialDialogEvent = SingleLiveEvent<Unit>()
    val biometryInitialDialogEvent: LiveData<Unit> = _biometryInitialDialogEvent

    private val _resetApplicationEvent = SingleLiveEvent<Unit>()
    val resetApplicationEvent: LiveData<Unit> = _resetApplicationEvent

    private val _switchAccountEvent = SingleLiveEvent<Unit>()
    val switchAccountEvent: LiveData<Unit> = _switchAccountEvent

    lateinit var addresses: List<String>

    init {
        viewModelScope.launch {
            triesUsed = interactor.retrieveTriesUsed()
            timerStartedTimeStamp = interactor.retrieveTimerStartedTimestamp()

            if (timerStartedTimeStamp > 0L) {
                startTimer()

                if (!buttonsDisabled) {
                    interactor.resetTimerStartedTimestamp()
                    timerStartedTimeStamp = 0
                }
            }
        }

        connectionManager.connectionState
            .catch { onError(it) }
            .onEach {
                val reconnected = it && !state.isConnected
                state = state.copy(
                    isConnected = it
                )
                // U2: "We'll continue as soon as you're back online" - continue by itself.
                if (reconnected && state.migrating && state.checkNeedsNetwork) {
                    viewModelScope.launch { tryCatch { proceed() } }
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            tryCatch {
                isBiometryEnabled = interactor.isBiometryEnabled()
            }
        }
    }

    /**
     * A failure while a typed PIN is checked or saved (for example a busy Keystore) clears the
     * entry: the message is shown and the PIN can be typed again, instead of a full row of
     * dots that takes no more digits.
     */
    override fun onErrorResetProgress() {
        if (inputedCode.isNotEmpty()) {
            inputedCode = ""
            state = state.copy(checkedDotsCount = 0, isBackButtonVisible = false)
        }
    }

    private fun startTimer() {
        buttonsDisabled = true
        showFingerPrintEventLiveData.value = false
        createIdleTimer().start()
    }

    fun startAuth(pinCodeAction: PinCodeAction) {
        // Back on the removal screen (from the JSON export): it is already past the PIN and
        // keeps its state; nothing is asked or started again.
        if (removal != null) return
        if (pinCodeAction == PinCodeAction.TIMEOUT_CHECK) WalletSessionState.markLocked()
        viewModelScope.launch {
            action = pinCodeAction
            when (action) {
                PinCodeAction.CREATE_PIN_CODE -> {
                    state = state.copy(
                        toolbarTitleString = resourceManager.getString(R.string.pincode_set_your_pin_code),
                        isBackButtonVisible = false
                    )
                }

                PinCodeAction.OPEN_PASSPHRASE,
                PinCodeAction.OPEN_SEED,
                PinCodeAction.OPEN_JSON,
                PinCodeAction.LOGOUT,
                PinCodeAction.CUSTOM_NODE,
                PinCodeAction.SELECT_NODE -> {
                    state = state.copy(
                        toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code),
                        isBiometricButtonVisible = true
                    )
                    showFingerPrintEventLiveData.value = isBiometryEnabled
                }

                PinCodeAction.CHANGE_PIN_CODE -> {
                    isPincodeUpdateNeeded = interactor.isPincodeUpdateNeeded()
                    // The forced 4-to-6 digit change replaces the unlock (U10).
                    if (isPincodeUpdateNeeded) WalletSessionState.markLocked()
                    val maxDotsLength = if (isPincodeUpdateNeeded) {
                        PinCodeInteractor.OLD_PINCODE_LENGTH
                    } else {
                        PinCodeInteractor.PINCODE_LENGTH
                    }
                    state = state.copy(
                        maxDotsCount = maxDotsLength,
                        toolbarTitleString = resourceManager.getString(R.string.pincode_enter_current_pin_code),
                        isBackButtonVisible = true
                    )

                    showFingerPrintEventLiveData.value = isBiometryEnabled
                }

                PinCodeAction.TIMEOUT_CHECK -> {
                    try {
                        if (interactor.isCodeSet()) {
                            state = state.copy(
                                toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code),
                                isBackButtonVisible = false
                            )
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                        } else {
                            state = state.copy(
                                toolbarTitleString = resourceManager.getString(R.string.pincode_set_your_pin_code),
                                isBackButtonVisible = false
                            )
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                            action = PinCodeAction.CREATE_PIN_CODE
                        }
                    } catch (t: Throwable) {
                        onError(t)
                        if (t is KeyMaterialUnavailableException) {
                            // The stored PIN exists but the wallet key cannot be used right now
                            // (busy or missing Keystore key). Keep asking for the existing PIN;
                            // offering to create a new one would replace the stored PIN.
                            state = state.copy(
                                toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code),
                                isBackButtonVisible = false
                            )
                            showFingerPrintEventLiveData.value = isBiometryEnabled
                        } else {
                            action = PinCodeAction.CREATE_PIN_CODE
                        }
                    }
                }
            }
        }
    }

    fun pinCodeNumberClicked(
        pinCodeNumber: String,
    ) {
        if (!buttonsDisabled) {
            if (inputedCode.length >= state.maxDotsCount) {
                return
            }
            inputedCode += pinCodeNumber
            state = state.copy(
                checkedDotsCount = inputedCode.length,
                isBackButtonVisible = inputedCode.isNotEmpty()
            )
            if (inputedCode.length == state.maxDotsCount) {
                pinCodeEntered()
            }
        }
    }

    fun pinCodeDeleteClicked() {
        if (inputedCode.isEmpty()) {
            return
        }
        inputedCode = inputedCode.substring(0, inputedCode.length - 1)
        state = state.copy(
            checkedDotsCount = inputedCode.length,
            isBackButtonVisible = inputedCode.isNotEmpty()
        )
    }

    fun onWrongPinAnimationEnd() {
        state = state.copy(enableShakeAnimation = false)
    }

    private fun pinCodeEntered() {
        viewModelScope.launch {
            tryCatch {
                if (PinCodeAction.CREATE_PIN_CODE == action) {
                    if (tempCode.isEmpty()) {
                        tempCode = inputedCode
                        state = state.copy(
                            toolbarTitleString = resourceManager.getString(
                                if (isChanging) R.string.pincode_confirm_new_pin_code else R.string.pincode_confirm_your_pin_code
                            ),
                            isBackButtonVisible = false,
                            checkedDotsCount = 0
                        )
                        inputedCode = ""
                    } else {
                        pinCodeEnterComplete()
                    }
                } else {
                    checkPinCode()
                }
            }
        }
    }

    private fun pinCodeEnterComplete() {
        if (tempCode == inputedCode) {
            registerPinCode()
        } else {
            tempCode = ""
            inputedCode = ""
            state = state.copy(
                toolbarTitleString = resourceManager.getString(
                    if (isChanging) R.string.pincode_enter_new_pin_code else R.string.pincode_set_your_pin_code
                ),
                isBackButtonVisible = false,
                checkedDotsCount = 0,
                enableShakeAnimation = true
            )
            deviceVibrator.makeShortVibration()
        }
    }

    private fun registerPinCode() {
        viewModelScope.launch {
            tryCatch {
                interactor.savePin(inputedCode)
                if (isChanging) {
                    _pincodeChangedEvent.trigger()
                    WalletSessionState.markUnlocked()
                    mainRouter.popBackStack()
                } else {
                    val available = interactor.isBiometryAvailable()
                    if (available) {
                        _biometryInitialDialogEvent.trigger()
                    } else {
                        proceed()
                    }
                }
            }
        }
    }

    private suspend fun checkPinCode() {
        val result = interactor.checkPin(inputedCode)

        if (result) {
            processSuccessfullAuth()
        } else {
            inputedCode = ""
            state = state.copy(
                isBackButtonVisible = false,
                checkedDotsCount = 0,
                enableShakeAnimation = true
            )
            deviceVibrator.makeShortVibration()
            proccessFailedAuthIdleTimer(true)
        }
    }

    override fun onBackPressed() {
        if (removal != null) {
            // Back on the removal screen cancels: nothing was removed.
            onRemovalCancelled()
            return
        }
        if (PinCodeAction.CREATE_PIN_CODE == action) {
            if (tempCode.isEmpty()) {
                if (isChanging && !isPincodeUpdateNeeded) {
                    mainRouter.popBackStack()
                } else {
                    _closeAppLiveData.trigger()
                }
            } else {
                tempCode = ""
                inputedCode = ""

                state = state.copy(
                    toolbarTitleString = resourceManager.getString(R.string.pincode_set_your_pin_code),
                    isBackButtonVisible = false,
                    checkedDotsCount = 0
                )
            }
        } else {
            if (PinCodeAction.TIMEOUT_CHECK == action || isPincodeUpdateNeeded) {
                _closeAppLiveData.trigger()
            } else {
                mainRouter.popBackStack()
            }
        }
    }

    fun onResume() {
        // The removal screen is already behind the PIN: coming back to it from the JSON export
        // (whose Back returns here) does not ask for the PIN again. The phrase is veiled again
        // whenever the screen was left ([onRemovalHidden]).
        if (removal != null) return
        if (action != PinCodeAction.CREATE_PIN_CODE && !buttonsDisabled) {
            startFingerprintScannerEventLiveData.value = isBiometryEnabled
        }
        if (state.migrating && state.isConnected) {
            viewModelScope.launch {
                delay(300)
                proceed()
            }
        }
    }

    fun onAuthenticationError(errString: String) {
        fingerPrintErrorLiveData.value = errString
    }

    fun onAuthenticationSucceeded() {
        processSuccessfullAuth()
    }

    private fun proccessFailedAuthIdleTimer(countAttempts: Boolean) {
        viewModelScope.launch {
            if (countAttempts) triesUsed++
            interactor.saveTriesUsed(triesUsed)

            if (triesUsed >= 3) {
                startTimer()
            } else {
                if (triesUsed == 2) {
                    snackBarLiveData.value =
                        SnackBarState(resourceManager.getString(R.string.pincode_one_try_left))
                }
            }
        }
    }

    private fun processSuccessfullAuth() {
        triesUsed = 0
        viewModelScope.launch {
            interactor.resetTriesUsed()
            interactor.resetTimerStartedTimestamp()

            when (action) {
                PinCodeAction.OPEN_PASSPHRASE -> {
                    mainRouter.popBackStack()
                    if (this@PinCodeViewModel::addresses.isInitialized) {
                        mainRouter.showBackupPassphrase(addresses.first())
                    }
                }

                PinCodeAction.OPEN_JSON -> {
                    mainRouter.popBackStack()
                    if (this@PinCodeViewModel::addresses.isInitialized) {
                        mainRouter.showBackupJson(addresses)
                    }
                }

                PinCodeAction.OPEN_SEED -> {
                    mainRouter.popBackStack()
                    if (this@PinCodeViewModel::addresses.isInitialized) {
                        mainRouter.showBackupSeed(addresses.first())
                    }
                }

                PinCodeAction.LOGOUT -> {
                    logout()
                }

                PinCodeAction.CHANGE_PIN_CODE -> {
                    changePinProcessing()
                }

                PinCodeAction.CUSTOM_NODE,
                PinCodeAction.SELECT_NODE -> {
                    selectNodeRouter.returnFromPinCodeCheck()
                }

                else -> {
                    proceed()
                }
            }
        }
    }

    /**
     * The PIN (or biometrics) was just confirmed for "Forget account". Before anything is
     * removed the person sees the backup that re-derives the account (its recovery phrase, else
     * its raw seed), or is told plainly that this phone holds none, and what stays on the phone.
     * The PIN is not asked for again to show it.
     */
    private fun logout() {
        viewModelScope.launch {
            try {
                check(this@PinCodeViewModel::addresses.isInitialized) {
                    "WALLET_DELETION_TARGETS_MISSING"
                }
                val briefing = interactor.walletRemovalBriefing(addresses.toList())
                removal = AccountRemovalState(
                    accounts = briefing.accounts.map { account ->
                        AccountRemovalState.Account(
                            address = account.address,
                            name = account.name,
                            phraseWords = account.phraseWords,
                            seed = account.seedHex.takeIf(String::isNotEmpty)?.let { "0x$it" }.orEmpty(),
                            canExportJson = account.canExportJson,
                        )
                    },
                    removesEverything = briefing.removesEverything,
                    retainedKeySets = briefing.retainedKeySets,
                    restorableKeySets = briefing.restorableKeySets,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                // Nothing was removed and nothing was started. The message is shown on this
                // screen (leaving it now would take the message away with it); the keypad is
                // back, and Back leaves.
                if (!showRemovalPrecondition(error)) onError(error)
            }
        }
    }

    /**
     * A removal that cannot happen until something else does gets a text that says what, never
     * "try again". True when [error] was such a refusal and its message is shown.
     */
    private fun showRemovalPrecondition(error: Throwable): Boolean {
        if (error.message != WALLET_DELETION_PENDING_TRANSACTIONS) return false
        onErrorResetProgress()
        alertDialogLiveData.value = Pair(
            resourceManager.getString(R.string.account_removal_not_removed_title),
            resourceManager.getString(R.string.account_removal_pending_transactions),
        )
        return true
    }

    fun onRemovalRevealed() {
        removal = removal?.copy(revealed = true, shownOnce = true)
    }

    /**
     * The removal screen is no longer in front (another app, the recents screen, the JSON
     * export): the phrase or seed is veiled again, like every screen that shows a secret. What
     * was already acknowledged stays.
     */
    fun onRemovalHidden() {
        val state = removal ?: return
        if (state.revealed) removal = state.copy(revealed = false)
    }

    fun onRemovalWrittenDown(checked: Boolean) {
        val state = removal ?: return
        // Only after the phrase or seed was actually shown.
        if (state.shownOnce) removal = state.copy(writtenDown = checked)
    }

    fun onRemovalUnderstoodNoBackup(checked: Boolean) {
        removal = removal?.copy(understoodNoBackup = checked)
    }

    fun onRemovalUnderstoodFoundWallets(checked: Boolean) {
        removal = removal?.copy(understoodFoundWallets = checked)
    }

    /** "Export JSON first": the encrypted JSON export of the accounts whose key still signs. */
    fun onRemovalExportJson() {
        val exportable = removal?.accounts?.filter { it.canExportJson }?.map { it.address }
        if (exportable.isNullOrEmpty()) return
        mainRouter.showBackupJson(exportable)
    }

    fun onRemovalCancelled() {
        if (removal?.working == true) return
        removal = null
        mainRouter.popBackStack()
    }

    fun onAuthenticationFailed() {
        deviceVibrator.makeShortVibration()
        proccessFailedAuthIdleTimer(false)
    }

    fun setBiometryAvailable(isAuthReady: Boolean) {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryAvailable(isAuthReady)
            }
        }
    }

    /**
     * "Remove from this phone", after the acknowledgement. The deletion is prepared and carried
     * out only now, so writing the phrase down can take as long as it takes. If what would be
     * removed is no longer what the screen described, nothing is removed and the screen is
     * described again.
     */
    fun onRemovalConfirmed() {
        val state = removal ?: return
        if (!state.canRemove) return
        removal = state.copy(working = true)
        viewModelScope.launch {
            progress.showProgress()
            try {
                val preview = interactor.createWalletDeletionPreview(addresses.toList())
                val described = state.accounts.map { it.address }.toSet()
                val sameAsDescribed =
                    preview.targets.map { it.walletId }.toSet() == described &&
                        (preview.scope == WalletDeletionScope.ALL) == state.removesEverything
                if (!sameAsDescribed) {
                    // The accounts on this phone changed while the screen was open.
                    removal = null
                    logout()
                    return@launch
                }
                val result = interactor.confirmAndExecuteWalletDeletion(preview)
                when (result.scope) {
                    WalletDeletionScope.ALL -> _resetApplicationEvent.trigger()
                    WalletDeletionScope.SINGLE -> _switchAccountEvent.trigger()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleWalletDeletionError(error)
            } finally {
                progress.hideProgress()
            }
        }
    }

    private suspend fun handleWalletDeletionError(error: Throwable) {
        jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(error)
        val failureCode = try {
            interactor.walletDeletionFailureCode()
        } catch (lookupError: CancellationException) {
            throw lookupError
        } catch (_: Throwable) {
            null
        }
        if (failureCode != null) {
            removal = null
            errorLiveData.value = failureCode
            // Startup resumes the durable journal and routes the user to the
            // encrypted-backup recovery screen. Never continue in a partially
            // deleted session.
            _resetApplicationEvent.trigger()
            return
        }
        val stillListed = try {
            interactor.areListed(addresses.toList())
        } catch (lookupError: CancellationException) {
            throw lookupError
        } catch (_: Throwable) {
            false
        }
        if (!stillListed) {
            // The removal was journaled and has gone past the point where the accounts are
            // listed (or its state cannot be read). The next start finishes it; this session
            // must not go on as if nothing had happened.
            removal = null
            _resetApplicationEvent.trigger()
            return
        }
        // The accounts are all still listed: nothing was removed. The screen stays, with the
        // same acknowledgement, and the person can try again or cancel.
        removal = removal?.copy(working = false)
        if (showRemovalPrecondition(error)) return
        if (FailureNotice.of(error) == FailureNotice.WALLET_READ_ONLY) {
            // Wallet changes went off for the whole app meanwhile: trying again cannot help,
            // and the read-only text says what does.
            onError(error)
            return
        }
        alertDialogLiveData.value = Pair(
            resourceManager.getString(R.string.account_removal_not_removed_title),
            resourceManager.getString(R.string.account_removal_not_removed)
                .format(error.walletDeletionSafeCode()),
        )
    }

    private companion object {
        /** `UserRepositoryImpl.createWalletDeletionPreview`: a transaction is not finished. */
        const val WALLET_DELETION_PENDING_TRANSACTIONS = "WALLET_DELETION_PENDING_TRANSACTIONS"
    }

    private fun Throwable.walletDeletionSafeCode(): String =
        message
            ?.takeIf { it.matches(Regex("^[A-Z0-9_]{1,128}$")) }
            ?: "WALLET_DELETION_FAILED"

    fun biometryDialogYesClicked() {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryEnabled(true)
                proceed()
            }
        }
    }

    fun biometryDialogNoClicked() {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryEnabled(false)
                proceed()
            }
        }
    }

    fun onSwitchNode() {
        selectNodeRouter.showSelectNode()
    }

    private var proceeding = false
    private var proceeded = false

    /**
     * After unlocking: checks whether the account still has an older SORA balance to claim.
     * Only an unchecked 15-word legacy phrase needs the network for that; every other account is
     * checked on the phone, so being offline never keeps the person at this screen (U2).
     */
    private suspend fun proceed() {
        // Set before the first suspension: a second trigger (reconnect, resume, biometrics
        // dialog) while this one waits must not navigate a second time.
        if (proceeding || proceeded) return
        proceeding = true
        try {
            val needsNetwork = try {
                interactor.migrationCheckNeedsNetwork()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // Unreadable right now: the optional check below is skipped, never waited for.
                false
            }
            // Only a check that needs the network ever says it waits for it (U2).
            state = state.copy(
                migrating = true,
                checkNeedsNetwork = needsNetwork,
            )
            if (state.isConnected || !needsNetwork) {
                val needs = try {
                    interactor.needsMigration()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    // The claim check is optional: it runs again at the next unlock (nothing was
                    // recorded). It never keeps the person out of the wallet.
                    jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(error)
                    false
                }
                state = state.copy(
                    migrating = false,
                    checkNeedsNetwork = false,
                )
                proceeded = true
                WalletSessionState.markUnlocked()
                if (needs) {
                    mainRouter.showClaim()
                } else {
                    mainRouter.popBackStack()
                }
            }
            // Otherwise it waits for the network; the reconnect handler calls this again.
        } finally {
            proceeding = false
        }
    }

    private fun changePinProcessing() {
        viewModelScope.launch {
            action = PinCodeAction.CREATE_PIN_CODE
            if (state.maxDotsCount != PinCodeInteractor.PINCODE_LENGTH) {
                state = state.copy(
                    maxDotsCount = PinCodeInteractor.PINCODE_LENGTH,
                    isLengthInfoAlertVisible = true
                )
            }
            isChanging = true
            isBiometryEnabled = false
            showFingerPrintEventLiveData.value = isBiometryEnabled
            state =
                state.copy(
                    toolbarTitleString = resourceManager.getString(R.string.pincode_enter_new_pin_code),
                    checkedDotsCount = 0
                )
            inputedCode = ""
        }
    }

    fun canceledFromPrompt() {
        _fingerPrintCanceledFromPromptEvent.trigger()
    }

    fun popBackToAccountList() {
        mainRouter.popBackStackToAccountList()
    }

    private fun getMillisFromCounter(counter: Int): Long {
        return when (counter) {
            3 -> 1
            4 -> 5
            5 -> 15
            in 6..Int.MAX_VALUE -> 30
            else -> 0
        } * 60 * 1000L
    }

    private fun createIdleTimer(): CountDownTimer {
        val currentTime = Date().time
        val time = if (timerStartedTimeStamp == 0L) {
            viewModelScope.launch { interactor.saveTimerStartedTimestamp(currentTime) }
            getMillisFromCounter(triesUsed)
        } else {
            getMillisFromCounter(triesUsed) - (currentTime - timerStartedTimeStamp)
        }

        return object : CountDownTimer(time, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val allSecondsLeft = millisUntilFinished / 1000
                val minutesLeft = allSecondsLeft / 60
                val secondsLeftWithoutMinutes = allSecondsLeft - minutesLeft * 60

                val minutesString = "$minutesLeft ${resourceManager.getString(R.string.common_min)}"
                val secondsString =
                    "$secondsLeftWithoutMinutes ${resourceManager.getString(R.string.common_sec)}"
                state = state.copy(
                    toolbarTitleString = resourceManager.getString(
                        R.string.pin_locked_for_title,
                        "$minutesString $secondsString"
                    )
                )
            }

            override fun onFinish() {
                buttonsDisabled = false
                showFingerPrintEventLiveData.value = isBiometryEnabled
                viewModelScope.launch { interactor.resetTimerStartedTimestamp() }
                state =
                    state.copy(toolbarTitleString = resourceManager.getString(R.string.pincode_enter_pin_code))
            }
        }
    }

    fun changeFingerPrintButtonVisibility(isVisible: Boolean) {
        state = state.copy(isBiometricButtonVisible = isVisible)
    }

    fun setNewLengthPinCodeClicked() {
        state = state.copy(isLengthInfoAlertVisible = false, isBackButtonVisible = false)
    }
}
