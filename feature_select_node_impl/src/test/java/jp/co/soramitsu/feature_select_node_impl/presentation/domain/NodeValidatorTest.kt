/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_select_node_impl.domain.InetAddressesWrapper
import jp.co.soramitsu.feature_select_node_impl.domain.NodeValidator
import jp.co.soramitsu.feature_select_node_impl.domain.ValidationEvent
import jp.co.soramitsu.test_shared.MainCoroutineRule
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
