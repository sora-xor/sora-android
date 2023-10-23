/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.sora.substrate.response

import java.math.BigInteger
import jp.co.soramitsu.common.util.ParseModel
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.requireHexPrefix
import jp.co.soramitsu.xsubstrate.extensions.fromUnsignedBytes

class FeeResponse2(
    val inclusionFee: InclusionFee
) : ParseModel()

class InclusionFee(
    private val baseFee: String?,
    private val lenFee: String?,
    private val adjustedWeightFee: String?
) : ParseModel() {
    val sum: BigInteger
        get() = BrokenSubstrateHex(baseFee).decodeBigInt() + BrokenSubstrateHex(lenFee).decodeBigInt() + BrokenSubstrateHex(
            adjustedWeightFee
        ).decodeBigInt()
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
