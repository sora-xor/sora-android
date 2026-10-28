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
