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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.txdetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.DetailsItem
import jp.co.soramitsu.common.presentation.compose.components.DetailsItemNetworkFee
import jp.co.soramitsu.common.util.ext.testTagAsId
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.toColor
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.toName
import jp.co.soramitsu.ui_core.component.button.BleachedButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.component.card.ContentCard
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun BasicTxDetails(
    modifier: Modifier,
    state: BasicTxDetailsState,
    imageContent: @Composable BoxScope.() -> Unit,
    amountContent: @Composable BoxScope.() -> Unit,
    onCloseClick: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(
        modifier = modifier.padding(vertical = Dimens.x1_5, horizontal = Dimens.x2),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            ContentCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(top = 36.dp),
                innerPadding = PaddingValues(
                    top = 46.dp,
                    bottom = Dimens.x3,
                    start = Dimens.x3,
                    end = Dimens.x3
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(Dimens.x1_5)
                                .align(Alignment.CenterVertically),
                            painter = painterResource(id = state.txTypeIcon),
                            tint = MaterialTheme.customColors.fgSecondary,
                            contentDescription = state.txTypeTitle,
                        )
                        Text(
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .padding(horizontal = Dimens.x1),
                            text = state.txTypeTitle,
                            style = MaterialTheme.customTypography.textXSBold,
                            color = MaterialTheme.customColors.fgSecondary,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Dimens.x1)
                            .wrapContentHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        amountContent()
                    }
                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x1),
                        text = stringResource(id = R.string.status_title),
                        value1 = stringResource(id = state.txStatus.toName()),
                        valueColor = state.txStatus.toColor(),
                    )
                    Divider(
                        modifier = Modifier.padding(bottom = Dimens.x1),
                        thickness = 1.dp,
                        color = MaterialTheme.customColors.bgPage
                    )
                    DetailsItem(
                        modifier = Modifier.padding(bottom = Dimens.x1),
                        text = stringResource(id = R.string.transaction_date_1),
                        value1 = state.time,
                    )
                    if (state.networkFee != null) {
                        Divider(
                            modifier = Modifier.padding(bottom = Dimens.x1),
                            thickness = 1.dp,
                            color = MaterialTheme.customColors.bgPage
                        )
                        DetailsItemNetworkFee(
                            fee = state.networkFee,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(Dimens.x9)
                    .align(Alignment.TopCenter)
            ) {
                imageContent()
            }
        }
        ContentCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(
                    vertical = Dimens.x2,
                ),
            innerPadding = PaddingValues(Dimens.x3),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                DetailInfoItem(
                    modifier = Modifier.padding(bottom = Dimens.x3),
                    title = stringResource(id = R.string.transaction_id),
                    info = state.txHash,
                    onItemClick = { onCopy.invoke(state.txHash) },
                )
                DetailInfoItem(
                    modifier = Modifier.padding(bottom = Dimens.x3),
                    title = stringResource(id = R.string.block_id),
                    info = state.blockHash.orEmpty(),
                    onItemClick = { state.blockHash?.let { onCopy.invoke(state.blockHash) } },
                )
                state.infos.forEach {
                    DetailInfoItem(
                        modifier = Modifier.padding(bottom = Dimens.x3),
                        title = it.title,
                        info = it.info,
                        onItemClick = { onCopy.invoke(it.info) },
                    )
                }
                DetailInfoItem(
                    title = stringResource(id = R.string.common_sender),
                    info = state.sender,
                    onItemClick = { onCopy.invoke(state.sender) },
                )
            }
        }
        BleachedButton(
            size = Size.Large,
            modifier = Modifier
                .testTagAsId("PrimaryButton")
                .fillMaxWidth(),
            order = Order.TERTIARY,
            text = stringResource(id = R.string.common_close),
            onClick = onCloseClick,
        )
    }
}

@Composable
private fun DetailInfoItem(
    modifier: Modifier = Modifier,
    title: String,
    info: String,
    onItemClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(onClick = onItemClick),
    ) {
        Text(
            modifier = Modifier.wrapContentSize(),
            text = title,
            maxLines = 1,
            style = MaterialTheme.customTypography.textXSBold,
            color = MaterialTheme.customColors.fgSecondary
        )
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = info,
            style = MaterialTheme.customTypography.textXS,
            color = MaterialTheme.customColors.fgPrimary
        )
    }
}
