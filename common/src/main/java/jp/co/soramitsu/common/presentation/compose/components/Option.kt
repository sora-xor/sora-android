/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun Option(
    modifier: Modifier = Modifier,
    icon: Painter,
    label: String,
    description: String? = null,
    bottomDivider: Boolean,
    @DrawableRes iconEnd: Int = R.drawable.ic_chevron_right_24,
    tint: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (description == null) Dimens.x7 else Dimens.x8),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier
                    .size(size = Dimens.x3),
                painter = icon,
                tint = if (tint) MaterialTheme.customColors.fgSecondary else Color.Unspecified,
                contentDescription = null
            )
            Row(
                modifier = Modifier
                    .padding(start = Dimens.x1)
                    .fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight()
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.customTypography.textM,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.customTypography.textXSBold,
                            color = MaterialTheme.customColors.fgSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    modifier = Modifier
                        .size(size = Dimens.x3),
                    painter = painterResource(iconEnd),
                    tint = MaterialTheme.customColors.fgSecondary,
                    contentDescription = null,
                )
            }
        }
        if (bottomDivider) {
            Divider(
                modifier = Modifier.padding(start = Dimens.x4),
                color = MaterialTheme.customColors.fgOutline,
                thickness = 1.dp
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 16777215)
@Composable
private fun PreviewOptions() {
    Column {
        Option(
            icon = painterResource(id = R.drawable.ic_connection_indicator_red),
            label = "label 1",
            bottomDivider = true,
            onClick = {},
        )
        Option(
            icon = painterResource(id = R.drawable.ic_connection_indicator_green),
            label = "label 2",
            bottomDivider = true,
            onClick = {},
        )
        Option(
            icon = painterResource(id = R.drawable.ic_settings_heart),
            label = "label 3",
            description = "label 3 desc",
            iconEnd = R.drawable.ic_arrow_top_right_24,
            bottomDivider = false,
            onClick = {},
        )
    }
}
