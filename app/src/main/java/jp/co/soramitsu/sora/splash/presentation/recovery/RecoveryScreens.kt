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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.NeumorphicSurface
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AssuranceRow
import jp.co.soramitsu.common.presentation.compose.neon.safemode.PhraseGrid
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReassuranceCallout
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeCard
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeModeScreen
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeSheet
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStep
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeStepState
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafetyPanel
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ShieldEmblem
import jp.co.soramitsu.common.presentation.compose.neon.safemode.StepList
import jp.co.soramitsu.common.presentation.compose.neon.safemode.TechnicalDetail
import jp.co.soramitsu.common.presentation.compose.neon.safemode.TechnicalDetails
import jp.co.soramitsu.common.presentation.compose.neon.safemode.UpgradeProgressContent
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.core_db.SetUpAgainScope
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.delay
import jp.co.soramitsu.common.R as CommonR

/** Test tags for the splash Safe Mode screens. */
object RecoveryTags {
    const val REVEAL = "recoveryReveal"
    const val PIN_DOTS = "recoveryPinDots"
    const val WALLET_LIST = "recoveryWalletList"
    fun action(action: RecoveryAction) = "recoveryAction_${action.name}"
    fun pinKey(digit: Char) = "recoveryPinKey_$digit"
}

/** Every callback of the splash Safe Mode screens. Defaults do nothing (previews, tests). */
class SafeModeCallbacks(
    val onAction: (RecoveryAction) -> Unit = {},
    val onCopyDetail: (TechnicalDetail) -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onConfirmReadOnly: () -> Unit = {},
    val onConfirmRestore: () -> Unit = {},
    val onCreateExport: () -> Unit = {},
    val onEmailSupport: () -> Unit = {},
    val onCopySupport: () -> Unit = {},
    val onGetHelp: () -> Unit = {},
    val onPinDigit: (Char) -> Unit = {},
    val onPinDelete: () -> Unit = {},
    val onBiometric: () -> Unit = {},
    val onRetryAuth: () -> Unit = {},
    val onOpenEntry: (PhraseEntry) -> Unit = {},
    val onReveal: (Boolean) -> Unit = {},
    val onRevealBack: () -> Unit = {},
    val onRevealClose: () -> Unit = {},
)

/**
 * The splash Safe Mode host: the upgrade progress (U1, U5) or the recovery panel (U4, U6), the
 * phrase reveal on top of either, and the sheets. Recovery phrases are drawn only in the
 * activity window (which carries FLAG_SECURE while they show), never in a dialog window.
 */
@Composable
fun SplashSafeMode(
    state: SafeModeUiState,
    details: List<TechnicalDetail>,
    callbacks: SafeModeCallbacks,
    progressElapsedForPreview: Long? = null,
) {
    Box(Modifier.fillMaxSize()) {
        val progress = state.progress
        val panel = state.panel
        when {
            progress != null -> SafeModeScreen {
                UpgradeProgress(progress, callbacks, progressElapsedForPreview)
            }

            panel != null -> SafeModeScreen {
                RecoveryPanel(state, panel, details, callbacks)
            }
        }
        state.reveal?.let { reveal ->
            RevealFlow(reveal, callbacks)
        }
        if (state.reveal == null) {
            when (state.sheet) {
                SafeSheetKind.READ_ONLY -> ReadOnlySheet(state, callbacks)
                SafeSheetKind.RESTORE_PHRASE,
                SafeSheetKind.RESTORE_DRIVE -> RestoreSheet(state, callbacks)
                SafeSheetKind.EXPORT -> ExportSheet(state, callbacks)
                SafeSheetKind.SUPPORT -> SupportSheet(state, details, callbacks)
                null -> Unit
            }
        }
    }
}

@Composable
private fun UpgradeProgress(progress: ProgressUi, callbacks: SafeModeCallbacks, fixedElapsed: Long?) {
    val steps = listOf(
        SafeStep(stringResource(CommonR.string.safe_mode_step_check_data), progress.checkingData),
        SafeStep(stringResource(CommonR.string.safe_mode_step_storage), progress.storage),
        SafeStep(
            label = stringResource(CommonR.string.safe_mode_step_accounts),
            state = progress.accounts,
            detail = if (progress.accounts == SafeStepState.RUNNING && progress.accountsTotal != null) {
                stringResource(
                    CommonR.string.safe_mode_step_accounts_progress,
                    progress.accountsChecked ?: 0,
                    progress.accountsTotal,
                )
            } else {
                null
            },
        ),
        SafeStep(stringResource(CommonR.string.safe_mode_step_finishing), progress.finishing),
    )
    val finishing = progress.title == ProgressTitle.FINISHING
    UpgradeProgressContent(
        title = stringResource(
            if (finishing) CommonR.string.safe_mode_finishing_title else CommonR.string.safe_mode_updating_title,
        ),
        body = stringResource(
            if (finishing) CommonR.string.safe_mode_finishing_body else CommonR.string.safe_mode_updating_body,
        ),
        steps = steps,
        onGetHelp = callbacks.onGetHelp,
        fixedElapsedMillis = fixedElapsed,
    )
}

@Composable
private fun actionLabel(action: RecoveryAction, kind: RecoveryPanelKind): String = stringResource(
    when (action) {
        RecoveryAction.TRY_AGAIN ->
            if (kind == RecoveryPanelKind.STORAGE) R.string.recovery_check_again else R.string.recovery_try_again
        RecoveryAction.RESTORE_PHRASE -> R.string.recovery_restore_phrase
        RecoveryAction.RESTORE_DRIVE -> R.string.recovery_restore_drive
        RecoveryAction.SHOW_PHRASE -> R.string.recovery_show_phrase
        RecoveryAction.READ_ONLY -> R.string.recovery_read_only
        RecoveryAction.EXPORT -> R.string.recovery_export
        RecoveryAction.OPEN_STORAGE -> R.string.recovery_open_storage
        RecoveryAction.CONTACT_SUPPORT ->
            if (kind == RecoveryPanelKind.STORAGE) CommonR.string.safe_mode_get_help else R.string.recovery_support
    },
)

@Composable
private fun RecoveryPanel(
    state: SafeModeUiState,
    model: RecoveryPanelModel,
    details: List<TechnicalDetail>,
    callbacks: SafeModeCallbacks,
) {
    val n = MaterialTheme.neon
    val title = stringResource(
        when (model.kind) {
            RecoveryPanelKind.STORAGE -> R.string.recovery_storage_title
            RecoveryPanelKind.KEYS_UNAVAILABLE -> R.string.recovery_keys_title
            RecoveryPanelKind.RECOVERY, RecoveryPanelKind.TRANSIENT -> R.string.recovery_paused_title
        },
    )
    val body = when (model.kind) {
        RecoveryPanelKind.STORAGE -> model.requiredMegabytes
            ?.let { stringResource(R.string.recovery_storage_body, it.toString()) }
            ?: stringResource(R.string.recovery_storage_body_generic)
        RecoveryPanelKind.KEYS_UNAVAILABLE -> stringResource(R.string.recovery_keys_body)
        RecoveryPanelKind.TRANSIENT -> stringResource(R.string.recovery_transient_body)
        RecoveryPanelKind.RECOVERY -> stringResource(
            if (model.interruptedRemoval) R.string.recovery_removal_body else R.string.recovery_paused_body,
        )
    }
    val running = state.running
    SafetyPanel(
        title = title,
        body = body,
        callout = stringResource(CommonR.string.safe_mode_keep_installed),
        beforeActions = {
            if (model.kind == RecoveryPanelKind.STORAGE) {
                Spacer(Modifier.size(16.dp))
                StepList(
                    listOf(
                        SafeStep(stringResource(CommonR.string.safe_mode_step_check_data), SafeStepState.DONE),
                        SafeStep(
                            label = stringResource(R.string.recovery_storage_step),
                            state = SafeStepState.NEEDS_ACTION,
                            detail = model.missingMegabytes?.let {
                                stringResource(R.string.recovery_storage_step_detail, it.toString())
                            },
                        ),
                        SafeStep(stringResource(R.string.recovery_step_update), SafeStepState.WAITING),
                    ),
                )
            }
        },
    ) {
        SafeAction(
            modifier = Modifier.testTag(RecoveryTags.action(model.primary)),
            text = actionLabel(model.primary, model.kind),
            variant = NeonVariant.Safe,
            enabled = running == null,
            loading = running == model.primary,
            onClick = { callbacks.onAction(model.primary) },
        )
        model.secondary.forEach { action ->
            SafeAction(
                modifier = Modifier.testTag(RecoveryTags.action(action)),
                text = actionLabel(action, model.kind),
                variant = NeonVariant.Secondary,
                enabled = running == null,
                loading = running == action,
                onClick = { callbacks.onAction(action) },
            )
        }
        if (state.autoRetrying || state.runningLong) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                text = stringResource(
                    if (state.runningLong) CommonR.string.safe_mode_still_working else R.string.recovery_auto_retrying,
                ),
                style = MaterialTheme.customTypography.textS,
                color = n.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        model.tertiary.forEach { action ->
            SafeAction(
                modifier = Modifier.testTag(RecoveryTags.action(action)),
                text = actionLabel(action, model.kind),
                variant = NeonVariant.Tertiary,
                // Contacting support stays available while another action runs.
                enabled = running == null || action == RecoveryAction.CONTACT_SUPPORT,
                loading = running == action,
                onClick = { callbacks.onAction(action) },
            )
        }
        TechnicalDetails(items = details, onCopy = callbacks.onCopyDetail)
    }
}

@Composable
private fun SheetText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.customTypography.paragraphM,
        color = MaterialTheme.neon.textSecondary,
    )
}

@Composable
private fun ReadOnlySheet(state: SafeModeUiState, callbacks: SafeModeCallbacks) {
    val running = state.running == RecoveryAction.READ_ONLY
    SafeSheet(
        title = stringResource(R.string.recovery_read_only),
        onClose = callbacks.onCloseSheet,
        dismissEnabled = !running,
        footer = {
            SafeAction(
                text = stringResource(R.string.recovery_read_only_confirm),
                variant = NeonVariant.Safe,
                loading = running,
                onClick = callbacks.onConfirmReadOnly,
            )
            SafeAction(
                text = stringResource(CommonR.string.common_cancel),
                variant = NeonVariant.Tertiary,
                enabled = !running,
                onClick = callbacks.onCloseSheet,
            )
        },
    ) {
        SheetText(stringResource(R.string.recovery_read_only_sheet_body))
    }
}

@Composable
private fun RestoreSheet(state: SafeModeUiState, callbacks: SafeModeCallbacks) {
    val drive = state.sheet == SafeSheetKind.RESTORE_DRIVE
    val running = state.running == RecoveryAction.RESTORE_PHRASE || state.running == RecoveryAction.RESTORE_DRIVE
    val model = state.panel
    SafeSheet(
        title = stringResource(if (drive) R.string.recovery_restore_drive else R.string.recovery_restore_phrase),
        onClose = callbacks.onCloseSheet,
        dismissEnabled = !running,
        footer = {
            SafeAction(
                text = stringResource(R.string.recovery_restore_confirm),
                variant = NeonVariant.Safe,
                loading = running,
                onClick = callbacks.onConfirmRestore,
            )
            SafeAction(
                text = stringResource(CommonR.string.common_cancel),
                variant = NeonVariant.Tertiary,
                enabled = !running,
                onClick = callbacks.onCloseSheet,
            )
        },
    ) {
        SheetText(stringResource(R.string.recovery_restore_sheet_body))
        when (model?.setUpAgainScope) {
            SetUpAgainScope.DATABASE_ONLY -> SheetText(stringResource(R.string.recovery_restore_sheet_keys_kept))
            SetUpAgainScope.ALL_WALLET_FILES -> SheetText(stringResource(R.string.recovery_restore_sheet_keys_unavailable))
            else -> Unit
        }
        if (model?.canShowPhrase == true) {
            ReassuranceCallout(stringResource(R.string.recovery_restore_sheet_write_down))
            SafeAction(
                text = stringResource(R.string.recovery_show_phrase),
                variant = NeonVariant.Secondary,
                enabled = !running,
                onClick = { callbacks.onAction(RecoveryAction.SHOW_PHRASE) },
            )
        }
    }
}

@Composable
private fun ExportSheet(state: SafeModeUiState, callbacks: SafeModeCallbacks) {
    val n = MaterialTheme.neon
    val running = state.running == RecoveryAction.EXPORT
    SafeSheet(
        title = stringResource(R.string.recovery_export),
        onClose = callbacks.onCloseSheet,
        dismissEnabled = !running,
        footer = {
            SafeAction(
                text = stringResource(R.string.recovery_export_create),
                variant = NeonVariant.Safe,
                loading = running,
                onClick = callbacks.onCreateExport,
            )
            if (state.exportCreated) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    text = stringResource(R.string.recovery_export_done),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        },
    ) {
        SheetText(stringResource(R.string.recovery_export_body))
        ChecklistHeading(stringResource(R.string.recovery_export_included))
        AssuranceRow(stringResource(R.string.recovery_export_included_addresses))
        AssuranceRow(stringResource(R.string.recovery_export_included_settings))
        AssuranceRow(stringResource(R.string.recovery_export_included_encrypted))
        ChecklistHeading(stringResource(R.string.recovery_export_never))
        LockedRow(stringResource(R.string.recovery_export_never_secrets))
        LockedRow(stringResource(R.string.recovery_export_never_pin))
        LockedRow(stringResource(R.string.recovery_export_never_keystore))
    }
}

@Composable
private fun ChecklistHeading(text: String) {
    Text(
        modifier = Modifier
            .padding(top = 4.dp)
            .semantics { heading() },
        text = text,
        style = MaterialTheme.customTypography.headline3,
        color = MaterialTheme.neon.textPrimary,
    )
}

/** A "never included" row: a mint lock and primary text. */
@Composable
private fun LockedRow(text: String) {
    val n = MaterialTheme.neon
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(20.dp),
            painter = painterResource(CommonR.drawable.ic_neu_lock),
            tint = n.success,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.customTypography.textM, color = n.textPrimary)
    }
}

@Composable
private fun SupportSheet(
    state: SafeModeUiState,
    details: List<TechnicalDetail>,
    callbacks: SafeModeCallbacks,
) {
    val n = MaterialTheme.neon
    SafeSheet(
        title = stringResource(R.string.recovery_support),
        onClose = callbacks.onCloseSheet,
        footer = {
            SafeAction(
                text = stringResource(R.string.recovery_support_email),
                variant = NeonVariant.Secondary,
                onClick = callbacks.onEmailSupport,
            )
            SafeAction(
                text = stringResource(R.string.recovery_support_copy),
                variant = NeonVariant.Tertiary,
                onClick = callbacks.onCopySupport,
            )
        },
    ) {
        SheetText(stringResource(R.string.recovery_support_body))
        SafeCard {
            details.forEach { item ->
                Text(text = item.label, style = MaterialTheme.customTypography.textXS, color = n.textSecondary)
                Text(text = item.value, style = NeonType.mono, color = n.textPrimary)
                Spacer(Modifier.size(6.dp))
            }
        }
        if (state.supportNoEmail) {
            SelectionContainer {
                Text(
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    text = stringResource(R.string.recovery_support_no_email, SafeModeViewModel.SUPPORT_EMAIL),
                    style = MaterialTheme.customTypography.paragraphS,
                    color = n.textPrimary,
                )
            }
        }
    }
}

// ---- Show my recovery phrase ------------------------------------------------------------------

@Composable
private fun RevealFlow(reveal: RevealUi, callbacks: SafeModeCallbacks) {
    BackHandler { callbacks.onRevealBack() }
    Box(
        Modifier
            .fillMaxSize()
            .testTag(RecoveryTags.REVEAL),
    ) {
        SafeModeScreen {
            when (reveal) {
                is RevealUi.Auth -> AuthStep(reveal, callbacks)
                is RevealUi.Wallets -> WalletsStep(reveal, callbacks)
                is RevealUi.Shown -> ShownStep(reveal, callbacks)
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String?) {
    val n = MaterialTheme.neon
    ShieldEmblem(modifier = Modifier.fillMaxWidth(), size = 56.dp)
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { heading() },
        text = title,
        style = MaterialTheme.customTypography.headline1,
        color = n.textPrimary,
        textAlign = TextAlign.Center,
    )
    if (body != null) {
        Spacer(Modifier.size(8.dp))
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = body,
            style = MaterialTheme.customTypography.paragraphM,
            color = n.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ColumnScope.AuthStep(auth: RevealUi.Auth, callbacks: SafeModeCallbacks) {
    val n = MaterialTheme.neon
    StepHeader(
        title = stringResource(R.string.recovery_auth_title),
        body = stringResource(if (auth.noPin) R.string.recovery_auth_no_pin else R.string.recovery_auth_body),
    )
    Spacer(Modifier.size(24.dp))
    if (!auth.noPin && auth.error != AuthError.UNAVAILABLE) {
        PinDots(total = auth.pinLength, entered = auth.entered)
    }
    val error = when (auth.error) {
        AuthError.WRONG -> stringResource(R.string.recovery_auth_wrong)
        AuthError.LOCKED -> stringResource(R.string.recovery_auth_locked, formatLock(auth.lockedForMillis))
        AuthError.UNAVAILABLE -> stringResource(R.string.recovery_auth_unavailable)
        AuthError.NO_SCREEN_LOCK -> stringResource(R.string.recovery_auth_no_screen_lock)
        null -> null
    }
    if (error != null) {
        Spacer(Modifier.size(12.dp))
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
            text = error,
            style = MaterialTheme.customTypography.textS,
            color = n.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.size(16.dp))
    when {
        auth.loading -> Unit
        auth.error == AuthError.UNAVAILABLE -> SafeAction(
            text = stringResource(R.string.recovery_try_again),
            variant = NeonVariant.Safe,
            onClick = callbacks.onRetryAuth,
        )
        auth.noPin -> if (auth.error == null) {
            SafeAction(
                text = stringResource(R.string.recovery_auth_screen_lock),
                variant = NeonVariant.Safe,
                onClick = callbacks.onBiometric,
            )
        }
        else -> PinPad(
            enabled = !auth.checking && auth.error != AuthError.LOCKED,
            biometric = auth.biometric,
            onDigit = callbacks.onPinDigit,
            onDelete = callbacks.onPinDelete,
            onBiometric = callbacks.onBiometric,
        )
    }
    Spacer(Modifier.size(8.dp))
    SafeAction(
        text = stringResource(CommonR.string.common_cancel),
        variant = NeonVariant.Tertiary,
        onClick = callbacks.onRevealClose,
    )
}

private fun formatLock(millis: Long): String {
    val seconds = ((millis + 999) / 1_000).coerceAtLeast(1)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun PinDots(total: Int, entered: Int) {
    val n = MaterialTheme.neon
    val description = stringResource(R.string.recovery_pin_progress, entered, total)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecoveryTags.PIN_DOTS)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { index ->
            val filled = index < entered
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) n.cyan else n.surfaceVariant)
                    .border(1.5.dp, if (filled) n.cyan else n.borderInput, CircleShape),
            )
        }
    }
}

@Composable
private fun PinPad(
    enabled: Boolean,
    biometric: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
) {
    // Digits keep their positions in RTL locales (design spec 7.9).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { digit -> PinKey(digit.toString(), enabled, Modifier.testTag(RecoveryTags.pinKey(digit))) { onDigit(digit) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (biometric) {
                    PinKey(
                        label = null,
                        enabled = enabled,
                        description = stringResource(R.string.recovery_auth_biometric),
                        icon = CommonR.drawable.ic_pin_biometric,
                        onClick = onBiometric,
                    )
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                PinKey("0", enabled, Modifier.testTag(RecoveryTags.pinKey('0'))) { onDigit('0') }
                PinKey(
                    label = null,
                    enabled = enabled,
                    description = stringResource(R.string.recovery_pin_delete),
                    icon = CommonR.drawable.ic_neu_chevron_back,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Int? = null,
    onClick: () -> Unit,
) {
    val n = MaterialTheme.neon
    val interaction = remember { MutableInteractionSource() }
    NeumorphicSurface(
        shape = CircleShape,
        interactionSource = interaction,
        modifier = modifier
            .size(72.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (description != null) contentDescription = description },
    ) {
        if (label != null) {
            Text(text = label, style = NeonType.pinDigit, color = if (enabled) n.textPrimary else n.textDisabled)
        } else if (icon != null) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(icon),
                tint = if (enabled) n.textPrimary else n.textDisabled,
                contentDescription = null,
            )
        }
    }
}

@Composable
private fun ColumnScope.WalletsStep(step: RevealUi.Wallets, callbacks: SafeModeCallbacks) {
    val n = MaterialTheme.neon
    StepHeader(
        title = stringResource(R.string.recovery_wallets_title),
        body = stringResource(if (step.entries.isEmpty()) R.string.recovery_phrase_none else R.string.recovery_wallets_body),
    )
    Spacer(Modifier.size(16.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(RecoveryTags.WALLET_LIST),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        step.entries.forEach { entry ->
            val shape = RoundedCornerShape(16.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(n.surface)
                    .border(1.dp, n.hairline, shape)
                    .clickable(enabled = step.loading == null, role = Role.Button) { callbacks.onOpenEntry(entry) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = when {
                        entry.olderVersion -> stringResource(R.string.recovery_wallet_older)
                        else -> entry.name ?: stringResource(R.string.recovery_wallet_unnamed)
                    },
                    style = MaterialTheme.customTypography.textMBold,
                    color = n.textPrimary,
                )
                entry.address?.let {
                    Text(
                        text = shortAddress(it),
                        style = NeonType.mono,
                        color = n.textSecondary,
                    )
                }
            }
        }
    }
    Spacer(Modifier.size(16.dp))
    SafeAction(
        text = stringResource(R.string.recovery_done),
        variant = NeonVariant.Tertiary,
        onClick = callbacks.onRevealClose,
    )
}

private fun shortAddress(address: String): String =
    if (address.length <= 16) address else "${address.take(8)}…${address.takeLast(8)}"

@Composable
private fun ColumnScope.ShownStep(step: RevealUi.Shown, callbacks: SafeModeCallbacks) {
    val n = MaterialTheme.neon
    // The veil returns after 60 s without interaction (design spec 7.18).
    LaunchedEffect(step.revealed) {
        if (step.revealed) {
            delay(60_000)
            callbacks.onReveal(false)
        }
    }
    val secret = step.secret
    val title = when {
        secret is RevealedSecret.Seed -> stringResource(R.string.recovery_seed_title)
        step.entry.olderVersion -> stringResource(R.string.recovery_wallet_older)
        else -> stringResource(R.string.recovery_phrase_title)
    }
    StepHeader(title = title, body = step.entry.name ?: step.entry.address?.let(::shortAddress))
    Spacer(Modifier.size(16.dp))
    when (secret) {
        is RevealedSecret.Phrase -> {
            ReassuranceCallout(stringResource(R.string.recovery_phrase_warning))
            Spacer(Modifier.size(16.dp))
            PhraseGrid(
                words = secret.words,
                revealed = step.revealed,
                onReveal = { callbacks.onReveal(true) },
            )
        }
        is RevealedSecret.Seed -> {
            ReassuranceCallout(stringResource(R.string.recovery_seed_warning))
            Spacer(Modifier.size(16.dp))
            SeedBox(secret.value, step.revealed) { callbacks.onReveal(true) }
        }
        RevealedSecret.None -> SheetText(stringResource(R.string.recovery_phrase_none))
        RevealedSecret.Unavailable -> SheetText(stringResource(R.string.recovery_phrase_unavailable))
    }
    Spacer(Modifier.size(16.dp))
    if (step.canGoBack) {
        SafeAction(
            text = stringResource(R.string.recovery_back),
            variant = NeonVariant.Secondary,
            onClick = callbacks.onRevealBack,
        )
    }
    SafeAction(
        text = stringResource(R.string.recovery_done),
        variant = NeonVariant.Tertiary,
        onClick = callbacks.onRevealClose,
    )
}

@Composable
private fun SeedBox(seed: String, revealed: Boolean, onReveal: () -> Unit) {
    val n = MaterialTheme.neon
    val shape = RoundedCornerShape(12.dp)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(shape)
                .background(n.glassSolid)
                .border(1.dp, n.hairline, shape)
                .then(if (revealed) Modifier else Modifier.clickable(role = Role.Button, onClick = onReveal))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (revealed) {
                Text(text = seed, style = NeonType.mono, color = n.textPrimary)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(CommonR.string.safe_mode_tap_to_reveal),
                        style = MaterialTheme.customTypography.textMBold,
                        color = n.textPrimary,
                    )
                    Text(
                        text = stringResource(CommonR.string.safe_mode_reveal_privacy),
                        style = MaterialTheme.customTypography.textS,
                        color = n.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
