/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.substrate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.sora.substrate.substrate.AccountInfo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalUnsignedTypes
@RunWith(MockitoJUnitRunner::class)
class ExtrinsicTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Test
    fun `state getStorage method`() {
        val account =
            AccountInfo.read("0x03000000020000000100000000f8dcd274f60c7b0000000000000000000000000000000000000000000000000000f4448291634500000000000000000000f444829163450000000000000000")
        assert(account[AccountInfo.nonce] == 3.toUInt())
    }
}