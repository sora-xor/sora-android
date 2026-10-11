package jp.co.soramitsu.common.presentation.compose.neon.safemode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.common.account.SelectedAccountSigningSource
import jp.co.soramitsu.common.presentation.compose.neon.NeonButton
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.neonBorder
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.component.button.properties.Size as ButtonSize
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * True while the selected account is listed but cannot sign on this phone (and the app as a
 * whole is not in read-only mode, which has its own banner). Provided by every screen host
 * (`SoraBaseFragment`); false in previews and wherever nothing provides it.
 *
 * Display only: it disables a button and shows the reason. The signer decides by itself.
 */
val LocalSelectedAccountReadOnly = compositionLocalOf { false }

/**
 * What the host of every screen (`SoraBaseFragment`) does about the selected account's ability
 * to sign. It follows [source] and provides [LocalSelectedAccountReadOnly] to [content], so a
 * form can switch its action off. On a screen where the person signs
 * ([explainsReadOnlyAccount]) it also puts the notice with the repair action above the form;
 * [onRepair] gets the selected account's address.
 *
 * While the whole app is in read-only mode nothing is said about the account: the app-wide
 * banner speaks, and an account cannot be repaired until that mode ends.
 */
@Composable
fun SelectedAccountSigningHost(
    source: SelectedAccountSigningSource,
    explainsReadOnlyAccount: Boolean,
    onRepair: (address: String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val signingFlow = remember(source) { source.flow() }
    val signing = signingFlow.collectAsState(initial = SelectedAccountSigning.NONE).value
    val accountReadOnly = signing.readOnly && !rememberWalletReadOnly()
    CompositionLocalProvider(LocalSelectedAccountReadOnly provides accountReadOnly) {
        Column(modifier = modifier.fillMaxSize()) {
            if (explainsReadOnlyAccount && accountReadOnly) {
                AccountReadOnlyNotice(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    compact = true,
                    onRepair = { onRepair(signing.address) },
                )
            }
            content()
        }
    }
}

/** Test tags of the read-only account surfaces. */
object AccountReadOnlyTags {
    const val NOTICE = "accountReadOnlyNotice"
    const val REPAIR = "accountReadOnlyRepair"
    const val CHIP = "accountReadOnlyChip"
    const val REASON = "accountReadOnlyReason"
    const val BUTTON_REASON = "accountReadOnlyButtonReason"
}

/**
 * Says, before anything is filled in, that the selected account cannot send from this phone,
 * and offers the way to repair it: Account options, where its keys are restored in place with
 * its recovery phrase, raw seed, backup file or Google backup. An information card (design spec
 * 9.2 U9 pattern): no warning colour, and nothing here asks to remove anything.
 *
 * [compact] is the form used above a form (send, swap, liquidity, referral, staking, claim): no
 * emblem and one sentence, so the form keeps most of the screen. The home screen shows the full
 * card.
 */
@Composable
fun AccountReadOnlyNotice(
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val shape = RoundedCornerShape(if (compact) 16.dp else 24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(n.surface)
            .neonBorder(shape, n.borderSafe, fx)
            .padding(if (compact) 16.dp else 20.dp)
            .testTag(AccountReadOnlyTags.NOTICE),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!compact) {
                ShieldEmblem(size = 40.dp)
                Spacer(Modifier.width(4.dp))
            }
            Column(Modifier.weight(1f)) {
                ReadOnlyChip(modifier = Modifier.testTag(AccountReadOnlyTags.CHIP))
                Spacer(Modifier.size(6.dp))
                Text(
                    modifier = Modifier.semantics { heading() },
                    text = stringResource(R.string.account_read_only_title),
                    style = if (compact) {
                        MaterialTheme.customTypography.textMBold
                    } else {
                        MaterialTheme.customTypography.headline2
                    },
                    color = n.textPrimary,
                )
            }
        }
        Spacer(Modifier.size(if (compact) 4.dp else 8.dp))
        Text(
            modifier = Modifier.testTag(AccountReadOnlyTags.REASON),
            text = stringResource(
                if (compact) R.string.account_read_only_body_short else R.string.account_read_only_body,
            ),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textSecondary,
        )
        Spacer(Modifier.size(if (compact) 8.dp else 12.dp))
        NeonButton(
            modifier = Modifier.testTag(AccountReadOnlyTags.REPAIR),
            size = ButtonSize.Small,
            variant = NeonVariant.Safe,
            text = stringResource(R.string.account_read_only_repair),
            maxLines = 2,
            onClick = onRepair,
        )
    }
}

/**
 * The reason next to a button that is off because the selected account cannot sign here (the
 * account-level counterpart of [ReadOnlyReason]).
 */
@Composable
fun AccountReadOnlyReason(modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    Text(
        modifier = modifier.testTag(AccountReadOnlyTags.BUTTON_REASON),
        text = stringResource(R.string.account_read_only_action_off),
        style = MaterialTheme.customTypography.textS,
        color = n.textSecondary,
    )
}
