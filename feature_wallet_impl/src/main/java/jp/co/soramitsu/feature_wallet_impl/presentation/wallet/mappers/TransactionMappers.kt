package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import java.util.Date

fun mapTransactionToSoraTransactionWithHeaders(
    transactions: List<Transaction>,
    resourceManager: ResourceManager,
    numbersFormatter: NumbersFormatter,
    dateTimeFormatter: DateTimeFormatter
): List<Any> {
    val transactionsWithHeaders = mutableListOf<Any>()
    var lastDateString = ""

    transactions.forEach {
        val recipientWithPrefix = when (it.type) {
            Transaction.Type.INCOMING, Transaction.Type.REWARD -> "${resourceManager.getString(R.string.wallet_from)} ${it.peerName}"
            Transaction.Type.OUTGOING -> "${resourceManager.getString(R.string.wallet_to)} ${it.peerName}"
            Transaction.Type.WITHDRAW -> it.peerName
        }

        val soraTransaction = with(it) {
            SoraTransaction(
                status.toString().substring(0, 1).toUpperCase() + status.toString().substring(1).toLowerCase(),
                transactionId.substring(0, 8),
                Date(timestamp * 1000L),
                this.peerId ?: "",
                recipientWithPrefix,
                it.peerName,
                it.amount,
                "${Const.SORA_SYMBOL} ${numbersFormatter.format(it.amount)}",
                type,
                details,
                it.fee,
                it.amount + it.fee
            )
        }

        val dayString = dateTimeFormatter.date2Day(soraTransaction.dateTime, resourceManager.getString(R.string.common_today), resourceManager.getString(R.string.common_yesterday))

        if (lastDateString != dayString) {
            lastDateString = dayString
            transactionsWithHeaders.add(EventHeader(dayString))
        }
        transactionsWithHeaders.add(soraTransaction)
    }

    return transactionsWithHeaders
}