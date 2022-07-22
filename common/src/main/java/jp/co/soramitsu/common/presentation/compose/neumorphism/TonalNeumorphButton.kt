/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.neumorphism

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.ui_core.extensions.withOpacity
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.resources.Padding
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TonalNeumorphButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    onClick: () -> Unit
) {
    var buttonState by remember { mutableStateOf(ButtonState.IDLE) }
    val buttonColor = ButtonDefaults.buttonColors(
        backgroundColor = Color.Transparent,
        contentColor = MaterialTheme.customColors.accentPrimary,
        disabledBackgroundColor = MaterialTheme.customColors.fgPrimary.withOpacity(0.04f),
        disabledContentColor = MaterialTheme.customColors.fgPrimary.withOpacity(0.16f)
    )
    Box(
        modifier = modifier
            .padding(Padding.x2)
            .fillMaxWidth()
            .height(Dimens.ButtonHeight)
            .noRippleClickable(enabled = enabled)
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        awaitFirstDown()
                        buttonState = ButtonState.PRESSED
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            buttonState = ButtonState.IDLE
                            onClick()
                        }
                    }
                }
            }
    ) {
        NeumorphWrap(
            enabled = enabled,
            buttonState = buttonState
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(buttonColor.backgroundColor(enabled).value),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                leftIcon?.let { painter ->
                    Icon(
                        modifier = Modifier.padding(end = Padding.x1),
                        painter = painter,
                        tint = buttonColor.contentColor(enabled).value,
                        contentDescription = null
                    )
                }

                Text(
                    text = text.uppercase(),
                    style = MaterialTheme.customTypography.buttonM,
                    color = buttonColor.contentColor(enabled).value,
                )

                rightIcon?.let { painter ->
                    Icon(
                        modifier = Modifier.padding(start = Padding.x1),
                        painter = painter,
                        tint = buttonColor.contentColor(enabled).value,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun NeumorphWrap(
    modifier: Modifier = Modifier,
    buttonState: ButtonState,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    } else {
        when (buttonState) {
            ButtonState.IDLE -> {
                NeuCardPunched(
                    modifier = modifier,
                    radius = 28
                ) {
                    content()
                }
            }
            ButtonState.PRESSED -> {
                NeuCardPressed(
                    modifier = modifier,
                    radius = 28
                ) {
                    content()
                }
            }
        }
    }
}

@Preview
@Composable
private fun PreviewTonalNeumorphButton() {
    SoraAppTheme {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            TonalNeumorphButton(
                text = "Button",
                onClick = {},
                enabled = true
            )

            TonalNeumorphButton(
                text = "Button",
                onClick = {},
                enabled = false
            )
        }
    }
}
