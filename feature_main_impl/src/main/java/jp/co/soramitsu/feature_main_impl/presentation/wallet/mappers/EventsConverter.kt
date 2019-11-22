/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.wallet.mappers

import android.content.Context
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.date2Day
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.presentation.wallet.model.SoraTransaction
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.recent_events.list.models.EventItem

object EventsConverter {

    fun fromTransactionVmToCard(transactionVms: List<SoraTransaction>, context: Context, numbersFormatter: NumbersFormatter): Map<String, List<EventItem>> {
        val cards = LinkedHashMap<String, List<EventItem>>()

        transactionVms.forEach {
            val dayString = it.dateTime.date2Day(context.getString(R.string.today), context.getString(R.string.yesterday))

            if (!cards.containsKey(dayString)) cards[dayString] = ArrayList()

            val recipient = when (it.type) {
                Transaction.Type.INCOMING -> "${context.getString(R.string.from)} ${it.recipient}"
                Transaction.Type.REWARD -> "${context.getString(R.string.from)} ${it.recipient}"
                Transaction.Type.OUTGOING -> "${context.getString(R.string.to)} ${it.recipient}"
                Transaction.Type.WITHDRAW -> it.recipient
            }

            val isIncoming = Transaction.Type.OUTGOING != it.type && Transaction.Type.WITHDRAW != it.type

            (cards[dayString] as ArrayList<EventItem>)
                .add(
                    EventItem(
                        recipient,
                        it.transactionId,
                        "${Const.SORA_SYMBOL} ${numbersFormatter.format(it.amount)}",
                        it.status,
                        it.dateTime,
                        isIncoming,
                        it.description
                    )
                )
        }
        return cards
    }
}