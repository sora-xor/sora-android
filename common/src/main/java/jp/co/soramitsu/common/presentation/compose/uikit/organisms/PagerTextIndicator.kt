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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.retrieveString
import jp.co.soramitsu.ui_core.resources.Dimens
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
    Row(
        modifier = modifier
            .drawWithCache {
                val elementWidth = size.width.div(indicatorsArray.size)
                val elementWidthWithPaddingInPercent = .7f
                val paddingInPercent = .15f
                val cornerRadius = CornerRadius(Dimens.x3.toPx(), Dimens.x3.toPx())

                onDrawBehind {
                    val currentPage = currentPageRetriever.invoke()
                    val offset = slideOffsetRetriever.invoke()

                    val normalizedOffset = 1f + offset

                    val offsetX = elementWidth.times(currentPage - 1)
                        .plus(elementWidth.times(normalizedOffset))
                        .plus(elementWidth.times(paddingInPercent))

                    val offsetY = 0f // Sliding only in horizontal plane

                    val width = elementWidth.times(elementWidthWithPaddingInPercent)
                        .times(1f + abs(offset))

                    val height = size.height

                    drawRoundRect(
                        color = Color.Black,
                        topLeft = Offset(offsetX, offsetY),
                        size = Size(width, height),
                        cornerRadius = cornerRadius
                    )
                }
            },
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(indicatorsArray.size) {
            Text(
                modifier = Modifier
                    .noRippleClickable { onIndicatorClick.invoke(it) },
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                text = indicatorsArray[it].retrieveString(),
                color = if (it == currentPageRetriever()) Color.White else Color.Black,
                style = MaterialTheme.customTypography.textSBold,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewSlidingPagerIndicator() {
    val ci = remember {
        mutableStateOf(0)
    }

    Box(
        modifier = Modifier
            .clickable { ci.value = ci.value + 1 }
            .fillMaxWidth()
            .height(Dimens.x7)
    ) {
        PagerTextIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            indicatorsArray = listOf(
                Text.SimpleText("1"),
                Text.SimpleText("2"),
                Text.SimpleText("3")
            ),
            currentPageRetriever = { ci.value },
            slideOffsetRetriever = { ci.value.toFloat() },
            onIndicatorClick = {}
        )
    }
}
