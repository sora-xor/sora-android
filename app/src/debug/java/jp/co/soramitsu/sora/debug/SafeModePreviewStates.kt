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

package jp.co.soramitsu.sora.debug

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountCheckProgress
import jp.co.soramitsu.common.presentation.compose.neon.safemode.DeviceTransferNotice
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LegacyPinUpgradePanel
import jp.co.soramitsu.common.presentation.compose.neon.safemode.PendingCheckCard
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyBanner
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyLearnMoreSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyReason
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeScreen
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStepState
import jp.co.soramitsu.common.presentation.compose.neon.safemode.TechnicalDetail
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteChecks
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeCompleteSheet
import jp.co.soramitsu.core_db.RecoveryFacts
import jp.co.soramitsu.core_db.WalletRecoveryOptions
import jp.co.soramitsu.sora.BuildConfig
import jp.co.soramitsu.sora.splash.presentation.recovery.AuthError
import jp.co.soramitsu.sora.splash.presentation.recovery.PhraseEntry
import jp.co.soramitsu.sora.splash.presentation.recovery.ProgressTitle
import jp.co.soramitsu.sora.splash.presentation.recovery.ProgressUi
import jp.co.soramitsu.sora.splash.presentation.recovery.RecoveryPanels
import jp.co.soramitsu.sora.splash.presentation.recovery.RevealUi
import jp.co.soramitsu.sora.splash.presentation.recovery.RevealedSecret
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeCallbacks
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeModeUiState
import jp.co.soramitsu.sora.splash.presentation.recovery.SafeSheetKind
import jp.co.soramitsu.sora.splash.presentation.recovery.SheetFailure
import jp.co.soramitsu.sora.splash.presentation.recovery.SplashSafeMode

/**
 * Every Safe Mode state (design spec 9.2) with sample data, for the debug preview screen, the
 * QA screenshots and the device tests. Nothing here touches wallet data.
 */
enum class SafeModePreviewState {
    U1_UPDATING,
    U1_STILL_WORKING,
    U1_GET_HELP,
    U1_PIN_CHECK,
    U1_PIN_CHECK_OFFLINE,
    U2_WAITING_FOR_NETWORK,
    U4_FREE_SPACE,
    U5_FINISHING,
    U6_RECOVERY,
    U6_TRANSIENT,
    U6_KEYS_UNAVAILABLE,
    U6_REMOVAL_INTERRUPTED,
    U6_RESTORE_PENDING,
    U6_READ_ONLY_SHEET,
    U6_RESTORE_SHEET,
    U6_EXPORT_SHEET,
    U6_SUPPORT_SHEET,
    U6_READ_ONLY_UNAVAILABLE,
    U6_EXPORT_FAILED,
    U6_RESTORE_FAILED,
    U6_REVEAL_PIN,
    U6_REVEAL_WRONG_PIN,
    U6_REVEAL_WALLETS,
    U6_REVEAL_VEILED,
    U6_REVEAL_SHOWN,
    U6_REVEAL_UNCHECKED,
    U6_REVEAL_OTHER_WALLET,
    U6_REVEAL_UNREADABLE,
    U9_READ_ONLY_HOME,
    U9_LEARN_MORE,
    U10_NEW_PIN,
    U11_UPGRADE_COMPLETE,
    U11_UPGRADE_COMPLETE_BACKED_UP,
    U12_DEVICE_TRANSFER,
    U13_PENDING_CHECK,
}

object SafeModePreviewData {

    /** A well-known test phrase (BIP-39 test vector); never a real wallet. */
    val samplePhrase = (
        "abandon ability able about above absent absorb abstract absurd abuse access accident"
        ).split(' ')

    private val facts = RecoveryFacts(
        hasWalletFiles = true,
        hasRecoveryPhraseOrSeed = true,
        databaseAtCurrentVersion = true,
        accountsReadableReadOnly = true,
    )

    fun options(code: String, transient: Boolean = false, f: RecoveryFacts = facts) =
        WalletRecoveryOptions.forState(code = code, transientFailure = transient, facts = f)

    val recovery = options("WALLET_MIGRATION_RECOVERY_REQUIRED")
    val transient = options("KEYSTORE_UNAVAILABLE", transient = true)
    val keysUnavailable = options("KEYSTORE_KEY_UNAVAILABLE")
    val removal = options("WALLET_DELETION_JOURNAL_INVALID")
    val storage = WalletRecoveryOptions.forState(
        code = "INSUFFICIENT_BACKUP_STORAGE",
        transientFailure = false,
        facts = facts.copy(accountsReadableReadOnly = false, databaseAtCurrentVersion = false),
        requiredBytes = 120L * 1_024 * 1_024,
        availableBytes = 40L * 1_024 * 1_024,
    )

    /** The start that should finish a confirmed restore could not set the old files aside. */
    val restorePending = WalletRecoveryOptions.forState(
        code = "SET_UP_AGAIN_INCOMPLETE",
        transientFailure = false,
        facts = RecoveryFacts(hasWalletFiles = true),
    )

    fun panelState(
        options: WalletRecoveryOptions,
        driveAvailable: Boolean = true,
        restorePending: Boolean = false,
    ) = SafeModeUiState(
        visible = true,
        panel = RecoveryPanels.model(options, driveAvailable, restorePending),
        shownAtMillis = 1_791_590_400_000L,
    )

    fun progress(title: ProgressTitle, checkingDone: Boolean = true) = SafeModeUiState(
        visible = true,
        progress = ProgressUi(
            title = title,
            checkingData = if (checkingDone) SafeStepState.DONE else SafeStepState.RUNNING,
            storage = if (checkingDone) SafeStepState.DONE else SafeStepState.WAITING,
            accounts = if (checkingDone) SafeStepState.RUNNING else SafeStepState.WAITING,
            accountsChecked = if (checkingDone) 2 else null,
            accountsTotal = if (checkingDone) 3 else null,
            finishing = SafeStepState.WAITING,
            startedAtElapsed = 0,
        ),
    )

    val details = listOf(
        TechnicalDetail("Code", "WALLET_MIGRATION_RECOVERY_REQUIRED"),
        TechnicalDetail("App version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"),
        TechnicalDetail("Time", "10 Oct 2026, 10:00:00"),
        TechnicalDetail("Android version", "16 (API 36)"),
    )

    private val entries = listOf(
        PhraseEntry("cnVko1", "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm", "Main", false),
        PhraseEntry("cnTQ1", "cnTQ1kbv7PBNNQrEb1tZpmK7hZjk7SWsrCnjYJ3SbEZVTjyBP", "Savings", false),
        PhraseEntry("older", null, null, true),
    )

    fun state(preview: SafeModePreviewState): SafeModeUiState = when (preview) {
        SafeModePreviewState.U1_UPDATING,
        SafeModePreviewState.U1_STILL_WORKING,
        SafeModePreviewState.U1_GET_HELP -> progress(ProgressTitle.UPDATING)
        SafeModePreviewState.U5_FINISHING -> progress(ProgressTitle.FINISHING, checkingDone = false)
        SafeModePreviewState.U4_FREE_SPACE -> panelState(storage)
        SafeModePreviewState.U6_RECOVERY -> panelState(recovery)
        SafeModePreviewState.U6_TRANSIENT -> panelState(transient).copy(autoRetrying = true, running = null)
        SafeModePreviewState.U6_KEYS_UNAVAILABLE -> panelState(keysUnavailable)
        SafeModePreviewState.U6_REMOVAL_INTERRUPTED -> panelState(removal)
        SafeModePreviewState.U6_RESTORE_PENDING -> panelState(restorePending, restorePending = true)
        SafeModePreviewState.U6_READ_ONLY_UNAVAILABLE -> panelState(recovery).copy(
            sheet = SafeSheetKind.READ_ONLY,
            sheetFailure = SheetFailure.READ_ONLY_UNAVAILABLE,
        )
        SafeModePreviewState.U6_EXPORT_FAILED -> panelState(recovery).copy(
            sheet = SafeSheetKind.EXPORT,
            sheetFailure = SheetFailure.EXPORT_FAILED,
        )
        SafeModePreviewState.U6_RESTORE_FAILED -> panelState(recovery).copy(
            sheet = SafeSheetKind.RESTORE_PHRASE,
            sheetFailure = SheetFailure.RESTORE_FAILED,
        )
        SafeModePreviewState.U6_READ_ONLY_SHEET -> panelState(recovery).copy(sheet = SafeSheetKind.READ_ONLY)
        SafeModePreviewState.U6_RESTORE_SHEET -> panelState(recovery).copy(sheet = SafeSheetKind.RESTORE_PHRASE)
        SafeModePreviewState.U6_EXPORT_SHEET -> panelState(recovery).copy(sheet = SafeSheetKind.EXPORT, exportCreated = true)
        SafeModePreviewState.U6_SUPPORT_SHEET -> panelState(recovery).copy(sheet = SafeSheetKind.SUPPORT, supportNoEmail = true)
        SafeModePreviewState.U6_REVEAL_PIN -> panelState(recovery).copy(
            reveal = RevealUi.Auth(loading = false, pinLength = 6, entered = 2, biometric = true),
        )
        SafeModePreviewState.U6_REVEAL_WRONG_PIN -> panelState(recovery).copy(
            reveal = RevealUi.Auth(loading = false, pinLength = 6, entered = 0, error = AuthError.WRONG),
        )
        SafeModePreviewState.U6_REVEAL_WALLETS -> panelState(recovery).copy(reveal = RevealUi.Wallets(entries))
        SafeModePreviewState.U6_REVEAL_VEILED -> panelState(recovery).copy(
            reveal = RevealUi.Shown(entries[0], RevealedSecret.Phrase(samplePhrase), revealed = false, canGoBack = true),
        )
        SafeModePreviewState.U6_REVEAL_SHOWN -> panelState(recovery).copy(
            reveal = RevealUi.Shown(entries[0], RevealedSecret.Phrase(samplePhrase), revealed = true, canGoBack = true),
        )
        SafeModePreviewState.U6_REVEAL_UNCHECKED -> panelState(recovery).copy(
            reveal = RevealUi.Shown(
                entries[0],
                RevealedSecret.Phrase(samplePhrase, checked = false),
                revealed = false,
                canGoBack = true,
            ),
        )
        SafeModePreviewState.U6_REVEAL_OTHER_WALLET -> panelState(recovery).copy(
            reveal = RevealUi.Shown(
                entries[0],
                RevealedSecret.Phrase(samplePhrase, checked = false, otherWallet = true),
                revealed = false,
                canGoBack = true,
            ),
        )
        SafeModePreviewState.U6_REVEAL_UNREADABLE -> panelState(recovery).copy(
            reveal = RevealUi.Shown(entries[1], RevealedSecret.Unreadable, canGoBack = true),
        )
        else -> SafeModeUiState()
    }
}

/** Renders one preview state full screen. */
@Composable
fun SafeModePreview(preview: SafeModePreviewState, callbacks: SafeModeCallbacks = SafeModeCallbacks()) {
    when (preview) {
        SafeModePreviewState.U1_PIN_CHECK -> AccountCheckProgress(
            connected = true,
            onSwitchNode = {},
            onGetHelp = {},
            silentMillis = 0,
            fixedElapsedMillis = 0,
        )
        // Offline, but this account is checked on the phone: no network copy (design spec 9.1-2).
        SafeModePreviewState.U1_PIN_CHECK_OFFLINE -> AccountCheckProgress(
            connected = false,
            needsNetwork = false,
            onSwitchNode = {},
            onGetHelp = {},
            silentMillis = 0,
            fixedElapsedMillis = 0,
        )
        SafeModePreviewState.U2_WAITING_FOR_NETWORK -> AccountCheckProgress(
            connected = false,
            onSwitchNode = {},
            onGetHelp = {},
            fixedElapsedMillis = 0,
        )
        SafeModePreviewState.U9_READ_ONLY_HOME,
        SafeModePreviewState.U9_LEARN_MORE -> SafeModeScreen {
            ReadOnlyBanner(onTryAgain = {}, onLearnMore = {})
            Spacer(Modifier.size(24.dp))
            ReadOnlyReason()
            if (preview == SafeModePreviewState.U9_LEARN_MORE) {
                ReadOnlyLearnMoreSheet(onTryAgain = {}, onClose = {})
            }
        }
        SafeModePreviewState.U10_NEW_PIN -> SafeModeScreen { LegacyPinUpgradePanel(onContinue = {}) }
        SafeModePreviewState.U11_UPGRADE_COMPLETE,
        SafeModePreviewState.U11_UPGRADE_COMPLETE_BACKED_UP -> {
            SafeModeScreen { }
            UpgradeCompleteSheet(
                checks = UpgradeCompleteChecks(
                    accountsOnDevice = true,
                    protectedByPin = true,
                    balancesLoaded = true,
                    needsBackup = preview == SafeModePreviewState.U11_UPGRADE_COMPLETE,
                ),
                onBackUpNow = {},
                onClose = {},
            )
        }
        SafeModePreviewState.U12_DEVICE_TRANSFER -> SafeModeScreen { DeviceTransferNotice() }
        SafeModePreviewState.U13_PENDING_CHECK -> SafeModeScreen { PendingCheckCard(onOptions = {}) }
        else -> SplashSafeMode(
            state = SafeModePreviewData.state(preview),
            details = SafeModePreviewData.details,
            callbacks = callbacks,
            progressElapsedForPreview = when (preview) {
                SafeModePreviewState.U1_STILL_WORKING -> 15_000L
                SafeModePreviewState.U1_GET_HELP -> 65_000L
                else -> 0L
            },
        )
    }
}
