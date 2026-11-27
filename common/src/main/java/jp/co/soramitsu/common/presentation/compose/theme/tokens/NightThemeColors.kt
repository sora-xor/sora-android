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
import jp.co.soramitsu.ui_core.lit.LitNight

/**
 * The night palette: warm charcoal, ivory type, the red as a lighter ink. Nothing glows; a filled button keeps its
 * own deeper red (`LitNight.red`).
 */
object NightThemeColors {

    val AccentPrimary = LitNight.redText

    val AccentPrimaryContainer = Color(0xFF4A2620)

    val AccentSecondary = LitNight.ink

    val AccentSecondaryContainer = LitNight.padLit

    val AccentTertiary = LitNight.inkTertiary

    val AccentTertiaryContainer = LitNight.padLit

    val BgPage = LitNight.page

    val BgSurface = LitNight.surface

    val BgSurfaceVariant = LitNight.wellFloor

    val BgSurfaceInverted = LitNight.ink

    val FgPrimary = LitNight.ink

    val FgSecondary = LitNight.inkSecondary

    val FgTertiary = LitNight.inkTertiary

    val FgInverted = LitNight.page

    val FgOutline = Color(0xFF4A4541)

    val StatusSuccess = Color(0xFF7CC49A)

    val StatusSuccessContainer = Color(0xFF23382C)

    /** The ochre lifted off the charcoal, without glare. */
    val StatusWarning = Color(0xFFD9B36A)

    val StatusWarningContainer = Color(0xFF3A3122)

    /** A warning as a filled mark, with the day ink on it. */
    val StatusWarningFill = Color(0xFFC8983C)

    val StatusError = LitNight.redText

    val StatusErrorContainer = Color(0xFF4A2620)
}
