/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.neumorphism

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonColors
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.theme.customColors

@Composable
fun OutlinedNeumorphButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    onClick: () -> Unit
) {
    ButtonNeumorph(
        modifier = modifier,
        enabled = enabled,
        text = text,
        leftIcon = leftIcon,
        rightIcon = rightIcon,
        onClick = onClick,
        buttonColors = ButtonDefaults.buttonColors(
            backgroundColor = Color.Transparent,
            contentColor = MaterialTheme.customColors.accentPrimary,
            disabledBackgroundColor = Color.Transparent,
            disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(0.16f)
        ),
        neumorphWrap = { buttonState, buttonColors, content ->
            NeumorphOutlinedWrap(
                enabled = enabled,
                buttonState = buttonState,
                buttonColors = buttonColors
            ) {
                content(buttonState)
            }
        }
    )
}

@Composable
private fun NeumorphOutlinedWrap(
    modifier: Modifier = Modifier,
    buttonState: ButtonState,
    buttonColors: ButtonColors,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (enabled && buttonState == ButtonState.PRESSED) {
        NeuCardPressed(
            modifier = modifier
                .border(
                    width = 1.dp,
                    color = buttonColors.contentColor(enabled).value,
                    shape = RoundedCornerShape(28.dp)
                ),
            radius = 28
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .border(
                    width = 1.dp,
                    color = buttonColors.contentColor(enabled).value,
                    shape = RoundedCornerShape(28.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun PreviewOutlinedNeumorphButton() {
    SoraAppTheme {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedNeumorphButton(
                text = "Button",
                onClick = {},
                enabled = true
            )

            OutlinedNeumorphButton(
                text = "Button",
                onClick = {},
                enabled = false
            )
        }
    }
}
