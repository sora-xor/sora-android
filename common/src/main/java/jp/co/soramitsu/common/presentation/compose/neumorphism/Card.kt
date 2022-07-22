/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.neumorphism

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import jp.co.soramitsu.common.presentation.compose.theme.NeuColorsCompat
import jp.co.soramitsu.common.presentation.compose.theme.ThemeColors
import me.nikhilchaudhari.library.NeuInsets
import me.nikhilchaudhari.library.neumorphic
import me.nikhilchaudhari.library.shapes.Pressed
import me.nikhilchaudhari.library.shapes.Punched

@Composable
fun NeuCardPunched(
    modifier: Modifier = Modifier,
    radius: Int = 36,
    content: @Composable () -> Unit
) {
    Card(
        backgroundColor = ThemeColors.Background,
        elevation = 0.dp,
        shape = RoundedCornerShape(radius.dp),
        modifier = modifier
            .fillMaxWidth()
            .neumorphic(
                lightShadowColor = Color(NeuColorsCompat.ShadowLight),
                darkShadowColor = Color(NeuColorsCompat.ShadowDark),
                neuInsets = NeuInsets(Dimens.x1, Dimens.x1),
                neuShape = Punched.Rounded(36.dp)
            ),
        content = content,
    )
}

@Composable
fun NeuCardPressed(
    modifier: Modifier,
    radius: Int = 24,
    content: @Composable () -> Unit,
) {
    Card(
        backgroundColor = Color(0xfff5f2f2),
        elevation = 0.dp,
        modifier = modifier
            .clip(RoundedCornerShape(radius.dp))
            .neumorphic(
                neuShape = Pressed.Rounded(radius.dp),
                darkShadowColor = Color(0xffe4e0e0),
                lightShadowColor = Color(0xfffefafa),
                neuInsets = NeuInsets(4.dp, 4.dp),
                strokeWidth = 6.dp,
                elevation = 4.dp,
            ),
        content = content,
    )
}
