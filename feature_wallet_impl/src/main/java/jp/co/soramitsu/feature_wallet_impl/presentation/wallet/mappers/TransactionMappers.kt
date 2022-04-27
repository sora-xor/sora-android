/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionLiquidityType
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
                return if (tx.transferType == TransactionTransferType.INCOMING)
                    EventUiModel.EventTxUiModel.EventTransferInUiModel(
                        tx.txHash,
                        tx.token.icon,
                        tx.peer.truncateUserAddress(),
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.timestamp)),
                        tx.timestamp,
                        Pair(
                            "+%s".format(
                                numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                            ),
                            tx.token.symbol,
                        ),
                        tx.status == TransactionStatus.PENDING,
                        !(tx.status == TransactionStatus.REJECTED || tx.successStatus == false)
                    ) else
                    EventUiModel.EventTxUiModel.EventTransferOutUiModel(
                        tx.txHash,
                        tx.token.icon,
                        tx.peer.truncateUserAddress(),
                        dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.timestamp)),
                        tx.timestamp,
                        Pair(
                            "-%s".format(
                                numbersFormatter.formatBigDecimal(tx.amount, AssetHolder.ROUNDING)
                            ),
                            tx.token.symbol,
                        ),
                        tx.status == TransactionStatus.PENDING,
                        !(tx.status == TransactionStatus.REJECTED || tx.successStatus == false)
                    )
            }
            is Transaction.Swap -> {
                return EventUiModel.EventTxUiModel.EventLiquiditySwapUiModel(
                    tx.txHash,
                    tx.tokenFrom.icon,
                    tx.tokenTo.icon,
                    Pair(
                        "-%s".format(
                            numbersFormatter.formatBigDecimal(
                                tx.amountFrom,
                                AssetHolder.ROUNDING
                            )
                        ),
                        tx.tokenFrom.symbol
                    ),
                    Pair(
                        "+%s".format(
                            numbersFormatter.formatBigDecimal(
                                tx.amountTo,
                                AssetHolder.ROUNDING
                            )
                        ),
                        tx.tokenTo.symbol
                    ),
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.timestamp)),
                    tx.timestamp,
                    tx.status == TransactionStatus.PENDING,
                    !(tx.status == TransactionStatus.REJECTED || tx.successStatus == false)
                )
            }
            is Transaction.Liquidity -> {
                return EventUiModel.EventTxUiModel.EventLiquidityAddUiModel(
                    tx.txHash,
                    tx.timestamp,
                    tx.status == TransactionStatus.PENDING,
                    !(tx.status == TransactionStatus.REJECTED || tx.successStatus == false),
                    dateTimeFormatter.formatTimeWithoutSeconds(Date(tx.timestamp)),
                    tx.token1.icon,
                    tx.token2.icon,
                    Pair(
                        numbersFormatter.formatBigDecimal(
                            tx.amount1,
                            AssetHolder.ROUNDING
                        ),
                        tx.token1.symbol
                    ),
                    Pair(
                        numbersFormatter.formatBigDecimal(
                            tx.amount2,
                            AssetHolder.ROUNDING
                        ),
                        tx.token2.symbol
                    ),
                    tx.type == TransactionLiquidityType.ADD
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
