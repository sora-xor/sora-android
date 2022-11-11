/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.neumorphism

import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.NeuColorsCompat
import jp.co.soramitsu.common.presentation.compose.theme.neuButton
import jp.co.soramitsu.ui_core.theme.customColors

@ExperimentalComposeUiApi
@Preview
@Composable
@Deprecated("use neumorph component buttons")
private fun PreviewNeumorphButton() {
    NeumorphButton(
        label = "Label",
        textStyle = MaterialTheme.typography.neuButton,
        onClick = {}
    )
}

@ExperimentalComposeUiApi
@Composable
@Deprecated("use neumorph component buttons")
fun NeumorphButton(
    modifier: Modifier = Modifier,
    label: String,
    textStyle: TextStyle,
    icon: Int,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    NeumorphButtonShape(
        modifier = modifier,
        label = label,
        icon = icon,
        textColor = textColor,
        textStyle = textStyle,
        backgroundColor = backgroundColor,
        onClick = onClick
    )
}

@ExperimentalComposeUiApi
@Composable
@Deprecated("use neumorph component buttons")
fun NeumorphButton(
    modifier: Modifier = Modifier,
    label: String,
    textStyle: TextStyle,
    textColor: Color = MaterialTheme.customColors.fgPrimary,
    onClick: () -> Unit
) {
    NeumorphButtonShape(
        modifier = modifier,
        label = label,
        textStyle = textStyle,
        textColor = textColor,
        onClick = onClick
    )
}

@ExperimentalComposeUiApi
@Composable
private fun NeumorphButtonShape(
    modifier: Modifier = Modifier,
    label: String,
    textStyle: TextStyle,
    icon: Int = -1,
    backgroundColor: Color = MaterialTheme.customColors.bgSurface,
    textColor: Color = MaterialTheme.customColors.fgPrimary,
    onClick: () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        var buttonState by remember { mutableStateOf(ButtonState.IDLE) }

        val buttonWidth = with(LocalDensity.current) {
            maxWidth.roundToPx()
        }
        val buttonHeight = with(LocalDensity.current) {
            Dimens.x7.roundToPx()
        }

        val buttonSize = remember {
            IntSize(
                width = buttonWidth,
                height = buttonHeight
            )
        }

        Shadow(
            modifier = Modifier
                .align(Alignment.Center),
            backgroundColor = NeuColorsCompat.Content,
            buttonState = buttonState,
            buttonSize = buttonSize
        )

        Box(
            modifier = Modifier
                .clickable { }
                .pointerInteropFilter {
                    when (it.action) {
                        MotionEvent.ACTION_DOWN -> {
                            buttonState = ButtonState.PRESSED
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            buttonState = ButtonState.IDLE
                            onClick()
                            true
                        }
                        else -> {
                            true
                        }
                    }
                }
                .fillMaxWidth()
                .height(Dimens.x7)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(Dimens.x4))
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Row {
                if (icon != -1) {
                    Icon(
                        modifier = Modifier.padding(top = Dimens.x05, end = Dimens.x1),
                        painter = painterResource(id = icon),
                        tint = textColor,
                        contentDescription = null
                    )
                }

                Text(
                    text = label.uppercase(),
                    color = textColor,
                    style = textStyle
                )
            }
        }
    }
}

@Composable
private fun Shadow(
    modifier: Modifier = Modifier,
    backgroundColor: Int,
    buttonState: ButtonState,
    buttonSize: IntSize
) {
    val cornerRadius = with(LocalDensity.current) { Dimens.x4.toPx() }
    val lightShadowTopOffset = with(LocalDensity.current) { 8.dp.toPx() }
    val lightShadowLeftOffset = with(LocalDensity.current) { 2.dp.toPx() }
    val darkShadowTopOffset = with(LocalDensity.current) { 24.dp.toPx() }
    val darkShadowLeftOffset = with(LocalDensity.current) { 8.dp.toPx() }

    val offset = with(LocalDensity.current) { 4.dp.toPx() }

    val value by remember(key1 = buttonState) {
        mutableStateOf(
            when (buttonState) {
                ButtonState.IDLE -> 0f
                ButtonState.PRESSED -> 1f
            }
        )
    }

    val shadowSize = remember {
        buttonSize.toSize()
    }
    val lightRect = remember(key1 = value) {
        RectF(0f, 0f, shadowSize.width, shadowSize.height).apply {
            offset(
                lightShadowLeftOffset + offset * value,
                lightShadowTopOffset + offset * value
            )
        }
    }
    val darkRect = remember(key1 = value) {
        RectF(0f, 0f, shadowSize.width, shadowSize.height).apply {
            offset(
                darkShadowLeftOffset + offset * value,
                darkShadowTopOffset - offset * value
            )
        }
    }

    val extra = with(LocalDensity.current) { 32.dp.roundToPx() }
    val bitMapSize = remember {
        IntSize(
            buttonSize.width + extra,
            buttonSize.height + extra
        )
    }
    val bitmap = createBitmap(bitMapSize.width, bitMapSize.height)

    val paint = remember {
        Paint().apply {
            flags = Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG
        }
    }

    val result = bitmap.applyCanvas {
        drawColor(backgroundColor)
        drawRoundRect(
            darkRect,
            cornerRadius,
            cornerRadius,
            paint.apply { color = NeuColorsCompat.ShadowDark }
        )
        drawRoundRect(
            lightRect,
            cornerRadius,
            cornerRadius,
            paint.apply { color = NeuColorsCompat.ShadowLight }
        )
    }.let {
        fastblur(it, scale = 1f, radius = 24)
    }

    Canvas(
        modifier = modifier.then(
            with(LocalDensity.current) {
                Modifier.size(
                    bitMapSize.width.toDp(),
                    bitMapSize.height.toDp()
                )
            }
        )
    ) {
        result?.let {
            drawImage(it.asImageBitmap())
        }
    }
}
