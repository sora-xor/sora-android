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
 * SORA Neon dark palette mapped onto the ui-core [jp.co.soramitsu.ui_core.theme.CustomColors]
 * slots (design spec 2.3). Keep in sync with `res/values-night/colors.xml` (neon_*) and
 * [jp.co.soramitsu.common.presentation.compose.theme.neon.NeonDark].
 *
 * fgInverted is the dark "ink": it is both the label on neon fills (primary button) and the
 * text on the inverted surface.
 */
object NightThemeColors {

    val AccentPrimary = Color(0xFFFF3355)

    val AccentPrimaryContainer = Color(0xFF2A0C15)

    val AccentSecondary = Color(0xFFF5F6FB)

    val AccentSecondaryContainer = Color(0xFF1C1C28)

    val AccentTertiary = Color(0xFF7CCFE0)

    val AccentTertiaryContainer = Color(0xFF0F1E24)

    val BgPage = Color(0xFF06060B)

    val BgSurface = Color(0xFF14141F)

    val BgSurfaceVariant = Color(0xFF1C1C28)

    val BgSurfaceInverted = Color(0xFFF5F6FB)

    val FgPrimary = Color(0xFFF5F6FB)

    val FgSecondary = Color(0xFFB3B7CB)

    val FgTertiary = Color(0xFF969BB4)

    val FgInverted = Color(0xFF06060B)

    val FgOutline = Color(0xFF26263A)

    val StatusSuccess = Color(0xFF2EF2A4)

    val StatusSuccessContainer = Color(0xFF0B221D)

    val StatusWarning = Color(0xFFFFB547)

    val StatusWarningContainer = Color(0xFF241B12)

    val StatusError = Color(0xFFFF6B85)

    val StatusErrorContainer = Color(0xFF2A1119)

    /** ui-core `elevation` slot: no grey shadows on the dark canvas. */
    val Elevation = Color(0x00000000)
}
