/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuRegular15

@ExperimentalUnitApi
@Composable
fun SoraToolbar(
    toolbarData: ToolbarData,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        backgroundColor = ThemeColors.Background,
        elevation = 0.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.height(Dimens.x7)
        ) {
            IconButton(
                onClick = toolbarData.leftClickHandler
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_toolbar_back),
                    contentDescription = stringResource(id = R.string.common_back),
                    tint = ThemeColors.Primary
                )
            }

            Text(
                text = stringResource(id = toolbarData.titleResource),
                style = MaterialTheme.typography.neuRegular15,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(2f)
            )
            if (toolbarData.rightIconResource > 0) {
                IconButton(
                    onClick = toolbarData.rightClickHandler,
                ) {
                    Icon(
                        painter = painterResource(id = toolbarData.rightIconResource),
                        contentDescription = "",
                        tint = ThemeColors.Primary,
                    )
                }
            } else {
                Box(modifier = Modifier.size(Dimens.x6))
            }
        }
    }
}

data class ToolbarData(
    val titleResource: Int,
    val leftClickHandler: () -> Unit = {},
    val rightIconResource: Int = 0,
    val rightClickHandler: () -> Unit = {},
)
