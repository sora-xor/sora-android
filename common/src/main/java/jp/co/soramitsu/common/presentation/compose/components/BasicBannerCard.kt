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

package jp.co.soramitsu.common.presentation.compose.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.testTagAsId
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BasicBannerCard(
    @DrawableRes imageContent: Int,
    title: String,
    description: String,
    button: String,
    onButtonClicked: () -> Unit,
    closeEnabled: Boolean,
    onCloseCard: () -> Unit,
) {
    ContentCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onButtonClicked,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            ConstraintLayout(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
            ) {
                val (card, image) = createRefs()
                CardContent(
                    modifier = Modifier
                        .testTagAsId("StartInviting")
                        .constrainAs(card) {
                            top.linkTo(parent.top)
                            start.linkTo(parent.start)
                            end.linkTo(image.start)
                            width = Dimension.fillToConstraints
                            height = Dimension.wrapContent
                        },
                    title = title,
                    description = description,
                    button = button,
                    onStartClicked = onButtonClicked,
                )

                Image(
                    modifier = Modifier.constrainAs(image) {
                        top.linkTo(parent.top)
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                        width = Dimension.value(128.dp)
                        height = Dimension.fillToConstraints
                    },
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomEnd,
                    painter = painterResource(imageContent),
                    contentDescription = null,
                )
            }

            if (closeEnabled) {
                BleachedButton(
                    modifier = Modifier
                        .wrapContentWidth()
                        .align(Alignment.TopEnd)
                        .padding(Dimens.x1)
                        .alpha(0.8f),
                    size = Size.ExtraSmall,
                    order = Order.TERTIARY,
                    shape = CircleShape,
                    onClick = onCloseCard,
                    leftIcon = painterResource(jp.co.soramitsu.ui_core.R.drawable.ic_cross),
                )
            }
        }
    }
}

@Composable
private fun CardContent(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    button: String,
    onStartClicked: () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(
                start = Dimens.x3,
                top = Dimens.x3,
                bottom = Dimens.x2,
            ),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.customTypography.headline2,
            color = MaterialTheme.customColors.fgPrimary,
        )

        Text(
            modifier = Modifier.padding(top = Dimens.x1),
            text = description,
            style = MaterialTheme.customTypography.paragraphXS,
            color = MaterialTheme.customColors.fgPrimary,
        )

        FilledButton(
            modifier = Modifier
                .wrapContentWidth()
                .padding(top = Dimens.x1_5),
            text = button,
            size = Size.ExtraSmall,
            order = Order.PRIMARY,
            onClick = onStartClicked,
        )
    }
}

@Preview
@Composable
private fun PreviewBasicBannerCard1() {
    BasicBannerCard(
        imageContent = R.drawable.image_friends,
        title = "Some title of banner card, let it be longeeerr",
        description = "Long description of banner card, The quick brown fox jumps over the lazy dog, The quick brown fox jumps over the lazy dog.And I, even I Artaxerxes the king, do make a decree to all the treasurers which are beyond the river, that whatsoever Ezra the priest, the scribe of the law of the God of heaven, shall require of you, it be done speedily",
        button = "Just button title",
        closeEnabled = true,
        onCloseCard = {},
        onButtonClicked = {},
    )
}

@Preview
@Composable
private fun PreviewBasicBannerCard12() {
    BasicBannerCard(
        imageContent = R.drawable.image_friends,
        title = "Some title",
        description = "Long description of banner",
        button = "Just button title",
        closeEnabled = false,
        onCloseCard = {},
        onButtonClicked = {},
    )
}

@Preview
@Composable
private fun PreviewBasicBannerCard2() {
    BasicBannerCard(
        imageContent = R.drawable.ic_buy_xor_banner_sora,
        title = "Title",
        description = "Description",
        button = "Button",
        closeEnabled = true,
        onCloseCard = {},
        onButtonClicked = {},
    )
}
