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

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
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
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun OptionSwitch(
    icon: Painter?,
    label: String,
    bottomDivider: Boolean,
    available: Boolean,
    checked: Boolean,
    onClick: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 13.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(
                    modifier = Modifier
                        .size(size = Dimens.x3),
                    painter = it,
                    tint = MaterialTheme.customColors.fgSecondary,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.size(Dimens.x1))
            }
            Row(
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f).padding(end = Dimens.x1),
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textM,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    modifier = Modifier.size(50.dp, 30.dp),
                    checked = checked,
                    enabled = available,
                    onCheckedChange = onClick,
                    // SORA Neon toggle (design spec 7.8): cyan track when on; when off, a grey
                    // thumb (border.input, 3:1 on the card) so the state never relies on a
                    // white-on-white thumb.
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.neon.cyan,
                        checkedTrackAlpha = 1f,
                        uncheckedThumbColor = MaterialTheme.neon.borderInput,
                        uncheckedTrackColor = MaterialTheme.neon.divider,
                        uncheckedTrackAlpha = 1f,
                    )
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
        OptionSwitch(
            icon = painterResource(id = R.drawable.ic_connection_indicator_red),
            label = "label 1",
            bottomDivider = true,
            onClick = {},
            checked = true,
            available = true,
        )
        OptionSwitch(
            icon = painterResource(id = R.drawable.ic_connection_indicator_green),
            label = "label 2",
            bottomDivider = true,
            onClick = {},
            checked = false,
            available = true,
        )
        OptionSwitch(
            icon = painterResource(id = R.drawable.ic_settings_heart),
            label = "label 3",
            bottomDivider = false,
            onClick = {},
            checked = true,
            available = false,
        )
    }
}
