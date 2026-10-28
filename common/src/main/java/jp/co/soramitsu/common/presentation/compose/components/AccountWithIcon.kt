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

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.AccountIcon
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountWithIcon(
    modifier: Modifier,
    address: String,
    accountIcon: Drawable?,
    rightIcon: Int = R.drawable.ic_chevron_right_24,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .combinedClickable(
                onLongClick = onLongClick,
                onClick = onClick
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountIcon(drawable = accountIcon, size = Size.Small)
        Text(
            modifier = Modifier
                .weight(1f)
                .wrapContentHeight()
                .padding(horizontal = Dimens.x1),
            text = address,
            color = MaterialTheme.customColors.fgPrimary,
            style = MaterialTheme.customTypography.textS,
        )
        Icon(
            modifier = Modifier
                .size(size = Dimens.x3),
            painter = painterResource(rightIcon),
            tint = MaterialTheme.customColors.fgSecondary,
            contentDescription = null,
        )
    }
}
