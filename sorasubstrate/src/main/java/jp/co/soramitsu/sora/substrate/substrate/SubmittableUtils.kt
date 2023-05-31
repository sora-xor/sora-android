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

package jp.co.soramitsu.sora.substrate.substrate

import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object SubmittableUtils {

    sealed class Era {
        object Immortal : Era()
        class Mortal(val period: Byte, val phase: Byte) : Era()
    }

    fun getMortalEraPeriodAndPhase(currentBlockNumber: Int, periodInBlocks: Int = SubstrateOptionsProvider.mortalEraLength): Pair<Int, Int> {
        var calPeriod = 2.0.pow(ceil(log2(periodInBlocks.toDouble()))).toInt()
        calPeriod = min(1 shl 16, max(calPeriod, 4))
        val phase = currentBlockNumber % calPeriod
        val quantizeFactor = max(1, calPeriod shr 12)
        val quantizePhase = phase / quantizeFactor * quantizeFactor
        return Pair(calPeriod, quantizePhase)
    }

    fun encodeMortalEra(period: Int, phase: Int): Pair<Byte, Byte> {
        val quantizeFactor = max(1, period shr 12)
        val trailingZeros = period.countTrailingZeroBits()
        val encoded = min(15, max(1, trailingZeros - 1)) + (((phase / quantizeFactor) shl 4))
        val first = encoded shr 8
        val second = encoded and 0xff
        return Pair(second.toByte(), first.toByte())
    }

    fun decodeMortalEra(v1: Int, v2: Int): Pair<Int, Int> {
        val encoded = v1 + (v2 shl 8)
        val period = 2 shl (encoded % (1 shl 4))
        val quantizeFactor = max(period shr 12, 1)
        val phase = (encoded shr 4) * quantizeFactor
        return Pair(period, phase)
    }

    fun getEraMortal(currentBlock: Int): Era.Mortal =
        getMortalEraPeriodAndPhase(currentBlock).let {
            encodeMortalEra(it.first, it.second).let { p ->
                Era.Mortal(p.first, p.second)
            }
        }
}
