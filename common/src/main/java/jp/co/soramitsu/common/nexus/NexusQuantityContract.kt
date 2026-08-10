package jp.co.soramitsu.common.nexus

/**
 * Shared exact-decimal wire contract for Nexus balances, fees, quotes, sends, history, and durable
 * pending records. The value stays a decimal string/BigDecimal throughout transaction handling.
 */
object NexusQuantityContract {
    // Reviewed Nexus Norito quantities encode scale as an unsigned byte.
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
}
