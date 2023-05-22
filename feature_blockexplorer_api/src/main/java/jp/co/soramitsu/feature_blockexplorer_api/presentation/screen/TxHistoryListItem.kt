/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel -> {
            EventSwap(
                modifier = modifier
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventReferralProgramUiModel -> {
            EventReferral(
                modifier = modifier
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventTransferInUiModel -> {
            EventTransferReceived(
                modifier = modifier
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        is EventUiModel.EventTxUiModel.EventTransferOutUiModel -> {
            EventTransferSent(
                modifier = modifier
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel
            )
        }
        EventUiModel.EventUiLoading -> {
            CircularProgressIndicator(modifier = Modifier.size(Size.Small))
        }
    }
}
