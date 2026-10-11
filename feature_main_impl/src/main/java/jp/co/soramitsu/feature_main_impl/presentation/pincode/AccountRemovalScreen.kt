package jp.co.soramitsu.feature_main_impl.presentation.pincode

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.safemode.PhraseGrid
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReassuranceCallout
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeCard
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.ui_core.theme.customTypography

/** Test tags of the removal screen. */
internal object AccountRemovalTags {
    const val SCREEN = "AccountRemovalScreen"
    const val SEED_REVEAL = "AccountRemovalSeedReveal"
    const val SEED = "AccountRemovalSeed"
    const val NO_BACKUP = "AccountRemovalNoBackup"
    const val EXPORT_JSON = "AccountRemovalExportJson"
    const val WRITTEN_DOWN = "AccountRemovalWrittenDown"
    const val UNDERSTOOD_NO_BACKUP = "AccountRemovalUnderstoodNoBackup"
    const val STAYS = "AccountRemovalWhatStays"
    const val REMOVE = "AccountRemovalRemove"
    const val CANCEL = "AccountRemovalCancel"
}

/**
 * Keeps the activity window secure (no screenshots, no recents thumbnail) while [enabled].
 * Secrets are drawn in the activity window, never in a dialog window, which would not carry
 * the flag (design spec 7.18).
 */
@Composable
internal fun SecureWindow(enabled: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        if (enabled) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * "Remove this account from this phone", shown after the PIN or biometrics and before anything
 * is removed (design spec 10.2-6: a destructive action needs a backup check and an explicit
 * confirmation).
 *
 * - The backup that re-derives the account is shown first: its recovery phrase, else its raw
 *   seed, behind a veil the person lifts. "I have written it down" can be ticked only after
 *   that, and Remove is enabled only once it is ticked.
 * - When nothing stored here re-derives the account, the screen says plainly that this phone
 *   cannot bring it back, offers the encrypted JSON export first (when its key still signs),
 *   and asks for that to be acknowledged instead.
 * - It says what stays on the phone.
 */
@Composable
internal fun AccountRemovalScreen(
    state: AccountRemovalState,
    onReveal: () -> Unit,
    onWrittenDown: (Boolean) -> Unit,
    onUnderstoodNoBackup: (Boolean) -> Unit,
    onExportJson: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit,
) {
    val n = MaterialTheme.neon
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag(AccountRemovalTags.SCREEN),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SafeCard {
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(R.string.account_removal_title),
                style = MaterialTheme.customTypography.headline1,
                color = n.textPrimary,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(
                    if (state.removesEverything) {
                        R.string.account_removal_body_last
                    } else {
                        R.string.account_removal_body
                    },
                ),
                style = MaterialTheme.customTypography.paragraphM,
                color = n.textSecondary,
            )
        }

        state.accounts.forEach { account ->
            SafeCard {
                Text(
                    text = account.name.ifBlank { stringResource(R.string.account_removal_unnamed) },
                    style = MaterialTheme.customTypography.headline2,
                    color = n.textPrimary,
                )
                Text(
                    text = account.address,
                    style = NeonType.mono,
                    color = n.textSecondary,
                )
                Spacer(Modifier.size(12.dp))
                when {
                    account.phraseWords.isNotEmpty() -> {
                        Text(
                            text = stringResource(R.string.account_removal_phrase_first),
                            style = MaterialTheme.customTypography.paragraphS,
                            color = n.textPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                        PhraseGrid(
                            words = account.phraseWords,
                            revealed = state.revealed,
                            onReveal = onReveal,
                        )
                    }
                    account.seed.isNotEmpty() -> {
                        Text(
                            text = stringResource(R.string.account_removal_seed_first),
                            style = MaterialTheme.customTypography.paragraphS,
                            color = n.textPrimary,
                        )
                        Spacer(Modifier.size(12.dp))
                        if (state.revealed) {
                            Text(
                                modifier = Modifier.testTag(AccountRemovalTags.SEED),
                                text = account.seed,
                                style = NeonType.mono,
                                color = n.textPrimary,
                            )
                        } else {
                            SafeAction(
                                modifier = Modifier.testTag(AccountRemovalTags.SEED_REVEAL),
                                text = stringResource(R.string.safe_mode_tap_to_reveal),
                                variant = NeonVariant.Secondary,
                                onClick = onReveal,
                            )
                        }
                    }
                    else -> {
                        Text(
                            modifier = Modifier.testTag(AccountRemovalTags.NO_BACKUP),
                            text = stringResource(R.string.account_removal_no_backup),
                            style = MaterialTheme.customTypography.paragraphS,
                            color = n.textPrimary,
                        )
                        if (account.canExportJson) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.account_removal_no_backup_export),
                                style = MaterialTheme.customTypography.paragraphS,
                                color = n.textSecondary,
                            )
                            Spacer(Modifier.size(12.dp))
                            SafeAction(
                                modifier = Modifier.testTag(AccountRemovalTags.EXPORT_JSON),
                                text = stringResource(R.string.account_removal_export_json),
                                variant = NeonVariant.Safe,
                                enabled = !state.working,
                                onClick = onExportJson,
                            )
                        }
                    }
                }
            }
        }

        SafeCard(modifier = Modifier.testTag(AccountRemovalTags.STAYS)) {
            Text(
                modifier = Modifier.semantics { heading() },
                text = stringResource(R.string.account_removal_stays_title),
                style = MaterialTheme.customTypography.headline2,
                color = n.textPrimary,
            )
            Spacer(Modifier.size(8.dp))
            val stays = buildList {
                if (state.removesEverything) {
                    add(
                        if (state.retainedKeySets > 0) {
                            stringResource(
                                R.string.account_removal_stays_found_wallets_count,
                                state.retainedKeySets.toString(),
                            )
                        } else {
                            stringResource(R.string.account_removal_stays_found_wallets)
                        },
                    )
                } else {
                    add(stringResource(R.string.account_removal_stays_other_accounts))
                }
                add(stringResource(R.string.account_removal_stays_copies))
            }
            stays.forEach { line ->
                Text(
                    modifier = Modifier.padding(vertical = 4.dp),
                    text = line,
                    style = MaterialTheme.customTypography.paragraphS,
                    color = n.textSecondary,
                )
            }
        }

        SafeCard {
            if (state.showsBackup) {
                AcknowledgeRow(
                    modifier = Modifier.testTag(AccountRemovalTags.WRITTEN_DOWN),
                    text = stringResource(R.string.account_removal_written_down),
                    checked = state.writtenDown,
                    // Only what was shown can have been written down.
                    enabled = state.revealed && !state.working,
                    onChecked = onWrittenDown,
                )
                if (!state.revealed) {
                    ReassuranceCallout(stringResource(R.string.account_removal_reveal_first))
                }
            }
            if (state.hasAccountWithoutBackup) {
                AcknowledgeRow(
                    modifier = Modifier.testTag(AccountRemovalTags.UNDERSTOOD_NO_BACKUP),
                    text = stringResource(R.string.account_removal_understood_no_backup),
                    checked = state.understoodNoBackup,
                    enabled = !state.working,
                    onChecked = onUnderstoodNoBackup,
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SafeAction(
                    modifier = Modifier.testTag(AccountRemovalTags.CANCEL),
                    text = stringResource(R.string.account_removal_keep),
                    variant = NeonVariant.Safe,
                    enabled = !state.working,
                    onClick = onCancel,
                )
                SafeAction(
                    modifier = Modifier.testTag(AccountRemovalTags.REMOVE),
                    text = stringResource(R.string.account_removal_remove),
                    variant = NeonVariant.Secondary,
                    enabled = state.canRemove,
                    loading = state.working,
                    onClick = onRemove,
                )
            }
        }
    }
}

@Composable
private fun AcknowledgeRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = MaterialTheme.neon
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Checkbox) { onChecked(!checked) }
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            enabled = enabled,
            // The row is the click target.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = n.cyan,
                uncheckedColor = n.textSecondary,
            ),
        )
        Spacer(Modifier.size(12.dp))
        Text(
            text = text,
            style = MaterialTheme.customTypography.textM,
            color = if (enabled) n.textPrimary else n.textSecondary,
        )
    }
}
