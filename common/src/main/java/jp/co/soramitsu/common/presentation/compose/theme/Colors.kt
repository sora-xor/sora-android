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

import android.graphics.Color.parseColor
import android.graphics.Color.rgb
import androidx.compose.ui.graphics.Color

@Deprecated("use SoraAppTheme palette")
private object Colors {

    val NeuColor300 = Color(rgb(246, 242, 242))
    val NeuBlack = Color(rgb(34, 17, 17))
    val NeuSelectedRed = Color(rgb(238, 34, 51))
    val NeuDisabledGrey = Color(rgb(236, 229, 229))
    val NeuDisabledTextGrey = Color(rgb(220, 210, 210))
    val NeuSecondary10 = Color(rgb(232, 225, 225))
    val NeuSecondary50 = Color(rgb(157, 129, 129))

    val BrandPmsBlack = Color(rgb(45, 41, 38))
}

@Deprecated("use SoraAppTheme palette LightThemeColors")
object ThemeColors {

    val Primary = Colors.NeuSelectedRed
    val OnPrimary = Color.White

    val Secondary = Colors.NeuSecondary10
    val OnSecondary = Colors.NeuSecondary50

    val Background = Colors.NeuColor300
    val OnBackground = Colors.BrandPmsBlack
    val BackgroundDisabled = Colors.NeuDisabledGrey

    val TextDefault = Colors.NeuBlack
    val TextDisabled = Colors.NeuDisabledTextGrey
}

@Deprecated("use SoraAppTheme palette NightThemeColors")
object ThemeColorsDark {

    val Primary = Colors.NeuSelectedRed
    val OnPrimary = Color.White

    val Secondary = Colors.NeuSecondary10
    val OnSecondary = Colors.NeuSecondary50

    val Background = Colors.NeuColor300
}

object NeuColorsCompat {

    val Content = parseColor("#f6f2f2")
    val ShadowLight = parseColor("#eefefafa")
    val ShadowDark = parseColor("#eeeae6e6")
    val neuOnBackground = Color(0xff998888)
    val color281818 = Color(0xff281818)
    val color9a9a9a = Color(0xff9a9a9a)
    val neuDividerColor = Color(0xffe5dddd)
    val neuBackgroundAbove = Color(0xfffaf6f6)
    val neuBackgroundImage = Color(0xffe8e1e1)
    val neuTintDark = Color(0xffaa9999)
    val neuTintLight = Color(0xffccbbbb)
    val neuColor9d8181 = Color(0xff9d8181)
}
