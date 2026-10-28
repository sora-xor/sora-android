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
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.ReceivedTransfersNote
import jp.co.soramitsu.feature_blockexplorer_api.presentation.screen.TxHistoryListItem
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.EventUiModel
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.common.presentation.compose.neon.TextButton
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
            ReceivedTransfersNote(
                modifier = Modifier.padding(start = Dimens.x3, end = Dimens.x3, top = Dimens.x2),
            )
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
