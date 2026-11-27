package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.litPlate
import jp.co.soramitsu.ui_core.theme.customTypography

object ClaimEntryTags {
    const val ENTRY = "ClaimEntry"
}

/**
 * The wallet home's "Claim" entry: shown for as long as a claim of an older SORA balance is
 * known on this phone for the selected account, and gone once it is done. It opens the claim
 * screen. Nothing about a claim is put in front of the wallet after the PIN (update rule of
 * 2026-11-09); the person opens it from here when they choose to.
 */
@Composable
fun ClaimEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val title = stringResource(R.string.common_claim)
    val shape = RoundedCornerShape(Lit.SheetRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .litPlate(shape = shape, elevation = 8.dp)
            .clip(shape)
            .clickable(onClickLabel = title, role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(ClaimEntryTags.ENTRY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.customTypography.textMBold,
                color = n.textPrimary,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                text = stringResource(R.string.portfolio_claim_body),
                style = MaterialTheme.customTypography.paragraphS,
                color = n.textSecondary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_24),
            contentDescription = null,
            tint = n.textTertiary,
            modifier = Modifier.size(24.dp),
        )
    }
}
