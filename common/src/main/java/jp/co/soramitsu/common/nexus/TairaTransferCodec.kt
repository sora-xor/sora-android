package jp.co.soramitsu.common.nexus

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.crypto.digests.Blake2bDigest
import org.bouncycastle.crypto.digests.Blake3Digest
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** Narrow fixed-V1 Norito writer; parity with upstream nexus_connect_transfer_v1 is tested. */
object TairaTransferCodec {
    data class FeeLimit(val kind: Int, val assetDefinition: String, val maximum: String)

    fun payload(network: ByteArray, authorityKey: ByteArray, recipientKey: ByteArray,
        assetDefinition: String, amount: String, creationTimeMs: Long, ttlMs: Long = 100_000,
        nonce: Long? = null, fees: List<FeeLimit> = emptyList(), metadata: Map<String, String> = emptyMap(),
    ): ByteArray {
        require(network.size == 32 && network.last().toInt() and 1 == 1)
        require(creationTimeMs > 0 && ttlMs in 1..100_000 && creationTimeMs <= Long.MAX_VALUE - ttlMs)
        require(nonce == null || nonce in 1..0xffffffffL)
        val instruction = instructionBody(authorityKey, recipientKey, assetDefinition, amount)
        val executable = u32(0) + field(u64(1) + field(instruction))
        val limits = fees.mapIndexed { index, fee ->
            require(fee.kind in 0..1 && (index == 0 || fees[index - 1].kind < fee.kind))
            field(field(u32(fee.kind)) + field(asset(fee.assetDefinition)) + field(quantity(fee.maximum, true)))
        }.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
        val feePayment = u32(0) + field(field(u64(fees.size.toLong()) + limits) + field(byteArrayOf(0)))
        var meta = u64(metadata.size.toLong())
        metadata.toSortedMap().forEach { (name, value) ->
            require(name.isNotEmpty() && name.length <= 255 && name.all { it.code in 33..126 && it !in "#$@" })
            meta += field(field(field(name.toByteArray())) + field(field(field(JsonPrimitive(value).toString().toByteArray()))))
        }
        return field(u32(0) + field(network)) + field(account(authorityKey)) + field(u64(creationTimeMs)) + field(executable) +
            field(byteArrayOf(1) + field(u64(ttlMs))) + field(nonce?.let { byteArrayOf(1) + field(u32(it.toInt())) } ?: byteArrayOf(0)) +
            field(feePayment) + field(meta) + field(byteArrayOf(0))
    }

    fun signingMessage(payload: ByteArray): ByteArray = hash(payload)
    fun transactionHash(payload: ByteArray): String = hex(hash(u32(0) + field(payload)))

    fun sign(seed: ByteArray, message: ByteArray): ByteArray {
        require(seed.size == 32)
        return Ed25519Signer().run {
            init(true, Ed25519PrivateKeyParameters(seed, 0)); update(message, 0, message.size); generateSignature()
        }
    }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        return Ed25519Signer().run {
            init(false, Ed25519PublicKeyParameters(publicKey, 0)); update(message, 0, message.size); verifySignature(signature)
        }
    }

    fun envelope(payload: ByteArray, signature: ByteArray, publicKey: ByteArray): ByteArray {
        require(payload.isNotEmpty() && verify(publicKey, signingMessage(payload), signature)) { "TAIRA_SIGNATURE_INVALID" }
        return byteArrayOf(1) + field(field(u64(signature.size.toLong()) + byteFields(signature))) + field(payload) + field(byteArrayOf(0))
    }

    fun instructionJSON(authorityKey: ByteArray, recipientKey: ByteArray, assetDefinition: String, amount: String): String =
        Base64.getEncoder().encodeToString(frame(instructionBody(authorityKey, recipientKey, assetDefinition, amount),
            unhex("862a7d77075d4d23ff6c1261db027811")))

    fun canonicalQuantity(raw: String, allowZero: Boolean = false): String {
        require(raw.length <= 164 && Regex("^(0|[1-9][0-9]*)(\\.[0-9]{1,9})?$").matches(raw)) { "TAIRA_QUANTITY_INVALID" }
        val canonical = raw.toBigDecimal().stripTrailingZeros().toPlainString()
        require(allowZero || canonical != "0") { "TAIRA_QUANTITY_NOT_POSITIVE" }
        return canonical
    }

    fun unhex(value: String): ByteArray {
        require(value.length % 2 == 0 && value.all { it in "0123456789abcdefABCDEF" })
        return value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    fun hex(value: ByteArray): String = value.joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun instructionBody(authority: ByteArray, recipient: ByteArray, definition: String, amount: String): ByteArray {
        val source = field(account(authority)) + field(asset(definition)) + field(u32(0))
        val transfer = field(source) + field(quantity(amount, false)) + field(account(recipient))
        val framed = frame(u32(2) + field(transfer), unhex("a4174c78d6341f8f98fc2adae8ed67b9"))
        return field(field("iroha.transfer".toByteArray())) + field(u64(framed.size.toLong()) + framed)
    }
    private fun account(key: ByteArray): ByteArray {
        require(key.size == 32 && key.any { it != 0.toByte() })
        return u32(0) + field(u64(33) + byteFields(byteArrayOf(0) + key))
    }
    private fun asset(literal: String): ByteArray {
        require(literal.length in 27..29)
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var value = BigInteger.ZERO
        literal.forEach { c -> val digit = alphabet.indexOf(c); require(digit >= 0); value = value * BigInteger.valueOf(58) + BigInteger.valueOf(digit.toLong()) }
        val payload = value.toByteArray().let { if (it.size == 22 && it[0] == 0.toByte()) it.drop(1).toByteArray() else it }
        require(payload.size == 21 && payload[0] == 1.toByte())
        val digest = ByteArray(32)
        Blake3Digest().run { update(payload, 0, 17); doFinal(digest, 0) }
        require(payload.copyOfRange(17, 21).contentEquals(digest.copyOf(4))) { "TAIRA_ASSET_CHECKSUM_INVALID" }
        val uuid = payload.copyOfRange(1, 17)
        require((uuid[6].toInt() and 0xf0) == 0x40 && (uuid[8].toInt() and 0xc0) == 0x80)
        return byteFields(uuid)
    }
    private fun quantity(raw: String, allowZero: Boolean): ByteArray {
        val value = canonicalQuantity(raw, allowZero).toBigDecimal()
        val scale = value.scale().coerceAtLeast(0)
        val magnitude = value.setScale(scale).unscaledValue().toByteArray().reversedArray()
        require(magnitude.size <= 64)
        return field(u32(magnitude.size) + magnitude) + field(u32(scale))
    }
    private fun hash(bytes: ByteArray): ByteArray = ByteArray(32).also { out ->
        Blake2bDigest(256).run { update(bytes, 0, bytes.size); doFinal(out, 0) }
        out[31] = (out[31].toInt() or 1).toByte()
    }
    private fun byteFields(bytes: ByteArray): ByteArray = bytes.flatMap { listOf(1.toByte(), it) }.toByteArray()
    private fun field(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(); var length = bytes.size
        while (length >= 128) { out.write((length and 127) or 128); length = length ushr 7 }
        out.write(length); out.write(bytes); return out.toByteArray()
    }
    private fun u32(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun u64(value: Long): ByteArray = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(value).array()
    private fun frame(body: ByteArray, schema: ByteArray): ByteArray = "NRT0".toByteArray() + byteArrayOf(0, 0) + schema +
        byteArrayOf(0) + u64(body.size.toLong()) + u64(crc64(body)) + byteArrayOf(2) + body
    private fun crc64(bytes: ByteArray): Long {
        var crc = -1L
        bytes.forEach { b -> crc = crc xor (b.toLong() and 255); repeat(8) { crc = if (crc and 1L == 1L) (crc ushr 1) xor 0xc96c5795d7870f42uL.toLong() else crc ushr 1 } }
        return crc.inv()
    }
}
