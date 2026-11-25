package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import jp.co.soramitsu.common.ethereum.EthAddress

/**
 * The SCALE bytes of `ethBridge.transferToSidechain(asset_id, to, amount, network_id)` on runtime
 * 142: `0x1f03 ‖ AssetId32 ‖ H160 ‖ u128 (LE) ‖ u32 (LE)`. The app signs the call through its
 * pinned call table; this independent encoding pins the same bytes in tests.
 */
object HashiSoraCalls {

    private val U128_MAX: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)

    fun transferToSidechain(assetId: ByteArray, to: ByteArray, amount: BigInteger, networkId: Int): ByteArray {
        require(assetId.size == 32) { "HASHI_ASSET_ID_INVALID" }
        require(to.size == EthAddress.SIZE) { "HASHI_RECIPIENT_INVALID" }
        require(amount.signum() >= 0 && amount <= U128_MAX) { "HASHI_AMOUNT_RANGE_INVALID" }
        require(networkId >= 0) { "HASHI_NETWORK_INVALID" }
        val buffer = ByteBuffer.allocate(2 + 32 + 20 + 16 + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(HashiBridgeContract.PALLET_INDEX.toByte())
        buffer.put(HashiBridgeContract.TRANSFER_TO_SIDECHAIN_CALL_INDEX.toByte())
        buffer.put(assetId)
        buffer.put(to)
        buffer.put(u128LittleEndian(amount))
        buffer.putInt(networkId)
        return buffer.array()
    }

    fun isU128(amount: BigInteger): Boolean = amount.signum() >= 0 && amount <= U128_MAX

    private fun u128LittleEndian(value: BigInteger): ByteArray {
        val bigEndian = value.toByteArray().let { if (it.size > 16) it.copyOfRange(it.size - 16, it.size) else it }
        val out = ByteArray(16)
        for (i in bigEndian.indices) out[i] = bigEndian[bigEndian.size - 1 - i]
        return out
    }
}

/** `ethBridge` module errors the wallet explains (indices of runtime 142). */
object HashiSoraErrors {

    private val NAMES = mapOf(
        3 to "RequestIsAlreadyRegistered",
        10 to "InvalidAmount",
        14 to "InvalidAddress",
        29 to "UnsupportedToken",
        56 to "UnknownNetwork",
        60 to "Unavailable",
        65 to "UnknownAssetId",
        76 to "NonZeroDust",
        80 to "RequestsQueueFull",
        89 to "FailedToApplyDenomination",
        91 to "DeprecatedLegacyXor",
        96 to "RecoveryHalted",
    )

    fun nameOf(index: Int): String? = NAMES[index]

    /** True for a dispatch error text (`EthBridge.RecoveryHalted`, …) that means the bridge is paused. */
    fun meansPaused(detail: String?): Boolean =
        detail != null && (detail.contains("RecoveryHalted") || detail.contains("Unavailable"))
}
