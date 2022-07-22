/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.theme.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import jp.co.soramitsu.common.presentation.compose.theme.tokens.FontFamilies.HEADLINE
import jp.co.soramitsu.common.presentation.compose.theme.tokens.FontFamilies.TEXT
import jp.co.soramitsu.common.presentation.compose.theme.tokens.FontWeights.ONE
import jp.co.soramitsu.common.presentation.compose.theme.tokens.FontWeights.ZERO

val displayL: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 34.sp,
    )

val displayM: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 24.sp,
    )

val displayS: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 18.sp,
    )

val headline1: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 24.sp,
    )

val headline2: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 18.sp,
    )

val headline3: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 15.sp,
    )

val headline4: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 13.sp,
    )

val textL: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 18.sp,
    )

val textM: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 15.sp,
    )

val textS: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 13.sp,
    )

val textXS: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 11.sp,
    )

val paragraphL: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 18.sp,
    )

val paragraphM: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 15.sp,
    )

val paragraphS: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 13.sp,
    )

val paragraphXS: TextStyle
    get() = TextStyle(
        fontWeight = ZERO.value,
        fontFamily = TEXT.value,
        fontSize = 11.sp,
    )

val buttonM: TextStyle
    get() = TextStyle(
        fontWeight = ONE.value,
        fontFamily = HEADLINE.value,
        fontSize = 15.sp,
    )
