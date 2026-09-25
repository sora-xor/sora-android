package jp.co.soramitsu.common.irohaconnect

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CodingErrorAction

enum class IrohaConnectDirection(val tag: Long) {
    APP_TO_WALLET(0),
    WALLET_TO_APP(1);

    companion object {
        fun fromTag(tag: Long): IrohaConnectDirection = when (tag) {
            0L -> APP_TO_WALLET
            1L -> WALLET_TO_APP
            else -> throw IrohaConnectWireException("Unsupported IrohaConnect direction")
        }
    }
}

data class IrohaConnectAppMetadata(
    val name: String,
    val url: String?,
    val iconHash: String?,
)

data class IrohaConnectPermissions(
    val methods: List<String>,
    val events: List<String>,
    val resources: List<String>?,
)

sealed interface IrohaConnectConstraint {
    data class NetworkId(val bytes: ByteArray) : IrohaConnectConstraint
}

sealed interface IrohaConnectControl {
    data class Open(
        val appPublicKey: ByteArray,
        val appMetadata: IrohaConnectAppMetadata?,
        val constraint: IrohaConnectConstraint.NetworkId,
        val permissions: IrohaConnectPermissions?,
    ) : IrohaConnectControl

    data class Approve(
        val walletPublicKey: ByteArray,
        val accountId: String,
        val permissions: IrohaConnectPermissions?,
        val proof: ByteArray?,
        val signature: IrohaConnectSignature,
    ) : IrohaConnectControl

    data class Reject(val code: Int, val codeId: String, val reason: String) : IrohaConnectControl
    data class Close(val who: String, val code: Int, val reason: String, val retryable: Boolean) : IrohaConnectControl
    data class Ping(val nonce: Long) : IrohaConnectControl
    data class Pong(val nonce: Long) : IrohaConnectControl
    data class ServerEvent(val raw: ByteArray) : IrohaConnectControl
}

sealed interface IrohaConnectFrame {
    val sid: ByteArray
    val direction: IrohaConnectDirection
    val sequence: Long

    data class Control(
        override val sid: ByteArray,
        override val direction: IrohaConnectDirection,
        override val sequence: Long,
        val control: IrohaConnectControl,
    ) : IrohaConnectFrame

    data class Ciphertext(
        override val sid: ByteArray,
        override val direction: IrohaConnectDirection,
        override val sequence: Long,
        val ciphertextDirection: IrohaConnectDirection,
        val aead: ByteArray,
    ) : IrohaConnectFrame
}

class IrohaConnectWireException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object IrohaConnectWire {
    const val MAX_FRAME_BYTES = 1_048_576
    const val MAX_SIGNING_BYTES = 524_288
    private const val MAX_TEXT_BYTES = 16_384

    fun decodeFrame(bytes: ByteArray): IrohaConnectFrame {
        requireBounded(bytes, "IrohaConnect frame")
        val frame = Reader(bytes)
        val sid = decodeFixed32(frame.field("frame.sid"), "frame.sid")
        val direction = decodeDirection(frame.field("frame.direction"))
        val sequence = Reader(frame.field("frame.sequence")).u64("frame.sequence").also {
            require(it > 0) { "IrohaConnect frame sequence must be positive" }
        }
        val kindReader = Reader(frame.field("frame.kind"))
        frame.done("frame")
        val kind = kindReader.u32("frame.kind.tag")
        val body = kindReader.bytes(kindReader.u64Length("frame.kind.length"), "frame.kind.body")
        kindReader.done("frame.kind")
        return when (kind) {
            0L -> IrohaConnectFrame.Control(
                sid = sid,
                direction = direction,
                sequence = sequence,
                control = decodeControl(body),
            )
            1L -> {
                val ciphertext = Reader(body)
                val ciphertextDirection = decodeDirection(ciphertext.field("ciphertext.direction"))
                val aead = decodeByteVector(ciphertext.field("ciphertext.aead"), "ciphertext.aead")
                ciphertext.done("ciphertext")
                IrohaConnectFrame.Ciphertext(
                    sid = sid,
                    direction = direction,
                    sequence = sequence,
                    ciphertextDirection = ciphertextDirection,
                    aead = aead,
                )
            }
            else -> throw IrohaConnectWireException("Unsupported IrohaConnect frame kind")
        }
    }

    fun encodeOpen(
        sid: ByteArray,
        appPublicKey: ByteArray,
        networkIdBytes: ByteArray,
        appMetadata: IrohaConnectAppMetadata?,
        permissions: IrohaConnectPermissions?,
    ): ByteArray {
        require(sid.size == 32 && appPublicKey.size == 32 && networkIdBytes.size == 32)
        val body = struct(
            appPublicKey,
            option(appMetadata) { metadata ->
                struct(
                    string(metadata.name),
                    option(metadata.url, ::string),
                    option(metadata.iconHash, ::string),
                )
            },
            encodeConstraint(networkIdBytes),
            option(permissions, ::encodePermissions),
        )
        return encodeControlFrame(
            sid,
            IrohaConnectDirection.APP_TO_WALLET,
            1,
            0,
            body,
        )
    }

    fun encodeApproval(
        sid: ByteArray,
        sequence: Long,
        walletPublicKey: ByteArray,
        accountId: String,
        permissions: IrohaConnectPermissions?,
        signature: ByteArray,
    ): ByteArray {
        require(sid.size == 32 && walletPublicKey.size == 32 && signature.size == 64)
        val signaturePayload = struct(
            byteArrayOf(0),
            byteVector(signature),
        )
        val body = struct(
            walletPublicKey,
            string(accountId),
            option(permissions, ::encodePermissions),
            option<ByteArray>(null) { it },
            signaturePayload,
        )
        return encodeControlFrame(
            sid,
            IrohaConnectDirection.WALLET_TO_APP,
            sequence,
            1,
            body,
        )
    }

    fun encodeReject(
        sid: ByteArray,
        sequence: Long,
        code: Int,
        codeId: String,
        reason: String,
    ): ByteArray = encodeControlFrame(
        sid,
        IrohaConnectDirection.WALLET_TO_APP,
        sequence,
        2,
        struct(u16(code), string(codeId), string(reason)),
    )

    fun encodeClose(
        sid: ByteArray,
        sequence: Long,
        code: Int,
        reason: String,
        retryable: Boolean = false,
    ): ByteArray = encodeControlFrame(
        sid,
        IrohaConnectDirection.WALLET_TO_APP,
        sequence,
        3,
        struct(u32(1), u16(code), string(reason), byteArrayOf(if (retryable) 1 else 0)),
    )

    fun encodePong(sid: ByteArray, sequence: Long, nonce: Long): ByteArray = encodeControlFrame(
        sid,
        IrohaConnectDirection.WALLET_TO_APP,
        sequence,
        5,
        struct(u64(nonce)),
    )

    fun encodeCiphertext(
        sid: ByteArray,
        sequence: Long,
        aead: ByteArray,
        direction: IrohaConnectDirection = IrohaConnectDirection.WALLET_TO_APP,
    ): ByteArray {
        require(sid.size == 32 && sequence > 0 && aead.size <= MAX_FRAME_BYTES)
        val body = struct(u32(direction.tag), byteVector(aead))
        val kind = tagged(1, body)
        return struct(sid, u32(direction.tag), u64(sequence), kind)
    }

    fun encodePermissions(permissions: IrohaConnectPermissions): ByteArray = struct(
        stringVector(permissions.methods),
        stringVector(permissions.events),
        option(permissions.resources, ::stringVector),
    )

    fun encodeConstraint(networkIdBytes: ByteArray): ByteArray {
        require(networkIdBytes.size == 32)
        return struct(networkIdBytes)
    }

    fun associatedData(
        sid: ByteArray,
        direction: IrohaConnectDirection,
        sequence: Long,
    ): ByteArray =
        "connect:v1".toByteArray() + sid + byteArrayOf(direction.tag.toByte()) + u64(sequence) + byteArrayOf(1)

    internal fun struct(vararg fields: ByteArray): ByteArray = join(fields.map(::field))

    internal fun field(payload: ByteArray): ByteArray = u64(payload.size.toLong()) + payload

    internal fun string(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES)
        return u64(bytes.size.toLong()) + bytes
    }

    internal fun byteVector(value: ByteArray): ByteArray = u64(value.size.toLong()) + value

    internal fun tagged(tag: Long, body: ByteArray): ByteArray = u32(tag) + u64(body.size.toLong()) + body

    internal fun u16(value: Int): ByteArray = IrohaConnectEncoding.littleEndianU16(value)
    internal fun u32(value: Long): ByteArray = IrohaConnectEncoding.littleEndianU32(value)
    internal fun u64(value: Long): ByteArray = IrohaConnectEncoding.littleEndianU64(value)

    private fun decodeControl(bytes: ByteArray): IrohaConnectControl {
        val control = Reader(bytes)
        val tag = control.u32("control.tag")
        val body = control.bytes(control.u64Length("control.length"), "control.body")
        control.done("control")
        return when (tag) {
            0L -> decodeOpen(body)
            1L -> decodeApprove(body)
            2L -> {
                val reader = Reader(body)
                val code = Reader(reader.field("reject.code")).u16("reject.code")
                val codeId = decodeString(reader.field("reject.code_id"), "reject.code_id")
                val reason = decodeString(reader.field("reject.reason"), "reject.reason")
                reader.done("reject")
                IrohaConnectControl.Reject(code, codeId, reason)
            }
            3L -> {
                val reader = Reader(body)
                val role = when (Reader(reader.field("close.who")).u32("close.who")) {
                    0L -> "app"
                    1L -> "wallet"
                    else -> throw IrohaConnectWireException("Invalid IrohaConnect close role")
                }
                val code = Reader(reader.field("close.code")).u16("close.code")
                val reason = decodeString(reader.field("close.reason"), "close.reason")
                val retryableBytes = reader.field("close.retryable")
                if (retryableBytes.size != 1 || retryableBytes[0].toInt() !in 0..1) {
                    throw IrohaConnectWireException("Invalid IrohaConnect close retry flag")
                }
                reader.done("close")
                IrohaConnectControl.Close(role, code, reason, retryableBytes[0].toInt() == 1)
            }
            4L, 5L -> {
                val reader = Reader(body)
                val nonce = Reader(reader.field("ping.nonce")).u64("ping.nonce")
                reader.done("ping")
                if (tag == 4L) IrohaConnectControl.Ping(nonce) else IrohaConnectControl.Pong(nonce)
            }
            6L -> IrohaConnectControl.ServerEvent(body)
            else -> throw IrohaConnectWireException("Unsupported IrohaConnect control tag")
        }
    }

    private fun decodeOpen(bytes: ByteArray): IrohaConnectControl.Open {
        val reader = Reader(bytes)
        val appPublicKey = decodeFixed32(reader.field("open.app_pk"), "open.app_pk")
        val metadata = decodeOption(reader.field("open.app_meta"), "open.app_meta") {
            val meta = Reader(it)
            val name = decodeString(meta.field("app_meta.name"), "app_meta.name")
            val url = decodeOption(meta.field("app_meta.url"), "app_meta.url") { value ->
                decodeString(value, "app_meta.url.value")
            }
            val iconHash =
                decodeOption(meta.field("app_meta.icon_hash"), "app_meta.icon_hash") { value ->
                    decodeString(value, "app_meta.icon_hash.value")
                }
            meta.done("app_meta")
            IrohaConnectAppMetadata(name, url, iconHash)
        }
        val constraint = decodeConstraint(reader.field("open.constraints"))
        val permissions =
            decodeOption(reader.field("open.permissions"), "open.permissions", ::decodePermissions)
        reader.done("open")
        return IrohaConnectControl.Open(appPublicKey, metadata, constraint, permissions)
    }

    private fun decodeApprove(bytes: ByteArray): IrohaConnectControl.Approve {
        val reader = Reader(bytes)
        val walletPublicKey = decodeFixed32(reader.field("approve.wallet_pk"), "approve.wallet_pk")
        val accountId = decodeString(reader.field("approve.account_id"), "approve.account_id")
        val permissions = decodeOption(
            reader.field("approve.permissions"),
            "approve.permissions",
            ::decodePermissions,
        )
        val proof = decodeOption(reader.field("approve.proof"), "approve.proof") { it }
        val signatureReader = Reader(reader.field("approve.sig_wallet"))
        val algorithm = signatureReader.field("approve.sig_wallet.algorithm")
        if (algorithm.size != 1 || algorithm[0].toInt() != 0) {
            throw IrohaConnectWireException("IrohaConnect approval signature must use Ed25519")
        }
        val signature = decodeByteVector(
            signatureReader.field("approve.sig_wallet.signature"),
            "approve.sig_wallet.signature",
        )
        signatureReader.done("approve.sig_wallet")
        if (signature.size != 64) {
            throw IrohaConnectWireException("IrohaConnect approval signature length is invalid")
        }
        reader.done("approve")
        return IrohaConnectControl.Approve(
            walletPublicKey,
            accountId,
            permissions,
            proof,
            IrohaConnectSignature(signature = signature),
        )
    }

    private fun decodeConstraint(bytes: ByteArray): IrohaConnectConstraint.NetworkId {
        val reader = Reader(bytes)
        val value = reader.field("constraints.network")
        reader.done("constraints")
        if (value.size != 32) throw IrohaConnectWireException("Open constraint must contain a NetworkId")
        return IrohaConnectConstraint.NetworkId(value)
    }

    private fun decodePermissions(bytes: ByteArray): IrohaConnectPermissions {
        val reader = Reader(bytes)
        val methods = decodeStringVector(reader.field("permissions.methods"), "permissions.methods")
        val events = decodeStringVector(reader.field("permissions.events"), "permissions.events")
        val resources =
            decodeOption(reader.field("permissions.resources"), "permissions.resources") {
                decodeStringVector(it, "permissions.resources.value")
            }
        reader.done("permissions")
        return IrohaConnectPermissions(methods, events, resources)
    }

    private fun decodeStringVector(bytes: ByteArray, label: String): List<String> {
        val reader = Reader(bytes)
        val count = reader.u64Length("$label.count")
        require(count <= 128) { "$label has too many entries" }
        val result =
            List(count) { index -> decodeString(reader.field("$label[$index]"), "$label[$index]") }
        reader.done(label)
        return result
    }

    private fun stringVector(values: List<String>): ByteArray {
        require(values.size <= 128)
        return u64(values.size.toLong()) + join(values.map { field(string(it)) })
    }

    private fun <T> option(value: T?, encoder: (T) -> ByteArray): ByteArray =
        if (value == null) byteArrayOf(0) else byteArrayOf(1) + field(encoder(value))

    private fun <T> decodeOption(bytes: ByteArray, label: String, decoder: (ByteArray) -> T): T? {
        val reader = Reader(bytes)
        val tag = reader.bytes(1, "$label.tag")[0].toInt() and 0xff
        val result = when (tag) {
            0 -> null
            1 -> decoder(reader.field("$label.value"))
            else -> throw IrohaConnectWireException("Invalid $label option tag")
        }
        reader.done(label)
        return result
    }

    private fun decodeDirection(bytes: ByteArray): IrohaConnectDirection {
        val reader = Reader(bytes)
        val direction = IrohaConnectDirection.fromTag(reader.u32("direction"))
        reader.done("direction")
        return direction
    }

    private fun decodeFixed32(bytes: ByteArray, label: String): ByteArray {
        if (bytes.size != 32) throw IrohaConnectWireException("$label must contain 32 raw bytes")
        return bytes
    }

    private fun decodeByteVector(bytes: ByteArray, label: String): ByteArray {
        val reader = Reader(bytes)
        val count = reader.u64Length("$label.count")
        if (count > MAX_FRAME_BYTES) throw IrohaConnectWireException("$label is too large")
        val result = reader.bytes(count, label)
        reader.done(label)
        return result
    }

    private fun decodeString(bytes: ByteArray, label: String): String {
        val reader = Reader(bytes)
        val length = reader.u64Length("$label.length")
        if (length > MAX_TEXT_BYTES) throw IrohaConnectWireException("$label is too long")
        val raw = reader.bytes(length, label)
        reader.done(label)
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(raw))
                .toString()
        } catch (error: Exception) {
            throw IrohaConnectWireException("$label is not UTF-8", error)
        }
    }

    private fun encodeControlFrame(
        sid: ByteArray,
        direction: IrohaConnectDirection,
        sequence: Long,
        controlTag: Long,
        controlBody: ByteArray,
    ): ByteArray {
        require(sid.size == 32 && sequence > 0)
        val control = tagged(controlTag, controlBody)
        val kind = tagged(0, control)
        return struct(sid, u32(direction.tag), u64(sequence), kind)
    }

    private fun requireBounded(bytes: ByteArray, label: String) {
        if (bytes.isEmpty() || bytes.size > MAX_FRAME_BYTES) throw IrohaConnectWireException("$label size is invalid")
    }

    private fun join(parts: List<ByteArray>): ByteArray {
        val stream = ByteArrayOutputStream(parts.sumOf(ByteArray::size))
        parts.forEach(stream::write)
        return stream.toByteArray()
    }

    internal class Reader(private val bytes: ByteArray) {
        private var offset = 0
        val remaining: Int get() = bytes.size - offset

        fun bytes(length: Int, label: String): ByteArray {
            if (length < 0 || length > remaining) throw IrohaConnectWireException("$label is truncated")
            return bytes.copyOfRange(offset, offset + length).also { offset += length }
        }

        fun u16(label: String): Int {
            val raw = bytes(2, label)
            return ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
        }

        fun u32(label: String): Long {
            val raw = bytes(4, label)
            return ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffff_ffffL
        }

        fun u64(label: String): Long {
            val raw = bytes(8, label)
            val value = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).long
            if (value < 0) throw IrohaConnectWireException("$label exceeds the supported range")
            return value
        }

        fun u64Length(label: String): Int {
            val value = u64(label)
            if (value > Int.MAX_VALUE) throw IrohaConnectWireException("$label is too large")
            return value.toInt()
        }

        fun field(label: String): ByteArray = bytes(u64Length("$label.length"), label)

        fun done(label: String) {
            if (remaining != 0) throw IrohaConnectWireException("$label has trailing bytes")
        }
    }
}

data class IrohaConnectSignature(
    val algorithmCode: Int = 0,
    val signature: ByteArray,
)

sealed interface IrohaConnectEnvelopePayload {
    data class SignRaw(val domainTag: String, val message: ByteArray) : IrohaConnectEnvelopePayload
    data class SignTransaction(val transaction: ByteArray) : IrohaConnectEnvelopePayload
    data class SignResultOk(val signature: IrohaConnectSignature) : IrohaConnectEnvelopePayload
    data class SignResultError(val code: String, val message: String) : IrohaConnectEnvelopePayload
    data class Display(val title: String, val body: String) : IrohaConnectEnvelopePayload
    data class Close(val reason: String) : IrohaConnectEnvelopePayload
    data class Reject(val code: Int, val codeId: String, val reason: String) : IrohaConnectEnvelopePayload
}

data class IrohaConnectEnvelope(val sequence: Long, val payload: IrohaConnectEnvelopePayload)

object IrohaConnectEnvelopeWire {
    private val magic = "NRT0".toByteArray()
    private val schema = byteArrayOf(
        0x69, 0x47, 0xaf.toByte(), 0xe3.toByte(), 0xe8.toByte(), 0xa8.toByte(), 0x54, 0x46,
        0xd6.toByte(), 0xa3.toByte(), 0xee.toByte(), 0x7b, 0xb1.toByte(), 0xa3.toByte(), 0x7c, 0x0a,
    )
    private val crcPolynomial = 0xc96c5795d7870f42uL.toLong()

    fun decrypt(
        key: ByteArray,
        sid: ByteArray,
        direction: IrohaConnectDirection,
        sequence: Long,
        aead: ByteArray,
    ): IrohaConnectEnvelope {
        val plaintext = IrohaConnectCrypto.decrypt(
            key,
            sequence,
            IrohaConnectWire.associatedData(sid, direction, sequence),
            aead,
        )
        return try {
            decode(plaintext).also {
                if (it.sequence != sequence) throw IrohaConnectWireException("IrohaConnect envelope sequence mismatch")
            }
        } finally {
            plaintext.fill(0)
        }
    }

    fun encryptResult(
        key: ByteArray,
        sid: ByteArray,
        sequence: Long,
        payload: IrohaConnectEnvelopePayload,
    ): ByteArray {
        val envelope = encode(sequence, payload)
        return try {
            IrohaConnectCrypto.encrypt(
                key,
                sequence,
                IrohaConnectWire.associatedData(sid, IrohaConnectDirection.WALLET_TO_APP, sequence),
                envelope,
            )
        } finally {
            envelope.fill(0)
        }
    }

    fun decode(bytes: ByteArray): IrohaConnectEnvelope {
        if (bytes.size < 40 || !bytes.copyOfRange(0, 4).contentEquals(magic)) {
            throw IrohaConnectWireException("IrohaConnect envelope header is invalid")
        }
        if (bytes[4].toInt() != 0 || bytes[5].toInt() != 0 || !bytes.copyOfRange(6, 22).contentEquals(schema)) {
            throw IrohaConnectWireException("IrohaConnect envelope schema is unsupported")
        }
        if (bytes[22].toInt() != 0 || bytes[39].toInt() != 0) {
            throw IrohaConnectWireException("IrohaConnect envelope layout is unsupported")
        }
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val payloadLength = header.getLong(23)
        val expectedCrc = header.getLong(31)
        if (payloadLength < 0 || payloadLength != (bytes.size - 40).toLong()) {
            throw IrohaConnectWireException("IrohaConnect envelope length is invalid")
        }
        val payloadBytes = bytes.copyOfRange(40, bytes.size)
        if (crc64Xz(payloadBytes) != expectedCrc) {
            throw IrohaConnectWireException("IrohaConnect envelope checksum is invalid")
        }
        val reader = IrohaConnectWire.Reader(payloadBytes)
        val sequence = IrohaConnectWire.Reader(reader.field("envelope.sequence")).u64("envelope.sequence")
        val payload = decodePayload(reader.field("envelope.payload"))
        reader.done("envelope")
        return IrohaConnectEnvelope(sequence, payload)
    }

    fun encode(sequence: Long, payload: IrohaConnectEnvelopePayload): ByteArray {
        val barePayload = IrohaConnectWire.struct(
            IrohaConnectWire.u64(sequence),
            encodePayload(payload),
        )
        val output = ByteArray(40 + barePayload.size)
        magic.copyInto(output, 0)
        schema.copyInto(output, 6)
        val header = ByteBuffer.wrap(output).order(ByteOrder.LITTLE_ENDIAN)
        header.putLong(23, barePayload.size.toLong())
        header.putLong(31, crc64Xz(barePayload))
        barePayload.copyInto(output, 40)
        return output
    }

    private fun decodePayload(bytes: ByteArray): IrohaConnectEnvelopePayload {
        val reader = IrohaConnectWire.Reader(bytes)
        val tag = reader.u32("envelope.payload.tag")
        val payload = when (tag) {
            0L -> decodeEncryptedControl(reader.field("envelope.control"))
            1L -> {
                val domain = decodeString(reader.field("sign_raw.domain"), "sign_raw.domain")
                val message = decodeByteVector(reader.field("sign_raw.message"), "sign_raw.message")
                IrohaConnectEnvelopePayload.SignRaw(domain, message)
            }
            2L -> IrohaConnectEnvelopePayload.SignTransaction(
                decodeByteVector(reader.field("sign_tx.transaction"), "sign_tx.transaction"),
            )
            3L -> IrohaConnectEnvelopePayload.SignResultOk(
                decodeSignature(reader.field("sign_result.signature")),
            )
            4L -> IrohaConnectEnvelopePayload.SignResultError(
                decodeString(reader.field("sign_error.code"), "sign_error.code"),
                decodeString(reader.field("sign_error.message"), "sign_error.message"),
            )
            5L -> IrohaConnectEnvelopePayload.Display(
                decodeString(reader.field("display.title"), "display.title"),
                decodeString(reader.field("display.body"), "display.body"),
            )
            else -> throw IrohaConnectWireException("Unsupported IrohaConnect envelope payload tag")
        }
        reader.done("envelope.payload")
        return payload
    }

    private fun encodePayload(payload: IrohaConnectEnvelopePayload): ByteArray = when (payload) {
        is IrohaConnectEnvelopePayload.SignRaw ->
            IrohaConnectWire.u32(1) +
                IrohaConnectWire.field(IrohaConnectWire.string(payload.domainTag)) +
                IrohaConnectWire.field(IrohaConnectWire.byteVector(payload.message))
        is IrohaConnectEnvelopePayload.SignTransaction ->
            IrohaConnectWire.u32(2) +
                IrohaConnectWire.field(IrohaConnectWire.byteVector(payload.transaction))
        is IrohaConnectEnvelopePayload.SignResultOk ->
            IrohaConnectWire.u32(3) + IrohaConnectWire.field(encodeSignature(payload.signature))
        is IrohaConnectEnvelopePayload.SignResultError ->
            IrohaConnectWire.u32(4) +
                IrohaConnectWire.field(IrohaConnectWire.string(payload.code)) +
                IrohaConnectWire.field(IrohaConnectWire.string(payload.message))
        is IrohaConnectEnvelopePayload.Display ->
            IrohaConnectWire.u32(5) +
                IrohaConnectWire.field(IrohaConnectWire.string(payload.title)) +
                IrohaConnectWire.field(IrohaConnectWire.string(payload.body))
        is IrohaConnectEnvelopePayload.Close ->
            IrohaConnectWire.u32(0) + IrohaConnectWire.field(
                IrohaConnectWire.u32(0) +
                    IrohaConnectWire.field(IrohaConnectWire.u32(1)) +
                    IrohaConnectWire.field(IrohaConnectWire.u16(1000)) +
                    IrohaConnectWire.field(IrohaConnectWire.string(payload.reason)) +
                    IrohaConnectWire.field(byteArrayOf(0)),
            )
        is IrohaConnectEnvelopePayload.Reject ->
            IrohaConnectWire.u32(0) + IrohaConnectWire.field(
                IrohaConnectWire.u32(1) +
                    IrohaConnectWire.field(IrohaConnectWire.u16(payload.code)) +
                    IrohaConnectWire.field(IrohaConnectWire.string(payload.codeId)) +
                    IrohaConnectWire.field(IrohaConnectWire.string(payload.reason)),
            )
    }

    private fun encodeSignature(signature: IrohaConnectSignature): ByteArray {
        require(signature.algorithmCode == 0 && signature.signature.size == 64)
        return IrohaConnectWire.struct(byteArrayOf(0), IrohaConnectWire.byteVector(signature.signature))
    }

    private fun decodeSignature(bytes: ByteArray): IrohaConnectSignature {
        val reader = IrohaConnectWire.Reader(bytes)
        val algorithm = reader.field("signature.algorithm")
        if (algorithm.size != 1 || algorithm[0].toInt() != 0) {
            throw IrohaConnectWireException("IrohaConnect wallet signature must be Ed25519")
        }
        val signature = decodeByteVector(reader.field("signature.bytes"), "signature.bytes")
        reader.done("signature")
        if (signature.size != 64) throw IrohaConnectWireException("Invalid Ed25519 signature length")
        return IrohaConnectSignature(signature = signature)
    }

    private fun decodeEncryptedControl(bytes: ByteArray): IrohaConnectEnvelopePayload {
        val reader = IrohaConnectWire.Reader(bytes)
        val tag = reader.u32("encrypted_control.tag")
        val result = when (tag) {
            0L -> {
                val whoReader = IrohaConnectWire.Reader(reader.field("encrypted_control.who"))
                val who = whoReader.u32("encrypted_control.who").also {
                    whoReader.done("encrypted_control.who")
                }
                if (who !in 0L..1L) {
                    throw IrohaConnectWireException("Encrypted IrohaConnect close role is invalid")
                }
                val codeReader = IrohaConnectWire.Reader(reader.field("encrypted_control.code"))
                codeReader.u16("encrypted_control.code")
                codeReader.done("encrypted_control.code")
                val reason =
                    decodeString(reader.field("encrypted_control.reason"), "encrypted_control.reason")
                val retryable = reader.field("encrypted_control.retryable")
                if (retryable.size != 1 || retryable[0].toInt() !in 0..1) {
                    throw IrohaConnectWireException("Encrypted IrohaConnect close is malformed")
                }
                IrohaConnectEnvelopePayload.Close(reason)
            }
            1L -> {
                val codeReader = IrohaConnectWire.Reader(reader.field("encrypted_control.code"))
                val code = codeReader.u16("encrypted_control.code").also {
                    codeReader.done("encrypted_control.code")
                }
                val codeId = decodeString(
                    reader.field("encrypted_control.code_id"),
                    "encrypted_control.code_id",
                )
                val reason = decodeString(
                    reader.field("encrypted_control.reason"),
                    "encrypted_control.reason",
                )
                IrohaConnectEnvelopePayload.Reject(code, codeId, reason)
            }
            else -> throw IrohaConnectWireException("Unsupported encrypted IrohaConnect control tag")
        }
        reader.done("encrypted_control")
        return result
    }

    private fun decodeString(bytes: ByteArray, label: String): String {
        val reader = IrohaConnectWire.Reader(bytes)
        val raw = reader.bytes(reader.u64Length("$label.length"), label)
        reader.done(label)
        if (raw.size > 16_384) throw IrohaConnectWireException("$label is too long")
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(raw))
            .toString()
    }

    private fun decodeByteVector(bytes: ByteArray, label: String): ByteArray {
        val reader = IrohaConnectWire.Reader(bytes)
        val count = reader.u64Length("$label.count")
        if (count > IrohaConnectWire.MAX_SIGNING_BYTES) throw IrohaConnectWireException("$label is too large")
        val value = reader.bytes(count, label)
        reader.done(label)
        return value
    }

    private fun crc64Xz(bytes: ByteArray): Long {
        var crc = -1L
        bytes.forEach { byte ->
            crc = crc xor (byte.toLong() and 0xffL)
            repeat(8) {
                crc = if (crc and 1L != 0L) (crc ushr 1) xor crcPolynomial else crc ushr 1
            }
        }
        return crc xor -1L
    }
}

object IrohaConnectApprovalPreimage {
    private val approveDomain = "iroha-connect|approve|v1".toByteArray()
    private val relayDomain = "iroha-connect|relay-auth|v1".toByteArray()

    fun modern(
        networkIdBytes: ByteArray,
        sid: ByteArray,
        appPublicKey: ByteArray,
        walletPublicKey: ByteArray,
        accountId: String,
        relayToken: String,
        permissions: IrohaConnectPermissions?,
    ): ByteArray {
        require(networkIdBytes.size == 32 && sid.size == 32 && appPublicKey.size == 32 && walletPublicKey.size == 32)
        val fields = mutableListOf(
            taggedField("domain", approveDomain),
            taggedField("network_id", networkIdBytes),
            taggedField("constraints", IrohaConnectCrypto.blake2b256(IrohaConnectWire.encodeConstraint(networkIdBytes))),
            taggedField("sid", sid),
            taggedField("app_pk", appPublicKey),
            taggedField("wallet_pk", walletPublicKey),
            taggedField("account_id", accountId.toByteArray()),
        )
        permissions?.let {
            fields += taggedField("permissions", IrohaConnectCrypto.blake2b256(IrohaConnectWire.encodePermissions(it)))
        }
        fields += taggedField(
            "relay_auth",
            IrohaConnectCrypto.sha256(relayDomain + sid + relayToken.toByteArray()),
        )
        return fields.fold(ByteArray(0), ByteArray::plus)
    }

    private fun taggedField(tag: String, value: ByteArray): ByteArray {
        val tagBytes = tag.toByteArray()
        return IrohaConnectWire.u16(tagBytes.size) + tagBytes + IrohaConnectWire.u64(value.size.toLong()) + value
    }
}
