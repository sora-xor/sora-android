/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
