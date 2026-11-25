package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.xcrypto.hash.keccak256

/** EIP-1559 fees for one transaction. */
class EthFees(val maxPriorityFeePerGas: BigInteger, val maxFeePerGas: BigInteger, val gasLimit: BigInteger) {
    /** The most this transaction can cost. */
    val maximumCost: BigInteger get() = maxFeePerGas.multiply(gasLimit)
}

object HashiEthereumPolicy {

    /**
     * `maxPriorityFeePerGas = min(eth_maxPriorityFeePerGas, 3 gwei)`, `maxFeePerGas = 2 × baseFee
     * + priority`, `gasLimit = estimate × 1.2` (rounded up).
     */
    fun fees(baseFee: BigInteger, suggestedPriority: BigInteger, gasEstimate: BigInteger): EthFees {
        require(baseFee.signum() >= 0 && suggestedPriority.signum() >= 0 && gasEstimate.signum() > 0) { "ETH_FEES_INVALID" }
        val priority = suggestedPriority.min(HashiBridgeContract.MAX_PRIORITY_FEE_PER_GAS)
        val gasLimit = gasEstimate.multiply(BigInteger.valueOf(12)).add(BigInteger.valueOf(9)).divide(BigInteger.TEN)
        return EthFees(priority, baseFee.shiftLeft(1).add(priority), gasLimit)
    }

    /**
     * A replacement at the same nonce: both fees at least 12.5 % above the old ones (rounded up),
     * and never below what the network asks now ([current]).
     */
    fun speedUp(old: EthFees, current: EthFees): EthFees {
        fun bumped(value: BigInteger) = value.multiply(BigInteger.valueOf(1125)).add(BigInteger.valueOf(999)).divide(BigInteger.valueOf(1000))
        val priority = bumped(old.maxPriorityFeePerGas).max(current.maxPriorityFeePerGas)
        val maxFee = bumped(old.maxFeePerGas).max(current.maxFeePerGas).max(priority)
        return EthFees(priority, maxFee, old.gasLimit)
    }

    fun isValidSpeedUp(old: EthFees, new: EthFees): Boolean =
        new.maxPriorityFeePerGas.multiply(BigInteger.valueOf(1000)) >= old.maxPriorityFeePerGas.multiply(BigInteger.valueOf(1125)) &&
            new.maxFeePerGas.multiply(BigInteger.valueOf(1000)) >= old.maxFeePerGas.multiply(BigInteger.valueOf(1125)) &&
            new.gasLimit == old.gasLimit
}

/**
 * What proves an Ethereum step happened: a canonical receipt with status 1 *and* the event the
 * contract emits for it (spec §8 E). Bridge events have no indexed fields.
 */
object HashiReceiptProof {

    val DEPOSIT_TOPIC: ByteArray = "Deposit(bytes32,uint256,address,bytes32)".toByteArray().keccak256()
    val WITHDRAWAL_TOPIC: ByteArray = "Withdrawal(bytes32)".toByteArray().keccak256()
    val TRANSFER_TOPIC: ByteArray = "Transfer(address,address,uint256)".toByteArray().keccak256()

    /** A Bridge `Deposit(destination, amount, token, …)` log with these values. */
    fun provesDeposit(receipt: EthReceipt, destination: ByteArray, amount: BigInteger, token: ByteArray): Boolean =
        receipt.status == 1 && receipt.logs.any { log ->
            EthAddress.equal(log.address, HashiBridgeContract.bridge) &&
                log.topics.size == 1 && log.topics[0].contentEquals(DEPOSIT_TOPIC) &&
                log.data.size == 128 &&
                log.data.copyOfRange(0, 32).contentEquals(destination) &&
                log.data.copyOfRange(32, 64).contentEquals(EthAbi.uintWord(amount)) &&
                log.data.copyOfRange(64, 96).contentEquals(ByteArray(12) + token)
        }

    /**
     * A claim: Bridge `Withdrawal(txHash)`, or for a VAL mint the VAL token's `Transfer` from the
     * zero address to the recipient of exactly the amount.
     */
    fun provesClaim(receipt: EthReceipt, claim: HashiClaimCall): Boolean {
        if (receipt.status != 1) return false
        return when (claim.method) {
            HashiClaimCall.Method.MINT_TOKENS_BY_PEERS -> receipt.logs.any { log ->
                EthAddress.equal(log.address, HashiBridgeContract.valToken) &&
                    log.topics.size == 3 && log.topics[0].contentEquals(TRANSFER_TOPIC) &&
                    log.topics[1].contentEquals(ByteArray(32)) &&
                    log.topics[2].contentEquals(ByteArray(12) + claim.recipient) &&
                    log.data.contentEquals(EthAbi.uintWord(claim.amount))
            }
            else -> receipt.logs.any { log ->
                EthAddress.equal(log.address, HashiBridgeContract.bridge) &&
                    log.topics.size == 1 && log.topics[0].contentEquals(WITHDRAWAL_TOPIC) &&
                    log.data.contentEquals(claim.txHash)
            }
        }
    }

    fun topicHex(topic: ByteArray): String = EthHex.encode(topic)
}
