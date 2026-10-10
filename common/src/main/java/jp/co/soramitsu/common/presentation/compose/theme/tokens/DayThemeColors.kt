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

package jp.co.soramitsu.common.presentation.compose.theme.tokens

import androidx.compose.ui.graphics.Color

/**
 * SORA Neon light ("Daylight") palette mapped onto the ui-core
 * [jp.co.soramitsu.ui_core.theme.CustomColors] slots (design spec 2.3). Keep in sync with
 * `res/values/colors.xml` (neon_*) and
 * [jp.co.soramitsu.common.presentation.compose.theme.neon.NeonLight].
 *
 * fgInverted is white: the label on accent fills in light.
 */
object DayThemeColors {

    val AccentPrimary = Color(0xFFC80E34)

    val AccentPrimaryContainer = Color(0xFFFBE7EB)

    val AccentSecondary = Color(0xFF0B0C14)

    val AccentSecondaryContainer = Color(0xFFE9EBF2)

    val AccentTertiary = Color(0xFF1F6B7C)

    val AccentTertiaryContainer = Color(0xFFE2F3F7)

    val BgPage = Color(0xFFF2F3F8)

    val BgSurface = Color(0xFFFFFFFF)

    val BgSurfaceVariant = Color(0xFFE9EBF2)

    val BgSurfaceInverted = Color(0xFF14141F)

    val FgPrimary = Color(0xFF0B0C14)

    val FgSecondary = Color(0xFF454A5E)

    val FgTertiary = Color(0xFF5E6278)

    val FgInverted = Color(0xFFFFFFFF)

    /** Dividers and decorative outlines. Input borders use NeonTokens.borderInput (3:1). */
    val FgOutline = Color(0xFFDADCE5)

    val StatusSuccess = Color(0xFF08704D)

    val StatusSuccessContainer = Color(0xFFE3F6F0)

    /** Amber text. Filled warning badges use NeonTokens.warningFill with an ink label. */
    val StatusWarning = Color(0xFF8A4F00)

    val StatusWarningContainer = Color(0xFFFFF0DA)

    val StatusError = Color(0xFFBC0E2D)

    val StatusErrorContainer = Color(0xFFF9E7EA)

    /** ui-core `elevation` slot: #0B0C14 at 8%. */
    val Elevation = Color(0x140B0C14)
}
