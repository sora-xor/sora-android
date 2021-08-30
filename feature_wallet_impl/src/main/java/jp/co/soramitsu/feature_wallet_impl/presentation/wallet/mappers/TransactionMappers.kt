/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventUiModel
import java.util.Date
import javax.inject.Inject

class TransactionMappers @Inject constructor(
    val resourceManager: ResourceManager,
    val numbersFormatter: NumbersFormatter,
    val dateTimeFormatter: DateTimeFormatter,
) {

    fun mapTransaction(tx: Transaction): EventUiModel.EventTxUiModel {
        when (tx) {
            is Transaction.Transfer -> {
                val assetName = tx.token.symbol
                val amountFormatted =
                    if (tx.status == TransactionStatus.REJECTED || tx.successStatus == false) "" else "${
                    numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                    } $assetName"
                val amountFullFormatted =
                    if (tx.status == TransactionStatus.REJECTED || tx.successStatus == false) "" else numbersFormatter.formatBigDecimal(
                        tx.amount,
                        tx.token.precision
                    )
                return EventUiModel.EventTxUiModel.EventTransferUiModel(
                    tx.txHash,
                    tx.transferType == TransactionTransferType.INCOMING,
                    tx.token.icon,
                    tx.peer,
                    dateTimeFormatter.formatDateTime(Date(tx.timestamp)),
                    tx.timestamp,
                    amountFormatted,
                    amountFullFormatted,
                    tx.status == TransactionStatus.PENDING,
                    tx.successStatus
                )
            }
            is Transaction.Swap -> {
                return EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel(
                    tx.txHash,
                    tx.tokenFrom.icon,
                    tx.tokenTo.icon,
                    "- %s %s".format(numbersFormatter.formatBigDecimal(tx.amountFrom, AssetHolder.ROUNDING), tx.tokenFrom.symbol),
                    "+ %s %s".format(numbersFormatter.formatBigDecimal(tx.amountTo, AssetHolder.ROUNDING), tx.tokenTo.symbol),
                    "+ %s".format(numbersFormatter.formatBigDecimal(tx.amountTo, tx.tokenTo.precision)),
                    tx.timestamp,
                    tx.status == TransactionStatus.PENDING,
                    tx.successStatus
                )
            }
        }
    }

//    fun mapTransactionToSoraTransactionWithHeaders(
//        transactions: List<Transaction>,
//    ): List<Any> {
//        val transactionsWithHeaders = mutableListOf<Any>()
//        var lastDateString = ""
//
//        transactions.forEach {
//            val createdAt = Date(it.timestamp)
//
//            val soraTransaction = with(it) {
//                val title = it.peerId.orEmpty()
//                val dateString = dateTimeFormatter.formatDateTime(createdAt)
//                val assetName = this.token.symbol
//
//                val amountFormatted =
//                    if (it.status == Transaction.Status.REJECTED || it.successStatus == false) "" else "${
//                        numbersFormatter.formatBigDecimal(it.amount, AssetHolder.ROUNDING)
//                    } $assetName"
//                val amountFullFormatted =
//                    if (it.status == Transaction.Status.REJECTED || it.successStatus == false) "" else numbersFormatter.formatBigDecimal(
//                        it.amount,
//                        this.token.precision
//                    )
//
//                SoraTransaction(
//                    it.soranetTxHash + it.ethTxHash,
//                    Transaction.Type.OUTGOING != it.type && Transaction.Type.WITHDRAW != it.type,
//                    this.token.icon,
//                    title,
//                    dateString,
//                    amountFormatted,
//                    amountFullFormatted,
//                    status == Transaction.Status.PENDING,
//                    it.successStatus
//                )
//            }
//            val dayString = dateTimeFormatter.formatDate(createdAt, DateTimeFormatter.MMMM_YYYY)
//
//            if (lastDateString != dayString) {
//                lastDateString = dayString
//                transactionsWithHeaders.add(EventHeader(dayString))
//            }
//            transactionsWithHeaders.add(soraTransaction)
//        }
//        return transactionsWithHeaders
//    }
}
