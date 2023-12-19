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

package jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.common.presentation.compose.TokenIcon
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.util.StringTriple
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_ecosystem_impl.R
import jp.co.soramitsu.feature_ecosystem_impl.presentation.farmdetails.model.FarmDetailsState
import jp.co.soramitsu.ui_core.component.button.FilledButton
import jp.co.soramitsu.ui_core.component.button.FilledPolkaswapButton
import jp.co.soramitsu.ui_core.component.button.TonalButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
internal fun FarmDetailsScreen(
    state: FarmDetailsState,
    onStartStakingClicked: () -> Unit,
    onSupplyClicked: () -> Unit,
    onClaim: () -> Unit
) {
    Column {
        FarmCard(
            state = state,
            onStartStakingClicked = onStartStakingClicked,
            onClaim = onClaim
        )
        Divider(
            modifier = Modifier.padding(bottom = Dimens.x1_2),
            color = Color.Transparent
        )
        if (!state.hasSupplyInPool) {
            SupplyLiquidity(
                state = state,
                onSupplyClicked = onSupplyClicked
            )
        }
    }
}

@Composable
private fun FarmCard(state: FarmDetailsState, onStartStakingClicked: () -> Unit, onClaim: () -> Unit) {
    ContentCard(
        modifier = Modifier
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(Dimens.x6)
                        .padding(Dimens.x1),
                    color = MaterialTheme.customColors.accentPrimary
                )
            }
        } else {
            Column {
                Row(
                    modifier = Modifier
                        .padding(bottom = Dimens.x3)
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
                                uri = state.rewardsTokenIcon,
                                size = 22.dp,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .wrapContentHeight()
                            .weight(1f)
                            .padding(start = Dimens.x1, end = Dimens.x1),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            modifier = Modifier.wrapContentHeight(),
                            color = MaterialTheme.customColors.fgPrimary,
                            style = MaterialTheme.customTypography.headline2,
                            text = state.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            modifier = Modifier.wrapContentHeight(),
                            color = MaterialTheme.customColors.fgSecondary,
                            style = MaterialTheme.customTypography.textXSBold,
                            text = state.tvlSubtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        painterResource(id = R.drawable.ic_farm_leaf_grey),
                        tint = if (state.poolShareStacked == null) {
                            MaterialTheme.customColors.fgTertiary
                        } else {
                            MaterialTheme.customColors.statusSuccess
                        },
                        contentDescription = ""
                    )
                }

                DetailsItem(
                    modifier = Modifier.padding(bottom = Dimens.x1_5),
                    text = stringResource(id = R.string.polkaswap_apr),
                    hint = stringResource(id = R.string.apr_description),
                    value1 = state.apr,
                    value1Bold = true
                )

                Divider(
                    modifier = Modifier.padding(bottom = Dimens.x1_5),
                    thickness = 1.dp,
                    color = MaterialTheme.customColors.bgPage
                )

                DetailsItem(
                    modifier = Modifier.padding(bottom = Dimens.x1_5),
                    text = stringResource(id = R.string.polkaswap_reward_payout),
                    value1 = state.rewardsTokenSymbol,
                    value1Uri = state.rewardsTokenIcon
                )

                Divider(
                    modifier = Modifier.padding(bottom = Dimens.x1_5),
                    thickness = 1.dp,
                    color = MaterialTheme.customColors.bgPage
                )

                if (state.poolShareStacked == null) {
                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x2),
                        text = stringResource(id = R.string.common_fee),
                        value1 = state.fee
                    )

                    FilledButton(
                        modifier = Modifier
                            .testTagAsId("StartStaking")
                            .fillMaxWidth(),
                        size = Size.Large,
                        order = Order.PRIMARY,
                        text = stringResource(id = R.string.start_staking),
                        enabled = state.hasSupplyInPool,
                        onClick = onStartStakingClicked
                    )
                } else {
                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        text = stringResource(id = R.string.polkaswap_farming_pool_share),
                        value1 = state.poolShareStackedText ?: "",
                        value1Percent = state.poolShareStacked / 100
                    )

                    Divider(
                        modifier = Modifier.padding(bottom = Dimens.x1_5),
                        thickness = 1.dp,
                        color = MaterialTheme.customColors.bgPage
                    )

                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x3),
                        text = stringResource(id = R.string.farm_details_your_rewards),
                        value1 = state.userRewardsAmount ?: "",
                    )

                    FilledPolkaswapButton(
                        modifier = Modifier
                            .padding(bottom = Dimens.x2)
                            .testTagAsId("ClaimRewards")
                            .fillMaxWidth(),
                        size = Size.Large,
                        enabled = state.hasRewardsAvailable,
                        order = Order.PRIMARY,
                        text = stringResource(id = R.string.claim_rewards),
                        onClick = onClaim
                    )

                    TonalButton(
                        modifier = Modifier
                            .testTagAsId("EditFarm")
                            .fillMaxWidth(),
                        size = Size.Large,
                        order = Order.PRIMARY,
                        text = stringResource(id = R.string.edit_farm),
                        enabled = state.hasSupplyInPool,
                        onClick = onStartStakingClicked
                    )
                }

                Divider(
                    modifier = Modifier.padding(bottom = Dimens.x1_5),
                    thickness = 1.dp,
                    color = MaterialTheme.customColors.bgPage
                )

                Text(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    text = stringResource(id = R.string.polkaswap_farming_demeter_power),
                    color = MaterialTheme.customColors.fgSecondary,
                    style = MaterialTheme.customTypography.textXSBold
                )
            }
        }
    }
}

@Composable
private fun SupplyLiquidity(
    state: FarmDetailsState,
    onSupplyClicked: () -> Unit
) {
    ContentCard(
        modifier = Modifier
            .padding(vertical = Dimens.x1, horizontal = Dimens.x2)
            .wrapContentHeight(),
        innerPadding = PaddingValues(Dimens.x3),
    ) {
        Column {
            Text(
                style = MaterialTheme.customTypography.paragraphM,
                text = stringResource(id = R.string.request_supply_liquidity)
            )

            Divider(
                modifier = Modifier.padding(bottom = Dimens.x2),
                color = Color.Transparent,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clickable { },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ConstraintLayout(
                        modifier = Modifier.wrapContentSize()
                    ) {
                        val (token1, token2) = createRefs()
                        TokenIcon(
                            uri = state.token1Icon, size = Size.Small,
                            modifier = Modifier.constrainAs(token1) {
                                top.linkTo(parent.top)
                                start.linkTo(parent.start)
                            }
                        )
                        TokenIcon(
                            uri = state.token2Icon, size = Size.Small,
                            modifier = Modifier.constrainAs(token2) {
                                top.linkTo(parent.top)
                                start.linkTo(token1.start, margin = 24.dp)
                            }
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
                        text = state.poolTitle ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        modifier = Modifier.wrapContentHeight(),
                        color = MaterialTheme.customColors.fgSecondary,
                        style = MaterialTheme.customTypography.textXSBold,
                        text = state.tvlSubtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "%s %s".format(
                        state.apyText ?: "",
                        stringResource(id = jp.co.soramitsu.common.R.string.polkaswap_apy)
                    ),
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

            Divider(
                modifier = Modifier.padding(bottom = Dimens.x2),
                color = Color.Transparent,
            )

            TonalButton(
                modifier = Modifier.fillMaxWidth(),
                size = Size.Large,
                order = Order.PRIMARY,
                text = stringResource(id = R.string.common_supply_liquidity_title),
                onClick = onSupplyClicked
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAllPoolsInternal() {
    Column {
        FarmDetailsScreen(
            state = FarmDetailsState(
                StringTriple("", "", ""),
                "XOR-VAL Farm",
                "$123,294.53",
                "2.95 %",
                DEFAULT_ICON_URI,
                DEFAULT_ICON_URI,
                "DEO",
                DEFAULT_ICON_URI,
                "2 %",
            ),
            { },
            { },
            { }
        )
        FarmDetailsScreen(
            state = FarmDetailsState(
                StringTriple("", "", ""),
                "XOR-VAL Farm",
                "$123,294.53",
                "2.95 %",
                DEFAULT_ICON_URI,
                DEFAULT_ICON_URI,
                "DEO",
                DEFAULT_ICON_URI,
                "2 %",
                "34.1 %",
                13.3f,
                false,
                "98.7 %",
            ),
            { },
            { },
            { }
        )
    }
}
