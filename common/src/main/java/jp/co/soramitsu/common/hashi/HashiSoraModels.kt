package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** How an asset is registered on the bridge (`ethBridge` `AssetKind`). */
enum class HashiAssetKind(val wireName: String) {
    /** Native to SORA (XOR, PSWAP, …); its ERC-20 is minted and burned by the bridge contract. */
    THISCHAIN("Thischain"),

    /** Native to Ethereum (USDT, DAI, ETH, …); locked in the bridge contract. */
    SIDECHAIN("Sidechain"),

    /** Native to Ethereum but minted by its own master contract (VAL). */
    SIDECHAIN_OWNED("SidechainOwned");

    /** The pallet's `is_owned`: no precision conversion and no dust rule. */
    val isOwned: Boolean get() = this != SIDECHAIN

    companion object {
        fun fromWire(name: String): HashiAssetKind =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
                ?: throw IllegalArgumentException("HASHI_ASSET_KIND_UNKNOWN")
    }
}

/** One row of `ethBridge_getRegisteredAssets`. */
class HashiRegisteredAsset(
    val kind: HashiAssetKind,
    /** `0x` + 64 lower-case hex digits. */
    val assetId: String,
    val soraPrecision: Int,
    /** The token the pallet knows; null for `Thischain` rows (read from the bridge contract). */
    val ethToken: ByteArray?,
    val ethDecimals: Int?,
)

/**
 * An asset that can cross the bridge: the registration plus its Ethereum token and decimals as
 * the Ethereum side reports them.
 */
class HashiBridgeAsset(
    val kind: HashiAssetKind,
    val assetId: String,
    val soraPrecision: Int,
    ethToken: ByteArray,
    val ethDecimals: Int,
) {
    val ethToken: ByteArray = ethToken.copyOf()

    init {
        require(ethToken.size == EthAddress.SIZE) { "HASHI_ASSET_TOKEN_INVALID" }
        require(soraPrecision in 0..38 && ethDecimals in 0..77) { "HASHI_ASSET_PRECISION_INVALID" }
    }

    /** Ether itself (`Sidechain` with the zero token address): sent as value, no allowance. */
    val isNativeEther: Boolean get() = kind == HashiAssetKind.SIDECHAIN && ethToken.all { it == 0.toByte() }

    val ethTokenChecksum: String get() = EthAddress.checksum(ethToken)
}

/** The `currency_id` of an approved outgoing transfer. */
sealed class HashiCurrency {
    class AssetId(val id: ByteArray) : HashiCurrency()
    class TokenAddress(val address: ByteArray) : HashiCurrency()
}

/** One peer's approval: r, s and v exactly as the RPC returns them (0/1, or already 27/28). */
class HashiPeerSignature(val r: ByteArray, val s: ByteArray, val v: Int) {
    init {
        require(r.size == 32 && s.size == 32) { "HASHI_APPROVED_SIGNATURE_INVALID" }
        require(v == 0 || v == 1 || v == 27 || v == 28) { "HASHI_APPROVED_SIGNATURE_V_INVALID" }
    }

    /** What the contract's `ecrecover` takes: a recovery id plus 27; 27 and 28 stay as they are. */
    val contractV: Int get() = if (v < 27) v + 27 else v
}

/** `ethBridge_getApprovedRequests` for one outgoing transfer. */
class HashiApprovedRequest(
    val currency: HashiCurrency,
    /** Already in Ethereum units with precision and denomination applied by the chain. */
    val amount: BigInteger,
    val to: ByteArray,
    val from: ByteArray,
    val txHash: ByteArray,
    val signatures: List<HashiPeerSignature>,
)

/** `ethBridge.requestStatuses` / the status column of `ethBridge_getRequests`. */
enum class HashiRequestStatus(val wireName: String) {
    PENDING("Pending"),
    FROZEN("Frozen"),
    APPROVALS_READY("ApprovalsReady"),
    FAILED("Failed"),
    DONE("Done"),
    BROKEN("Broken");

    /**
     * Only `Failed` is final. `Frozen` can return to `ApprovalsReady` and `Broken` waits for the
     * operators: both are watched further, and neither is proof of a refund.
     */
    val isFinal: Boolean get() = this == FAILED || this == DONE

    companion object {
        /** Decodes the SCALE value of `requestStatuses`: its first byte is the variant. */
        fun fromScale(hex: String): HashiRequestStatus {
            val bytes = EthHex.decode(hex)
            require(bytes.isNotEmpty()) { "HASHI_REQUEST_STATUS_INVALID" }
            val index = bytes[0].toInt() and 0xff
            val status = entries.getOrNull(index) ?: throw IllegalArgumentException("HASHI_REQUEST_STATUS_INVALID")
            // Only Failed and Broken carry a dispatch error after the variant byte.
            require(bytes.size == 1 || status == FAILED || status == BROKEN) { "HASHI_REQUEST_STATUS_INVALID" }
            return status
        }

        fun fromWire(name: String): HashiRequestStatus =
            entries.firstOrNull { it.wireName.equals(name, ignoreCase = true) }
                ?: throw IllegalArgumentException("HASHI_REQUEST_STATUS_UNKNOWN")
    }
}

/** One row of `ethBridge_getRequests`. */
class HashiRequestRow(
    /** `Outgoing`, `Incoming`, `LoadIncoming`, … */
    val direction: String,
    /** The request's hash on SORA when the row carries it. */
    val requestHash: String?,
    val status: HashiRequestStatus,
)

/**
 * Strict readers of the bridge RPC answers. Anything that is not exactly the documented shape is
 * refused with an [IllegalArgumentException]; callers treat that as "cannot continue", never as
 * an empty or default value.
 */
object HashiRpcParsers {

    private fun JsonElement.ok(): JsonElement {
        require(this is JsonObject) { "HASHI_RPC_RESULT_INVALID" }
        require(!containsKey("Err") && !containsKey("err")) { "HASHI_RPC_REJECTED" }
        return get("Ok") ?: get("ok") ?: throw IllegalArgumentException("HASHI_RPC_RESULT_INVALID")
    }

    private fun JsonElement.array(): JsonArray = this as? JsonArray ?: throw IllegalArgumentException("HASHI_RPC_SHAPE_INVALID")

    private fun JsonElement.obj(): JsonObject = this as? JsonObject ?: throw IllegalArgumentException("HASHI_RPC_SHAPE_INVALID")

    private fun JsonElement.string(): String {
        val primitive = this as? JsonPrimitive ?: throw IllegalArgumentException("HASHI_RPC_SHAPE_INVALID")
        require(primitive.isString) { "HASHI_RPC_SHAPE_INVALID" }
        return primitive.content
    }

    private fun JsonElement.smallInt(): Int {
        val primitive = this as? JsonPrimitive ?: throw IllegalArgumentException("HASHI_RPC_SHAPE_INVALID")
        require(!primitive.isString) { "HASHI_RPC_SHAPE_INVALID" }
        return primitive.intOrNull ?: throw IllegalArgumentException("HASHI_RPC_SHAPE_INVALID")
    }

    private fun JsonObject.field(name: String): JsonElement =
        get(name) ?: get(camel(name)) ?: throw IllegalArgumentException("HASHI_RPC_FIELD_MISSING")

    private fun camel(snake: String): String =
        snake.split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercaseChar) }
            .joinToString("")

    /** A single-key object `{Name: body}` or a bare string `"Name"`, as `(Name, body?)`. */
    private fun JsonElement.variant(): Pair<String, JsonElement?> = when (this) {
        is JsonPrimitive -> {
            require(isString && content.isNotEmpty()) { "HASHI_RPC_VARIANT_INVALID" }
            content.replaceFirstChar(Char::uppercaseChar) to null
        }
        is JsonObject -> {
            require(size == 1) { "HASHI_RPC_VARIANT_INVALID" }
            val (name, body) = entries.single()
            name.replaceFirstChar(Char::uppercaseChar) to body
        }
        else -> throw IllegalArgumentException("HASHI_RPC_VARIANT_INVALID")
    }

    /** `0x` + exactly [size] bytes, or a JSON array of exactly [size] bytes (0..255). */
    private fun JsonElement.fixedBytes(size: Int): ByteArray = when (this) {
        is JsonPrimitive -> EthHex.decodeFixed(string(), size)
        is JsonArray -> {
            require(this.size == size) { "HASHI_RPC_BYTES_INVALID" }
            ByteArray(size) { index ->
                val value = this[index].smallInt()
                require(value in 0..255) { "HASHI_RPC_BYTES_INVALID" }
                value.toByte()
            }
        }
        else -> throw IllegalArgumentException("HASHI_RPC_BYTES_INVALID")
    }

    /** A U256 as `0x` hex or decimal digits. */
    private fun JsonElement.u256(): BigInteger {
        val text = string()
        val value = if (text.startsWith("0x")) {
            EthHex.parseQuantity(text)
        } else {
            require(text.isNotEmpty() && text.length <= 78 && text.all(Char::isDigit)) { "HASHI_RPC_AMOUNT_INVALID" }
            BigInteger(text)
        }
        require(value.bitLength() <= 256) { "HASHI_RPC_AMOUNT_INVALID" }
        return value
    }

    private val ASSET_ID = Regex("^0x[0-9a-f]{64}$")

    fun registeredAssets(result: JsonElement): List<HashiRegisteredAsset> =
        result.ok().array().map { row ->
            val cells = row.array()
            require(cells.size == 3) { "HASHI_RPC_SHAPE_INVALID" }
            val kind = HashiAssetKind.fromWire(cells[0].string())
            val sora = cells[1].array()
            require(sora.size == 2) { "HASHI_RPC_SHAPE_INVALID" }
            val assetId = sora[0].string().lowercase()
            require(ASSET_ID.matches(assetId)) { "HASHI_RPC_ASSET_ID_INVALID" }
            val precision = sora[1].smallInt()
            val external = cells[2]
            val (token, decimals) = if (external is JsonNull) {
                null to null
            } else {
                val pair = external.array()
                require(pair.size == 2) { "HASHI_RPC_SHAPE_INVALID" }
                pair[0].fixedBytes(EthAddress.SIZE) to pair[1].smallInt()
            }
            // Only Thischain assets have no token in the registration.
            require((kind == HashiAssetKind.THISCHAIN) == (token == null)) { "HASHI_RPC_ASSET_ROW_INVALID" }
            HashiRegisteredAsset(kind, assetId, precision, token, decimals)
        }

    /**
     * The approved request for [requestHash] (outgoing transfers only). Null when the chain has
     * no approved request for it yet (an empty list). The request must be a `Transfer` on network
     * [HashiBridgeContract.NETWORK_ID] whose `tx_hash` is [requestHash]; v must be 0, 1, 27 or 28.
     */
    fun approvedRequest(result: JsonElement, requestHash: ByteArray): HashiApprovedRequest? {
        val rows = result.ok().array()
        if (rows.isEmpty()) return null
        require(rows.size == 1) { "HASHI_APPROVED_REQUEST_COUNT_INVALID" }
        val row = rows[0].array()
        require(row.size == 2) { "HASHI_RPC_SHAPE_INVALID" }
        val (kind, body) = row[0].variant()
        require(kind == "Transfer" && body != null) { "HASHI_APPROVED_REQUEST_NOT_TRANSFER" }
        val transfer = body.obj()
        val (currencyKind, currencyBody) = transfer.field("currency_id").variant()
        val currency = when (currencyKind) {
            "AssetId" -> HashiCurrency.AssetId(requireNotNull(currencyBody).fixedBytes(32))
            "TokenAddress" -> HashiCurrency.TokenAddress(requireNotNull(currencyBody).fixedBytes(EthAddress.SIZE))
            else -> throw IllegalArgumentException("HASHI_APPROVED_CURRENCY_INVALID")
        }
        val txHash = transfer.field("tx_hash").fixedBytes(32)
        require(txHash.contentEquals(requestHash)) { "HASHI_APPROVED_REQUEST_HASH_MISMATCH" }
        val network = transfer.field("network_id").fixedBytes(32)
        require(BigInteger(1, network) == BigInteger.valueOf(HashiBridgeContract.NETWORK_ID.toLong())) {
            "HASHI_APPROVED_REQUEST_NETWORK_MISMATCH"
        }
        val amount = transfer.field("amount").u256()
        require(amount.signum() > 0) { "HASHI_APPROVED_AMOUNT_INVALID" }
        val signatures = row[1].array().map { entry ->
            val signature = entry.obj()
            HashiPeerSignature(
                r = signature.field("r").fixedBytes(32),
                s = signature.field("s").fixedBytes(32),
                v = signature.field("v").smallInt(),
            )
        }
        require(signatures.isNotEmpty()) { "HASHI_APPROVED_SIGNATURES_MISSING" }
        return HashiApprovedRequest(
            currency = currency,
            amount = amount,
            to = transfer.field("to").fixedBytes(EthAddress.SIZE),
            from = transfer.field("from").fixedBytes(EthAddress.SIZE),
            txHash = txHash,
            signatures = signatures,
        )
    }

    /** Rows of `ethBridge_getRequests([[hash], 0, true])`; empty while the chain knows nothing. */
    fun requests(result: JsonElement): List<HashiRequestRow> =
        result.ok().array().map { entry ->
            val row = entry.array()
            require(row.size == 2) { "HASHI_RPC_SHAPE_INVALID" }
            val (direction, body) = row[0].variant()
            val hash = (body as? JsonArray)?.takeIf { it.size == 2 }?.get(1)
                ?.let { runCatching { EthHex.encode(it.fixedBytes(32)) }.getOrNull() }
            val (statusName, _) = row[1].variant()
            HashiRequestRow(direction, hash, HashiRequestStatus.fromWire(statusName))
        }

    /** `payment_queryInfo(...).partialFee` (a decimal string or a number). */
    fun partialFee(result: JsonElement): BigInteger {
        val fee = result.obj()["partialFee"] ?: throw IllegalArgumentException("HASHI_FEE_INVALID")
        val primitive = fee as? JsonPrimitive ?: throw IllegalArgumentException("HASHI_FEE_INVALID")
        val text = primitive.content
        val value = if (text.startsWith("0x")) EthHex.parseQuantity(text) else {
            require(text.isNotEmpty() && text.all(Char::isDigit)) { "HASHI_FEE_INVALID" }
            BigInteger(text)
        }
        require(value.signum() > 0) { "HASHI_FEE_INVALID" }
        return value
    }
}
