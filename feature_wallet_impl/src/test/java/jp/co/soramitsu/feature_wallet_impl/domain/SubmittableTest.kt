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

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.sora.substrate.substrate.SubmittableUtils.decodeMortalEra
import jp.co.soramitsu.sora.substrate.substrate.SubmittableUtils.encodeMortalEra
import jp.co.soramitsu.sora.substrate.substrate.SubmittableUtils.getEraMortal
import jp.co.soramitsu.sora.substrate.substrate.SubmittableUtils.getMortalEraPeriodAndPhase
import org.junit.Test

class SubmittableTest {

    @Test
    fun `calc mortal era`() {
        val currentBlock = 97506
        val encodedByte1 = 37.toByte()
        val encodedByte2 = 2.toByte()
        val p1 = getMortalEraPeriodAndPhase(currentBlock, 64)
        val p2 = encodeMortalEra(p1.first, p1.second)
        val p3 = decodeMortalEra(encodedByte1.toInt(), encodedByte2.toInt())
        assert(p3.first >= 4 && p3.second < p3.first && p2.first == encodedByte1 && p2.second == encodedByte2 && p3.first == p1.first && p3.second == p1.second)
    }

    @Test
    fun `calc mortal era def`() {
        val currentBlock = 97506
        val encodedByte1 = 37.toByte()
        val encodedByte2 = 2.toByte()
        val p1 = getMortalEraPeriodAndPhase(currentBlock)
        val p2 = encodeMortalEra(p1.first, p1.second)
        val p3 = decodeMortalEra(encodedByte1.toInt(), encodedByte2.toInt())
        assert(p3.first >= 4 && p3.second < p3.first && p2.first == encodedByte1 && p2.second == encodedByte2 && p3.first == p1.first && p3.second == p1.second)
    }

    @Test
    fun `mortal era`() {
        val currentBlock = 97506
        val encodedByte1 = 37.toByte()
        val encodedByte2 = 2.toByte()
        val p1 = getEraMortal(currentBlock)
        assert(p1.period == encodedByte1 && p1.phase == encodedByte2)
    }
}
