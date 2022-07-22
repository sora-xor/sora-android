/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
