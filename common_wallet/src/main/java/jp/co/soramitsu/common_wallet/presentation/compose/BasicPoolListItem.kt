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
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

data class BasicPoolListItemState(
    val ids: StringPair,
    val number: String,
    val token1Icon: String,
    val token2Icon: String,
    val text1: String,
    val text2: String,
    val text3: String,
)

val basicPoolListItemStateEmpty = BasicPoolListItemState(
    ids = "" to "",
    number = "",
    token1Icon = DEFAULT_ICON_URI,
    token2Icon = DEFAULT_ICON_URI,
    text1 = "",
    text2 = "",
    text3 = "",
)

val previewBasicPoolListItemState = listOf(
    BasicPoolListItemState(
        ids = "0" to "1",
        number = "1",
        token1Icon = DEFAULT_ICON_URI,
        token2Icon = DEFAULT_ICON_URI,
        text1 = "XOR-VAL",
        text2 = "123.4M",
        text3 = "1234.3%",
    ),
    BasicPoolListItemState(
        ids = "2" to "3",
        number = "2",
        token1Icon = DEFAULT_ICON_URI,
        token2Icon = DEFAULT_ICON_URI,
        text1 = "XSTUSD-PSWAP",
        text2 = "234.4512",
        text3 = "19876.23%",
    ),
)

@Composable
fun BasicPoolListItem(
    modifier: Modifier = Modifier,
    state: BasicPoolListItemState,
    onPoolClick: ((StringPair) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable { onPoolClick?.invoke(state.ids) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ConstraintLayout(
                modifier = Modifier.wrapContentSize()
            ) {
                val (token1, token2) = createRefs()
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
            text = "%s %s".format(state.text3, stringResource(id = R.string.polkaswap_apy)),
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

@Preview(showBackground = true)
@Composable
private fun PreviewBasicPoolListItem() {
    BasicPoolListItem(state = previewBasicPoolListItemState[0])
}
