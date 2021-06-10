/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.mappers

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.EventHeader
import jp.co.soramitsu.feature_wallet_impl.presentation.wallet.model.SoraTransaction
import java.util.Date
import javax.inject.Inject

class TransactionMappers @Inject constructor(
    val resourceManager: ResourceManager,
    val numbersFormatter: NumbersFormatter,
    val dateTimeFormatter: DateTimeFormatter,
    val assetHolder: AssetHolder,
) {

    fun mapTransactionToSoraTransactionWithHeaders(
        transactions: List<Transaction>,
        assets: List<Asset>?,
        myAccountId: String?,
        myEthAddress: String?
    ): List<Any> {
        val transactionsWithHeaders = mutableListOf<Any>()
        var lastDateString = ""

        myAccountId?.let {
            myEthAddress?.let {
                transactions.forEach {
                    val createdAt = Date(it.timestamp)

                    val soraTransaction = with(it) {
                        val statusIcon = assetHolder.iconShadow(it.assetId)
                        val title = it.peerId.orEmpty()
                        val dateString = dateTimeFormatter.formatDateTime(createdAt)
                        val asset = requireNotNull(assets?.find { asset -> asset.id == assetId }, { "Asset not found" })
                        val assetName = asset.symbol

                        val amountFormatted =
                            if (it.status == Transaction.Status.REJECTED || it.successStatus == false) "" else "${
                            numbersFormatter.formatBigDecimal(it.amount, asset.roundingPrecision)
                            } $assetName"
                        val amountFullFormatted =
                            if (it.status == Transaction.Status.REJECTED || it.successStatus == false) "" else numbersFormatter.formatBigDecimal(it.amount, asset.precision)

                        SoraTransaction(
                            it.soranetTxHash + it.ethTxHash,
                            Transaction.Type.OUTGOING != it.type && Transaction.Type.WITHDRAW != it.type,
                            statusIcon,
                            peerName,
                            title,
                            dateString,
                            amountFormatted,
                            amountFullFormatted,
                            status == Transaction.Status.PENDING,
                            it.successStatus
                        )
                    }
                    val dayString = dateTimeFormatter.formatDate(createdAt, DateTimeFormatter.MMMM_YYYY)

                    if (lastDateString != dayString) {
                        lastDateString = dayString
                        transactionsWithHeaders.add(EventHeader(dayString))
                    }
                    transactionsWithHeaders.add(soraTransaction)
                }
            }
        }

        return transactionsWithHeaders
    }
}
