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

package jp.co.soramitsu.feature_select_node_impl.presentation.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.androidfoundation.testing.MainCoroutineRule
import jp.co.soramitsu.feature_select_node_impl.domain.InetAddressesWrapper
import jp.co.soramitsu.feature_select_node_impl.domain.NodeValidator
import jp.co.soramitsu.feature_select_node_impl.domain.ValidationEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class NodeValidatorTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var nodeValidator: NodeValidator

    @Mock
    private lateinit var inetAddressesWrapper: InetAddressesWrapper

    @Before
    fun setUp() = runTest {
        nodeValidator = NodeValidator(inetAddressesWrapper)
    }

    @Test
    fun `validate called`() {
        val testVector = mutableMapOf<String, Boolean>()
        testVector["ws://192.168.1.1"] = true
        testVector["192.168.123.13"] = false
        testVector["ws://192.168.1.1:1"] = true
        testVector["http://192.168.1.1:1"] = false
        testVector["ws://192.168.1.1:0"] = false
        testVector["ws://192.168.1.1:65535"] = true
        testVector["ws://192.168.1.1:65536"] = false
        testVector["ws://192.168.1.1:6553)"] = false
        testVector["ws://192.168..1:6553)"] = false
        testVector["ws://192.168.257.1:6553)"] = false
        testVector["ws://2001:4860:4860::8888"] = true
        testVector["ws://2606:2800:220:1:248:1893:25c8:1946"] = true
        testVector["ws://[2606:2800:220:1:248:1893:25c8:1946]:1"] = true
        testVector["ws://[2606:2800:220:1:248:1893:25c8:1946]:0"] = false
        testVector["ws://[2606:2800:220:1:248:1893:25c8:1946]:65535"] = true
        testVector["ws://[2606:2800:220:1:248:1893:25c8:1946]:65536"] = false
        testVector["ws://2606:2800:220:::"] = false
        testVector["ws://2606:2800:220::cdfea:"] = false
        testVector["ws://polaswap.io"] = true
        testVector["ws://test.polkaswap.io"] = true
        testVector["ws://test.polkaswap.io:0"] = false
        testVector["ws://test.polkaswap.io:1"] = true
        testVector["ws://test.polkaswap.io:1)"] = false
        testVector["ws://test.polkaswap.io:65535"] = true
        testVector["ws://test.polkaswap.io:65536"] = false

        testVector.forEach { (address, result) ->
            given(inetAddressesWrapper.isIpAddressValid(address.removePrefix("ws://"))).willReturn(result)
            assertEquals(result, nodeValidator.validate(address) == ValidationEvent.Succeed)
        }
    }
}
