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

package jp.co.soramitsu.feature_main_impl.presentation.profile.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.Option
import jp.co.soramitsu.common.presentation.compose.components.OptionSwitch
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.neon.safemode.DeviceTransferNotice
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeCard
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun LoginSecurityScreen(
    bioAvailable: Boolean,
    bioEnabled: Boolean,
    onChangePinClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    retained: RetainedWalletsUi = RetainedWalletsUi(),
    onRestoreRetained: (String) -> Unit = {},
    onImportRecoveryPhrase: () -> Unit = {},
) {
    Column {
        jp.co.soramitsu.common.presentation.compose.components.PreservedWalletDataButton()
        ContentCard(
            modifier = Modifier
                .padding(horizontal = Dimens.x2)
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(vertical = Dimens.x1, horizontal = Dimens.x3)
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Option(
                    icon = painterResource(id = R.drawable.ic_keyboard_digital_24),
                    label = stringResource(id = R.string.change_pin),
                    bottomDivider = true,
                    onClick = onChangePinClick,
                )
                OptionSwitch(
                    icon = painterResource(id = R.drawable.ic_face_24),
                    label = stringResource(id = R.string.profile_biometry_title),
                    bottomDivider = false,
                    available = bioAvailable,
                    checked = bioEnabled,
                    onClick = onToggle,
                )
            }
        }
        // U12: app data never moves to a new phone (deny_data_extraction.xml).
        DeviceTransferNotice(
            modifier = Modifier
                .padding(horizontal = Dimens.x2)
                .padding(top = Dimens.x2),
        )
        if (retained.addresses.isNotEmpty()) {
            RetainedWallets(
                modifier = Modifier
                    .padding(horizontal = Dimens.x2)
                    .padding(top = Dimens.x2),
                retained = retained,
                onRestore = onRestoreRetained,
                onImportRecoveryPhrase = onImportRecoveryPhrase,
            )
        }
    }
}

/** The "Wallets found on this phone" section: addresses and what happened to each. */
internal data class RetainedWalletsUi(
    val addresses: List<String> = emptyList(),
    val restoring: String? = null,
    val restored: List<String> = emptyList(),
    val notRestorable: List<String> = emptyList(),
    /** Left by "Remove all accounts": only their own recovery phrase brings them back. */
    val needRecoveryPhrase: List<String> = emptyList(),
)

/** Test tags of the "Wallets found on this phone" section. */
internal object RetainedWalletsTags {
    const val NEEDS_PHRASE = "RetainedWalletNeedsPhrase"
    const val IMPORT_PHRASE = "RetainedWalletImportPhrase"
    const val RESTORE = "RetainedWalletRestore"
}

/**
 * Key sets kept on this phone that no account uses (U8: an unfinished account setup is kept
 * safely; also keys left by an older version or by "Set up this device again"). Restoring one
 * adds the account only after its keys are proven to belong to the address.
 */
@Composable
private fun RetainedWallets(
    retained: RetainedWalletsUi,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
    onImportRecoveryPhrase: () -> Unit = {},
) {
    val n = MaterialTheme.neon
    SafeCard(modifier = modifier) {
        Text(
            modifier = Modifier.semantics { heading() },
            text = stringResource(R.string.safe_mode_retained_title),
            style = MaterialTheme.customTypography.headline2,
            color = n.textPrimary,
        )
        Spacer(Modifier.size(Dimens.x1))
        Text(
            text = stringResource(R.string.safe_mode_retained_body),
            style = MaterialTheme.customTypography.paragraphS,
            color = n.textSecondary,
        )
        retained.addresses.forEach { address ->
            Spacer(Modifier.size(Dimens.x2))
            Text(
                text = if (address.length > 16) "${address.take(8)}…${address.takeLast(8)}" else address,
                style = NeonType.mono,
                color = n.textPrimary,
            )
            when (address) {
                in retained.restored -> Text(
                    text = stringResource(R.string.safe_mode_retained_restored),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                )
                in retained.notRestorable -> Text(
                    text = stringResource(R.string.safe_mode_retained_not_restorable),
                    style = MaterialTheme.customTypography.textS,
                    color = n.textSecondary,
                )
                // Left on the phone when every account was removed: its own recovery phrase is
                // asked for. "Restore this wallet" is not offered, and the repository refuses.
                in retained.needRecoveryPhrase -> {
                    Text(
                        modifier = Modifier.testTag(RetainedWalletsTags.NEEDS_PHRASE),
                        text = stringResource(R.string.safe_mode_retained_needs_phrase),
                        style = MaterialTheme.customTypography.textS,
                        color = n.textSecondary,
                    )
                    SafeAction(
                        modifier = Modifier.testTag(RetainedWalletsTags.IMPORT_PHRASE),
                        text = stringResource(R.string.safe_mode_retained_import_phrase),
                        variant = NeonVariant.Secondary,
                        enabled = retained.restoring == null,
                        onClick = onImportRecoveryPhrase,
                    )
                }
                else -> SafeAction(
                    modifier = Modifier.testTag(RetainedWalletsTags.RESTORE),
                    text = stringResource(R.string.safe_mode_retained_restore),
                    variant = NeonVariant.Secondary,
                    enabled = retained.restoring == null,
                    loading = retained.restoring == address,
                    onClick = { onRestore(address) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewLoginSecurityScreen() {
    LoginSecurityScreen(
        bioEnabled = false,
        bioAvailable = true,
        onChangePinClick = { },
        onToggle = { },
    )
}
