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

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.lit.litPad
import jp.co.soramitsu.ui_core.lit.litPress

/**
 * A soft pad for thumb-pressed secondary controls only: PIN keys, icon buttons and the flip button (design spec
 * 4.6). Raised from the surface at rest; a press sets it down and lets it into the surface. Never for cards or
 * the primary action.
 *
 * Depth alone fails WCAG 1.4.11, so [content] must always carry a digit, an icon or a ring. The pad is a baked
 * image shared by all keys of one size. Pair it with `Modifier.clickable(interactionSource, indication = null)`:
 * the press is the feedback.
 */
@Composable
fun NeumorphicSurface(
    shape: Shape,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val fx = LocalFx.current
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    Box(
        modifier
            .litPress(pressed, motion = !fx.reduceMotion)
            .litPad(shape, pressed = pressed)
            .then(if (focused) Modifier.border(1.5.dp, MaterialTheme.neon.focusRing, shape) else Modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
