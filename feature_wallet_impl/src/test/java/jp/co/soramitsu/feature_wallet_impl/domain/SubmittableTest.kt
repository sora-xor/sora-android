/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.feature_wallet_impl.util.SubmittableUtils.decodeMortalEra
import jp.co.soramitsu.feature_wallet_impl.util.SubmittableUtils.encodeMortalEra
import jp.co.soramitsu.feature_wallet_impl.util.SubmittableUtils.getEraMortal
import jp.co.soramitsu.feature_wallet_impl.util.SubmittableUtils.getMortalEraPeriodAndPhase
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