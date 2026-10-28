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

import jp.co.soramitsu.feature_select_node_impl.domain.NodeValidator
import jp.co.soramitsu.feature_select_node_impl.domain.ValidationEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class NodeValidatorTest {
    // Exercise real parsing: stubbing IP validation with each expected answer hid DNS/port bugs.
    private val validator = NodeValidator()

    @Test
    fun `valid websocket endpoints support ports paths queries and IPv6`() {
        listOf(
            "ws://192.168.1.1",
            "ws://192.168.1.1:1",
            "ws://192.168.1.1:65535",
            "wss://test.polkaswap.io:443/rpc",
            "wss://test.polkaswap.io:9944/rpc/v1?network=sora",
            "wss://test.polkaswap.io/RPC-v1_2/",
            "WSS://TEST.POLKASWAP.IO/rpc",
            "ws://localhost:9944",
            "ws://x:9944",
            "ws://[::1]",
            "ws://[::1]:9944/rpc",
            "wss://[2606:2800:220:1:248:1893:25c8:1946]:65535/rpc",
        ).forEach { address ->
            assertEquals(address, ValidationEvent.Succeed, validator.validate(address))
        }
    }

    @Test
    fun `missing and non websocket protocols report a protocol error`() {
        listOf("192.168.1.1", "http://example.org", "https://example.org", "ftp://example.org", "wss:example.org")
            .forEach { address ->
                assertEquals(address, ValidationEvent.ProtocolValidationFailed, validator.validate(address))
            }
    }

    @Test
    fun `malformed authorities and unsupported websocket components report an address error`() {
        listOf(
            "ws://", "ws:///rpc", "ws://:9944", "ws://example.org:",
            "ws://example.org:0", "ws://example.org:65536", "ws://example.org:-1",
            "ws://example.org:abc", "ws://example.org:999999999999999999",
            "ws://192.168..1", "ws://192.168.257.1", "ws://256.256.256.256",
            "ws://2001:4860:4860::8888", "ws://[::1", "ws://[::1]:0",
            "ws://[::1]:65536", "ws://[example.org]:9944", "ws://example.org/rpc#fragment",
            "ws://user:password@example.org", "ws://example.org/path with spaces",
            "ws://example.org\n", "ws://example.org/%zz",
        ).forEach { address ->
            assertEquals(address, ValidationEvent.AddressValidationFailed, validator.validate(address))
        }
    }

    @Test
    fun `blocked nodes are rejected before the repository saves them`() {
        listOf("ws.mof.sora.org", "mof.sora.org", "mof3.sora.org", "207.254.29.218")
            .forEach { host ->
                listOf("wss://$host", "wss://${host.uppercase()}.:443/rpc").forEach { address ->
                    assertEquals(address, ValidationEvent.AddressValidationFailed, validator.validate(address))
                }
            }
    }
}
