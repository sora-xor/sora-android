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

package jp.co.soramitsu.sora.splash.presentation.recovery

import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.core_db.WalletRecoveryKind
import jp.co.soramitsu.core_db.WalletRecoveryOptions

/** Every action the recovery screen can offer (design spec 9.2 U4, U6). */
enum class RecoveryAction {
    CANCEL_RESTORE,
    TRY_AGAIN,
    RESTORE_PHRASE,
    RESTORE_DRIVE,
    SHOW_PHRASE,
    READ_ONLY,
    EXPORT,
    OPEN_STORAGE,
    CONTACT_SUPPORT,
}

/** Which title and body the panel shows. Each wording is true for its states only. */
enum class RecoveryPanelKind {
    /** U6: an interrupted or failed check, or stored data the app does not expect. */
    RECOVERY,

    /** U6 variant: a busy Keystore, I/O or a locked database; it usually passes on its own. */
    TRANSIENT,

    /** U6 variant: this phone cannot unlock the stored keys (for example after a transfer). */
    KEYS_UNAVAILABLE,

    /** U4: not enough free space to finish the upgrade safely. */
    STORAGE,

    /**
     * U6 variant: a restore the person confirmed ("Set up this device again") waits for the app
     * to restart, or the last start could not finish setting the old files aside. Try again
     * restarts the app; it never retries by itself.
     */
    RESTORE_PENDING,
}

/**
 * What the recovery screen shows for one state: the actions in the design spec 9.2 order, each
 * offered only when its flag in [WalletRecoveryOptions] says it works here.
 */
data class RecoveryPanelModel(
    val kind: RecoveryPanelKind,
    val code: String,
    val primary: RecoveryAction,
    val secondary: List<RecoveryAction>,
    val tertiary: List<RecoveryAction>,
    /** U4: free space needed in MB (rounded up), when known. */
    val requiredMegabytes: Long? = null,
    /** U4: how much more space is needed in MB (rounded up), when known. */
    val missingMegabytes: Long? = null,
    val retryAutomatically: Boolean = false,
    val setUpAgainScope: SetUpAgainScope = SetUpAgainScope.NONE,
    val canShowPhrase: Boolean = false,
    /** A removal the person confirmed was interrupted (its journal needs attention). */
    val interruptedRemoval: Boolean = false,
) {
    /** Every action on screen, in order. */
    val actions: List<RecoveryAction>
        get() = listOf(primary) + secondary + tertiary

    /** Actions that move the person forward other than trying the same thing again. */
    val forwardActionsBesidesRetry: List<RecoveryAction>
        get() = actions.filter { it != RecoveryAction.TRY_AGAIN }
}

object RecoveryPanels {

    private const val MEBIBYTE = 1_024L * 1_024L

    /**
     * [restorePending]: a confirmed "Set up this device again" waits for a new app process; the
     * panel then offers no restore (one is already on its way) and no read-only access (the old
     * files are about to be set aside), and Try again restarts the app.
     */
    fun model(
        options: WalletRecoveryOptions,
        driveAvailable: Boolean,
        restorePending: Boolean = false,
    ): RecoveryPanelModel {
        val kind = when {
            restorePending -> RecoveryPanelKind.RESTORE_PENDING
            else -> when (options.kind) {
                WalletRecoveryKind.STORAGE -> RecoveryPanelKind.STORAGE
                WalletRecoveryKind.TRANSIENT -> RecoveryPanelKind.TRANSIENT
                WalletRecoveryKind.KEYS_UNAVAILABLE -> RecoveryPanelKind.KEYS_UNAVAILABLE
                WalletRecoveryKind.MIGRATION,
                WalletRecoveryKind.INTEGRITY -> RecoveryPanelKind.RECOVERY
            }
        }
        val secondary = buildList {
            if (kind == RecoveryPanelKind.RESTORE_PENDING) add(RecoveryAction.CANCEL_RESTORE)
            if (kind != RecoveryPanelKind.STORAGE && kind != RecoveryPanelKind.RESTORE_PENDING) {
                if (options.canRestoreFromPhrase) add(RecoveryAction.RESTORE_PHRASE)
                if (options.canRestoreFromDrive && driveAvailable) add(RecoveryAction.RESTORE_DRIVE)
            }
        }
        val tertiary = buildList {
            if (kind == RecoveryPanelKind.STORAGE) add(RecoveryAction.OPEN_STORAGE)
            if (options.canShowPhraseAfterPin) add(RecoveryAction.SHOW_PHRASE)
            if (options.canReadOnly && kind != RecoveryPanelKind.RESTORE_PENDING) add(RecoveryAction.READ_ONLY)
            // An export needs free space as well, so it is not offered while space is short.
            if (options.canExport && kind != RecoveryPanelKind.STORAGE) add(RecoveryAction.EXPORT)
            // Always offered: contacting support works in every state (crash-safe, see
            // SplashActivity.contactSupport).
            add(RecoveryAction.CONTACT_SUPPORT)
        }
        val required = options.requiredBytes?.takeIf { it > 0 }
        val available = options.availableBytes?.takeIf { it >= 0 }
        return RecoveryPanelModel(
            kind = kind,
            code = options.code,
            primary = RecoveryAction.TRY_AGAIN,
            secondary = secondary,
            tertiary = tertiary,
            requiredMegabytes = required?.let(::ceilMegabytes),
            missingMegabytes = if (required != null && available != null && required > available) {
                ceilMegabytes(required - available)
            } else {
                null
            },
            retryAutomatically = options.retryAutomatically && kind != RecoveryPanelKind.RESTORE_PENDING,
            setUpAgainScope = if (kind == RecoveryPanelKind.RESTORE_PENDING) SetUpAgainScope.NONE else options.setUpAgainScope,
            canShowPhrase = options.canShowPhraseAfterPin,
            interruptedRemoval = options.code.startsWith("WALLET_DELETION_"),
        )
    }

    private fun ceilMegabytes(bytes: Long): Long = (bytes + MEBIBYTE - 1) / MEBIBYTE
}
