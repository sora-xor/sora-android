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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonColors
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun ButtonNeumorph(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors(),
    onClick: () -> Unit,
    neumorphWrap: @Composable (ButtonState, ButtonColors, content: @Composable (ButtonState) -> Unit) -> Unit,
) {
    var buttonState by remember { mutableStateOf(ButtonState.IDLE) }

    Box(
        modifier = modifier
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
                            onClick()
                        }
                        buttonState = ButtonState.IDLE
                    }
                }
            }
    ) {
        neumorphWrap(buttonState, buttonColors) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(buttonColors.backgroundColor(enabled).value),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                leftIcon?.let { painter ->
                    Icon(
                        modifier = Modifier.padding(end = Dimens.x1),
                        painter = painter,
                        tint = buttonColors.contentColor(enabled).value,
                        contentDescription = null
                    )
                }

                text?.let {
                    Text(
                        text = text.uppercase(),
                        style = MaterialTheme.customTypography.buttonM,
                        color = buttonColors.contentColor(enabled).value,
                    )
                }

                rightIcon?.let { painter ->
                    Icon(
                        modifier = Modifier.padding(start = Dimens.x1),
                        painter = painter,
                        tint = buttonColors.contentColor(enabled).value,
                        contentDescription = null
                    )
                }
            }
        }
    }
}
