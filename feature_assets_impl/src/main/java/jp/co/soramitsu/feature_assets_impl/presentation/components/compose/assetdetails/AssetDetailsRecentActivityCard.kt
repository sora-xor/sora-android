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

package jp.co.soramitsu.feature_assets_impl.presentation.components.compose.assetdetails

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.DEFAULT_ICON_URI
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryListItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.ui_core.component.button.TextButton
import jp.co.soramitsu.ui_core.component.button.properties.Order
import jp.co.soramitsu.ui_core.component.button.properties.Size
import jp.co.soramitsu.ui_core.resources.Dimens

@Composable
internal fun AssetDetailsRecentActivityCard(
    events: List<EventUiModel>,
    onShowMoreActivity: () -> Unit,
    onHistoryItemClick: (String) -> Unit
) {
    AssetDetailsCard(
        title = stringResource(id = R.string.asset_details_recent_activity),
        amount = "",
    ) {
        Column {
            Spacer(modifier = Modifier.size(Dimens.x1))
            events.forEach { event ->
                TxHistoryListItem(
                    modifier = Modifier.padding(horizontal = Dimens.x3),
                    eventUiModel = event,
                    onTxHistoryItemClick = onHistoryItemClick
                )
            }
            Spacer(modifier = Modifier.size(Dimens.x2))
            TextButton(
                modifier = Modifier
                    .padding(start = Dimens.x1, top = Dimens.x1)
                    .wrapContentSize(),
                text = stringResource(id = R.string.show_more),
                size = Size.ExtraSmall,
                order = Order.PRIMARY,
                onClick = onShowMoreActivity,
            )
        }
    }
}

@Preview
@Composable
private fun PreviewAssetDetailsRecentActivityCard() {
    AssetDetailsRecentActivityCard(
        events = listOf(
            EventUiModel.EventTxUiModel.EventTransferInUiModel(
                hash = "",
                tokenIcon = DEFAULT_ICON_URI,
                peerAddress = "address peer",
                dateTime = "12.08.2003",
                timestamp = 123123,
                amountFormatted = "123.123132 XOR",
                fiatFormatted = "$ 23.45",
                status = TransactionStatus.COMMITTED,
            )
        ),
        onHistoryItemClick = { },
        onShowMoreActivity = { },
    )
}
