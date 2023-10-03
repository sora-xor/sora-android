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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun DetailsItem(
    modifier: Modifier = Modifier,
    text: String,
    value1: String,
    value2: String? = null,
    value1Bold: Boolean = false,
    value1Uri: String? = null,
    value1Percent: Float? = null,
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
                backgroundColor = MaterialTheme.customColors.bgPage,
                title = {
                    Text(
                        text = text,
                        color = MaterialTheme.customColors.fgPrimary,
                        style = MaterialTheme.customTypography.textSBold
                    )
                },
                text = {
                    Text(
                        text = hint,
                        color = MaterialTheme.customColors.fgPrimary,
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
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Dimens.x1_4),
            horizontalArrangement = Arrangement.End,
        ) {
            if (value1Uri != null) {
                TokenIcon(
                    uri = value1Uri,
                    size = Dimens.x2,
                )
            }
            if (value1Percent != null) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(Dimens.x2)),
                    progress = value1Percent,
                    color = MaterialTheme.customColors.accentPrimary,
                    backgroundColor = MaterialTheme.customColors.bgSurfaceVariant,
                )
            }
        }
        Text(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = Dimens.x1_2),
            text = value1,
            textAlign = TextAlign.End,
            style = if (value1Bold) MaterialTheme.customTypography.textSBold else MaterialTheme.customTypography.textS,
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
            value1Bold = true,
            hint = null,
        )
        DetailsItem(
            text = "Min received",
            value1 = "1.234 XOR",
            value2 = "~$3.45",
            value1Uri = DEFAULT_ICON_URI,
            hint = "some hint",
        )
        DetailsItem(
            text = "Min received",
            value1 = "0.4 %",
            value1Percent = 0.4f,
        )
    }
}
