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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.ui_core.lit.Lit
import jp.co.soramitsu.ui_core.theme.borderRadius
import jp.co.soramitsu.ui_core.theme.customColors

/**
 * A calm card (design spec 4.3.1, 7.3): a sheet of frosted glass with small corners.
 *
 * [backgroundColor] defaults to [Color.Unspecified], which means the glass's own milk. An
 * explicit colour renders as an opaque sheet of that colour (QR plates rely on solid white).
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    NeonCardSurface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.coerceAtMost(Lit.SheetRadius)),
        innerPadding = innerPadding,
        backgroundColor = backgroundColor,
        onClick = onClick,
        content = content,
    )
}

/** Financial review and secret surfaces never show content through their fill. */
@Composable
fun OpaqueContentCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = MaterialTheme.neon.glassSolid,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassCard(modifier, cornerRadius, innerPadding, backgroundColor, onClick = onClick, content = content)

/** Backup/import surfaces are opaque. */
@Composable
fun SafeContentCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = MaterialTheme.neon.glassSolid,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassCard(modifier, cornerRadius, innerPadding, backgroundColor, onClick = onClick, content = content)

/**
 * Drop-in replacement for `jp.co.soramitsu.ui_core.component.card.ContentCard` with the same
 * parameter names and order: migrate by swapping the import. It is the ui-core card itself, a
 * sheet of frosted glass. [Color.Unspecified] is the surface colour.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MaterialTheme.borderRadius.ml,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    backgroundColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = jp.co.soramitsu.ui_core.component.card.ContentCard(
    modifier = modifier,
    cornerRadius = cornerRadius,
    innerPadding = innerPadding,
    backgroundColor = backgroundColor.takeOrElse { MaterialTheme.customColors.bgSurface },
    onClick = onClick,
    content = content,
)

/**
 * Drop-in replacement for `jp.co.soramitsu.ui_core.component.card.ContentCardEndless`, drawn as
 * the ui-core sheet itself.
 */
@Composable
fun ContentCardEndless(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = MaterialTheme.borderRadius.xl,
    innerPadding: PaddingValues = PaddingValues(0.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = jp.co.soramitsu.ui_core.component.card.ContentCardEndless(
    modifier = modifier,
    cornerRadius = cornerRadius,
    innerPadding = innerPadding,
    onClick = onClick,
    content = content,
)

/** Balance/receive hero: a plain card. */
@Composable
fun HeroCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = ContentCard(modifier = modifier, onClick = onClick, content = content)

@Composable
private fun NeonCardSurface(
    modifier: Modifier,
    shape: Shape,
    innerPadding: PaddingValues,
    backgroundColor: Color,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit,
) {
    val n = MaterialTheme.neon
    val legacy = backgroundColor.isSpecified
    val contentColor = if (legacy) {
        contentColorFor(backgroundColor).takeOrElse { n.textPrimary }
    } else {
        n.textPrimary
    }
    Box(
        modifier
            .neonGlass(shape, opaqueColor = backgroundColor.takeIf { legacy })
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(innerPadding),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}
