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

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Deprecated("use SoraAppTheme custom typography")
private val SoramitsuNeuTextStyle: TextStyle
    @Composable get() = MaterialTheme.typography.body1.copy(
        fontFamily = Sora,
        fontStyle = FontStyle.Normal,
        color = ThemeColors.TextDefault,
        letterSpacing = 0.sp
    )

@Deprecated("use SoraAppTheme custom typography")
private val NeuBold: TextStyle
    @Composable get() = SoramitsuNeuTextStyle.copy(
        fontWeight = FontWeight.Bold
    )

@Deprecated("use SoraAppTheme custom typography")
private val NeuMedium: TextStyle
    @Composable get() = SoramitsuNeuTextStyle.copy(
        fontWeight = FontWeight.Medium
    )

@Deprecated("use SoraAppTheme custom typography")
private val NeuRegular: TextStyle
    @Composable get() = SoramitsuNeuTextStyle.copy(
        fontWeight = FontWeight.Normal
    )

@Deprecated("use SoraAppTheme custom typography")
private val NeuSemiBold: TextStyle
    @Composable get() = SoramitsuNeuTextStyle.copy(
        fontWeight = FontWeight.SemiBold
    )

@Deprecated("use SoraAppTheme custom typography")
private val NeuLight: TextStyle
    @Composable get() = SoramitsuNeuTextStyle.copy(
        fontWeight = FontWeight.Light
    )

@Deprecated("use SoraAppTheme custom typography")
val Typography.neuBold34: TextStyle
    @Composable get() = NeuBold.copy(
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.02).em
    )

@Deprecated("use SoraAppTheme custom typography")
val neuBold18: TextStyle
    @Composable get() = NeuBold.copy(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).em
    )

@Deprecated("use SoraAppTheme custom typography")
val neuBold24: TextStyle
    @Composable get() = NeuBold.copy(
        fontSize = 24.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.03).em
    )

@Deprecated("use SoraAppTheme custom typography")
val Typography.neuBold15: TextStyle
    @Composable get() = NeuBold.copy(
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).em
    )

@Deprecated("use SoraAppTheme custom typography")
val neuMedium15: TextStyle
    @Composable get() = NeuMedium.copy(
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.02).em
    )

@Deprecated("use SoraAppTheme custom typography")
val neuRegular11: TextStyle
    @Composable get() = NeuRegular.copy(
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )

@Deprecated("use SoraAppTheme custom typography")
val neuRegular12: TextStyle
    @Composable get() = NeuRegular.copy(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.05).em
    )

@Deprecated("use SoraAppTheme custom typography")
val neuRegular16: TextStyle
    @Composable get() = NeuRegular.copy(
        fontSize = 16.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.05).em
    )

@Deprecated("use SoraAppTheme custom typography")
val Typography.neuRegular15: TextStyle
    @Composable get() = NeuRegular.copy(
        fontSize = 15.sp,
        lineHeight = 16.sp,
        letterSpacing = (-0.02).em
    )

@Deprecated("use SoraAppTheme custom typography")
val neuSemiBold11: TextStyle
    @Composable get() = NeuSemiBold.copy(
        fontSize = 11.sp,
        lineHeight = 11.sp,
        letterSpacing = (-0.05).em
    )

@Deprecated("use SoraAppTheme custom typography")
val Typography.neuLight15: TextStyle
    @Composable get() = NeuLight.copy(
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.02).em
    )

@Deprecated("use SoraAppTheme custom typography")
val Typography.neuButton: TextStyle
    @Composable get() = NeuBold.copy(
        fontSize = 21.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.03).em
    )
