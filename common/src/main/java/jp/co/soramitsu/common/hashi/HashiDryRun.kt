package jp.co.soramitsu.common.hashi

import jp.co.soramitsu.common.ethereum.EthHex

/**
 * The outcome of applying a signed extrinsic to the current state without importing it
 * (`ApplyExtrinsicResult` = `Result<Result<(), DispatchError>, TransactionValidityError>`).
 */
sealed class HashiDryRunResult {
    object Ok : HashiDryRunResult()

    /** The call would be included and fail: its fee is charged and nothing else happens. */
    class DispatchFailed(val error: String, val module: Int?, val moduleError: Int?) : HashiDryRunResult()

    /** The transaction would not be included at all (fees, nonce, signature, …). */
    class Invalid(val error: String) : HashiDryRunResult()

    val ok: Boolean get() = this is Ok
}

object HashiDryRun {

    private val DISPATCH_ERRORS = listOf(
        "Other", "CannotLookup", "BadOrigin", "Module", "ConsumerRemaining", "NoProviders",
        "TooManyConsumers", "Token", "Arithmetic", "Transactional", "Exhausted", "Corruption",
        "Unavailable", "RootNotAllowed",
    )

    private val INVALID_TRANSACTION = listOf(
        "Call", "Payment", "Future", "Stale", "BadProof", "AncientBirthBlock", "ExhaustsResources",
        "Custom", "BadMandatory", "MandatoryValidation", "BadSigner",
    )

    private val UNKNOWN_TRANSACTION = listOf("CannotLookup", "NoUnsignedValidator", "Custom")

    private val KNOWN_MODULES = mapOf(HashiBridgeContract.PALLET_INDEX to "EthBridge")

    /**
     * Decodes the SCALE result. [moduleErrorName] names a module error from the runtime metadata
     * (`Pallet.Error`); without it, `EthBridge` errors are named from the reviewed table.
     */
    fun decode(hex: String, moduleErrorName: (module: Int, error: Int) -> String? = { _, _ -> null }): HashiDryRunResult {
        val bytes = EthHex.decode(hex)
        require(bytes.size >= 2) { "HASHI_DRY_RUN_RESULT_INVALID" }
        val outer = bytes[0].toInt()
        return when (outer) {
            0 -> when (bytes[1].toInt()) {
                0 -> {
                    require(bytes.size == 2) { "HASHI_DRY_RUN_RESULT_INVALID" }
                    HashiDryRunResult.Ok
                }
                1 -> dispatchError(bytes, 2, moduleErrorName)
                else -> throw IllegalArgumentException("HASHI_DRY_RUN_RESULT_INVALID")
            }
            1 -> {
                val kind = bytes[1].toInt()
                val reason = bytes.getOrNull(2)?.toInt()?.and(0xff)
                val name = when (kind) {
                    0 -> "Invalid." + (reason?.let { INVALID_TRANSACTION.getOrNull(it) } ?: "?")
                    1 -> "Unknown." + (reason?.let { UNKNOWN_TRANSACTION.getOrNull(it) } ?: "?")
                    else -> throw IllegalArgumentException("HASHI_DRY_RUN_RESULT_INVALID")
                }
                HashiDryRunResult.Invalid(name)
            }
            else -> throw IllegalArgumentException("HASHI_DRY_RUN_RESULT_INVALID")
        }
    }

    private fun dispatchError(
        bytes: ByteArray,
        offset: Int,
        moduleErrorName: (Int, Int) -> String?,
    ): HashiDryRunResult.DispatchFailed {
        val variant = bytes.getOrNull(offset)?.toInt()?.and(0xff) ?: throw IllegalArgumentException("HASHI_DRY_RUN_RESULT_INVALID")
        val name = DISPATCH_ERRORS.getOrNull(variant) ?: throw IllegalArgumentException("HASHI_DRY_RUN_RESULT_INVALID")
        if (name != "Module") return HashiDryRunResult.DispatchFailed(name, null, null)
        require(bytes.size >= offset + 3) { "HASHI_DRY_RUN_RESULT_INVALID" }
        val module = bytes[offset + 1].toInt() and 0xff
        val error = bytes[offset + 2].toInt() and 0xff
        val described = moduleErrorName(module, error)
            ?: KNOWN_MODULES[module]?.let { pallet -> HashiSoraErrors.nameOf(error)?.let { "$pallet.$it" } }
            ?: "Module($module, $error)"
        return HashiDryRunResult.DispatchFailed(described, module, error)
    }
}
