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

package jp.co.soramitsu.common.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun WarningTextCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    text: String,
    error: Boolean = true,
) {
    val shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
    val container =
        if (error) MaterialTheme.customColors.statusErrorContainer else MaterialTheme.customColors.statusWarningContainer
    val main =
        if (error) MaterialTheme.customColors.statusError else MaterialTheme.customColors.statusWarning

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = container,
                shape = shape,
            )
            .clip(shape)
            .padding(
                vertical = Dimens.x2,
                horizontal = Dimens.x3,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(
                text = title,
                color = main,
                style = MaterialTheme.customTypography.paragraphSBold,
                textAlign = TextAlign.Center
            )
            Divider(
                color = Color.Companion.Transparent,
                thickness = Dimens.x1_2
            )
        }
        Text(
            text = text,
            color = main,
            style = MaterialTheme.customTypography.paragraphS,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun PreviewWarningTextCard() {
    SoraAppTheme {
        Column {
            WarningTextCard(
                title = "Warning",
                text = "AbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabraAbracabra",
                error = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            WarningTextCard(
                text = "AbracacabraAbracabraAbracabra",
                error = false,
            )
        }
    }
}
