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

package jp.co.soramitsu.sora.splash.presentation.recovery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import jp.co.soramitsu.common.backup.CloudBackupProvider
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStepState
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.core_db.WalletUpgradeBackupException
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserDatasource
import jp.co.soramitsu.feature_account_impl.domain.migration.MigrationProgress
import jp.co.soramitsu.sora.BuildConfig
import jp.co.soramitsu.sora.splash.domain.SplashInteractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which title the upgrade progress screen shows (design spec 9.2 U1 / U5). */
enum class ProgressTitle { UPDATING, FINISHING }

/** The upgrade progress screen. Each state comes from a real phase in code. */
data class ProgressUi(
    val title: ProgressTitle,
    val checkingData: SafeStepState,
    val storage: SafeStepState,
    val accounts: SafeStepState,
    val accountsChecked: Int? = null,
    val accountsTotal: Int? = null,
    val finishing: SafeStepState,
    val startedAtElapsed: Long,
)

enum class SafeSheetKind { READ_ONLY, RESTORE_PHRASE, RESTORE_DRIVE, EXPORT, SUPPORT }

enum class AuthError { WRONG, LOCKED, UNAVAILABLE, NO_SCREEN_LOCK }

/** "Show my recovery phrase": confirm, choose a wallet, then the veiled phrase. */
sealed interface RevealUi {
    data class Auth(
        val loading: Boolean = true,
        val pinLength: Int = 6,
        val entered: Int = 0,
        val error: AuthError? = null,
        val lockedForMillis: Long = 0,
        val biometric: Boolean = false,
        /** No app PIN is stored: the phone's screen lock confirms instead. */
        val noPin: Boolean = false,
        val checking: Boolean = false,
    ) : RevealUi

    data class Wallets(val entries: List<PhraseEntry>, val loading: PhraseEntry? = null) : RevealUi

    data class Shown(
        val entry: PhraseEntry,
        val secret: RevealedSecret,
        val revealed: Boolean = false,
        val canGoBack: Boolean,
    ) : RevealUi
}

data class SafeModeUiState(
    val visible: Boolean = false,
    val progress: ProgressUi? = null,
    val panel: RecoveryPanelModel? = null,
    val shownAtMillis: Long = 0,
    val running: RecoveryAction? = null,
    /** The running action has taken more than 10 s (design spec 9.1-5). */
    val runningLong: Boolean = false,
    val autoRetrying: Boolean = false,
    val sheet: SafeSheetKind? = null,
    val exportCreated: Boolean = false,
    val supportNoEmail: Boolean = false,
    val reveal: RevealUi? = null,
)

sealed interface SafeModeEvent {
    /** Try again passed the startup checks: run the wallet verification again. */
    data object RetryReady : SafeModeEvent

    /** Read-only access was authorized: open the wallet. */
    data object ReadOnlyReady : SafeModeEvent

    data class ShareExport(val file: File) : SafeModeEvent

    /** "Set up this device again" was recorded: restart the app process. */
    data object RestartForRestore : SafeModeEvent

    data object OpenStorageSettings : SafeModeEvent

    data class Email(val subject: String, val body: String) : SafeModeEvent

    data object Copied : SafeModeEvent

    /** Confirm with biometrics ([deviceCredential] = the phone's screen lock instead). */
    data class Authenticate(val deviceCredential: Boolean) : SafeModeEvent
}

/**
 * Safe Mode on the splash screen (design spec 9): the recovery panel (U4, U6), the upgrade
 * progress (U1, U5), their sheets and "Show my recovery phrase". Every action goes through the
 * existing [WalletUpgradeBackup] functions; nothing here deletes or rewrites wallet data.
 */
@HiltViewModel
class SafeModeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val interactor: SplashInteractor,
    private val credentialsRepository: CredentialsRepository,
    private val credentialsDatasource: CredentialsDatasource,
    private val userDatasource: UserDatasource,
    private val cloudBackupProvider: CloudBackupProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow(SafeModeUiState())
    val state: StateFlow<SafeModeUiState> = mutableState.asStateFlow()

    private val eventChannel = Channel<SafeModeEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    private val pinAttempts = SafeModePinAttempts(File(context.noBackupFilesDir, SafeModePinAttempts.FILE_NAME))
    private val enteredPin = StringBuilder()
    private var progressJob: Job? = null
    private var progressDelayJob: Job? = null
    private var autoRetryJob: Job? = null
    private var automaticAttempts = 0
    private var awaitingNewRun = false

    // ---- panel ------------------------------------------------------------------------------

    /** Shows the recovery options for [code] (U4 or U6). */
    fun showPanel(code: String, options: WalletRecoveryOptions? = null) {
        val resolved = options ?: runCatching { interactor.recoveryOptions(code) }.getOrNull()
            ?: WalletRecoveryOptions.forState(code, false, jp.co.soramitsu.core_db.RecoveryFacts())
        val model = RecoveryPanels.model(resolved, driveAvailable = cloudBackupProvider.isAvailable)
        stopProgress()
        mutableState.update {
            it.copy(
                visible = true,
                progress = null,
                panel = model,
                shownAtMillis = System.currentTimeMillis(),
                running = null,
                runningLong = false,
                autoRetrying = false,
            )
        }
        scheduleAutomaticRetry(model)
    }

    /**
     * Transient states are retried automatically, up to three times with growing pauses
     * (1, 3 and 10 s), before only the options stay (product decision; design spec 9.1-5).
     */
    private fun scheduleAutomaticRetry(model: RecoveryPanelModel) {
        autoRetryJob?.cancel()
        if (model.kind != RecoveryPanelKind.TRANSIENT || !model.retryAutomatically) return
        if (automaticAttempts >= AUTOMATIC_RETRY_DELAYS.size) return
        val pause = AUTOMATIC_RETRY_DELAYS[automaticAttempts]
        autoRetryJob = viewModelScope.launch {
            delay(pause)
            if (state.value.running == null && state.value.sheet == null && state.value.reveal == null) {
                automaticAttempts++
                runTryAgain(automatic = true)
            }
        }
    }

    /** The storage screen checks again when the person comes back (for example from Settings). */
    fun onResumed() {
        val current = state.value
        if (
            current.visible &&
            current.progress == null &&
            current.panel?.kind == RecoveryPanelKind.STORAGE &&
            current.running == null &&
            current.reveal == null
        ) {
            runTryAgain(automatic = true)
        }
    }

    /** The person left the app: drop any revealed phrase (the flow starts again with the PIN). */
    fun onStopped() {
        if (state.value.reveal != null) closeReveal()
    }

    fun onAction(action: RecoveryAction) {
        if (state.value.running != null && action != RecoveryAction.CONTACT_SUPPORT) return
        when (action) {
            RecoveryAction.TRY_AGAIN -> runTryAgain(automatic = false)
            RecoveryAction.RESTORE_PHRASE -> openSheet(SafeSheetKind.RESTORE_PHRASE)
            RecoveryAction.RESTORE_DRIVE -> openSheet(SafeSheetKind.RESTORE_DRIVE)
            RecoveryAction.SHOW_PHRASE -> startReveal()
            RecoveryAction.READ_ONLY -> openSheet(SafeSheetKind.READ_ONLY)
            RecoveryAction.EXPORT -> openSheet(SafeSheetKind.EXPORT)
            RecoveryAction.OPEN_STORAGE -> eventChannel.trySend(SafeModeEvent.OpenStorageSettings)
            RecoveryAction.CONTACT_SUPPORT -> openSheet(SafeSheetKind.SUPPORT)
        }
    }

    fun openSheet(kind: SafeSheetKind) {
        autoRetryJob?.cancel()
        mutableState.update { it.copy(sheet = kind, exportCreated = false, supportNoEmail = false) }
    }

    fun closeSheet() {
        if (state.value.running != null && state.value.sheet != SafeSheetKind.SUPPORT) return
        mutableState.update { it.copy(sheet = null) }
    }

    private fun runTryAgain(automatic: Boolean) {
        runAction(RecoveryAction.TRY_AGAIN, automatic) {
            // Re-runs the startup checks and, for an interrupted verification, authorizes one
            // more run. Never deletes or rewrites wallet data.
            WalletUpgradeBackup.retry(context)
        }
    }

    private fun <T> runAction(
        action: RecoveryAction,
        automatic: Boolean = false,
        work: () -> T,
    ): Job {
        autoRetryJob?.cancel()
        mutableState.update { it.copy(running = action, runningLong = false, autoRetrying = automatic) }
        return viewModelScope.launch {
            val long = launch {
                delay(STILL_WORKING_MILLIS)
                mutableState.update { it.copy(runningLong = true) }
            }
            val result = try {
                withContext(Dispatchers.IO) { runCatching { work() } }
            } finally {
                long.cancel()
            }
            onActionDone(action, result)
        }
    }

    private fun onActionDone(action: RecoveryAction, result: Result<Any?>) {
        val error = result.exceptionOrNull() ?: (result.getOrNull() as? Result<*>)?.exceptionOrNull()
        val value = (result.getOrNull() as? Result<*>)?.getOrNull() ?: result.getOrNull()
        when (action) {
            RecoveryAction.TRY_AGAIN -> if (error == null) {
                // The verification runs next; after 1.5 s the progress screen takes over (or it
                // is already showing, for "Try upgrade again" from the home screen).
                awaitingNewRun = true
                mutableState.update { current ->
                    val progress = current.progress ?: return@update current
                    current.copy(
                        progress = progressFor(
                            title = progress.title,
                            checkingData = SafeStepState.DONE,
                            phase = interactor.migrationProgress.value,
                            awaitingNewRun = true,
                            startedAtElapsed = progress.startedAtElapsed,
                        ),
                    )
                }
                eventChannel.trySend(SafeModeEvent.RetryReady)
                progressDelayJob?.cancel()
                progressDelayJob = viewModelScope.launch {
                    delay(SILENT_UPGRADE_MILLIS)
                    showProgress(ProgressTitle.FINISHING, checkingDataDone = true)
                }
            } else {
                showPanel(failureCode(error, "BACKUP_RETRY_FAILED"))
            }

            RecoveryAction.READ_ONLY -> {
                val route = value as? WalletUpgradeBackup.SafeReadOnlyRoute
                if (
                    error == null && (
                        route == WalletUpgradeBackup.SafeReadOnlyRoute.ROOM_READ_ONLY ||
                            route == WalletUpgradeBackup.SafeReadOnlyRoute.NORMAL_ACCESS
                        )
                ) {
                    mutableState.update { it.copy(sheet = null) }
                    eventChannel.trySend(SafeModeEvent.ReadOnlyReady)
                } else {
                    mutableState.update { it.copy(sheet = null) }
                    showPanel(failureCode(error, "LEGACY_RECOVERY_AUTHORIZATION_FAILED"))
                }
            }

            RecoveryAction.EXPORT -> {
                val file = value as? File
                if (error == null && file != null) {
                    mutableState.update { it.copy(running = null, runningLong = false, exportCreated = true) }
                    eventChannel.trySend(SafeModeEvent.ShareExport(file))
                } else {
                    mutableState.update { it.copy(sheet = null) }
                    showPanel(failureCode(error, "RECOVERY_EXPORT_FAILED"))
                }
            }

            RecoveryAction.RESTORE_PHRASE,
            RecoveryAction.RESTORE_DRIVE -> if (error == null) {
                mutableState.update { it.copy(running = null) }
                eventChannel.trySend(SafeModeEvent.RestartForRestore)
            } else {
                mutableState.update { it.copy(sheet = null) }
                showPanel(failureCode(error, "SET_UP_AGAIN_REQUEST_FAILED"))
            }

            else -> mutableState.update { it.copy(running = null, runningLong = false) }
        }
    }

    private fun failureCode(error: Throwable?, default: String): String =
        (error as? WalletUpgradeBackupException)?.code
            ?: WalletUpgradeBackup.blockingFailure()?.code
            ?: WalletUpgradeBackup.transientVerificationFailure()
            ?: default

    fun confirmReadOnly() {
        runAction(RecoveryAction.READ_ONLY) {
            WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow()
        }
    }

    fun confirmRestore() {
        val action = if (state.value.sheet == SafeSheetKind.RESTORE_DRIVE) {
            RecoveryAction.RESTORE_DRIVE
        } else {
            RecoveryAction.RESTORE_PHRASE
        }
        runAction(action) { WalletUpgradeBackup.requestSetUpAgain(context).getOrThrow() }
    }

    fun createExport() {
        runAction(RecoveryAction.EXPORT) { WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow() }
    }

    /** The verification result arrived for a retry (navigation follows) or recovery shows. */
    fun onVerificationRouted() {
        progressDelayJob?.cancel()
        mutableState.update { it.copy(running = null, runningLong = false, autoRetrying = false) }
    }

    // ---- support ----------------------------------------------------------------------------

    fun technicalDetails(): List<Pair<String, String>> {
        val current = state.value
        return listOf(
            DETAIL_CODE to (current.panel?.code ?: "-"),
            DETAIL_VERSION to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            DETAIL_TIME to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
                .format(Date(current.shownAtMillis.takeIf { it > 0 } ?: System.currentTimeMillis())),
            DETAIL_ANDROID to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
    }

    /** Support details: the code, versions and time. Never an address, a name or a secret. */
    fun supportText(labels: Map<String, String>): String =
        technicalDetails().joinToString("\n") { (key, value) -> "${labels[key] ?: key}: $value" }

    fun emailSupport(labels: Map<String, String>) {
        eventChannel.trySend(SafeModeEvent.Email(SUPPORT_SUBJECT, supportText(labels)))
    }

    fun onNoEmailApp() {
        mutableState.update { it.copy(supportNoEmail = true) }
    }

    fun copyToClipboard(label: String, text: String) {
        runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        }
        eventChannel.trySend(SafeModeEvent.Copied)
    }

    // ---- progress ---------------------------------------------------------------------------

    /**
     * Shows the upgrade progress screen (U1, or U5 when resuming). [checkingDataDone] is true
     * once the startup checks have passed (they ran before the first screen, or Try again
     * finished them).
     */
    fun showProgress(title: ProgressTitle, checkingDataDone: Boolean) {
        if (state.value.progress != null) return
        mutableState.update {
            it.copy(
                visible = true,
                progress = progressFor(
                    title = title,
                    checkingData = if (checkingDataDone) SafeStepState.DONE else SafeStepState.RUNNING,
                    phase = interactor.migrationProgress.value,
                    awaitingNewRun = awaitingNewRun,
                    startedAtElapsed = SystemClock.elapsedRealtime(),
                ),
            )
        }
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            interactor.migrationProgress.collect { phase ->
                if (phase is MigrationProgress.OpeningStorage || phase is MigrationProgress.CheckingAccounts) {
                    awaitingNewRun = false
                }
                mutableState.update { current ->
                    val progress = current.progress ?: return@update current
                    current.copy(
                        progress = progressFor(
                            title = progress.title,
                            checkingData = progress.checkingData,
                            phase = phase,
                            awaitingNewRun = awaitingNewRun,
                            startedAtElapsed = progress.startedAtElapsed,
                        ),
                    )
                }
            }
        }
    }

    /** "Try upgrade again" from the read-only home: the progress shows from the start. */
    fun startRetryFromHome() {
        showProgress(ProgressTitle.FINISHING, checkingDataDone = false)
        runAction(RecoveryAction.TRY_AGAIN) { WalletUpgradeBackup.retry(context) }
    }

    /** The startup verification is still running after 1.5 s (U0 is silent below that). */
    fun showStartupProgress() {
        showProgress(
            title = if (interactor.resumingInterruptedUpgrade) ProgressTitle.FINISHING else ProgressTitle.UPDATING,
            checkingDataDone = true,
        )
    }

    /** The verification succeeded: the last row gets its tick before the next screen opens. */
    fun markProgressFinished(): Boolean {
        val progress = state.value.progress ?: return false
        mutableState.update {
            it.copy(
                progress = progress.copy(
                    checkingData = SafeStepState.DONE,
                    storage = SafeStepState.DONE,
                    accounts = SafeStepState.DONE,
                    finishing = SafeStepState.DONE,
                ),
            )
        }
        return true
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
        progressDelayJob?.cancel()
        awaitingNewRun = false
    }

    // ---- show my recovery phrase -------------------------------------------------------------

    fun startReveal() {
        autoRetryJob?.cancel()
        enteredPin.clear()
        mutableState.update { it.copy(reveal = RevealUi.Auth(loading = true)) }
        viewModelScope.launch {
            val lock = pinAttempts.lockRemainingMillis()
            val stored = withContext(Dispatchers.IO) { runCatching { userDatasource.retrievePin() } }
            val biometryEnabled = withContext(Dispatchers.IO) {
                runCatching { userDatasource.isBiometryEnabled() }.getOrDefault(false)
            }
            val pin = stored.getOrNull()
            val auth = when {
                stored.isFailure -> RevealUi.Auth(loading = false, error = AuthError.UNAVAILABLE)
                pin.isNullOrEmpty() -> RevealUi.Auth(
                    loading = false,
                    noPin = true,
                    error = if (canAuthenticate(DEVICE_CREDENTIAL_AUTHENTICATORS)) null else AuthError.NO_SCREEN_LOCK,
                )
                else -> RevealUi.Auth(
                    loading = false,
                    pinLength = pin.length,
                    biometric = biometryEnabled && canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG),
                    error = if (lock > 0) AuthError.LOCKED else null,
                    lockedForMillis = lock,
                )
            }
            mutableState.update { it.copy(reveal = auth) }
        }
    }

    private fun canAuthenticate(authenticators: Int): Boolean =
        runCatching {
            BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)

    fun retryAuthCheck() = startReveal()

    fun requestBiometric() {
        val auth = state.value.reveal as? RevealUi.Auth ?: return
        if (auth.error == AuthError.LOCKED) return
        eventChannel.trySend(SafeModeEvent.Authenticate(deviceCredential = auth.noPin))
    }

    fun pinDigit(digit: Char) {
        val auth = state.value.reveal as? RevealUi.Auth ?: return
        if (auth.loading || auth.checking || auth.noPin || !digit.isDigit()) return
        if (pinAttempts.lockRemainingMillis() > 0) {
            mutableState.update {
                it.copy(reveal = auth.copy(error = AuthError.LOCKED, lockedForMillis = pinAttempts.lockRemainingMillis()))
            }
            return
        }
        if (enteredPin.length >= auth.pinLength) return
        enteredPin.append(digit)
        mutableState.update { it.copy(reveal = auth.copy(entered = enteredPin.length, error = null)) }
        if (enteredPin.length == auth.pinLength) submitPin(auth)
    }

    fun pinDelete() {
        val auth = state.value.reveal as? RevealUi.Auth ?: return
        if (enteredPin.isEmpty() || auth.checking) return
        enteredPin.setLength(enteredPin.length - 1)
        mutableState.update { it.copy(reveal = auth.copy(entered = enteredPin.length)) }
    }

    private fun submitPin(auth: RevealUi.Auth) {
        val entered = enteredPin.toString()
        enteredPin.clear()
        mutableState.update { it.copy(reveal = auth.copy(checking = true)) }
        viewModelScope.launch {
            val stored = withContext(Dispatchers.IO) { runCatching { userDatasource.retrievePin() } }
            val pin = stored.getOrNull()
            when {
                stored.isFailure || pin == null -> mutableState.update {
                    it.copy(reveal = auth.copy(entered = 0, checking = false, error = AuthError.UNAVAILABLE))
                }
                SafeModeVault.pinMatches(pin, entered) -> {
                    withContext(Dispatchers.IO) { pinAttempts.reset() }
                    onAuthenticated()
                }
                else -> {
                    val after = withContext(Dispatchers.IO) { pinAttempts.recordFailure() }
                    val lock = (after.lockedUntilMillis - System.currentTimeMillis()).coerceAtLeast(0)
                    mutableState.update {
                        it.copy(
                            reveal = auth.copy(
                                entered = 0,
                                checking = false,
                                error = if (lock > 0) AuthError.LOCKED else AuthError.WRONG,
                                lockedForMillis = lock,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** PIN, biometrics or the phone's screen lock confirmed the person. */
    fun onAuthenticated() {
        if (state.value.reveal !is RevealUi.Auth) return
        val entries = SafeModeVault.entries(WalletUpgradeBackup.recoveryFacts())
        if (entries.size == 1) {
            openEntry(entries.single(), canGoBack = false)
        } else {
            mutableState.update { it.copy(reveal = RevealUi.Wallets(entries)) }
        }
    }

    fun openEntry(entry: PhraseEntry, canGoBack: Boolean = true) {
        val current = state.value.reveal
        if (current is RevealUi.Wallets) {
            mutableState.update { it.copy(reveal = current.copy(loading = entry)) }
        }
        viewModelScope.launch {
            val secret = withContext(Dispatchers.IO) {
                SafeModeVault.reveal(
                    entry = entry,
                    legacyOwner = WalletUpgradeBackup.recoveryFacts().legacyOwner,
                    credentialsRepository = credentialsRepository,
                    credentialsDatasource = credentialsDatasource,
                )
            }
            if (state.value.reveal == null) return@launch
            mutableState.update { it.copy(reveal = RevealUi.Shown(entry, secret, canGoBack = canGoBack)) }
        }
    }

    fun setRevealed(revealed: Boolean) {
        val shown = state.value.reveal as? RevealUi.Shown ?: return
        mutableState.update { it.copy(reveal = shown.copy(revealed = revealed)) }
    }

    fun backFromReveal() {
        when (val current = state.value.reveal) {
            is RevealUi.Shown -> if (current.canGoBack) {
                mutableState.update {
                    it.copy(reveal = RevealUi.Wallets(SafeModeVault.entries(WalletUpgradeBackup.recoveryFacts())))
                }
            } else {
                closeReveal()
            }
            else -> closeReveal()
        }
    }

    fun closeReveal() {
        enteredPin.clear()
        mutableState.update { it.copy(reveal = null) }
    }

    override fun onCleared() {
        enteredPin.clear()
        super.onCleared()
    }

    companion object {
        const val DETAIL_CODE = "code"
        const val DETAIL_VERSION = "version"
        const val DETAIL_TIME = "time"
        const val DETAIL_ANDROID = "android"
        const val SUPPORT_EMAIL = "support@sora.org"
        const val SUPPORT_SUBJECT = "SORA wallet upgrade recovery"
        const val SILENT_UPGRADE_MILLIS = 1_500L
        const val STILL_WORKING_MILLIS = 10_000L
        val AUTOMATIC_RETRY_DELAYS = longArrayOf(1_000L, 3_000L, 10_000L)

        @Suppress("DEPRECATION")
        val DEVICE_CREDENTIAL_AUTHENTICATORS =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

        /** Maps a real verification phase to the four rows (design spec 7.16). */
        fun progressFor(
            title: ProgressTitle,
            checkingData: SafeStepState,
            phase: MigrationProgress,
            awaitingNewRun: Boolean,
            startedAtElapsed: Long,
        ): ProgressUi {
            val notStarted = checkingData != SafeStepState.DONE ||
                phase is MigrationProgress.Idle ||
                (awaitingNewRun && phase is MigrationProgress.Finished)
            if (notStarted) {
                return ProgressUi(
                    title = title,
                    checkingData = checkingData,
                    storage = SafeStepState.WAITING,
                    accounts = SafeStepState.WAITING,
                    finishing = SafeStepState.WAITING,
                    startedAtElapsed = startedAtElapsed,
                )
            }
            return when (phase) {
                MigrationProgress.OpeningStorage -> ProgressUi(
                    title, checkingData, SafeStepState.RUNNING, SafeStepState.WAITING,
                    finishing = SafeStepState.WAITING, startedAtElapsed = startedAtElapsed,
                )
                is MigrationProgress.CheckingAccounts -> ProgressUi(
                    title, checkingData, SafeStepState.DONE, SafeStepState.RUNNING,
                    accountsChecked = phase.checked, accountsTotal = phase.total,
                    finishing = SafeStepState.WAITING, startedAtElapsed = startedAtElapsed,
                )
                MigrationProgress.Saving,
                MigrationProgress.Finished -> ProgressUi(
                    title, checkingData, SafeStepState.DONE, SafeStepState.DONE,
                    finishing = SafeStepState.RUNNING, startedAtElapsed = startedAtElapsed,
                )
                MigrationProgress.Idle -> error("handled above")
            }
        }
    }
}
