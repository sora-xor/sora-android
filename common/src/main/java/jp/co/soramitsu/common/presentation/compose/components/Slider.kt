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

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.material.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import jp.co.soramitsu.ui_core.theme.customColors

private val polkaswapColor = Color(0xffff0066)

@Composable
fun PolkaswapSlider(value: Float, onValueChange: (Float) -> Unit) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        colors = PolkaswapSliderColors
    )
}

object PolkaswapSliderColors : SliderColors {

    @Composable
    override fun thumbColor(enabled: Boolean): State<Color> {
        return rememberUpdatedState(if (enabled) polkaswapColor else MaterialTheme.customColors.bgSurfaceVariant)
    }

    @Composable
    override fun trackColor(enabled: Boolean, active: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (active) polkaswapColor else MaterialTheme.customColors.bgSurfaceVariant
            } else {
                if (active) MaterialTheme.customColors.bgSurfaceVariant else MaterialTheme.customColors.bgSurfaceVariant
            }
        )
    }

    @Composable
    override fun tickColor(enabled: Boolean, active: Boolean): State<Color> {
        return rememberUpdatedState(
            if (enabled) {
                if (active) polkaswapColor else MaterialTheme.customColors.bgSurfaceVariant
            } else {
                if (active) MaterialTheme.customColors.bgSurfaceVariant else MaterialTheme.customColors.bgSurfaceVariant
            }
        )
    }
}
