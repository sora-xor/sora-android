/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuButton
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
fun ContainedButton(
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp) // TODO: Change to Dimens.x7
            .clip(RoundedCornerShape(Dimens.x4)),
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = ThemeColors.Primary,
            contentColor = ThemeColors.OnPrimary,
            disabledBackgroundColor = ThemeColors.BackgroundDisabled,
            disabledContentColor = ThemeColors.TextDisabled
        )
    ) {
        Text(
            modifier = Modifier.align(Alignment.CenterVertically),
            text = label,
            color = ThemeColors.OnPrimary,
            style = MaterialTheme.typography.neuButton
        )
    }
}
