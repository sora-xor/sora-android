/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun PercentBar(
    onOptionSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(color = Color(0xffc5c9d0))
            .padding(vertical = Dimens.x1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = { onOptionSelect(100) }),
            textAlign = TextAlign.Center,
            text = "100%",
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = { onOptionSelect(75) }),
            textAlign = TextAlign.Center,
            text = "75%",
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = { onOptionSelect(50) }),
            textAlign = TextAlign.Center,
            text = "50%",
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary,
        )
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = { onOptionSelect(25) }),
            textAlign = TextAlign.Center,
            text = "25%",
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary,
        )
    }
}

@Preview
@Composable
private fun PreviewPercentageBar() {
    PercentBar(
        onOptionSelect = {}
    )
}
