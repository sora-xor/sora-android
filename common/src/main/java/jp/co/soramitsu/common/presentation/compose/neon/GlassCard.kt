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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon

/**
 * A calm "fake glass" card (design spec 4.3.1, 7.3): translucent fill, top sheen and a gradient
 * hairline; a soft shadow in light only. Never glows and never uses neumorphic shadows.
 *
 * [backgroundColor] defaults to [Color.Unspecified], which means glass. An explicit colour keeps
 * legacy call sites safe: it renders as an opaque fill (QR plates rely on solid white).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = Color.Unspecified,
    border: Brush = MaterialTheme.neon.borderGlass,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    NeonCardSurface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        innerPadding = innerPadding,
        backgroundColor = backgroundColor,
        border = border,
        onClick = onClick,
        content = content,
    )
}

/**
 * Drop-in replacement for `jp.co.soramitsu.ui_core.component.card.ContentCard` with the same
 * parameter names and order: migrate by swapping the import. It removes ui-core's grey 10 dp
 * shadow that depends on the system (not the in-app) dark mode. The default radius is 24 (it was
 * 28); that change is intended by the design.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassCard(
    modifier = modifier,
    cornerRadius = cornerRadius,
    innerPadding = innerPadding,
    backgroundColor = backgroundColor,
    onClick = onClick,
    content = content,
)

/**
 * Drop-in replacement for `jp.co.soramitsu.ui_core.component.card.ContentCardEndless`: a glass
 * sheet with rounded top corners only (28, like sheets).
 */
@Composable
fun ContentCardEndless(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = NeonCardSurface(
    modifier = modifier,
    shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
    innerPadding = innerPadding,
    backgroundColor = Color.Unspecified,
    border = MaterialTheme.neon.borderGlass,
    onClick = onClick,
    content = content,
)

@Composable
private fun NeonCardSurface(
    modifier: Modifier,
    shape: Shape,
    innerPadding: PaddingValues,
    backgroundColor: Color,
    border: Brush,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val legacy = backgroundColor.isSpecified
    val contentColor = if (legacy) {
        contentColorFor(backgroundColor).takeOrElse { n.textPrimary }
    } else {
        n.textPrimary
    }
    Box(
        modifier
            .neonGlass(shape, n, fx, border, opaqueColor = backgroundColor.takeIf { legacy })
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(innerPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
