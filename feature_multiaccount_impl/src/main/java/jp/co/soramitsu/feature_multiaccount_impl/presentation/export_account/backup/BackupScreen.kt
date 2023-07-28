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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import jp.co.soramitsu.ui_core.theme.opacity

@Composable
internal fun BackupScreen(
    state: BackupScreenState,
    onButtonPressed: () -> Unit,
    onBackupWithGoogleButtonPressed: (() -> Unit)? = null,
) {
    ContentCard(
        modifier = Modifier.padding(horizontal = Dimens.x2, vertical = Dimens.x1),
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
                style = MaterialTheme.customTypography.paragraphM,
            )

            if (state.mnemonicWords.isNotEmpty()) {
                BackupMnemonicView(state.mnemonicWords)
            }

            if (state.seedString.isNotEmpty()) {
                Text(
                    modifier = Modifier.padding(
                        top = Dimens.x3,
                        start = Dimens.x4,
                        end = Dimens.x4
                    ),
                    text = state.seedString,
                    style = MaterialTheme.customTypography.paragraphMBold,
                    textAlign = TextAlign.Center
                )
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
                )
            }
        }
    }
}

@Composable
private fun BackupMnemonicView(words: List<String>) {
    Row(
        Modifier
            .padding(top = Dimens.x3)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        val div = words.size.div(2)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            MnemonicWordsColumn(
                startNumber = 1,
                words = words.subList(0, div),
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            MnemonicWordsColumn(
                startNumber = div + 1,
                words = words.subList(div, words.size),
            )
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
