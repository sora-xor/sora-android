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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import jp.co.soramitsu.common.presentation.compose.neon.rememberWalletSecretReveal
import jp.co.soramitsu.common.presentation.compose.neon.safemode.PhraseGrid
import jp.co.soramitsu.common.presentation.compose.neon.safemode.SafeAction
import jp.co.soramitsu.common.presentation.compose.neon.NeonVariant
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.common.presentation.compose.neon.SafeFilledButton as FilledButton
import jp.co.soramitsu.common.presentation.compose.neon.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.ui_core.theme.opacity

@Composable
fun BackupScreen(
    state: BackupScreenState,
    onButtonPressed: () -> Unit,
    onBackupWithGoogleButtonPressed: (() -> Unit)? = null,
    onExportJsonPressed: (() -> Unit)? = null,
) {
    if (state.noMatchingSecret) {
        NoMatchingSecretCard(onExportJsonPressed.takeIf { state.canExportJson })
        return
    }
    var revealed by rememberWalletSecretReveal(state.mnemonicWords to state.seedString)
    ContentCard(
        modifier = Modifier.padding(horizontal = Dimens.x2, vertical = Dimens.x1).fillMaxWidth().wrapContentHeight(),
        backgroundColor = MaterialTheme.neon.glassSolid,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(Dimens.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                text = stringResource(id = R.string.mnemonic_text),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )

            if (state.mnemonicWords.isNotEmpty()) {
                PhraseGrid(state.mnemonicWords, revealed, { revealed = true },
                    Modifier.padding(top = Dimens.x3))
            }

            if (state.seedString.isNotEmpty()) {
                if (revealed) {
                    Text(state.seedString, color = MaterialTheme.neon.textPrimary,
                        style = NeonType.mono, modifier = Modifier.padding(vertical = Dimens.x3).testTag("BackupSeed"))
                } else {
                    Text(stringResource(R.string.safe_mode_reveal_privacy),
                        color = MaterialTheme.neon.textSecondary, modifier = Modifier.padding(top = Dimens.x3))
                    SafeAction(stringResource(R.string.safe_mode_tap_to_reveal), NeonVariant.Safe,
                        modifier = Modifier.testTag("BackupSeedReveal"), onClick = { revealed = true })
                }
            }

            if (state.isCreatingFlow) {
                FilledButton(
                    modifier = Modifier
                        .testTagAsId("Continue")
                        .fillMaxWidth()
                        .padding(top = Dimens.x3),
                    text = stringResource(id = R.string.common_continue),
                    order = Order.PRIMARY,
                    size = Size.Large,
                    onClick = onButtonPressed,
                    enabled = revealed,
                )

                onBackupWithGoogleButtonPressed?.let {
                    Button(
                        modifier = Modifier
                            .padding(top = Dimens.x1)
                            .testTagAsId("GoogleBackup")
                            .fillMaxWidth()
                            .heightIn(Dimens.x7),
                        border = BorderStroke(
                            width = 1.dp,
                            color = Color(0xFF3579F7)
                        ),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.customColors.bgPage,
                            contentColor = MaterialTheme.customColors.accentPrimary,
                            disabledBackgroundColor = MaterialTheme.customColors.bgPage,
                            disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(
                                MaterialTheme.opacity.actionFgDisabled
                            )
                        ),
                        shape = RoundedCornerShape(MaterialTheme.borderRadius.ml),
                        onClick = it,
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Image(
                            modifier = Modifier.padding(end = Dimens.x1),
                            painter = painterResource(id = R.drawable.ic_google_white),
                            contentDescription = stringResource(id = R.string.onboarding_continue_with_google)
                        )

                        Text(
                            style = MaterialTheme.customTypography.buttonM,
                            color = MaterialTheme.customColors.fgPrimary,
                            text = stringResource(id = R.string.account_options_backup_google)
                        )
                    }
                }
            } else {
                TonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Dimens.x3),
                    leftIcon = painterResource(id = R.drawable.ic_copy_24),
                    text = stringResource(id = R.string.copy_to_clipboard),
                    order = Order.TERTIARY,
                    size = Size.Large,
                    onClick = onButtonPressed,
                    enabled = revealed,
                )
            }
        }
    }
}

/**
 * Nothing stored on this phone re-derives this account's address, so nothing is shown as its
 * recovery phrase or seed. When the stored key signs, the encrypted JSON export is the way to
 * back the account up and its button is shown; a read-only account is told to import its phrase.
 */
@Composable
private fun NoMatchingSecretCard(onExportJsonPressed: (() -> Unit)?) {
    ContentCard(
        modifier = Modifier.padding(horizontal = Dimens.x2, vertical = Dimens.x1).fillMaxWidth().wrapContentHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(Dimens.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                modifier = Modifier
                    .testTagAsId("NoMatchingSecret")
                    .wrapContentHeight()
                    .fillMaxWidth(),
                text = stringResource(
                    id = if (onExportJsonPressed != null) {
                        R.string.backup_no_matching_secret
                    } else {
                        R.string.backup_no_matching_secret_read_only
                    },
                ),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.paragraphM,
            )
            onExportJsonPressed?.let {
                FilledButton(
                    modifier = Modifier
                        .testTagAsId("ExportJson")
                        .fillMaxWidth()
                        .padding(top = Dimens.x3),
                    text = stringResource(id = R.string.export_protection_json_title),
                    order = Order.PRIMARY,
                    size = Size.Large,
                    onClick = it,
                )
            }
        }
    }
}

@Preview(locale = "ru")
@Composable
private fun PreviewBackup() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        BackupScreen(
            state = BackupScreenState(
                mnemonicWords = List(12) { p -> "abcde" }
            ),
            onButtonPressed = {},
        )
        Spacer(modifier = Modifier.size(10.dp))
        BackupScreen(
            state = BackupScreenState(
                isCreatingFlow = true,
                mnemonicWords = List(15) { p -> "zxcvb" },
            ),
            onButtonPressed = {},
        )
    }
}
