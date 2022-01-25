/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import com.google.gson.Gson
import com.orhanobut.logger.Logger
import jp.co.soramitsu.common.data.network.substrate.Method
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.getByIdOrEmpty
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicTransferTypes
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseItem
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseItemSwap
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseItemTransfer
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow

fun mapRemoteTransfersToLocal(
    txs: List<HistoryResponseItem>,
    myAddress: String,
    tokens: List<Token>,
    gson: Gson
): Pair<List<ExtrinsicLocal>, List<ExtrinsicParamLocal>> {
    val params = mutableListOf<ExtrinsicParamLocal>()
    val extrinsic = mutableListOf<ExtrinsicLocal>()

    txs.forEach { historyItem ->

        var type: ExtrinsicType? = null

        if (historyItem.module.lowercase() == Pallete.ASSETS.palleteName.lowercase() &&
            historyItem.method.lowercase() == Method.TRANSFER.methodName.lowercase()
        ) {
            val transfer = gson.fromJson(historyItem.data, HistoryResponseItemTransfer::class.java)
            val curToken: Token? = tokens.find { it.id == transfer.assetId }

            if (curToken != null) {
                val (transferType, peer) = if (transfer.from == myAddress)
                    ExtrinsicTransferTypes.OUT to transfer.to
                else
                    ExtrinsicTransferTypes.IN to transfer.from
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.TOKEN.paramName,
                        transfer.assetId
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.PEER.paramName,
                        peer
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.TRANSFER_TYPE.paramName,
                        transferType.name
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.AMOUNT.paramName,
                        transfer.amount
                    )
                )
                type = ExtrinsicType.TRANSFER
            }
        } else if (historyItem.module.lowercase() == Pallete.LIQUIDITY_PROXY.palleteName.lowercase() &&
            historyItem.method.lowercase() == Method.SWAP.methodName.lowercase()
        ) {
            val swap = gson.fromJson(historyItem.data, HistoryResponseItemSwap::class.java)
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.TOKEN.paramName,
                    swap.baseAssetId,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.TOKEN2.paramName,
                    swap.targetAssetId,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.AMOUNT.paramName,
                    swap.baseAssetAmount.takeIf { it.isNotEmpty() }
                        ?: "0.0",
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.AMOUNT2.paramName,
                    swap.targetAssetAmount.takeIf { it.isNotEmpty() }
                        ?: "0.0",
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.AMOUNT3.paramName,
                    swap.liquidityProviderFee,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.SWAP_MARKET.paramName,
                    swap.selectedMarket,
                )
            )
            type = ExtrinsicType.SWAP
        }
        if (type != null) {
            val e = ExtrinsicLocal(
                txHash = historyItem.id,
                blockHash = historyItem.blockHash,
                fee = BigDecimal(historyItem.networkFee),
                status = ExtrinsicStatus.COMMITTED,
                timestamp = (
                    historyItem.timestamp.substringBefore(".").toLongOrNull()
                        ?: 0
                    ) * 1000,
                type = type,
                eventSuccess = historyItem.execution.success,
                localPending = false,
            )
            extrinsic.add(e)
        }
    }

    return extrinsic to params
}

fun mapBalance(
    bigInteger: BigInteger,
    precision: Int,
): BigDecimal =
    bigInteger.toBigDecimal().divide(BigDecimal(10.0.pow(precision)))

fun mapBalance(balance: BigDecimal, precision: Int): BigInteger =
    balance.multiply(BigDecimal(10.0.pow(precision))).toBigInteger()

fun mapTransactionLocalToTransaction(
    extrinsicLocal: ExtrinsicLocal,
    tokens: List<Token>,
    extrinsicParamLocal: List<ExtrinsicParamLocal>,
): Transaction {
    return with(extrinsicLocal) {
        when (type) {
            ExtrinsicType.TRANSFER -> Transaction.Transfer(
                txHash,
                blockHash,
                fee,
                mapTransactionStatusLocalToTransactionStatus(status),
                timestamp,
                eventSuccess,
                BigDecimal(
                    extrinsicParamLocal.valueOrDefault(
                        ExtrinsicParam.AMOUNT.paramName,
                        "0.0",
                        txHash
                    )
                ),
                extrinsicParamLocal.valueOrDefault(ExtrinsicParam.PEER.paramName, "", txHash),
                mapTransactionTransferType(extrinsicParamLocal, txHash),
                extrinsicParamLocal.valueOrDefault(ExtrinsicParam.TOKEN.paramName, "", txHash)
                    .let { tokenId ->
                        tokens.getByIdOrEmpty(tokenId)
                    },
            )
            ExtrinsicType.SWAP -> Transaction.Swap(
                txHash,
                blockHash,
                fee,
                mapTransactionStatusLocalToTransactionStatus(status),
                timestamp,
                eventSuccess,
                extrinsicParamLocal.valueOrDefault(ExtrinsicParam.TOKEN.paramName, "", txHash)
                    .let { tokenId ->
                        tokens.getByIdOrEmpty(tokenId)
                    },
                extrinsicParamLocal.valueOrDefault(ExtrinsicParam.TOKEN2.paramName, "", txHash)
                    .let { tokenId ->
                        tokens.getByIdOrEmpty(tokenId)
                    },
                BigDecimal(
                    extrinsicParamLocal.valueOrDefault(
                        ExtrinsicParam.AMOUNT.paramName,
                        "0.0",
                        txHash
                    )
                ),
                BigDecimal(
                    extrinsicParamLocal.valueOrDefault(
                        ExtrinsicParam.AMOUNT2.paramName,
                        "0.0",
                        txHash
                    )
                ),
                extrinsicParamLocal.valueOrDefault(ExtrinsicParam.SWAP_MARKET.paramName, "", txHash)
                    .let {
                        Market.values().find { m -> m.backString == it } ?: Market.SMART
                    },
                BigDecimal(
                    extrinsicParamLocal.valueOrDefault(
                        ExtrinsicParam.AMOUNT3.paramName,
                        "0.0",
                        txHash
                    )
                )
            )
        }
    }
}

private fun mapTransactionTransferType(
    extrinsicParamLocal: List<ExtrinsicParamLocal>,
    txHash: String
): TransactionTransferType {
    return when (
        ExtrinsicTransferTypes.valueOf(
            extrinsicParamLocal.valueOrDefault(
                ExtrinsicParam.TRANSFER_TYPE.paramName,
                "IN",
                txHash
            )
        )
    ) {
        ExtrinsicTransferTypes.OUT -> TransactionTransferType.OUTGOING
        ExtrinsicTransferTypes.IN -> TransactionTransferType.INCOMING
    }
}

private fun mapTransactionStatusLocalToTransactionStatus(statusRemote: ExtrinsicStatus): TransactionStatus {
    return when (statusRemote) {
        ExtrinsicStatus.COMMITTED -> TransactionStatus.COMMITTED
        ExtrinsicStatus.PENDING -> TransactionStatus.PENDING
        ExtrinsicStatus.REJECTED -> TransactionStatus.REJECTED
    }
}

private fun List<ExtrinsicParamLocal>.valueOrDefault(
    paramName: String,
    default: String,
    txHash: String
): String {
    val v = this.find { it.paramName == paramName }?.paramValue
    return if (v == null) {
        Logger.d("Extrinsic [$paramName] param not found in ${this.size} params in $txHash")
        default
    } else {
        v
    }
}
