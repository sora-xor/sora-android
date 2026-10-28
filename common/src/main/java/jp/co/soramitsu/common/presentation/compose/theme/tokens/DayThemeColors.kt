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

    /**
     * Dividers and decorative outlines only. Text fields use NeonTokens.borderInput (3:1) through
     * compose.neon.InputText, which replaces this slot for the field border.
     */
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
