/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
