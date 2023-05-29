/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun BackupScreen(
    state: BackupScreenState,
    onButtonPressed: () -> Unit,
    onSkipButtonPressed: (() -> Unit)? = null,
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

                TonalButton(
                    modifier = Modifier
                        .testTagAsId("Skip")
                        .fillMaxWidth()
                        .padding(top = Dimens.x1),
                    text = stringResource(id = R.string.common_skip),
                    order = Order.PRIMARY,
                    size = Size.Large,
                    onClick = { onSkipButtonPressed?.invoke() },
                )
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

@Preview
@Composable
private fun PreviewBackup() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        BackupScreen(
            state = BackupScreenState(
                mnemonicWords = List(12) { p -> "abcde n ${p + 1}" }
            ),
            onButtonPressed = {},
            onSkipButtonPressed = {}
        )
        Spacer(modifier = Modifier.size(10.dp))
        BackupScreen(
            state = BackupScreenState(
                isCreatingFlow = true,
                mnemonicWords = List(15) { p -> "zxcvb n ${p + 1}" },
            ),
            onButtonPressed = {},
            onSkipButtonPressed = {}
        )
    }
}
