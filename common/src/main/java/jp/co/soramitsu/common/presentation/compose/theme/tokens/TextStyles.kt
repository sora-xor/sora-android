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

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.presentation.compose.theme.Inter
import jp.co.soramitsu.common.presentation.compose.theme.Sora

/*
 * The classic SORA type scale (3.8.6 and earlier): Sora Bold for display, headlines and buttons,
 * Inter Regular for body text, with the platform line heights. The bold body styles are the
 * ui-core defaults that 3.8.6 used.
 */

private fun style(family: FontFamily, weight: FontWeight, size: Int) =
    TextStyle(fontFamily = family, fontWeight = weight, fontSize = size.sp)

private fun bold(size: Int, line: Int) = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = line.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

val displayL: TextStyle = style(Sora, FontWeight.Bold, 34)

val displayM: TextStyle = style(Sora, FontWeight.Bold, 24)

val displayS: TextStyle = style(Sora, FontWeight.Bold, 18)

val headline1: TextStyle = style(Sora, FontWeight.Bold, 24)

val headline2: TextStyle = style(Sora, FontWeight.Bold, 18)

val headline3: TextStyle = style(Sora, FontWeight.Bold, 15)

val headline4: TextStyle = style(Sora, FontWeight.Bold, 13)

val textL: TextStyle = style(Inter, FontWeight.Normal, 18)

val textM: TextStyle = style(Inter, FontWeight.Normal, 15)

val textS: TextStyle = style(Inter, FontWeight.Normal, 13)

val textXS: TextStyle = style(Inter, FontWeight.Normal, 11)

val textLBold: TextStyle = bold(18, 20)

val textMBold: TextStyle = bold(15, 16)

val textSBold: TextStyle = bold(13, 16)

val textXSBold: TextStyle = bold(11, 12)

val paragraphL: TextStyle = style(Inter, FontWeight.Normal, 18)

val paragraphM: TextStyle = style(Inter, FontWeight.Normal, 15)

val paragraphS: TextStyle = style(Inter, FontWeight.Normal, 13)

val paragraphXS: TextStyle = style(Inter, FontWeight.Normal, 11)

val paragraphLBold: TextStyle = bold(18, 28)

val paragraphMBold: TextStyle = bold(15, 24)

val paragraphSBold: TextStyle = bold(13, 20)

val paragraphXSBold: TextStyle = bold(11, 16)

val buttonM: TextStyle = style(Sora, FontWeight.Bold, 15)

/**
 * Styles that have no ui-core [jp.co.soramitsu.ui_core.theme.CustomTypography] slot, in the
 * classic weights. Colour is never part of a style; pass `NeonTokens` colours at the call site.
 */
object NeonType {

    /** Balance hero and send/swap amount. Auto-shrink to 28 sp with `TextAutoSize`. */
    val displayHero: TextStyle = style(Sora, FontWeight.Bold, 40).copy(fontFeatureSettings = "tnum")

    /**
     * Small upper-case section labels ("TOTAL BALANCE"). Upper-case with
     * `uppercase(locale)`; use [eyebrowNoTracking] for Arabic and Persian.
     */
    val eyebrow: TextStyle = style(Sora, FontWeight.Bold, 11).copy(letterSpacing = 1.2.sp)

    val eyebrowNoTracking: TextStyle = eyebrow.copy(letterSpacing = 0.sp)

    /** Asset and transaction amounts (tabular figures). */
    val amountM: TextStyle = style(Inter, FontWeight.Bold, 15).copy(fontFeatureSettings = "tnum")

    /** Fiat values, rates and fees (tabular figures). */
    val amountS: TextStyle = style(Inter, FontWeight.Normal, 13).copy(fontFeatureSettings = "tnum")

    /** Addresses, hashes, raw seed and recovery codes: distinct 0/O and 1/l/I. */
    val mono: TextStyle = style(FontFamily.Monospace, FontWeight.Normal, 13)

    /** Recovery phrase words. Indexes use [mono] at 12 sp. */
    val phraseWord: TextStyle = style(Inter, FontWeight.Bold, 15)

    /** PIN keypad digits. Cap the font scale at 1.3x at the call site. */
    val pinDigit: TextStyle = style(Sora, FontWeight.Bold, 34).copy(fontFeatureSettings = "tnum")
}
