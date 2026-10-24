package jp.co.soramitsu.common.nexus

/**
 * Shared exact-decimal wire contract for Nexus balances, fees, quotes, sends, history, and durable
 * pending records. The value stays a decimal string/BigDecimal throughout transaction handling.
 */
object NexusQuantityContract {
    // General read/display admission bound. The current Taira XOR signing writer separately
    // enforces scale <= 9 and the signed-512 magnitude encoded by current Norito.
    const val MAX_SCALE = 255
    const val MAX_WIRE_CHARACTERS = 4_096

    private val wirePattern =
        Regex("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,$MAX_SCALE})?$")
    private val inputPattern =
        Regex("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{0,$MAX_SCALE})?$")

    fun isWireQuantity(value: String): Boolean =
        value == value.trim() &&
            value.length <= MAX_WIRE_CHARACTERS &&
            wirePattern.matches(value)

    fun isInputQuantity(value: String): Boolean =
        value == value.trim() &&
            value.length <= MAX_WIRE_CHARACTERS &&
            inputPattern.matches(value)

    fun hasScaleAtMost(value: String, maximumScale: Int): Boolean {
        require(maximumScale in 0..MAX_SCALE)
        if (!isWireQuantity(value)) return false
        return value.substringAfter('.', missingDelimiterValue = "").length <= maximumScale
    }

    fun isTairaXorQuantity(value: String): Boolean =
        hasScaleAtMost(value, TairaTestnetContract.XOR_SCALE)
}
