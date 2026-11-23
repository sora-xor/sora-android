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

package jp.co.soramitsu.common.presentation.compose.neon

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.lit.litCutGlass
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.ui_core.lit.litPress
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonTokens
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.component.button.ButtonLabel
import jp.co.soramitsu.ui_core.component.button.TrailingIconReserve
import jp.co.soramitsu.ui_core.component.button.buttonLabelEnds
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

/** Button roles (design spec 7.1). One Primary, Swap or Safe button per screen. */
enum class NeonVariant { Primary, Secondary, Tertiary, Swap, Safe, Destructive }

/**
 * The default shape: a slab with small corners. `CircleShape` means "round icon button" (minimum width = height),
 * as in ui-core.
 */
val NeonPillShape: Shape = RoundedCornerShape(Lit.ControlRadius)

@Immutable
private data class NeonButtonStyle(
    /** Filled: cut glass of this colour when enabled, this flat colour when not. */
    val fill: Brush?,
    val pressedFill: Brush?,
    val label: Color,
    val edge: Brush?,
)

private fun styleFor(variant: NeonVariant, n: NeonTokens, enabled: Boolean): NeonButtonStyle {
    fun filled(fill: Brush) = if (enabled) {
        NeonButtonStyle(fill, null, n.onAccent, null)
    } else {
        NeonButtonStyle(SolidColor(n.ctaDisabled), null, n.textDisabled, null)
    }
    return when (variant) {
        NeonVariant.Primary -> filled(n.ctaPrimary)
        NeonVariant.Swap -> filled(n.ctaSwap)
        NeonVariant.Safe -> filled(n.ctaSafe)
        // A soft raised pad stands behind it; pressed, the pad is let into the surface.
        NeonVariant.Secondary -> NeonButtonStyle(
            fill = null,
            pressedFill = null,
            label = if (enabled) n.textPrimary else n.textDisabled,
            edge = null,
        )
        NeonVariant.Tertiary -> NeonButtonStyle(
            fill = null,
            pressedFill = SolidColor(n.cyan.copy(alpha = .10f)),
            label = if (enabled) n.link else n.textDisabled,
            edge = null,
        )
        NeonVariant.Destructive -> NeonButtonStyle(
            fill = null,
            pressedFill = null,
            label = if (enabled) n.error else n.textDisabled,
            edge = if (enabled) SolidColor(n.error.copy(alpha = .60f)) else null,
        )
    }
}

/**
 * The app's button (design spec 7.1). Heights: [jp.co.soramitsu.ui_core.component.button.properties.Size]
 * Large 56, Small 40, ExtraSmall 32. Primary, Swap and Safe are slabs of cut glass, set down on the surface when
 * pressed; Secondary and Destructive are soft raised pads; Tertiary is its label alone. Disabled buttons need a
 * reason (in the label or in a line below).
 */
@Composable
fun NeonButton(
    modifier: Modifier = Modifier,
    size: Dp,
    variant: NeonVariant,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    loading: Boolean = false,
    shape: Shape = NeonPillShape,
    onClick: () -> Unit,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val interaction = remember { MutableInteractionSource() }
    val held by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val style = styleFor(variant, n, enabled)
    val pressed = held && enabled && !loading
    val loadingLabel = stringResource(R.string.common_loading)
    val round = shape == CircleShape
    val radius = if (round) size else Lit.ControlRadius
    val glass = (style.fill as? SolidColor)?.value?.takeIf { enabled }
    val padded = variant == NeonVariant.Secondary || variant == NeonVariant.Destructive
    val material = when {
        glass != null -> Modifier.litPress(pressed, scale = .985f, motion = !fx.reduceMotion).litCutGlass(glass, pressed, radius)
        padded && enabled -> Modifier.litPress(pressed, motion = !fx.reduceMotion).litPad(radius, elevation = 8.dp, pressed = pressed)
        style.fill != null -> Modifier.background(style.fill, shape)
        pressed && style.pressedFill != null -> Modifier.background(style.pressedFill, shape)
        else -> Modifier
    }
    val sizeModifier = if (round) {
        Modifier.requiredSizeIn(minWidth = size, minHeight = size)
    } else {
        Modifier.requiredHeightIn(min = size)
    }
    Box(
        // The touch target (and the layout) is at least 48 x 48 dp even when the button is Small
        // (40) or ExtraSmall (32), as with the ui-core buttons (design spec 6, 10.1).
        // clickable comes before minimumInteractiveComponentSize, so it covers the whole 48 dp.
        modifier = modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (loading) stateDescription = loadingLabel }
            .minimumInteractiveComponentSize()
            .then(sizeModifier),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        Row(
            modifier = material
                .then(if (style.edge != null) Modifier.neonBorder(shape, style.edge, fx) else Modifier)
                .then(if (focused) Modifier.border(2.dp, n.focusRing, shape) else Modifier),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leftIcon?.let { painter ->
                Icon(
                    modifier = Modifier.padding(
                        start = text?.let { Dimens.x2 } ?: 0.dp,
                        end = text?.let { Dimens.x1 } ?: 0.dp,
                    ),
                    painter = painter,
                    tint = style.label,
                    contentDescription = null,
                )
            }
            text?.let {
                Box(contentAlignment = Alignment.Center) {
                    ButtonLabel(
                        text = text,
                        modifier = Modifier
                            .padding(vertical = Dimens.x1)
                            .buttonLabelEnds(
                                start = leftIcon?.let { 0.dp } ?: Dimens.x3,
                                end = rightIcon?.let { 0.dp } ?: Dimens.x3,
                                reserve = rightIcon?.let { TrailingIconReserve } ?: 0.dp,
                            )
                            .alpha(if (loading) 0f else 1f),
                        maxLines = maxLines,
                        color = style.label,
                    )
                    if (loading) SignalLoader(color = style.label)
                }
            }
            rightIcon?.let { painter ->
                Icon(
                    modifier = Modifier.padding(
                        start = text?.let { Dimens.x1 } ?: 0.dp,
                        end = text?.let { Dimens.x2 } ?: 0.dp,
                    ),
                    painter = painter,
                    tint = style.label,
                    contentDescription = null,
                )
            }
        }
    }
}

/**
 * The 3-bar "signal" loader that replaces a button label while it works (design spec 7.1).
 * Static under reduce motion and on MINIMAL.
 */
@Composable
private fun SignalLoader(color: Color) {
    val fx = LocalFx.current
    if (!fx.loops) {
        Text(text = "…", color = color, style = MaterialTheme.customTypography.buttonM)
        return
    }
    val transition = rememberInfiniteTransition(label = "signalLoader")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "signalLoaderPhase",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            if (i > 0) Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(width = 10.dp, height = 3.dp)
                    .graphicsLayer {
                        val phase = (t + i / 3f) % 1f
                        alpha = .3f + .7f * (1f - kotlin.math.abs(phase * 2f - 1f))
                    }
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}

/*
 * Drop-ins with the ui-core signatures (jp.co.soramitsu.ui_core.component.button.*): migrate a
 * file by swapping its imports; call sites stay unchanged. They are the ui-core buttons
 * themselves, with ui-core's default shape. SafeFilledButton is the filled button.
 */

@Composable
fun FilledButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.FilledButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)

@Composable
fun SafeFilledButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.FilledButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)

@Composable
fun FilledPolkaswapButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.FilledPolkaswapButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)

@Composable
fun TonalButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    text: String? = null,
    enabled: Boolean = true,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.TonalButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)

@Composable
fun BleachedButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.BleachedButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)

@Composable
fun OutlinedButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.OutlinedButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)

@Composable
fun TextButton(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Lit.ControlRadius),
    size: Dp,
    order: Order,
    text: String? = null,
    enabled: Boolean = true,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = jp.co.soramitsu.ui_core.component.button.TextButton(
    modifier = modifier, shape = shape, size = size, order = order, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, onClick = onClick,
)
