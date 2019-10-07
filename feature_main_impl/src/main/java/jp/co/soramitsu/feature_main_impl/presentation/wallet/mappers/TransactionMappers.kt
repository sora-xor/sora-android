/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.wallet.mappers

import android.content.Context
import jp.co.soramitsu.capital_ui.presentation.util.TransactionStatus
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.DeciminalFormatter
import jp.co.soramitsu.common.util.ext.formatDate
import jp.co.soramitsu.common.util.ext.formatTime
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.information_element.model.InformationItem
import java.util.Date

fun mapTransactionToSoraTransaction(transaction: Transaction): SoraTransaction {
    return with(transaction) {
        SoraTransaction(
            status.toString().substring(0, 1).toUpperCase() + status.toString().substring(1).toLowerCase(),
            transactionId.substring(0, 8),
            Date(timestamp * 1000L),
            this.peerId ?: "",
            peerName,
            amount,
            transaction.type,
            details,
            fee
        )
    }
}

fun mapTransactionToInformationItemList(transaction: SoraTransaction, context: Context): List<InformationItem> {
    return with(transaction) {
        val informationItems = mutableListOf<InformationItem>()

        if (transactionId.isNotEmpty()) {
            informationItems.add(InformationItem(context.getString(R.string.identifier), transactionId, null))
        }

        val status = TransactionStatus.fromString(status)

        informationItems.add(InformationItem(context.getString(R.string.status), context.getString(status.status), status.icon))

        var dateTimeString = "${dateTime.formatDate()}, ${dateTime.formatTime()}"

        informationItems.add(InformationItem(context.getString(R.string.date_and_time), dateTimeString, null))

        val typeStr = when (type) {
            Transaction.Type.INCOMING -> context.getString(R.string.receive)
            Transaction.Type.REWARD -> context.getString(R.string.receive)
            Transaction.Type.OUTGOING -> context.getString(R.string.send)
            Transaction.Type.WITHDRAW -> context.getString(R.string.withdraw)
        }

        if (type == Transaction.Type.WITHDRAW) informationItems.add(InformationItem(context.getString(R.string.type), typeStr, null))

        if (Transaction.Type.WITHDRAW != type && recipient.isNotEmpty()) {
            informationItems.add(InformationItem(if (Transaction.Type.INCOMING == type) context.getString(R.string.sender) else context.getString(R.string.recipient), recipient, null))
        }

        informationItems.add(InformationItem(context.getString(R.string.amount), "${Const.SORA_SYMBOL} $amount", if (Transaction.Type.INCOMING == type) R.drawable.ic_plus else null))

        if (type != Transaction.Type.INCOMING) {
            informationItems.add(InformationItem(context.getString(R.string.transaction_fee), "${Const.SORA_SYMBOL} $fee", null))

            informationItems.add(InformationItem(context.getString(R.string.total_amount), "${Const.SORA_SYMBOL} ${DeciminalFormatter.format(fee + amount)}", if (Transaction.Type.INCOMING == type) R.drawable.ic_plus else R.drawable.ic_minus))
        }

        informationItems
    }
}