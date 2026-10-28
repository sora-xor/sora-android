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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.LayoutDirection
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon

/** Shared shimmer clock: progress in [-0.4, 1.4], or -1 for a static skeleton. */
val LocalShimmer = staticCompositionLocalOf<() -> Float> { { -1f } }

/**
 * One shimmer clock per screen (design spec 4.7.5): 1200 ms sweep + 300 ms pause, so every
 * skeleton sweeps in sync. Static under reduce motion and on MINIMAL.
 */
@Composable
fun ProvideShimmer(content: @Composable () -> Unit) {
    val fx = LocalFx.current
    if (!fx.loops) {
        CompositionLocalProvider(LocalShimmer provides { -1f }, content = content)
        return
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x = transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(1200, delayMillis = 300, easing = LinearEasing)),
        label = "shimmerX",
    )
    CompositionLocalProvider(LocalShimmer provides { x.value }, content = content)
}

/**
 * Layout-matched loading placeholder. Mirror the final layout (same radii and row heights);
 * never show "0.00" while a value is unknown.
 */
fun Modifier.skeleton(shape: Shape): Modifier = composed {
    val n = MaterialTheme.neon
    val progress = LocalShimmer.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    var x0 by remember { mutableFloatStateOf(0f) }
    this
        .onGloballyPositioned { x0 = it.positionInRoot().x }
        .clip(shape)
        .drawBehind {
            drawRect(n.skeletonBase)
            val raw = progress()
            if (raw < 0f) return@drawBehind
            val p = if (rtl) 1f - raw else raw
            val width = if (windowWidth > 0f) windowWidth else size.width
            val cx = p * width - x0
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, n.skeletonHighlight, Color.Transparent),
                    start = Offset(cx - .2f * width, 0f),
                    end = Offset(cx + .2f * width, size.height * .36f),
                ),
            )
        }
}
