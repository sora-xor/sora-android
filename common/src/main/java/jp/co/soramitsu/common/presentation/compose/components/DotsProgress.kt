/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.resources.Dimens
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun InfiniteProgressDots(
    modifier: Modifier,
    alignment: Alignment,
) {
    val image = AnimatedImageVector.animatedVectorResource(
        R.drawable.animation_progress_dots
    )
    var atEnd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(920)
            atEnd = !atEnd
        }
    }
    Image(
        modifier = modifier,
        alignment = alignment,
        painter = rememberAnimatedVectorPainter(
            animatedImageVector = image, atEnd = atEnd
        ),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(colorResource(id = R.color.neu_disabled_text_grey))
    )
}

@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
fun InfiniteProgressDots2() {
    val image = AnimatedImageVector.animatedVectorResource(R.drawable.animation_progress_dots)
    var atEnd by remember { mutableStateOf(false) }
    var isRunning by remember { mutableStateOf(true) }
    suspend fun runAnimation() {
        while (isRunning) {
            delay(950) // set here your delay between animations
            atEnd = !atEnd
        }
    }
    LaunchedEffect(image) {
        runAnimation()
    }
    Image(
        modifier = Modifier
            .height(Dimens.x3)
            .fillMaxWidth(),
        alignment = Alignment.Center,
        painter = rememberAnimatedVectorPainter(
            animatedImageVector = image, atEnd = atEnd
        ),
        contentDescription = null,
        contentScale = ContentScale.Fit,
    )
}
