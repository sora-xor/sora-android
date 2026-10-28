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

package jp.co.soramitsu.common.util.ext

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.debugInspectorInfo
import jp.co.soramitsu.common.util.DebounceClickHandler

fun Modifier.debounceClickable(debounceClickHandler: DebounceClickHandler, onClick: () -> Unit): Modifier {
    return this.clickable { debounceClickHandler.debounceClick(onClick) }
}

fun Modifier.shake(enabled: Boolean, onAnimationEnd: () -> Unit = {}) = composed(
    factory = {
        val scale by animateFloatAsState(
            targetValue = if (enabled) 0f else 20f,
            animationSpec = repeatable(
                iterations = 5,
                animation = tween(durationMillis = 50, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            finishedListener = { onAnimationEnd() }, label = ""
        )

        Modifier.graphicsLayer {
            translationX = if (enabled) scale else 0f
        }
    },
    inspectorInfo = debugInspectorInfo {
        name = "shake"
        properties["enabled"] = enabled
    }
)
