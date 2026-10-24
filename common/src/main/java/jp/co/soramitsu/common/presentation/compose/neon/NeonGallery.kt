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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.theme.neon.FxTier
import jp.co.soramitsu.common.presentation.compose.theme.neon.GlowSpec
import jp.co.soramitsu.common.presentation.compose.theme.neon.LocalFx
import jp.co.soramitsu.common.presentation.compose.theme.neon.designBlur
import jp.co.soramitsu.common.presentation.compose.theme.neon.neon
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.theme.customTypography

/**
 * Debug-only SORA Neon parity screen (design spec 11.3): tokens, glows, neumorphic key, glass
 * over aurora, the HeroCard, text styles, PIN dots and every button variant, in the current
 * theme and effect tier. Used to review and calibrate the foundation before screens migrate.
 * Labels are developer-facing and intentionally not translated.
 */
@Composable
fun NeonGallery(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    Box(modifier.fillMaxSize()) {
        NeonBackground()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NeonButton(size = Size.Small, variant = NeonVariant.Tertiary, text = "Close", onClick = onClose)
            Caption(
                "Tier ${fx.tier} · motion ${if (fx.reduceMotion) "reduced" else "on"} · " +
                    "glass ${if (fx.opaqueGlass) "opaque" else "fake"} · high contrast ${fx.highContrast} · " +
                    "designBlur(24) = ${designBlur(24f)}",
            )

            Section("Colour tokens") {
                val swatches = listOf(
                    "canvas" to n.canvas, "surface" to n.surface, "variant" to n.surfaceVariant,
                    "text 1" to n.textPrimary, "text 2" to n.textSecondary, "text 3" to n.textTertiary,
                    "red" to n.brandRed, "cyan" to n.cyan, "magenta" to n.magenta,
                    "success" to n.success, "warning" to n.warning, "error" to n.error,
                )
                swatches.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (name, color) -> Swatch(name, color, Modifier.weight(1f)) }
                    }
                }
            }

            Section("Glow (B 8 / 12 / 24)") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    GlowSample(n.glowDot)
                    GlowSample(n.glowFocus)
                    GlowSample(n.glowPrimary)
                }
            }

            Section("Neumorphic key and PIN dots") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val interaction = remember { MutableInteractionSource() }
                    NeumorphicSurface(
                        shape = CircleShape,
                        interactionSource = interaction,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable(interactionSource = interaction, indication = null) { },
                    ) {
                        Text("5", style = NeonType.pinDigit, color = n.textPrimary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PinDots(count = 4, filled = 2)
                        PinDots(count = 6, filled = 3)
                    }
                }
            }

            GlassCard(innerPadding = PaddingValues(16.dp)) {
                Column {
                    Text("GlassCard", style = MaterialTheme.customTypography.headline2, color = n.textPrimary)
                    Text(
                        "Translucent fill, top sheen and a gradient hairline. No blur.",
                        style = MaterialTheme.customTypography.textS,
                        color = n.textSecondary,
                    )
                }
            }

            HeroSample()

            Section("Buttons") {
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Primary, text = "Create new wallet", pulse = true) { }
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Secondary, text = "I already have a wallet") { }
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Safe, text = "Try again") { }
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Swap, text = "Swap") { }
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Destructive, text = "Remove account") { }
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Primary, enabled = false, text = "Enter an amount") { }
                NeonButton(Modifier.fillMaxWidth(), Size.Large, NeonVariant.Primary, loading = true, text = "Sending") { }
                NeonButton(size = Size.Small, variant = NeonVariant.Tertiary, text = "Technical details") { }
            }

            Section("Skeleton") {
                ProvideShimmer {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.fillMaxWidth().height(20.dp).skeleton(RoundedCornerShape(6.dp)))
                        Box(Modifier.width(160.dp).height(40.dp).skeleton(RoundedCornerShape(8.dp)))
                    }
                }
            }

            Section("Type") {
                val t = MaterialTheme.customTypography
                val styles: List<Pair<String, TextStyle>> = listOf(
                    "display.hero 12,480.52" to NeonType.displayHero,
                    "displayL" to t.displayL, "headline1" to t.headline1, "headline2" to t.headline2,
                    "headline3" to t.headline3, "TOTAL BALANCE" to NeonType.eyebrow, "buttonM" to t.buttonM,
                    "textM body" to t.textM, "textS secondary" to t.textS, "textXS caption" to t.textXS,
                    "amount.M 1,204.55 XOR" to NeonType.amountM, "mono cnUJ6X…Jfq7a14M" to NeonType.mono,
                )
                styles.forEach { (label, style) -> Text(label, style = style, color = n.textPrimary) }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = NeonType.eyebrow, color = MaterialTheme.neon.textSecondary)
        content()
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.customTypography.textXS, color = MaterialTheme.neon.textSecondary)
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier) {
    val n = MaterialTheme.neon
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(1.dp, n.hairline, RoundedCornerShape(8.dp)),
        )
        Text(name, style = MaterialTheme.customTypography.textXS, color = n.textSecondary)
    }
}

@Composable
private fun GlowSample(spec: GlowSpec) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        NeonGlow(spec, CircleShape, Modifier.size(40.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(spec.color))
    }
}

@Composable
private fun PinDots(count: Int, filled: Int) {
    val n = MaterialTheme.neon
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
                if (i < filled) {
                    NeonGlow(n.glowDot, CircleShape, Modifier.size(14.dp))
                    Box(Modifier.size(14.dp).clip(CircleShape).background(n.cyan))
                } else {
                    Box(Modifier.size(14.dp).border(1.5.dp, n.textTertiary, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun HeroSample() {
    val n = MaterialTheme.neon
    val fx = LocalFx.current
    val shape = remember { ChamferShape() }
    val heroGlow = n.heroTextGlow?.takeIf { fx.glows && fx.tier != FxTier.MINIMAL }
    val glowRadius = with(LocalDensity.current) { designBlur(16f).toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .neonGlass(shape, n, fx, border = null)
            .neonBorder(shape, n.borderSpectrum, fx, width = 1.5.dp)
            .neonGrid(n, enabled = fx.ambientDetail)
            .padding(20.dp),
    ) {
        Column {
            Text("TOTAL BALANCE", style = NeonType.eyebrow, color = n.textSecondary)
            Text(
                "\$12,480.52",
                style = NeonType.displayHero.copy(
                    shadow = heroGlow?.let { androidx.compose.ui.graphics.Shadow(it, Offset.Zero, glowRadius) },
                ),
                color = n.textPrimary,
            )
            Text("HeroCard: chamfer, spectrum hairline, grid", style = MaterialTheme.customTypography.textS, color = n.textSecondary)
        }
    }
}
