/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.neuButton

@Composable
fun RegularButton(
    modifier: Modifier = Modifier,
    label: String = "",
    icon: Int = 0,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.x7)
            .clip(RoundedCornerShape(Dimens.x4)),
        enabled = enabled,
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = ThemeColors.Secondary,
            contentColor = ThemeColors.OnSecondary,
            disabledBackgroundColor = ThemeColors.BackgroundDisabled,
            disabledContentColor = ThemeColors.TextDisabled
        )
    ) {
        if (label.isEmpty() and (icon > 0)) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null
            )
        } else {
            Text(
                text = label,
                color = ThemeColors.OnSecondary,
                style = MaterialTheme.typography.neuButton
            )
        }
    }
}

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
            .height(Dimens.x7)
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

@Composable
fun OutlinedButton(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit
) {
    androidx.compose.material.OutlinedButton(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.x7)
            .clip(RoundedCornerShape(Dimens.x4))
            .background(ThemeColors.Background),
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            backgroundColor = ThemeColors.Background,
            contentColor = ThemeColors.OnBackground,
            disabledContentColor = ThemeColors.TextDisabled,
        )
    ) {
        Text(
            text = label.uppercase(),
            color = ThemeColors.OnBackground,
            style = MaterialTheme.typography.neuButton
        )
    }
}
