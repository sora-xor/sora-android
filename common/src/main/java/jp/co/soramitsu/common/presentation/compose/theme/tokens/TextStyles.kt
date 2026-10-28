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

package jp.co.soramitsu.common.presentation.compose.theme.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.presentation.compose.theme.Inter
import jp.co.soramitsu.common.presentation.compose.theme.Sora

/*
 * SORA Neon type scale (design spec 5.3). Sora for display, headlines and buttons; Inter for
 * body and amounts; the platform monospace for addresses, hashes and codes. Every line height is
 * at least 1.25x the size so fallback scripts (ar, fa, iw, ja, zh, hi) are not clipped.
 */

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: TextUnit = 0.sp,
    features: String? = null,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking,
    fontFeatureSettings = features,
    lineHeightStyle = Centered,
)

val displayL: TextStyle = style(Sora, FontWeight.Bold, 34, 40, (-0.4).sp)

val displayM: TextStyle = style(Sora, FontWeight.Bold, 24, 32, (-0.2).sp)

val displayS: TextStyle = style(Sora, FontWeight.SemiBold, 18, 24)

val headline1: TextStyle = style(Sora, FontWeight.Bold, 24, 32, (-0.2).sp)

val headline2: TextStyle = style(Sora, FontWeight.SemiBold, 18, 24)

val headline3: TextStyle = style(Sora, FontWeight.SemiBold, 15, 20)

val headline4: TextStyle = style(Sora, FontWeight.SemiBold, 13, 18, 0.1.sp)

val textL: TextStyle = style(Inter, FontWeight.Normal, 18, 24)

val textM: TextStyle = style(Inter, FontWeight.Normal, 16, 22)

val textS: TextStyle = style(Inter, FontWeight.Normal, 14, 18)

val textXS: TextStyle = style(Inter, FontWeight.Normal, 12, 16, 0.1.sp)

val textLBold: TextStyle = textL.copy(fontWeight = FontWeight.SemiBold)

val textMBold: TextStyle = textM.copy(fontWeight = FontWeight.SemiBold)

val textSBold: TextStyle = textS.copy(fontWeight = FontWeight.SemiBold)

val textXSBold: TextStyle = textXS.copy(fontWeight = FontWeight.SemiBold)

val paragraphL: TextStyle = style(Inter, FontWeight.Normal, 18, 28)

val paragraphM: TextStyle = style(Inter, FontWeight.Normal, 16, 24)

val paragraphS: TextStyle = style(Inter, FontWeight.Normal, 14, 20)

val paragraphXS: TextStyle = style(Inter, FontWeight.Normal, 12, 16)

val paragraphLBold: TextStyle = paragraphL.copy(fontWeight = FontWeight.SemiBold)

val paragraphMBold: TextStyle = paragraphM.copy(fontWeight = FontWeight.SemiBold)

val paragraphSBold: TextStyle = paragraphS.copy(fontWeight = FontWeight.SemiBold)

val paragraphXSBold: TextStyle = paragraphXS.copy(fontWeight = FontWeight.SemiBold)

val buttonM: TextStyle = style(Sora, FontWeight.SemiBold, 16, 20, 0.2.sp)

/**
 * Styles that have no ui-core [jp.co.soramitsu.ui_core.theme.CustomTypography] slot.
 * Colour is never part of a style; pass `NeonTokens` colours at the call site.
 */
object NeonType {

    /** Balance hero and send/swap amount. Auto-shrink to 28 sp with `TextAutoSize`. */
    val displayHero: TextStyle = style(Sora, FontWeight.SemiBold, 40, 48, (-0.5).sp, "tnum")

    /**
     * Small upper-case section labels ("TOTAL BALANCE"). Upper-case with
     * `uppercase(locale)`; use [eyebrowNoTracking] for Arabic and Persian.
     */
    val eyebrow: TextStyle = style(Sora, FontWeight.SemiBold, 11, 14, 1.2.sp)

    val eyebrowNoTracking: TextStyle = eyebrow.copy(letterSpacing = 0.sp)

    /** Asset and transaction amounts (tabular figures). */
    val amountM: TextStyle = style(Inter, FontWeight.SemiBold, 16, 22, features = "tnum")

    /** Fiat values, rates and fees (tabular figures). */
    val amountS: TextStyle = style(Inter, FontWeight.Normal, 14, 18, features = "tnum")

    /** Addresses, hashes, raw seed and recovery codes: distinct 0/O and 1/l/I. */
    val mono: TextStyle = style(FontFamily.Monospace, FontWeight.Normal, 13, 20)

    /** Recovery phrase words. Indexes use [mono] at 12 sp. */
    val phraseWord: TextStyle = style(Inter, FontWeight.SemiBold, 16, 22)

    /** PIN keypad digits. Cap the font scale at 1.3x at the call site. */
    val pinDigit: TextStyle = style(Sora, FontWeight.Normal, 28, 32, features = "tnum")
}
