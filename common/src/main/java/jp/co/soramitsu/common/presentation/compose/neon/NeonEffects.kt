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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxPolicy
import jp.co.soramitsu.common.presentation.compose.theme.neon.NeonTokens
import jp.co.soramitsu.ui_core.lit.litPage
import jp.co.soramitsu.ui_core.lit.litPlate

/*
 * Surface modifiers (design spec 4). Nothing here uses RenderEffect or a backdrop blur, so it renders
 * the same on API 26+.
 */

/**
 * The page behind all content, as a full-size layer: washi, light falling across it from the top left, and the
 * shippō print near its top (`litPage`). Put it first in a `Box`; it is decorative and has no semantics.
 */
@Composable
fun NeonBackground(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .litPage(),
    )
}

/**
 * A sheet of frosted glass for cards and panels, or a soft pad when it is small, clipped to [shape]. An explicit
 * [opaqueColor] is a sheet of that colour (secrets, review surfaces and QR plates).
 */
fun Modifier.neonGlass(shape: Shape, opaqueColor: Color? = null): Modifier =
    litPlate(shape, face = opaqueColor).clip(shape)

/**
 * Gradient hairline (design spec 4.4). 1 dp, 2 dp under high contrast. The brush runs corner to
 * corner and is never mirrored in RTL.
 */
fun Modifier.neonBorder(shape: Shape, brush: Brush, fx: FxPolicy, width: Dp = 1.dp): Modifier =
    border(if (fx.highContrast) width * 2 else width, brush, shape)

/**
 * Android chrome (design spec 4.3.2): bars stay transparent at the top of a scroll and fade to
 * the chrome tint once content scrolls under them. [visibility] is read in the draw phase only,
 * so animating it does not recompose.
 */
fun Modifier.neonChrome(n: NeonTokens, visibility: () -> Float): Modifier = drawBehind {
    val a = visibility().coerceIn(0f, 1f)
    if (a > 0f) drawRect(n.chromeTint, alpha = a)
}
