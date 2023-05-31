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
