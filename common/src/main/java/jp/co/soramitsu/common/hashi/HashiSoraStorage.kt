package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.xcrypto.hash.blake2.blake2b128
import jp.co.soramitsu.xcrypto.hash.xxhash.xxHash128
import jp.co.soramitsu.xcrypto.hash.xxhash.xxHash64Concat

/**
 * Storage keys of the `ethBridge` pallet values the wallet reads, built by hand so they do not
 * depend on node metadata: `twox128(pallet) ‖ twox128(item) ‖ hashed keys`.
 */
object HashiSoraStorage {

    private const val PALLET = "EthBridge"

    private fun prefix(pallet: String, item: String): ByteArray =
        pallet.toByteArray(Charsets.US_ASCII).xxHash128() + item.toByteArray(Charsets.US_ASCII).xxHash128()

    private fun networkKey(networkId: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(networkId).array().xxHash64Concat()

    /** `requestStatuses(networkId: Twox64Concat, hash: Identity)`. */
    fun requestStatusKey(requestHash: ByteArray, networkId: Int = HashiBridgeContract.NETWORK_ID): String {
        require(requestHash.size == 32) { "HASHI_REQUEST_HASH_INVALID" }
        return EthHex.encode(prefix(PALLET, "RequestStatuses") + networkKey(networkId) + requestHash)
    }

    /** `loadToIncomingRequestHash(networkId: Twox64Concat, ethTxHash: Identity)`. */
    fun loadToIncomingRequestHashKey(ethTxHash: ByteArray, networkId: Int = HashiBridgeContract.NETWORK_ID): String {
        require(ethTxHash.size == 32) { "HASHI_ETH_TX_HASH_INVALID" }
        return EthHex.encode(prefix(PALLET, "LoadToIncomingRequestHash") + networkKey(networkId) + ethTxHash)
    }

    fun bridgeStatusKey(networkId: Int = HashiBridgeContract.NETWORK_ID): String =
        EthHex.encode(prefix(PALLET, "BridgeStatuses") + networkKey(networkId))

    fun bridgeContractAddressKey(networkId: Int = HashiBridgeContract.NETWORK_ID): String =
        EthHex.encode(prefix(PALLET, "BridgeContractAddress") + networkKey(networkId))

    val valMasterContractAddressKey: String get() = EthHex.encode(prefix(PALLET, "ValMasterContractAddress"))

    /** `Denomination.Denominator`: the factor of the denominated assets (TBCD on the bridge). */
    val denominatorKey: String get() = EthHex.encode(prefix("Denomination", "Denominator"))

    /** `requestsQueue(networkId: Twox64Concat)`: `Vec<H256>` of requests the peers still work on. */
    fun requestsQueueKey(networkId: Int = HashiBridgeContract.NETWORK_ID): String =
        EthHex.encode(prefix(PALLET, "RequestsQueue") + networkKey(networkId))

    /** `requestSubmissionHeight(networkId: Twox64Concat, hash: Identity)`: `u32`, 0 when absent. */
    fun requestSubmissionHeightKey(requestHash: ByteArray, networkId: Int = HashiBridgeContract.NETWORK_ID): String {
        require(requestHash.size == 32) { "HASHI_REQUEST_HASH_INVALID" }
        return EthHex.encode(prefix(PALLET, "RequestSubmissionHeight") + networkKey(networkId) + requestHash)
    }

    /** `peers(networkId: Twox64Concat)`: `BTreeSet<AccountId>`. */
    fun peersKey(networkId: Int = HashiBridgeContract.NETWORK_ID): String =
        EthHex.encode(prefix(PALLET, "Peers") + networkKey(networkId))

    /** `pendingPeer(networkId: Twox64Concat)`: `Option<AccountId>`. */
    fun pendingPeerKey(networkId: Int = HashiBridgeContract.NETWORK_ID): String =
        EthHex.encode(prefix(PALLET, "PendingPeer") + networkKey(networkId))

    /**
     * `bridgeProxy.lockedAssets(GenericNetworkId: Blake2_128Concat, AssetId: Blake2_128Concat)`:
     * the liquidity the bridge can unlock on SORA. `GenericNetworkId::EVMLegacy(0)` is variant 2.
     */
    fun lockedAssetsKey(assetId: ByteArray, networkId: Int = HashiBridgeContract.NETWORK_ID): String {
        require(assetId.size == 32) { "HASHI_ASSET_ID_INVALID" }
        val network = byteArrayOf(EVM_LEGACY_VARIANT) +
            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(networkId).array()
        return EthHex.encode(
            prefix("BridgeProxy", "LockedAssets") + network.blake2b128() + network + assetId.blake2b128() + assetId
        )
    }

    private const val EVM_LEGACY_VARIANT: Byte = 2

    /** A `u32` value (little endian). */
    fun decodeU32(hex: String): Long {
        val bytes = EthHex.decodeFixed(hex, 4)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
    }

    /** `Vec<[u8; size]>` / `BTreeSet<[u8; size]>`: a compact length, then the items. */
    fun decodeFixedVector(hex: String, size: Int): List<ByteArray> {
        val bytes = EthHex.decode(hex)
        val (count, offset) = Scale.compact(bytes, 0)
        require(count <= 100_000 && offset + count * size == bytes.size.toLong()) { "HASHI_SCALE_VECTOR_INVALID" }
        return (0 until count.toInt()).map { index ->
            val start = offset.toInt() + index * size
            bytes.copyOfRange(start, start + size)
        }
    }

    /** An `H160` value: exactly 20 bytes. */
    fun decodeAddress(hex: String): ByteArray = EthHex.decodeFixed(hex, EthAddress.SIZE)

    /** A `u128` value (little endian, 16 bytes). */
    fun decodeU128(hex: String): BigInteger {
        val bytes = EthHex.decodeFixed(hex, 16)
        return BigInteger(1, bytes.reversedArray())
    }
}

/** The few SCALE pieces the bridge reads by hand. */
internal object Scale {

    /** A compact integer at [offset]: (value, offset after it). */
    fun compact(bytes: ByteArray, offset: Int): Pair<Long, Long> {
        require(offset < bytes.size) { "HASHI_SCALE_COMPACT_INVALID" }
        val first = bytes[offset].toInt() and 0xff
        return when (first and 0x03) {
            0 -> (first ushr 2).toLong() to (offset + 1).toLong()
            1 -> {
                require(offset + 2 <= bytes.size) { "HASHI_SCALE_COMPACT_INVALID" }
                (((bytes[offset + 1].toInt() and 0xff) shl 8 or first) ushr 2).toLong() to (offset + 2).toLong()
            }
            2 -> {
                require(offset + 4 <= bytes.size) { "HASHI_SCALE_COMPACT_INVALID" }
                val value = ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
                (value ushr 2) to (offset + 4).toLong()
            }
            else -> throw IllegalArgumentException("HASHI_SCALE_COMPACT_TOO_LARGE")
        }
    }
}
