/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
