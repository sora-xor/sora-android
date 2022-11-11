/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.components.RegularButton
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlin.math.ceil

@Composable
fun BackupScreen(
    state: BackupScreenState,
    viewModel: BackupViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.customColors.bgPage),
        horizontalAlignment = CenterHorizontally
    ) {
        Icon(
            modifier = Modifier.padding(top = Dimens.x7),
            painter = painterResource(id = R.drawable.ic_neu_explanation_triangle),
            tint = MaterialTheme.customColors.accentPrimary,
            contentDescription = ""
        )

        Text(
            modifier = Modifier.padding(vertical = Dimens.x1, horizontal = Dimens.x5),
            text = stringResource(id = R.string.mnemonic_text),
            textAlign = TextAlign.Center,
            style = MaterialTheme.customTypography.paragraphL
        )

        if (state.mnemonicWords.isNotEmpty()) {
            BackupMnemonicView(state.mnemonicWords)
        }

        if (state.seedString.isNotEmpty()) {
            Text(
                modifier = Modifier.padding(top = Dimens.x3, start = Dimens.x4, end = Dimens.x4),
                text = state.seedString,
                style = MaterialTheme.customTypography.paragraphM,
                textAlign = TextAlign.Center
            )
        }

        RegularButton(
            modifier = Modifier.padding(top = Dimens.x3, start = Dimens.x6, end = Dimens.x6),
            icon = R.drawable.ic_neu_share,
            onClick = viewModel::backupPressed
        )
    }
}

@Composable
fun BackupMnemonicView(words: List<String>) {
    Row(
        Modifier.padding(top = Dimens.x3),
        horizontalArrangement = Arrangement.Center
    ) {
        val rowCount = ceil(words.size / 2.0).toInt()
        Column {
            (0 until rowCount).forEach { index ->
                Row(
                    Modifier.widthIn(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.customTypography.paragraphL
                    )
                    Text(
                        modifier = Modifier.padding(start = Dimens.x2),
                        text = words[index],
                        style = MaterialTheme.customTypography.paragraphLBold
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .padding(start = Dimens.x7)
        ) {
            (rowCount until words.size).forEach { index ->
                Row(
                    Modifier.widthIn(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier.widthIn(min = Dimens.x3),
                        text = (index + 1).toString(),
                        style = MaterialTheme.customTypography.paragraphL
                    )
                    Text(
                        modifier = Modifier.padding(start = Dimens.x2),
                        text = words[index],
                        style = MaterialTheme.customTypography.paragraphLBold
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewBackupMnemonicView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        BackupMnemonicView(
            List(12) { i -> "word n ${i + 1}" }
        )
        Spacer(modifier = Modifier.size(10.dp))
        BackupMnemonicView(
            List(15) { i -> "word n ${i + 1}" }
        )
    }
}
