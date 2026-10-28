package jp.co.soramitsu.common.nexus

/** Android/iOS parity contract for an exact non-sentinel 32-byte transaction hash. */
object NexusTransactionHash {
    private val wireHash = Regex("^[0-9a-f]{64}$")

    fun normalized(value: String): String? {
        if (value != value.trim()) return null
        if (value.startsWith("hash:")) {
            if (!Regex("^hash:[0-9A-F]{64}#[0-9A-F]{4}$").matches(value)) return null
            val body = value.substring(5, 69)
            var crc = 0xffff
            "hash:$body".toByteArray(Charsets.UTF_8).forEach { byte ->
                crc = crc xor ((byte.toInt() and 255) shl 8)
                repeat(8) { crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xffff else (crc shl 1) and 0xffff }
            }
            if (crc != value.substring(70).toInt(16) || body.last().digitToInt(16) and 1 != 1) return null
            return body.lowercase()
        }
        val payload = when {
            value.startsWith("0x") || value.startsWith("0X") -> value.substring(2)
            else -> value
        }.lowercase()
        return payload.takeIf { wireHash.matches(it) && it.any { character -> character != '0' } }
    }
}
