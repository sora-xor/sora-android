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
