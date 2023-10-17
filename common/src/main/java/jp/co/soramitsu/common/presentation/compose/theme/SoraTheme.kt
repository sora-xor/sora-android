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

package jp.co.soramitsu.common.presentation.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import jp.co.soramitsu.common.presentation.compose.theme.tokens.DayThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.NightThemeColors
import jp.co.soramitsu.common.presentation.compose.theme.tokens.buttonM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.displayL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.displayM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.displayS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline1
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline2
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline3
import jp.co.soramitsu.common.presentation.compose.theme.tokens.headline4
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.paragraphXS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textL
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textM
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textS
import jp.co.soramitsu.common.presentation.compose.theme.tokens.textXS
import jp.co.soramitsu.ui_core.theme.AppTheme
import jp.co.soramitsu.ui_core.theme.BorderRadius
import jp.co.soramitsu.ui_core.theme.CustomTypography
import jp.co.soramitsu.ui_core.theme.borderRadiuses
import jp.co.soramitsu.ui_core.theme.darkColors
import jp.co.soramitsu.ui_core.theme.defaultCustomTypography
import jp.co.soramitsu.ui_core.theme.lightColors

@Composable
fun SoraAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    AppTheme(
        darkTheme = darkTheme,
        lightColors = soraLightColors,
        darkColors = soraDarkColors,
        typography = soraTypography,
        borderRadius = soraBorderRadius,
        content = content,
    )
}

private val soraLightColors = lightColors(
    accentPrimary = DayThemeColors.AccentPrimary,
    accentPrimaryContainer = DayThemeColors.AccentPrimaryContainer,
    accentSecondary = DayThemeColors.AccentSecondary,
    accentSecondaryContainer = DayThemeColors.AccentSecondaryContainer,
    accentTertiary = DayThemeColors.AccentTertiary,
    accentTertiaryContainer = DayThemeColors.AccentTertiaryContainer,
    bgPage = DayThemeColors.BgPage,
    bgSurface = DayThemeColors.BgSurface,
    bgSurfaceVariant = DayThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = DayThemeColors.BgSurfaceInverted,
    fgPrimary = DayThemeColors.FgPrimary,
    fgSecondary = DayThemeColors.FgSecondary,
    fgTertiary = DayThemeColors.FgTertiary,
    fgInverted = DayThemeColors.FgInverted,
    fgOutline = DayThemeColors.FgOutline,
    statusSuccess = DayThemeColors.StatusSuccess,
    statusSuccessContainer = DayThemeColors.StatusSuccessContainer,
    statusWarning = DayThemeColors.StatusWarning,
    statusWarningContainer = DayThemeColors.StatusWarningContainer,
    statusError = DayThemeColors.StatusError,
    statusErrorContainer = DayThemeColors.StatusErrorContainer
)

private val soraDarkColors = darkColors(
    accentPrimary = NightThemeColors.AccentPrimary,
    accentPrimaryContainer = NightThemeColors.AccentPrimaryContainer,
    accentSecondary = NightThemeColors.AccentSecondary,
    accentSecondaryContainer = NightThemeColors.AccentSecondaryContainer,
    accentTertiary = NightThemeColors.AccentTertiary,
    accentTertiaryContainer = NightThemeColors.AccentTertiaryContainer,
    bgPage = NightThemeColors.BgPage,
    bgSurface = NightThemeColors.BgSurface,
    bgSurfaceVariant = NightThemeColors.BgSurfaceVariant,
    bgSurfaceInverted = NightThemeColors.BgSurfaceInverted,
    fgPrimary = NightThemeColors.FgPrimary,
    fgSecondary = NightThemeColors.FgSecondary,
    fgTertiary = NightThemeColors.FgTertiary,
    fgInverted = NightThemeColors.FgInverted,
    fgOutline = NightThemeColors.FgOutline,
    statusSuccess = NightThemeColors.StatusSuccess,
    statusSuccessContainer = NightThemeColors.StatusSuccessContainer,
    statusWarning = NightThemeColors.StatusWarning,
    statusWarningContainer = NightThemeColors.StatusWarningContainer,
    statusError = NightThemeColors.StatusError,
    statusErrorContainer = NightThemeColors.StatusErrorContainer
)

private val soraTypography: CustomTypography = defaultCustomTypography(
    displayL = displayL,
    displayM = displayM,
    displayS = displayS,
    headline1 = headline1,
    headline2 = headline2,
    headline3 = headline3,
    headline4 = headline4,
    textL = textL,
    textM = textM,
    textS = textS,
    textXS = textXS,
    paragraphL = paragraphL,
    paragraphM = paragraphM,
    paragraphS = paragraphS,
    paragraphXS = paragraphXS,
    buttonM = buttonM
)

private val soraBorderRadius: BorderRadius = borderRadiuses(
    s = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.S,
    m = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.M,
    ml = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.ML,
    xl = jp.co.soramitsu.common.presentation.compose.theme.tokens.BorderRadius.L
)
