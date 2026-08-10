package jp.co.soramitsu.common.nexus

/** Android/iOS parity contract for an exact non-sentinel 32-byte transaction hash. */
object NexusTransactionHash {
    private val wireHash = Regex("^[0-9a-f]{64}$")

    fun normalized(value: String): String? {
        if (value != value.trim()) return null
        val payload = when {
            value.startsWith("0x") || value.startsWith("0X") -> value.substring(2)
            else -> value
        }.lowercase()
        return payload.takeIf { wireHash.matches(it) && it.any { character -> character != '0' } }
    }
}
