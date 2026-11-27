package jp.co.soramitsu.common.hashi

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Amount rules of the bridge, as the pallet applies them (`ethBridge` `OutgoingTransfer::validate`,
 * `IncomingRequest::try_from_contract_event`, `convert_precision`).
 *
 * - Outgoing `Sidechain` assets: the SORA amount must have no digits below the token's Ethereum
 *   decimals, or the pallet refuses with `NonZeroDust`. The wallet refuses such an input; it never
 *   rounds or truncates an amount the person typed.
 * - Owned assets (`Thischain`, `SidechainOwned`) are not converted: the Ethereum amount is the
 *   SORA amount times the denomination factor (1 except TBCD).
 * - Incoming: the Ethereum amount is divided by the denomination factor and, for `Sidechain`
 *   assets, converted to SORA precision. Both drop a remainder; the wallet only accepts amounts
 *   for which nothing is dropped and the SORA credit is not zero.
 */
object HashiAmounts {

    fun pow10(exponent: Int): BigInteger {
        require(exponent in 0..80) { "HASHI_EXPONENT_INVALID" }
        return BigInteger.TEN.pow(exponent)
    }

    /** The smallest step of an outgoing SORA amount (in SORA units). */
    fun outgoingStep(asset: HashiBridgeAsset): BigInteger =
        if (asset.kind == HashiAssetKind.SIDECHAIN && asset.soraPrecision > asset.ethDecimals) {
            pow10(asset.soraPrecision - asset.ethDecimals)
        } else {
            BigInteger.ONE
        }

    /** Decimal places an outgoing amount may have. */
    fun outgoingScale(asset: HashiBridgeAsset): Int =
        if (asset.kind == HashiAssetKind.SIDECHAIN) minOf(asset.soraPrecision, asset.ethDecimals) else asset.soraPrecision

    fun hasOutgoingDust(soraAmount: BigInteger, asset: HashiBridgeAsset): Boolean =
        soraAmount.mod(outgoingStep(asset)).signum() != 0

    /**
     * The SORA units of a typed outgoing amount, or null when it has more decimals than the asset
     * can cross with ([outgoingScale]) or is not positive.
     */
    fun outgoingUnits(amount: BigDecimal, asset: HashiBridgeAsset): BigInteger? {
        if (amount.signum() <= 0 || decimalsOf(amount) > outgoingScale(asset)) return null
        val units = amount.movePointRight(asset.soraPrecision).toBigIntegerExact()
        return units.takeUnless { hasOutgoingDust(it, asset) }
    }

    /**
     * The token units of a typed incoming amount, or null when it has more decimals than the token,
     * or when the pallet would drop part of it or credit nothing ([incomingSoraAmount]).
     */
    fun incomingUnits(amount: BigDecimal, asset: HashiBridgeAsset, denominationFactor: BigInteger): BigInteger? {
        if (amount.signum() <= 0 || decimalsOf(amount) > asset.ethDecimals) return null
        val units = amount.movePointRight(asset.ethDecimals).toBigIntegerExact()
        return units.takeIf { incomingSoraAmount(it, asset, denominationFactor) != null }
    }

    /** Significant decimal places of [amount] (trailing zeros do not count). */
    fun decimalsOf(amount: BigDecimal): Int = maxOf(0, amount.stripTrailingZeros().scale())

    /**
     * The Ethereum amount an outgoing transfer is expected to pay, for display only. The claim
     * itself always uses the amount the chain approved, never this.
     */
    fun expectedEthAmount(soraAmount: BigInteger, asset: HashiBridgeAsset, denominationFactor: BigInteger): BigInteger {
        require(denominationFactor.signum() > 0) { "HASHI_DENOMINATION_INVALID" }
        val converted = if (asset.kind == HashiAssetKind.SIDECHAIN) {
            convertPrecision(asset.soraPrecision, asset.ethDecimals, soraAmount).first
        } else {
            soraAmount
        }
        return converted.multiply(denominationFactor)
    }

    /** The smallest step of an incoming Ethereum amount (in token units) that loses nothing. */
    fun incomingStep(asset: HashiBridgeAsset, denominationFactor: BigInteger): BigInteger {
        require(denominationFactor.signum() > 0) { "HASHI_DENOMINATION_INVALID" }
        val precisionStep = if (asset.kind == HashiAssetKind.SIDECHAIN && asset.ethDecimals > asset.soraPrecision) {
            pow10(asset.ethDecimals - asset.soraPrecision)
        } else {
            BigInteger.ONE
        }
        return denominationFactor.multiply(precisionStep)
    }

    /**
     * The SORA amount an Ethereum deposit of [ethAmount] credits. Null when the pallet would drop
     * part of it (the amount is not a multiple of [incomingStep]) or credit nothing.
     */
    fun incomingSoraAmount(ethAmount: BigInteger, asset: HashiBridgeAsset, denominationFactor: BigInteger): BigInteger? {
        if (ethAmount.signum() <= 0) return null
        if (ethAmount.mod(incomingStep(asset, denominationFactor)).signum() != 0) return null
        val denominated = ethAmount.divide(denominationFactor)
        val sora = if (asset.kind == HashiAssetKind.SIDECHAIN) {
            convertPrecision(asset.ethDecimals, asset.soraPrecision, denominated).first
        } else {
            denominated
        }
        return sora.takeIf { it.signum() > 0 }
    }

    /** The pallet's `convert_precision`: (converted, dropped remainder). */
    fun convertPrecision(from: Int, to: Int, amount: BigInteger): Pair<BigInteger, BigInteger> = when {
        from == to -> amount to BigInteger.ZERO
        from < to -> amount.multiply(pow10(to - from)) to BigInteger.ZERO
        else -> {
            val step = pow10(from - to)
            val converted = amount.divide(step)
            converted to amount.subtract(converted.multiply(step))
        }
    }

    fun fromUnits(units: BigInteger, decimals: Int): BigDecimal = BigDecimal(units, decimals)
}
