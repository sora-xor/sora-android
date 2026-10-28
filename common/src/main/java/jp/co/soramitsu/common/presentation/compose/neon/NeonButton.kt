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
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.theme.neon.GlowSpec
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonTokens
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customTypography

/** Button roles (design spec 7.1). One Primary, Swap or Safe button per screen. */
enum class NeonVariant { Primary, Secondary, Tertiary, Swap, Safe, Destructive }

/**
 * A pill. Not `CircleShape`: that one means "round icon button" (minimum width = height), as in
 * ui-core.
 */
val NeonPillShape: Shape = RoundedCornerShape(999.dp)

@Immutable
private data class NeonButtonStyle(
    val fill: Brush?,
    val pressedFill: Brush?,
    val label: Color,
    val edge: Brush?,
    val glow: GlowSpec?,
    val pressedOverlay: Color?,
)

private fun styleFor(variant: NeonVariant, n: NeonTokens, enabled: Boolean): NeonButtonStyle {
    val ink = if (n.isDark) n.onAccent.copy(alpha = .08f) else Color(0xFF0B0C14).copy(alpha = .10f)
    fun filled(fill: Brush, glow: GlowSpec) = if (enabled) {
        NeonButtonStyle(fill, null, n.onAccent, n.borderCtaSheen, glow, ink)
    } else {
        NeonButtonStyle(SolidColor(n.ctaDisabled), null, n.textDisabled, null, null, null)
    }
    return when (variant) {
        NeonVariant.Primary -> filled(n.ctaPrimary, n.glowPrimary)
        NeonVariant.Swap -> filled(n.ctaSwap, n.glowSwap)
        NeonVariant.Safe -> filled(n.ctaSafe, n.glowSafe)
        NeonVariant.Secondary -> NeonButtonStyle(
            fill = SolidColor(n.glassFill),
            pressedFill = SolidColor(n.glassFillPressed),
            label = if (enabled) n.textPrimary else n.textDisabled,
            edge = if (enabled) n.borderGlass else null,
            glow = null,
            pressedOverlay = null,
        )
        NeonVariant.Tertiary -> NeonButtonStyle(
            fill = null,
            pressedFill = SolidColor(n.cyan.copy(alpha = .10f)),
            label = if (enabled) n.link else n.textDisabled,
            edge = null,
            glow = null,
            pressedOverlay = null,
        )
        NeonVariant.Destructive -> NeonButtonStyle(
            fill = SolidColor(n.glassFill),
            pressedFill = SolidColor(n.errorContainer),
            label = if (enabled) n.error else n.textDisabled,
            edge = if (enabled) SolidColor(n.error.copy(alpha = .60f)) else null,
            glow = null,
            pressedOverlay = null,
        )
    }
}

/**
 * SORA Neon button (design spec 7.1). Heights: [jp.co.soramitsu.ui_core.component.button.properties.Size]
 * Large 56, Small 40, ExtraSmall 32. Disabled buttons need a reason (in the label or in a line
 * below). [pulse] is only for the two whitelisted CTAs of design spec 4.7.2.
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
    pulse: Boolean = false,
    shape: Shape = NeonPillShape,
    onClick: () -> Unit,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val style = styleFor(variant, n, enabled)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading && !fx.reduceMotion) .98f else 1f,
        animationSpec = tween(if (fx.reduceMotion) 0 else NeonMotion.PRESS),
        label = "neonButtonScale",
    )
    val pulseAlpha = rememberCtaPulse(
        enabled = enabled && !loading,
        whitelisted = pulse && variant == NeonVariant.Primary,
        rest = style.glow?.rest ?: 0f,
        peak = style.glow?.peak ?: 0f,
    )
    val loadingLabel = stringResource(R.string.common_loading)
    val sizeModifier = if (shape == CircleShape) {
        Modifier.requiredSizeIn(minWidth = size, minHeight = size)
    } else {
        Modifier.requiredHeightIn(min = size)
    }
    Box(
        // The touch target (and the layout) is at least 48 x 48 dp even when the pill is Small
        // (40) or ExtraSmall (32), as with the ui-core Material buttons (design spec 6, 10.1).
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
        val glow = style.glow
        if (glow != null) {
            NeonGlow(
                spec = glow,
                shape = shape,
                modifier = Modifier.matchParentSize(),
                alpha = {
                    when {
                        loading -> .35f
                        pressed -> glow.pressed
                        else -> pulseAlpha()
                    }
                },
            )
        }
        Row(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(shape)
                .then(
                    when {
                        pressed && style.pressedFill != null -> Modifier.background(style.pressedFill, shape)
                        style.fill != null -> Modifier.background(style.fill, shape)
                        else -> Modifier
                    },
                )
                .drawWithContent {
                    drawContent()
                    if (pressed && style.pressedOverlay != null) drawRect(style.pressedOverlay)
                }
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
                    Text(
                        modifier = Modifier
                            .padding(
                                start = leftIcon?.let { 0.dp } ?: Dimens.x3,
                                end = rightIcon?.let { 0.dp } ?: Dimens.x3,
                                top = Dimens.x1,
                                bottom = Dimens.x1,
                            )
                            .alpha(if (loading) 0f else 1f),
                        textAlign = TextAlign.Center,
                        text = text,
                        style = MaterialTheme.customTypography.buttonM,
                        color = style.label,
                        maxLines = maxLines,
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

private fun Order.toNeon(): NeonVariant = when (this) {
    Order.PRIMARY -> NeonVariant.Primary
    Order.SECONDARY, Order.TERTIARY -> NeonVariant.Secondary
}

/*
 * Drop-ins with the ui-core signatures (jp.co.soramitsu.ui_core.component.button.*): migrate a
 * file by swapping its imports; call sites stay unchanged. Mapping (design spec 7.1):
 * FilledButton PRIMARY -> Primary, SECONDARY/TERTIARY -> Secondary; FilledPolkaswapButton ->
 * Swap; TonalButton, BleachedButton, OutlinedButton -> Secondary; TextButton -> Tertiary.
 */

@Composable
fun FilledButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = order.toNeon(), enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)

@Composable
fun SafeFilledButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = if (order == Order.PRIMARY) NeonVariant.Safe else NeonVariant.Secondary, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)

@Composable
fun FilledPolkaswapButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = NeonVariant.Swap, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)

@Composable
fun TonalButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    text: String? = null,
    enabled: Boolean = true,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = NeonVariant.Secondary, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)

@Composable
fun BleachedButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = NeonVariant.Secondary, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)

@Composable
fun OutlinedButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    enabled: Boolean = true,
    text: String? = null,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = NeonVariant.Secondary, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)

@Composable
fun TextButton(
    modifier: Modifier = Modifier,
    shape: Shape = NeonPillShape,
    size: Dp,
    order: Order,
    text: String? = null,
    enabled: Boolean = true,
    leftIcon: Painter? = null,
    rightIcon: Painter? = null,
    maxLines: Int = 1,
    onClick: () -> Unit,
) = NeonButton(
    modifier = modifier, size = size, variant = NeonVariant.Tertiary, enabled = enabled, text = text,
    leftIcon = leftIcon, rightIcon = rightIcon, maxLines = maxLines, shape = shape, onClick = onClick,
)
