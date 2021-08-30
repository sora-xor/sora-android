package jp.co.soramitsu.feature_wallet_impl.data.mappers

import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.unsafeCast
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
import jp.co.soramitsu.feature_wallet_impl.data.network.response.ExtrinsicRemote
import jp.co.soramitsu.feature_wallet_impl.data.network.response.TransactionRemote
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.pow
import kotlin.math.roundToLong

fun mapRemoteTransfersToLocal(
    txs: List<TransactionRemote>,
    myAddress: String,
    tokens: List<Token>,
): Pair<List<ExtrinsicLocal>, List<ExtrinsicParamLocal>> {
    val params = mutableListOf<ExtrinsicParamLocal>()
    val extrinsics = txs.map { remote ->
        val curToken = requireNotNull(
            tokens.find { it.id == remote.attributes.assetId },
            { "Asset not found in mapping" }
        )
        val (type, peer) = if (remote.attributes.sender.attributes.address == myAddress)
            ExtrinsicTransferTypes.OUT to remote.attributes.destination.attributes.address
        else
            ExtrinsicTransferTypes.IN to remote.attributes.sender.attributes.address
        params.add(
            ExtrinsicParamLocal(
                remote.attributes.transaction_hash,
                ExtrinsicParam.TOKEN.paramName,
                remote.attributes.assetId
            )
        )
        params.add(
            ExtrinsicParamLocal(
                remote.attributes.transaction_hash,
                ExtrinsicParam.PEER.paramName,
                peer
            )
        )
        params.add(
            ExtrinsicParamLocal(
                remote.attributes.transaction_hash,
                ExtrinsicParam.TRANSFER_TYPE.paramName,
                type.name
            )
        )
        params.add(
            ExtrinsicParamLocal(
                remote.attributes.transaction_hash,
                ExtrinsicParam.AMOUNT.paramName,
                mapBalance(remote.attributes.value.toBigInteger(), curToken.precision).toString()
            )
        )
        mapRemoteTransferToLocal(remote, curToken)
    }
    return extrinsics to params
}

private fun mapRemoteTransferToLocal(
    tx: TransactionRemote,
    curToken: Token,
): ExtrinsicLocal {
    return ExtrinsicLocal(
        tx.attributes.transaction_hash,
        tx.attributes.block_hash,
        mapBalance(tx.attributes.fee.toBigInteger(), curToken.precision),
        ExtrinsicStatus.COMMITTED,
        tx.attributes.transaction_timestamp.roundToLong() * 1000,
        ExtrinsicType.TRANSFER,
        eventSuccess = true,
        localPending = false,
    )
}

fun mapRemoteErrorTransfersToLocal(
    txs: List<ExtrinsicRemote>,
    tokens: List<Token>,
): Pair<List<ExtrinsicLocal>, List<ExtrinsicParamLocal>> {
    val params = mutableListOf<ExtrinsicParamLocal>()
    val extrinsics = txs.map { remote ->
        val (extrinsic, prms) = mapRemoteErrorTransferToLocal(remote, tokens)
        params.addAll(prms)
        extrinsic
    }
    return extrinsics to params
}

private fun mapRemoteErrorTransferToLocal(
    tx: ExtrinsicRemote,
    tokens: List<Token>
): Pair<ExtrinsicLocal, List<ExtrinsicParamLocal>> {
    fun <T> getParam(paramName: String): T {
        return requireNotNull(
            tx.attributes.params.find { p -> p.name == paramName },
            { "[$paramName] param not found in mapping" }
        ).value.unsafeCast()
    }

    val assetId = getParam<String>("asset_id")
    val to = getParam<String>("to")
    val amount = getParam<Double>("amount").toBigDecimal().toBigInteger()
    val curToken = requireNotNull(
        tokens.find { it.id == assetId },
        { "Asset not found in mapping" }
    )
    return ExtrinsicLocal(
        tx.attributes.extrinsic_hash,
        tx.attributes.block_hash,
        mapBalance(tx.attributes.fee, curToken.precision),
        ExtrinsicStatus.COMMITTED,
        tx.attributes.transaction_timestamp.roundToLong() * 1000,
        ExtrinsicType.TRANSFER,
        eventSuccess = false,
        localPending = false,
    ) to listOf(
        ExtrinsicParamLocal(
            tx.attributes.extrinsic_hash,
            ExtrinsicParam.TRANSFER_TYPE.paramName,
            ExtrinsicTransferTypes.OUT.name
        ),
        ExtrinsicParamLocal(
            tx.attributes.extrinsic_hash,
            ExtrinsicParam.TOKEN.paramName,
            assetId
        ),
        ExtrinsicParamLocal(
            tx.attributes.extrinsic_hash,
            ExtrinsicParam.AMOUNT.paramName,
            mapBalance(amount, curToken.precision).toString()
        ),
        ExtrinsicParamLocal(
            tx.attributes.extrinsic_hash,
            ExtrinsicParam.PEER.paramName,
            to
        ),
    )
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
                BigDecimal(extrinsicParamLocal.first { it.paramName == ExtrinsicParam.AMOUNT.paramName }.paramValue),
                extrinsicParamLocal.first { it.paramName == ExtrinsicParam.PEER.paramName }.paramValue,
                mapTransactionTransferType(extrinsicParamLocal),
                extrinsicParamLocal.first { it.paramName == ExtrinsicParam.TOKEN.paramName }.paramValue.let { tokenId ->
                    tokens.first { token -> token.id == tokenId }
                },
            )
            ExtrinsicType.SWAP -> Transaction.Swap(
                txHash,
                blockHash,
                fee,
                mapTransactionStatusLocalToTransactionStatus(status),
                timestamp,
                eventSuccess,
                extrinsicParamLocal.first { it.paramName == ExtrinsicParam.TOKEN.paramName }.paramValue.let { tokenId ->
                    tokens.first { token -> token.id == tokenId }
                },
                extrinsicParamLocal.first { it.paramName == ExtrinsicParam.TOKEN2.paramName }.paramValue.let { tokenId ->
                    tokens.first { token -> token.id == tokenId }
                },
                BigDecimal(extrinsicParamLocal.first { it.paramName == ExtrinsicParam.AMOUNT.paramName }.paramValue),
                BigDecimal(extrinsicParamLocal.first { it.paramName == ExtrinsicParam.AMOUNT2.paramName }.paramValue),
                extrinsicParamLocal.first { it.paramName == ExtrinsicParam.SWAP_MARKET.paramName }.paramValue.let {
                    Market.values().find { m -> m.backString == it } ?: Market.SMART
                }
            )
        }
    }
}

fun mapTransactionTransferType(extrinsicParamLocal: List<ExtrinsicParamLocal>): TransactionTransferType {
    return when (ExtrinsicTransferTypes.valueOf(extrinsicParamLocal.first { it.paramName == ExtrinsicParam.TRANSFER_TYPE.paramName }.paramValue)) {
        ExtrinsicTransferTypes.OUT -> TransactionTransferType.OUTGOING
        ExtrinsicTransferTypes.IN -> TransactionTransferType.INCOMING
    }
}

fun mapTransactionStatusLocalToTransactionStatus(statusRemote: ExtrinsicStatus): TransactionStatus {
    return when (statusRemote) {
        ExtrinsicStatus.COMMITTED -> TransactionStatus.COMMITTED
        ExtrinsicStatus.PENDING -> TransactionStatus.PENDING
        ExtrinsicStatus.REJECTED -> TransactionStatus.REJECTED
    }
}
