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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
import jp.co.soramitsu.common.presentation.compose.theme.neon.GlowSpec
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.shadow.Shadow as BoxShadow

/** Motion tokens (design spec 4.7.1). */
object NeonMotion {
    const val PRESS = 90
    const val FAST = 160
    const val BASE = 240
    const val SLOW = 320
    const val HERO = 500

    val EaseStandard = CubicBezierEasing(.2f, 0f, 0f, 1f)
    val EaseDecel = CubicBezierEasing(.05f, .7f, .1f, 1f)
    val EaseExit = CubicBezierEasing(.3f, 0f, 1f, 1f)
    val EaseSine = CubicBezierEasing(.37f, 0f, .63f, 1f)
}

/**
 * A glow drawn as a sibling layer behind a control of the same size (design spec 4.5).
 *
 * The shadow bitmap is static and cached; only the layer alpha changes ([alpha] is read in the
 * draw phase), so a pulse never re-rasterises the blur. The parent must not clip, and must leave
 * room for blur/2 + |offsetY| around the control. MINIMAL draws a 1.5 dp ring instead; high
 * contrast draws nothing. Never use this inside a lazy list item.
 */
@Composable
fun NeonGlow(
    spec: GlowSpec,
    shape: Shape,
    modifier: Modifier = Modifier,
    alpha: () -> Float = { spec.rest },
) {
    val fx = LocalFx.current
    if (!fx.glows) return
    if (fx.tier == FxTier.MINIMAL) {
        Box(modifier.clearAndSetSemantics { }.border(1.5.dp, spec.color.copy(alpha = .8f), shape))
        return
    }
    val shadow = remember(spec) {
        BoxShadow(radius = spec.blur, color = spec.color, offset = DpOffset(0.dp, spec.offsetY))
    }
    Box(
        modifier
            .clearAndSetSemantics { }
            .graphicsLayer {
                this.alpha = alpha().coerceIn(0f, 1f)
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .dropShadow(shape, shadow),
    )
}

/**
 * The finite primary-CTA pulse (design spec 4.7.2): glow alpha 0.45 -> 0.70 -> 0.45, 2.4 s per
 * cycle, exactly three cycles starting 600 ms after the CTA is enabled. FULL tier only, never
 * under reduce motion, and only where [whitelisted] (Welcome "Create new wallet" and the
 * empty-wallet "Receive" CTA). Never on money, PIN or Safe Mode screens.
 */
@Composable
fun rememberCtaPulse(enabled: Boolean, whitelisted: Boolean, rest: Float = .45f, peak: Float = .70f): () -> Float {
    val fx = LocalFx.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(enabled, whitelisted, fx.pulses) {
        progress.snapTo(0f)
        if (!enabled || !whitelisted || !fx.pulses) return@LaunchedEffect
        delay(600)
        repeat(3) {
            progress.animateTo(1f, tween(1200, easing = NeonMotion.EaseSine))
            progress.animateTo(0f, tween(1200, easing = NeonMotion.EaseSine))
        }
    }
    return { rest + (peak - rest) * progress.value }
}
