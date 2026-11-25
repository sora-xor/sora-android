package jp.co.soramitsu.common.ethereum

import java.math.BigInteger
import jp.co.soramitsu.xcrypto.hash.keccak256

/**
 * An EIP-1559 (type 2) transaction with an empty access list.
 *
 * Signing hash: `keccak256(0x02 ‖ rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
 * gasLimit, to, value, data, accessList]))`; the signed form appends `yParity, r, s`.
 */
class Eip1559Transaction(
    val chainId: Long,
    val nonce: BigInteger,
    val maxPriorityFeePerGas: BigInteger,
    val maxFeePerGas: BigInteger,
    val gasLimit: BigInteger,
    to: ByteArray,
    val value: BigInteger,
    data: ByteArray,
) {
    val to: ByteArray = to.copyOf()
    val data: ByteArray = data.copyOf()

    init {
        require(chainId > 0) { "ETH_TX_CHAIN_INVALID" }
        require(nonce.signum() >= 0 && value.signum() >= 0 && gasLimit.signum() > 0) { "ETH_TX_FIELD_INVALID" }
        require(maxPriorityFeePerGas.signum() >= 0 && maxFeePerGas >= maxPriorityFeePerGas) { "ETH_TX_FEE_INVALID" }
        require(to.size == EthAddress.SIZE) { "ETH_TX_TO_INVALID" }
    }

    private fun fields(): List<Rlp.Item> = listOf(
        Rlp.scalar(chainId),
        Rlp.scalar(nonce),
        Rlp.scalar(maxPriorityFeePerGas),
        Rlp.scalar(maxFeePerGas),
        Rlp.scalar(gasLimit),
        Rlp.bytes(to),
        Rlp.scalar(value),
        Rlp.bytes(data),
        Rlp.list(emptyList()),
    )

    fun unsignedSerialized(): ByteArray = byteArrayOf(TYPE) + Rlp.encode(Rlp.list(fields()))

    fun signingHash(): ByteArray = unsignedSerialized().keccak256()

    fun signedSerialized(signature: EthSignature): ByteArray = byteArrayOf(TYPE) + Rlp.encode(
        Rlp.list(
            fields() + listOf(
                Rlp.scalar(signature.yParity.toLong()),
                Rlp.scalar(signature.rValue),
                Rlp.scalar(signature.sValue),
            )
        )
    )

    /** Signs with [privateKey] (not kept) and checks that the signature recovers [expectedFrom]. */
    fun sign(privateKey: ByteArray, expectedFrom: ByteArray? = null): SignedEthTransaction {
        val hash = signingHash()
        val signature = EthereumSigner.sign(hash, privateKey)
        if (expectedFrom != null) {
            val recovered = EthereumSigner.recoverPublicKey(hash, signature)
                ?: error("ETH_TX_SIGNATURE_UNRECOVERABLE")
            check(EthAddress.fromPublicKey(recovered).contentEquals(expectedFrom)) { "ETH_TX_SIGNER_MISMATCH" }
        }
        val raw = signedSerialized(signature)
        return SignedEthTransaction(raw = raw, hash = raw.keccak256(), signature = signature)
    }

    companion object {
        const val TYPE: Byte = 0x02
    }
}

class SignedEthTransaction(raw: ByteArray, hash: ByteArray, val signature: EthSignature) {
    val raw: ByteArray = raw.copyOf()
    val hash: ByteArray = hash.copyOf()
    val rawHex: String get() = EthHex.encode(raw)
    val hashHex: String get() = EthHex.encode(hash)
}
