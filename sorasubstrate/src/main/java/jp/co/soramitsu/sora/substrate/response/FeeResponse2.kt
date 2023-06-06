/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate.response

import androidx.annotation.Keep
import java.math.BigInteger
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.extensions.fromUnsignedBytes
import jp.co.soramitsu.shared_utils.extensions.requireHexPrefix

@Keep
class FeeResponse2(
    val inclusionFee: InclusionFee
)

@Keep
class InclusionFee(
    private val baseFee: String?,
    private val lenFee: String?,
    private val adjustedWeightFee: String?
) {
    val sum: BigInteger
        get() = BrokenSubstrateHex(baseFee).decodeBigInt() + BrokenSubstrateHex(lenFee).decodeBigInt() + BrokenSubstrateHex(adjustedWeightFee).decodeBigInt()
}

@JvmInline
value class BrokenSubstrateHex(private val originalHex: String?) {

    private val Int.isEven: Boolean
        get() = this % 2 == 0

    fun decodeBigInt(): BigInteger {
        // because substrate returns hexes with different length:
        // 0x3b9aca00
        // 0x3486ced00
        // 0xb320334
        if (originalHex == null) return BigInteger.ZERO
        return if (originalHex.length.isEven.not()) {
            val withoutPrefix = originalHex.removePrefix("0x")
            "0$withoutPrefix".requireHexPrefix()
        } else {
            originalHex
        }.fromHex().fromUnsignedBytes()
    }
}
