/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun DetailsItem(
    modifier: Modifier = Modifier,
    text: String,
    value1: String,
    value2: String? = null,
    hint: String? = null,
    valueColor: Color = MaterialTheme.customColors.fgPrimary,
) {
    var hintVisible by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hintVisible && hint != null) {
            AlertDialog(
                title = {
                    Text(
                        text = text,
                        style = MaterialTheme.customTypography.textSBold
                    )
                },
                text = {
                    Text(
                        text = hint,
                        style = MaterialTheme.customTypography.paragraphSBold
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { hintVisible = false }
                    ) {
                        Text(
                            text = stringResource(id = R.string.common_ok),
                            color = Color.Red,
                        )
                    }
                },
                onDismissRequest = {
                    hintVisible = false
                },
            )
        }
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = Dimens.x1_2)
                .clickable { hintVisible = true },
            text = text,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary,
            maxLines = 1,
        )
        if (hint != null) {
            Icon(
                modifier = Modifier
                    .size(size = Dimens.x2)
                    .clickable { hintVisible = true },
                painter = painterResource(id = R.drawable.ic_neu_exclamation),
                tint = MaterialTheme.customColors.fgSecondary,
                contentDescription = null
            )
        }
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.x1_2),
            text = value1,
            textAlign = TextAlign.End,
            style = MaterialTheme.customTypography.textS,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        value2?.let {
            Text(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = Dimens.x1_2),
                text = it,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewSwapDetailsItem() {
    Column {
        DetailsItem(
            text = "Min received",
            value1 = "1.234 XOR",
            value2 = "~$3.45",
            hint = null,
        )
        DetailsItem(
            text = "Min received",
            value1 = "1.234 XOR",
            value2 = "~$3.45",
            hint = "some hint",
        )
    }
}
