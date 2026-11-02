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

package jp.co.soramitsu.feature_wallet_impl.presentation.cardshub

import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.ReadOnlyChip
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.extension.noRippleClickable
import jp.co.soramitsu.common.presentation.compose.neon.BleachedButton
import jp.co.soramitsu.common.presentation.compose.neon.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.common.presentation.compose.neon.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Preview
@Composable
private fun PreviewTopBar() {
    Box(modifier = Modifier.background(Color.Red)) {
        TopBar(account = "cnJkhsdlf...sdlndskf", {}, {})
    }
}

@Composable
fun TopBar(
    account: String,
    onAccountClick: () -> Unit,
    onQrClick: () -> Unit,
    readOnly: Boolean = false,
) {
    Row(
        modifier = Modifier
            .padding(start = Dimens.x3, end = Dimens.x2, top = Dimens.x3)
            .height(Size.Small)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .padding(end = Dimens.x2)
                .weight(1f)
                .clickable(onClick = onAccountClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .wrapContentWidth()
                    .weight(1f, false),
                text = account,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.customTypography.displayS,
                color = MaterialTheme.customColors.fgPrimary,
            )
            if (readOnly) {
                ReadOnlyChip(
                    modifier = Modifier
                        .padding(start = Dimens.x1)
                        .testTag(AccountReadOnlyTags.CHIP),
                )
            }
            Icon(
                modifier = Modifier.wrapContentWidth(),
                painter = painterResource(R.drawable.ic_arrows_chevron_right_rounded_24),
                tint = MaterialTheme.customColors.fgPrimary,
                contentDescription = null
            )
        }
        val scanLabel = stringResource(R.string.wallet_scan_qr)
        Box(modifier = Modifier.size(Size.Small)) {
            BleachedButton(
                modifier = Modifier.semantics { contentDescription = scanLabel },
//                value of 50 results in strange shadow behaviour
                shape = RoundedCornerShape(percent = 49),
                size = Size.Small,
                order = Order.TERTIARY,
                leftIcon = painterResource(R.drawable.ic_scan_qr),
                onClick = onQrClick,
            )
        }
    }
}

@Composable
fun CommonHubCard(
    @StringRes title: Int,
    amount: String,
    collapseState: Boolean,
    onOpenFullCardClick: () -> Unit,
    onCollapseClick: () -> Unit,
    hero: Boolean = false,
    content: @Composable (ColumnScope) -> Unit
) {
    val collapseLabel = stringResource(if (collapseState) R.string.wallet_collapsed else R.string.wallet_expanded)
    val cardContent: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
        Column(
            modifier = Modifier
                .padding(top = Dimens.x3, bottom = Dimens.x4)
                .animateContentSize(),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = Dimens.x3)
                    .fillMaxWidth()
                    .wrapContentHeight(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        modifier = Modifier
                            .semantics { stateDescription = collapseLabel }
                            .clickable(onClick = onCollapseClick),
                        text = stringResource(id = title),
                        style = MaterialTheme.customTypography.headline2,
                        color = MaterialTheme.customColors.fgPrimary,
                    )
                    Icon(
                        painter = painterResource(if (collapseState) R.drawable.ic_chevron_down_rounded_16 else R.drawable.ic_chevron_up_rounded_16),
                        tint = MaterialTheme.customColors.fgPrimary,
                        contentDescription = null
                    )
                }
                if (!hero) Text(
                    text = amount,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.customTypography.headline2,
                    color = MaterialTheme.customColors.fgPrimary,
                )
            }
            if (hero) {
                Text(amount, modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.x3, vertical = Dimens.x1),
                    style = jp.co.soramitsu.common.presentation.compose.theme.tokens.NeonType.displayHero,
                    color = MaterialTheme.customColors.fgPrimary)
            }
            if (!collapseState) {
                Spacer(modifier = Modifier.size(size = Dimens.x2))
                content.invoke(this)
                TextButton(
                    modifier = Modifier
                        .padding(start = Dimens.x1, top = Dimens.x3),
                    text = stringResource(id = R.string.common_expand),
                    size = Size.ExtraSmall,
                    order = Order.PRIMARY,
                    onClick = onOpenFullCardClick,
                )
            }
        }
    }
    if (hero) jp.co.soramitsu.common.presentation.compose.neon.HeroCard(
        modifier = Modifier.padding(top = Dimens.x1_5), onClick = onOpenFullCardClick, content = cardContent)
    else ContentCard(modifier = Modifier.padding(top = Dimens.x1_5),
        onClick = onOpenFullCardClick, content = cardContent)

}

@Preview(showBackground = true)
@Composable
private fun PreviewCommonHubCard() {
    CommonHubCard(
        title = R.string.common_ok,
        amount = "123.123",
        collapseState = false,
        onOpenFullCardClick = {},
        onCollapseClick = {}
    ) {
        Text(text = "text1")
        Text(text = "text2")
    }
}
