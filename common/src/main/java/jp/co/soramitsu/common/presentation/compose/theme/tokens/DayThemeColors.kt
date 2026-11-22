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
import jp.co.soramitsu.ui_core.lit.LitDay

/** The day palette: white paper and glass, sumi ink, SORA red (the materials are in `ui_core.lit`). */
object DayThemeColors {

    val AccentPrimary = LitDay.red

    val AccentPrimaryContainer = Color(0xFFF7E1DF)

    val AccentSecondary = LitDay.ink

    val AccentSecondaryContainer = LitDay.wellFloor

    val AccentTertiary = LitDay.inkTertiary

    val AccentTertiaryContainer = LitDay.wellFloor

    val BgPage = LitDay.page

    val BgSurface = LitDay.surface

    val BgSurfaceVariant = LitDay.wellFloor

    val BgSurfaceInverted = LitDay.ink

    val FgPrimary = LitDay.ink

    val FgSecondary = LitDay.inkSecondary

    val FgTertiary = LitDay.inkTertiary

    val FgInverted = LitDay.padLit

    val FgOutline = Color(0xFFDAD6CE)

    val StatusSuccess = Color(0xFF286448)

    val StatusSuccessContainer = Color(0xFFDCEBE2)

    /** A calm ochre: readable as ink on white, on its own container and on its own tint. */
    val StatusWarning = Color(0xFF8A5A0C)

    val StatusWarningContainer = Color(0xFFF3E9D2)

    /** A warning as a filled mark, with ink on it. */
    val StatusWarningFill = Color(0xFFDDA63A)

    val StatusError = LitDay.redDeep

    val StatusErrorContainer = Color(0xFFF7E1DF)
}
