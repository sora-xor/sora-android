/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.mappers

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.orhanobut.logger.Logger
import jp.co.soramitsu.common.data.network.substrate.Method
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.getByIdOrEmpty
import jp.co.soramitsu.common.util.ext.snakeCaseToCamelCase
import jp.co.soramitsu.core_db.model.ExtrinsicLiquidityType
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicTransferTypes
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionLiquidityType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionTransferType
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseBatchItem
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseItem
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseItemLiquidity
import jp.co.soramitsu.feature_wallet_impl.data.network.response.HistoryResponseItemLiquidityBatch
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

        if (historyItem.isMatch(Pallete.ASSETS, Method.TRANSFER)) {
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
                        ExtrinsicParam.EXTRINSIC_TYPE.paramName,
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
        } else if (historyItem.isMatch(Pallete.LIQUIDITY_PROXY, Method.SWAP)) {
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
        } else if (historyItem.isMatch(
                Pallete.POOL_XYK,
                Method.DEPOSIT_LIQUIDITY
            ) || historyItem.isMatch(Pallete.POOL_XYK, Method.WITHDRAW_LIQUIDITY)
        ) {
            val liquidity =
                gson.fromJson(historyItem.data, HistoryResponseItemLiquidity::class.java)
            val extrinsicLiquidityType = if (historyItem.isMatch(
                    Pallete.POOL_XYK,
                    Method.DEPOSIT_LIQUIDITY
                )
            ) ExtrinsicLiquidityType.ADD else ExtrinsicLiquidityType.WITHDRAW
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.EXTRINSIC_TYPE.paramName,
                    extrinsicLiquidityType.name,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.TOKEN.paramName,
                    liquidity.baseAssetId,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.TOKEN2.paramName,
                    liquidity.targetAssetId,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.AMOUNT.paramName,
                    liquidity.baseAssetAmount,
                )
            )
            params.add(
                ExtrinsicParamLocal(
                    historyItem.id,
                    ExtrinsicParam.AMOUNT2.paramName,
                    liquidity.targetAssetAmount,
                )
            )
            type = ExtrinsicType.ADD_REMOVE_LIQUIDITY
        } else if (historyItem.isMatch(
                Pallete.UTILITY,
                Method.BATCH
            ) || historyItem.isMatch(Pallete.UTILITY, Method.BATCH_ALL)
        ) {
            val batchItem = gson.fromJson<List<HistoryResponseBatchItem>>(
                historyItem.data,
                object : TypeToken<List<HistoryResponseBatchItem>>() {}.type
            )
            val dl = batchItem.find { it.method.isMatch(Method.DEPOSIT_LIQUIDITY) }
            if (dl != null) {
                val liquidity =
                    gson.fromJson(dl.data.args, HistoryResponseItemLiquidityBatch::class.java)
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.EXTRINSIC_TYPE.paramName,
                        ExtrinsicLiquidityType.ADD.name,
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.TOKEN.paramName,
                        liquidity.input_asset_a,
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.TOKEN2.paramName,
                        liquidity.input_asset_b,
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.AMOUNT.paramName,
                        liquidity.input_a_desired,
                    )
                )
                params.add(
                    ExtrinsicParamLocal(
                        historyItem.id,
                        ExtrinsicParam.AMOUNT2.paramName,
                        liquidity.input_b_desired,
                    )
                )
                type = ExtrinsicType.ADD_REMOVE_LIQUIDITY
            } else {
                val wl = batchItem.find { it.method.isMatch(Method.WITHDRAW_LIQUIDITY) }
                if (wl != null) {
                    val liquidity =
                        gson.fromJson(wl.data.args, HistoryResponseItemLiquidityBatch::class.java)
                    params.add(
                        ExtrinsicParamLocal(
                            historyItem.id,
                            ExtrinsicParam.EXTRINSIC_TYPE.paramName,
                            ExtrinsicLiquidityType.WITHDRAW.name,
                        )
                    )
                    params.add(
                        ExtrinsicParamLocal(
                            historyItem.id,
                            ExtrinsicParam.TOKEN.paramName,
                            liquidity.input_asset_a,
                        )
                    )
                    params.add(
                        ExtrinsicParamLocal(
                            historyItem.id,
                            ExtrinsicParam.TOKEN2.paramName,
                            liquidity.input_asset_b,
                        )
                    )
                    params.add(
                        ExtrinsicParamLocal(
                            historyItem.id,
                            ExtrinsicParam.AMOUNT.paramName,
                            liquidity.input_a_desired,
                        )
                    )
                    params.add(
                        ExtrinsicParamLocal(
                            historyItem.id,
                            ExtrinsicParam.AMOUNT2.paramName,
                            liquidity.input_b_desired,
                        )
                    )
                    type = ExtrinsicType.ADD_REMOVE_LIQUIDITY
                }
            }
        }
        if (type != null) {
            val e = ExtrinsicLocal(
                txHash = historyItem.id,
                accountAddress = myAddress,
                blockHash = historyItem.blockHash,
                fee = historyItem.networkFee.toBigDecimalOrDefault(),
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
                extrinsicParamLocal.valueOrDefault(
                    ExtrinsicParam.AMOUNT.paramName,
                    "0.0",
                    txHash
                ).toBigDecimalOrDefault(),
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
                extrinsicParamLocal.valueOrDefault(
                    ExtrinsicParam.AMOUNT.paramName,
                    "0.0",
                    txHash
                ).toBigDecimalOrDefault(),
                extrinsicParamLocal.valueOrDefault(
                    ExtrinsicParam.AMOUNT2.paramName,
                    "0.0",
                    txHash
                ).toBigDecimalOrDefault(),
                extrinsicParamLocal.valueOrDefault(ExtrinsicParam.SWAP_MARKET.paramName, "", txHash)
                    .let {
                        Market.values().find { m -> m.backString == it } ?: Market.SMART
                    },
                extrinsicParamLocal.valueOrDefault(
                    ExtrinsicParam.AMOUNT3.paramName,
                    "0.0",
                    txHash
                ).toBigDecimalOrDefault()
            )
            ExtrinsicType.ADD_REMOVE_LIQUIDITY -> Transaction.Liquidity(
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
                extrinsicParamLocal.valueOrDefault(
                    ExtrinsicParam.AMOUNT.paramName,
                    "0.0",
                    txHash
                ).toBigDecimalOrDefault(),
                extrinsicParamLocal.valueOrDefault(
                    ExtrinsicParam.AMOUNT2.paramName,
                    "0.0",
                    txHash
                ).toBigDecimalOrDefault(),
                mapTransactionLiquidityType(extrinsicParamLocal, txHash)
            )
        }
    }
}

private fun mapTransactionLiquidityType(
    extrinsicParamLocal: List<ExtrinsicParamLocal>,
    txHash: String
): TransactionLiquidityType {
    return when (
        ExtrinsicLiquidityType.valueOf(
            extrinsicParamLocal.valueOrDefault(
                ExtrinsicParam.EXTRINSIC_TYPE.paramName,
                "ADD",
                txHash
            )
        )
    ) {
        ExtrinsicLiquidityType.ADD -> TransactionLiquidityType.ADD
        ExtrinsicLiquidityType.WITHDRAW -> TransactionLiquidityType.WITHDRAW
    }
}

private fun mapTransactionTransferType(
    extrinsicParamLocal: List<ExtrinsicParamLocal>,
    txHash: String
): TransactionTransferType {
    return when (
        ExtrinsicTransferTypes.valueOf(
            extrinsicParamLocal.valueOrDefault(
                ExtrinsicParam.EXTRINSIC_TYPE.paramName,
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

private fun String.toBigDecimalOrDefault(v: BigDecimal = BigDecimal.ZERO): BigDecimal =
    runCatching { BigDecimal(this) }.getOrDefault(v)

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

private fun HistoryResponseItem.isMatch(pallet: Pallete, method: Method): Boolean =
    this.module.lowercase() == pallet.palletName.lowercase() && this.method.isMatch(method)

private fun String.isMatch(method: Method): Boolean {
    return this.lowercase() == method.methodName.lowercase() ||
        this.lowercase() == method.methodName.snakeCaseToCamelCase().lowercase() ||
        this.snakeCaseToCamelCase().lowercase() == method.methodName.snakeCaseToCamelCase()
        .lowercase()
}
