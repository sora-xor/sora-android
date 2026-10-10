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

package jp.co.soramitsu.common.presentation.compose.neon.safemode

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.neonBorder
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * True while wallet changes and signing are off (read-only or recovery mode). Display only: the
 * signer itself always calls `WalletRecoveryCapabilityGate.requireUserMutationAllowed()`.
 */
@Composable
fun rememberWalletReadOnly(): Boolean {
    val mode by WalletRecoveryCapabilityGate.modeFlow.collectAsState()
    return WalletRecoveryCapabilityGate.isUserMutationBlocked(mode)
}

/**
 * Whether the wallet was unlocked with the PIN in this process. The upgrade-complete sheet (U11)
 * waits for it, so it is never stacked over the PIN screen.
 */
object WalletSessionState {
    private val unlockedState = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = unlockedState.asStateFlow()

    fun markUnlocked() {
        unlockedState.value = true
    }

    fun markLocked() {
        unlockedState.value = false
    }
}

/**
 * The upgrade-complete sheet (U11) is shown once per install, only to people who updated from an
 * earlier version. Kept in its own small preferences file, never in the wallet preferences.
 */
object UpgradeWelcome {
    private const val FILE = "sora_upgrade_welcome"
    private const val KEY_DONE = "upgrade_welcome_3_9_done"

    /** True when this install was updated from an earlier version and the sheet was not shown. */
    fun isPending(context: Context): Boolean = runCatching {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return@runCatching false
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        // A fresh install has equal times; an update from an earlier version does not.
        info.firstInstallTime != info.lastUpdateTime
    }.getOrDefault(false)

    /** Shown, or not needed (a new wallet set up on this version). */
    fun markDone(context: Context) {
        runCatching {
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
        }
    }
}

/** Contacting support from Safe Mode surfaces. Never crashes without an email app. */
object SafeModeSupport {
    const val EMAIL = "support@sora.org"

    /** Opens an email to support; false when no email app can take it. */
    fun email(context: Context, subject: String, body: String): Boolean {
        val mailto = "mailto:$EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
        return try {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse(mailto)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

/**
 * Opens the upgrade check again from anywhere in the app (U9 "Try upgrade again"): the launcher
 * screen starts in a fresh task and runs Try again with the progress screen. Nothing is deleted;
 * it is the same action as Try again on the recovery screen.
 */
object WalletUpgradeRetry {
    const val EXTRA_RETRY_UPGRADE = "jp.co.soramitsu.sora.extra.RETRY_WALLET_UPGRADE"

    fun launch(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        launch.putExtra(EXTRA_RETRY_UPGRADE, true)
        context.startActivity(launch)
    }
}

/** The "Read-only" chip (design spec 7.6): steel outline, no warning colour. */
@Composable
fun ReadOnlyChip(modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(n.infoContainer)
            .border(1.dp, n.steel, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(14.dp),
            painter = painterResource(R.drawable.ic_neu_lock),
            tint = n.textPrimary,
            contentDescription = null,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.safe_mode_read_only_chip),
            style = MaterialTheme.customTypography.textXSBold,
            color = n.textPrimary,
        )
    }
}

/**
 * U9 (design spec 9.2): the read-only chip and an info banner on the wallet home. Balances and
 * history stay visible; the actions are "Try upgrade again" and "Learn more".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadOnlyBanner(
    onTryAgain: () -> Unit,
    onLearnMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(n.surface)
            .neonBorder(shape, n.borderSafe, fx)
            .padding(20.dp)
            .testTag(SafeModeTags.READ_ONLY_BANNER),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ShieldEmblem(size = 40.dp)
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                ReadOnlyChip()
                Spacer(Modifier.size(6.dp))
                Text(
                    text = stringResource(R.string.safe_mode_read_only_title),
                    style = MaterialTheme.customTypography.headline2,
                    color = n.textPrimary,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.safe_mode_read_only_body),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textSecondary,
        )
        Spacer(Modifier.size(12.dp))
        // Wraps to a second line when both labels do not fit (long translations, large fonts).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            NeonButton(
                size = ButtonSize.Small,
                variant = NeonVariant.Safe,
                text = stringResource(R.string.safe_mode_try_upgrade_again),
                maxLines = 2,
                onClick = onTryAgain,
            )
            NeonButton(
                size = ButtonSize.Small,
                variant = NeonVariant.Tertiary,
                text = stringResource(R.string.safe_mode_learn_more),
                maxLines = 2,
                onClick = onLearnMore,
            )
        }
    }
}

/** The "Learn more" sheet behind [ReadOnlyBanner]. */
@Composable
fun ReadOnlyLearnMoreSheet(
    onTryAgain: () -> Unit,
    onClose: () -> Unit,
) {
    val n = MaterialTheme.neon
    SafeSheet(
        title = stringResource(R.string.safe_mode_read_only_title),
        onClose = onClose,
        footer = {
            SafeAction(
                text = stringResource(R.string.safe_mode_try_upgrade_again),
                variant = NeonVariant.Safe,
                onClick = onTryAgain,
            )
        },
    ) {
        Text(
            text = stringResource(R.string.safe_mode_read_only_details),
            style = MaterialTheme.customTypography.paragraphM,
            color = n.textSecondary,
        )
        ReassuranceCallout(stringResource(R.string.safe_mode_keep_installed))
    }
}

/**
 * The reason a money action is off in read-only mode, shown next to a disabled Send or Swap
 * (design spec 9.2 U9: "Sending is off in read-only mode").
 */
@Composable
fun ReadOnlyReason(modifier: Modifier = Modifier, textAlign: TextAlign = TextAlign.Start) {
    val n = MaterialTheme.neon
    Row(
        modifier = modifier
            .testTag(SafeModeTags.READ_ONLY_REASON)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(R.drawable.ic_neu_lock),
            tint = n.textSecondary,
            contentDescription = null,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.safe_mode_read_only_send_off),
            style = MaterialTheme.customTypography.textS,
            color = n.textSecondary,
            textAlign = textAlign,
        )
    }
}

/**
 * U10 (design spec 9.2): after unlocking with a 4-digit PIN, before choosing the new 6-digit one.
 */
@Composable
fun LegacyPinUpgradePanel(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    SafetyPanel(
        modifier = modifier,
        title = stringResource(R.string.safe_mode_new_pin_title),
        body = stringResource(R.string.safe_mode_new_pin_body),
    ) {
        SafeAction(
            text = stringResource(R.string.common_continue),
            variant = NeonVariant.Safe,
            onClick = onContinue,
        )
    }
}

/**
 * The real checks behind the upgrade-complete sheet (U11). A row shows ✓ only when its check is
 * true; [balancesLoaded] false shows a running row ("Loading balances") instead.
 */
data class UpgradeCompleteChecks(
    val accountsOnDevice: Boolean,
    val protectedByPin: Boolean,
    val balancesLoaded: Boolean,
    val needsBackup: Boolean,
)

/**
 * U11 (design spec 9.2, 9.1-7): shown once, only after a successful upgrade, never over the PIN
 * and never in read-only mode (the caller decides when).
 */
@Composable
fun UpgradeCompleteSheet(
    checks: UpgradeCompleteChecks,
    onBackUpNow: () -> Unit,
    onClose: () -> Unit,
) {
    val n = MaterialTheme.neon
    SafeSheet(
        title = stringResource(R.string.safe_mode_whats_new_title),
        onClose = onClose,
        footer = {
            if (checks.needsBackup) {
                SafeAction(
                    text = stringResource(R.string.safe_mode_back_up_now),
                    variant = NeonVariant.Safe,
                    onClick = onBackUpNow,
                )
                SafeAction(
                    text = stringResource(R.string.safe_mode_later),
                    variant = NeonVariant.Tertiary,
                    onClick = onClose,
                )
            } else {
                SafeAction(
                    text = stringResource(R.string.common_continue),
                    variant = NeonVariant.Safe,
                    onClick = onClose,
                )
            }
        },
    ) {
        Text(
            text = stringResource(R.string.safe_mode_whats_new_body),
            style = MaterialTheme.customTypography.paragraphM,
            color = n.textSecondary,
        )
        val steps = buildList {
            if (checks.accountsOnDevice) {
                add(SafeStep(stringResource(R.string.safe_mode_whats_new_accounts), SafeStepState.DONE))
            }
            if (checks.protectedByPin) {
                add(SafeStep(stringResource(R.string.safe_mode_whats_new_pin), SafeStepState.DONE))
            }
            add(
                if (checks.balancesLoaded) {
                    SafeStep(stringResource(R.string.safe_mode_whats_new_balances), SafeStepState.DONE)
                } else {
                    SafeStep(stringResource(R.string.safe_mode_whats_new_balances_loading), SafeStepState.RUNNING)
                },
            )
            if (checks.needsBackup) {
                add(SafeStep(stringResource(R.string.safe_mode_whats_new_backup), SafeStepState.NEEDS_ACTION))
            }
        }
        StepList(steps)
    }
}

/**
 * U12 (design spec 9.2): app data is excluded from cloud backup and device transfer
 * (`allowBackup=false`, `deny_data_extraction.xml`), so the wallet stays on this phone.
 */
@Composable
fun DeviceTransferNotice(modifier: Modifier = Modifier) {
    ReassuranceCallout(
        modifier = modifier,
        text = stringResource(R.string.safe_mode_device_transfer),
    )
}

/**
 * U13 (design spec 9.2): an info StateCard (not an error) while an earlier transaction's outcome
 * is checked and sending is paused, with the cyan "Wallet and backup options" link.
 */
@Composable
fun PendingCheckCard(
    modifier: Modifier = Modifier,
    onOptions: (() -> Unit)? = null,
) {
    val n = MaterialTheme.neon
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(n.infoContainer)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { },
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(20.dp),
                painter = painterResource(R.drawable.ic_clock_time_24),
                tint = n.cyan,
                contentDescription = null,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.safe_mode_pending_check),
                style = MaterialTheme.customTypography.textS,
                color = n.textPrimary,
            )
        }
        if (onOptions != null) {
            Spacer(Modifier.size(4.dp))
            NeonButton(
                modifier = Modifier.padding(start = 24.dp),
                size = ButtonSize.ExtraSmall,
                variant = NeonVariant.Tertiary,
                text = stringResource(R.string.wallet_backup_options),
                onClick = onOptions,
            )
        }
    }
}

/**
 * Secrets display (design spec 7.18): an always-LTR two-column grid numbered top to bottom per
 * column, on opaque cells. Before [revealed], only placeholder pills are drawn; the words are not
 * composed at all (never rendered under a blur). Tapping the veil calls [onReveal].
 */
@Composable
fun PhraseGrid(
    words: List<String>,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Phrases are always laid out left to right, also in RTL locales.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        PhraseGridLtr(words, revealed, onReveal, modifier)
    }
}

@Composable
private fun PhraseGridLtr(
    words: List<String>,
    revealed: Boolean,
    onReveal: () -> Unit,
    modifier: Modifier,
) {
    val n = MaterialTheme.neon
    val rows = (words.size + 1) / 2
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SafeModeTags.PHRASE_GRID),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(2) { column ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(rows) { row ->
                        val index = column * rows + row
                        if (index < words.size) {
                            PhraseCell(
                                number = index + 1,
                                word = if (revealed) words[index] else null,
                            )
                        }
                    }
                }
            }
        }
        if (!revealed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(n.glassSolid)
                    .border(1.dp, n.hairline, RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button, onClick = onReveal)
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag(SafeModeTags.PHRASE_VEIL),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.ic_neu_lock),
                    tint = n.cyan,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.safe_mode_tap_to_reveal),
                    style = MaterialTheme.customTypography.textMBold,
                    color = n.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.safe_mode_reveal_privacy),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PhraseCell(number: Int, word: String?) {
    val n = MaterialTheme.neon
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(n.glassSolid)
            .border(1.dp, n.hairline, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = number.toString().padStart(2, '0'),
            style = NeonType.mono.copy(fontSize = NeonType.mono.fontSize * (12f / 13f)),
            color = n.textTertiary,
        )
        Spacer(Modifier.width(10.dp))
        if (word != null) {
            Text(text = word, style = NeonType.phraseWord, color = n.textPrimary)
        } else {
            Box(
                Modifier
                    .size(width = 64.dp, height = 10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(n.skeletonBase),
            )
        }
    }
}
