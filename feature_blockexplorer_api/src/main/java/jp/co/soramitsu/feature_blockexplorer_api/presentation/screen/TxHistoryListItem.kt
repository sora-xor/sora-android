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
        is EventUiModel.EventTxUiModel.EventEthTransfer -> {
            EventEthTransfer(
                modifier = modifier
                    .clickable { onTxHistoryItemClick(eventUiModel.txHash) },
                eventUiModel = eventUiModel,
            )
        }
        EventUiModel.EventUiLoading -> {
            CircularProgressIndicator(modifier = Modifier.size(Size.Small))
        }
    }
}
