/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
