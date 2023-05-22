/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.DebounceClickHandler

@OptIn(ExperimentalComposeUiApi::class)
@SuppressLint("ModifierFactoryUnreferencedReceiver")
fun Modifier.testTagAsId(tag: String): Modifier {
    return if (BuildUtils.isPlayMarket()) {
        this
    } else {
        this
            .semantics {
                testTagsAsResourceId = true
            }
            .testTag("jp.co.soramitsu.sora.develop:id/$tag")
    }
}

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
            finishedListener = { onAnimationEnd() }
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
