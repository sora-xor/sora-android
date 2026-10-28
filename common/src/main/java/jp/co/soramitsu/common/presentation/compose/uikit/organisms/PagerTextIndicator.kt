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

package jp.co.soramitsu.common.presentation.compose.uikit.organisms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import jp.co.soramitsu.androidfoundation.format.TextValue
import jp.co.soramitsu.androidfoundation.format.retrieveString
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlin.math.abs

@Composable
fun PagerTextIndicator(
    modifier: Modifier,
    indicatorsArray: List<TextValue>,
    currentPageRetriever: () -> Int,
    slideOffsetRetriever: () -> Float,
    onIndicatorClick: (Int) -> Unit
) {
    val elementsList = remember {
        Array(indicatorsArray.size) { Rect.Zero }
    }

    val layoutDirection = LocalLayoutDirection.current

    val surfaceColor = MaterialTheme.customColors.bgSurfaceInverted

    val selectedItemTextColor = MaterialTheme.customColors.fgInverted
    val notSelectedItemTextColor = MaterialTheme.customColors.fgPrimary

    Row(
        modifier = modifier
            .drawWithCache {
                val cornerRadius = CornerRadius(Dimens.x3.toPx(), Dimens.x3.toPx())

                onDrawWithContent {
                    val currentPage = currentPageRetriever.invoke()
                    val offset = slideOffsetRetriever.invoke()

                    if (elementsList.size == indicatorsArray.size) {
                        val start = elementsList[currentPage].left

                        val stop = (
                            elementsList.getOrNull(currentPage + 1)
                                ?: elementsList[currentPage]
                            ).run {
                            /* for Arabic languages */
                            if (layoutDirection === LayoutDirection.Ltr)
                                right else left
                        }

                        val sectorOffsetXLerp = lerp(
                            start = start,
                            stop = stop,
                            fraction = offset
                        )

                        val sectorOffsetY = 0f // Sliding only in horizontal plane

                        val sectorWidthLerp = lerp(
                            start = elementsList[currentPage].width,
                            stop = (elementsList.getOrNull(currentPage + 1) ?: elementsList[currentPage]).width,
                            fraction = abs(offset)
                        )

                        val sectorHeight = size.height

                        drawRoundRect(
                            color = surfaceColor,
                            topLeft = Offset(sectorOffsetXLerp, sectorOffsetY),
                            size = Size(sectorWidthLerp, sectorHeight),
                            cornerRadius = cornerRadius
                        )

                        drawContent()
                    }
                }
            },
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(indicatorsArray.size) { index ->
            Text(
                modifier = Modifier
                    .noRippleClickable { onIndicatorClick.invoke(index) }
                    .onPlaced { elementsList[index] = it.boundsInParent() }
                    .padding(horizontal = Dimens.x2),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                text = indicatorsArray[index].retrieveString(),
                style = MaterialTheme.customTypography.textSBold,
                color = if (index == currentPageRetriever())
                    selectedItemTextColor else notSelectedItemTextColor,
            )
        }
    }
}

/**
 * lerp short for Linear Interpolation, google-known function
 */
private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}

@Preview
@Composable
private fun PreviewSlidingPagerIndicator() {
    val ci = remember {
        androidx.compose.runtime.mutableIntStateOf(0)
    }

    Box(
        modifier = Modifier
            .clickable {
                ci.intValue = ci.intValue
                    .plus(1)
                    .rem(2)
            }
            .fillMaxWidth()
            .height(Dimens.x7)
    ) {
        PagerTextIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            indicatorsArray = listOf(
                TextValue.SimpleText("1234512345"),
                TextValue.SimpleText("3"),
            ),
            currentPageRetriever = { ci.intValue },
            slideOffsetRetriever = { 0f },
            onIndicatorClick = {}
        )
    }
}
