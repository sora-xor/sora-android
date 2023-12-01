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

package jp.co.soramitsu.common_wallet.presentation.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class BasicFarmListItemState(
    val ids: StringTriple,
    val number: String,
    val token1Icon: String,
    val token2Icon: String,
    val rewardTokenIcon: String,
    val rewardTokenSymbol: String,
    val text1: String,
    val text2: String,
    val text3: String,
)

data class BasicUserFarmListItemState(
    val ids: StringTriple,
    val token1Icon: String,
    val token2Icon: String,
    val rewardTokenIcon: String,
    val text1: String,
    val text2: String,
    val text3: String,
    val text4: String
)

val basicDemeterListItemStateEmpty = BasicFarmListItemState(
    ids = Triple("", "", ""),
    number = "",
    token1Icon = DEFAULT_ICON_URI,
    token2Icon = DEFAULT_ICON_URI,
    rewardTokenIcon = DEFAULT_ICON_URI,
    rewardTokenSymbol = "",
    text1 = "",
    text2 = "",
    text3 = "",
)

val previewBasicFarmListItemState = listOf(
    BasicFarmListItemState(
        ids = Triple("0", "1", "2"),
        number = "1",
        token1Icon = DEFAULT_ICON_URI,
        token2Icon = DEFAULT_ICON_URI,
        rewardTokenIcon = DEFAULT_ICON_URI,
        rewardTokenSymbol = "",
        text1 = "XOR-VAL",
        text2 = "123.4M",
        text3 = "1234.3%",
    ),
    BasicFarmListItemState(
        ids = Triple("2", "3", "4"),
        number = "2",
        token1Icon = DEFAULT_ICON_URI,
        token2Icon = DEFAULT_ICON_URI,
        rewardTokenIcon = DEFAULT_ICON_URI,
        rewardTokenSymbol = "DEO",
        text1 = "XSTUSD-PSWAP",
        text2 = "234.4512",
        text3 = "19876.23%",
    ),
)

@Composable
fun BasicFarmListItem(
    modifier: Modifier = Modifier,
    state: BasicFarmListItemState,
    isEnumerated: Boolean = true,
    onPoolClick: ((StringTriple) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onPoolClick?.invoke(state.ids) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isEnumerated) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .width(Dimens.x4),
                text = state.number,
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.customTypography.textXSBold,
                color = MaterialTheme.customColors.fgSecondary,
            )
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2, demeterIcon) = createRefs()
                TokenIcon(
                    modifier = Modifier.constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    },
                    uri = state.token1Icon,
                    size = Size.Small,
                )
                TokenIcon(
                    modifier = Modifier.constrainAs(token2) {
                        top.linkTo(parent.top)
                        start.linkTo(token1.start, margin = 24.dp)
                    },
                    uri = state.token2Icon,
                    size = Size.Small,
                )
                TokenIcon(
                    modifier = Modifier
                        .constrainAs(demeterIcon) {
                            bottom.linkTo(token2.bottom)
                            end.linkTo(token2.end)
                        },
                    uri = state.rewardTokenIcon,
                    size = 22.dp,
                )
            }
        }
        Column(
            modifier = Modifier
                .height(40.dp)
                .weight(1f)
                .padding(start = Dimens.x1, end = Dimens.x1),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
                text = state.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
                text = state.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "%s %s".format(state.text3, stringResource(id = R.string.polkaswap_apr)),
            modifier = Modifier
                .background(
                    color = MaterialTheme.customColors.bgSurfaceVariant,
                    shape = CircleShape,
                )
                .padding(all = Dimens.x1),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.customTypography.textSBold,
            color = MaterialTheme.customColors.accentSecondary,
        )
    }
}

@Composable
fun BasicUserFarmListItem(
    modifier: Modifier = Modifier,
    state: BasicUserFarmListItemState,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2, demeterIcon) = createRefs()
                TokenIcon(
                    modifier = Modifier.constrainAs(token1) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                    },
                    uri = state.token1Icon,
                    size = Size.Small,
                )
                TokenIcon(
                    modifier = Modifier.constrainAs(token2) {
                        top.linkTo(parent.top)
                        start.linkTo(token1.start, margin = 24.dp)
                    },
                    uri = state.token2Icon,
                    size = Size.Small,
                )
                TokenIcon(
                    modifier = Modifier
                        .constrainAs(demeterIcon) {
                            bottom.linkTo(token2.bottom)
                            end.linkTo(token2.end)
                        },
                    uri = state.rewardTokenIcon,
                    size = 22.dp,
                )
            }
        }
        Column(
            modifier = Modifier
                .height(40.dp)
                .weight(1f)
                .padding(start = Dimens.x1, end = Dimens.x1),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
                text = state.text1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
                text = state.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier
                .height(40.dp)
                .padding(start = Dimens.x1, end = Dimens.x1),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgPrimary,
                style = MaterialTheme.customTypography.textM,
                text = state.text3,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                modifier = Modifier.wrapContentHeight(),
                color = MaterialTheme.customColors.fgSecondary,
                style = MaterialTheme.customTypography.textXSBold,
                text = state.text4,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewBasicFarmListItem() {
    Column {
        BasicFarmListItem(state = previewBasicFarmListItemState[0])

        Divider(thickness = Dimens.x1)

        BasicUserFarmListItem(
            state = BasicUserFarmListItemState(
                ids = StringTriple("XOR", "VAL", "DEO"),
                DEFAULT_ICON_URI,
                DEFAULT_ICON_URI,
                DEFAULT_ICON_URI,
                "XOR-ETH",
                "Reward: 78.975 XSTUSD",
                "3.55% APR",
                "50%"
            )
        )
    }
}
