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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun CheckboxButton(
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    itemClicked: () -> Unit,
    text: String
) {
    val shape = RoundedCornerShape(MaterialTheme.borderRadius.ml)
    Row(
        modifier = modifier
            .padding(vertical = Dimens.x1_2)
            .border(
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.customColors.accentPrimary else MaterialTheme.customColors.bgSurfaceVariant
                ),
                shape = shape,
            )
            .clip(shape)
            .clickable { itemClicked() }
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            modifier = Modifier.size(Dimens.x3),
            painter = painterResource(id = if (isSelected) R.drawable.ic_selected_accent_pin_24 else R.drawable.ic_selected_pin_empty_24),
            contentDescription = null
        )

        Text(
            modifier = Modifier
                .weight(1f)
                .padding(start = Dimens.x2),
            style = MaterialTheme.customTypography.paragraphS,
            color = MaterialTheme.customColors.fgPrimary,
            text = text
        )
    }
}
