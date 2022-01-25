/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.util

import androidx.paging.PagingData
import androidx.paging.TerminalSeparatorType
import androidx.paging.insertSeparators
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.AssetBalanceStyle
import jp.co.soramitsu.common.presentation.view.assetselectbottomsheet.adapter.AssetListItemModel
import jp.co.soramitsu.common.util.DateTimeUtils
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers.TransactionMappers
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import java.util.Date

fun Asset.mapAssetToAssetModel(
    numbersFormatter: NumbersFormatter,
    style: AssetBalanceStyle
): AssetListItemModel {
    return AssetListItemModel(
        token.icon,
        token.name,
        AssetBalanceData(
            amount = numbersFormatter.formatBigDecimal(balance.transferable, AssetHolder.ROUNDING),
            style = style,
        ),
        token.symbol,
        position,
        token.id
    )
}

fun PagingData<EventUiModel.EventTxUiModel>.insertHistorySeparators(transactionMappers: TransactionMappers) =
    this.insertSeparators(terminalSeparatorType = TerminalSeparatorType.SOURCE_COMPLETE) { before, after ->
        when {
            before == null && after != null -> {
                EventUiModel.EventTimeSeparatorUiModel(
                    transactionMappers.dateTimeFormatter.formatDate(
                        Date(after.timestamp),
                        DateTimeFormatter.MMMM_DD_YYYY
                    )
                )
            }
            before == null -> {
                null
            }
            after != null && !DateTimeUtils.isSameDay(
                before.timestamp,
                after.timestamp
            ) -> {
                EventUiModel.EventTimeSeparatorUiModel(
                    transactionMappers.dateTimeFormatter.formatDate(
                        Date(after.timestamp),
                        DateTimeFormatter.MMMM_DD_YYYY
                    )
                )
            }
            else -> {
                null
            }
        }
    }
