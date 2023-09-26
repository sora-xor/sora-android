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
                    color = MaterialTheme.customColors.fgPrimary,
                    style = MaterialTheme.customTypography.textM,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(
                    modifier = Modifier.size(50.dp, 30.dp),
                    checked = checked,
                    enabled = available,
                    onCheckedChange = onClick,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.customColors.bgSurface,
                        checkedTrackColor = MaterialTheme.customColors.accentPrimary,
                        checkedTrackAlpha = 1f,
                        uncheckedThumbColor = MaterialTheme.customColors.bgSurface,
                        uncheckedTrackColor = MaterialTheme.customColors.accentSecondaryContainer,
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
