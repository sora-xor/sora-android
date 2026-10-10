/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.presentation.compose.neon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon

/**
 * Neumorphic face for thumb-pressed secondary controls only: PIN keys, quick-action discs,
 * icon buttons, the flip button and segmented tracks (design spec 4.6). Never for cards or the
 * primary CTA.
 *
 * Depth alone fails WCAG 1.4.11, so [content] must always carry a digit, an icon or a ring.
 * Outer and inner shadows are static cached bitmaps (all keys of one size share them); only the
 * layer alpha and the 0.96 press scale animate. Pair it with
 * `Modifier.clickable(interactionSource, indication = null)`: the inset is the feedback.
 */
@Composable
fun NeumorphicSurface(
    shape: Shape,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val pressed by interactionSource.collectIsPressedAsState()
    val p by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) NeonMotion.PRESS else NeonMotion.FAST),
        label = "neu",
    )
    val rich = fx.shadows
    Box(
        modifier.graphicsLayer {
            val s = 1f - .04f * p
            scaleX = s
            scaleY = s
        },
        contentAlignment = Alignment.Center,
    ) {
        if (rich) {
            Box(
                Modifier
                    .matchParentSize()
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        alpha = 1f - p
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .dropShadow(shape, n.neuDark)
                    .dropShadow(shape, n.neuLight),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics { }
                .clip(shape)
                .background(if (rich) n.neuFace else SolidColor(n.surface), shape)
                .drawWithContent {
                    drawContent()
                    if (p > 0f) drawRect(n.neuFacePressed, alpha = p)
                }
                .border(1.dp, if (rich) n.neuRim else SolidColor(n.hairline), shape),
        )
        if (rich) {
            Box(
                Modifier
                    .matchParentSize()
                    .clearAndSetSemantics { }
                    .graphicsLayer {
                        alpha = p
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .innerShadow(shape, n.neuInnerDark)
                    .innerShadow(shape, n.neuInnerLight),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics { }
                .graphicsLayer { alpha = p }
                .border(1.5.dp, n.focusRing.copy(alpha = .8f), shape),
        )
        content()
    }
}
