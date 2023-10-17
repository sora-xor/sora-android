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
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Brown10
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Brown30
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Brown5
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Brown50
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Brown90
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Green5
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Red5
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Yellow30
import jp.co.soramitsu.common.presentation.compose.theme.tokens.Colors.Yellow5

object DayThemeColors {

    val AccentPrimary = Color(0xFFEE2233)

    val AccentPrimaryContainer = Red5

    val AccentSecondary = Brown90

    val AccentSecondaryContainer = Brown10

    val AccentTertiary = Brown50

    val AccentTertiaryContainer = Brown10

    val BgPage = Brown5

    val BgSurface = Color(0xffffffff)

    val BgSurfaceVariant = Brown10

    val BgSurfaceInverted = Brown90

    val FgPrimary = Brown90

    val FgSecondary = Brown50

    val FgTertiary = Brown30

    val FgInverted = Brown5

    val FgOutline = Brown5

    val StatusSuccess = Color(0xFF169974)

    val StatusSuccessContainer = Green5

    val StatusWarning = Yellow30

    val StatusWarningContainer = Yellow5

    val StatusError = Color(0xFFCB0F1F)

    val StatusErrorContainer = Red5
}
