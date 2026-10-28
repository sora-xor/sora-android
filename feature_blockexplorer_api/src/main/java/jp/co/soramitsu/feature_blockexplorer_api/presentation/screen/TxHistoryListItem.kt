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

package jp.co.soramitsu.feature_blockexplorer_api.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens
import jp.co.soramitsu.ui_core.theme.customColors
import jp.co.soramitsu.ui_core.theme.customTypography

@Composable
fun TxHistoryListItem(
    modifier: Modifier,
    onTxHistoryItemClick: (String) -> Unit,
    eventUiModel: EventUiModel,
) {
    when (eventUiModel) {
        is EventUiModel.EventTimeSeparatorUiModel -> {
            Text(
                modifier = modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .padding(vertical = Dimens.x1, horizontal = Dimens.x3),
                text = eventUiModel.title.uppercase(),
                style = MaterialTheme.customTypography.headline4,
                color = MaterialTheme.customColors.fgSecondary,
                maxLines = 1,
            )
        }
        is EventUiModel.EventTxUiModel.EventLiquidityAddUiModel -> {
            EventLiquidity(
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventDemeterRewardUiModel -> {
            EventDemeterReward(
                eventUiModel = eventUiModel,
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
            )
        }
        is EventUiModel.EventTxUiModel.EventDemeterStakeUiModel -> {
            EventDemeterStake(
                eventUiModel = eventUiModel,
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
            )
        }
        is EventUiModel.EventTxUiModel.EventAdarIncomeUiModel -> {
            EventAdarIncome(
                eventUiModel = eventUiModel,
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
            )
        }
        is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel -> {
            EventSwap(
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventReferralProgramUiModel -> {
            EventReferral(
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventTransferInUiModel -> {
            EventTransferReceived(
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventTransferOutUiModel -> {
            EventTransferSent(
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventEthTransfer -> {
            EventEthTransfer(
                modifier = modifier
                    .background(color = MaterialTheme.customColors.bgSurface)
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel,
            )
        }
        EventUiModel.EventUiLoading -> {
            CircularProgressIndicator(
                modifier = Modifier.size(Size.Small),
                color = MaterialTheme.customColors.accentPrimary
            )
        }
    }
}
