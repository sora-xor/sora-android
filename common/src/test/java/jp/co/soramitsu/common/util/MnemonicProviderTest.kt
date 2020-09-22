/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import org.bouncycastle.util.encoders.Hex
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class MnemonicProviderTest {

    private lateinit var mnemonicProvider: MnemonicProvider

    @Before fun setUp() {
        mnemonicProvider = MnemonicProvider()
    }

    @Test fun `generate mnemonic called`() {
        val inputBytes = "qwertyuiopqwertyuiop".toByteArray()
        val expected = "imitate robot frame trophy nuclear regret saddle athlete jazz clog other very final response science"

        mnemonicProvider.generateMnemonic(inputBytes)
            .test()
            .assertResult(expected)
    }

    @Test fun `get bytes from mnemonic called`() {
        val mnemonic = "imitate robot frame trophy nuclear regret saddle athlete jazz clog other very final response science"
        val actualBytes = mnemonicProvider.getBytesFromMnemonic(mnemonic).blockingGet()
        val expected = "696d697461746520726f626f74206672616d652074726f706879206e75636c6561722072656772657420736164646c65206174686c657465206a617a7a20636c6f67206f7468657220766572792066696e616c20726573706f6e736520736369656e6365"

        assertEquals(expected, Hex.toHexString(actualBytes))
    }
}