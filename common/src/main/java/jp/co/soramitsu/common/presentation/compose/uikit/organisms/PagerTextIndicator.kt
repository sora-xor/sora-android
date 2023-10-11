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
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography
import kotlin.math.abs

@Composable
fun PagerTextIndicator(
    modifier: Modifier,
    indicatorsArray: List<Text>,
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
        mutableStateOf(0)
    }

    Box(
        modifier = Modifier
            .clickable {
                ci.value = ci.value
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
                Text.SimpleText("1234512345"),
                Text.SimpleText("3"),
            ),
            currentPageRetriever = { ci.value },
            slideOffsetRetriever = { 0f },
            onIndicatorClick = {}
        )
    }
}
