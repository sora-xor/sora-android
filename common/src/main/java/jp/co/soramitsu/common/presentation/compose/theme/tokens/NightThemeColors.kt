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
