/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
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

package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import org.junit.Test

class WsConnectionManagerLifecycleTest {

    @Test
    fun `resume starts a socket when manager missed process create`() {
        val socket = mockk<SocketService>()
        every { socket.started() } returns false
        every { socket.start(ADDRESS, true) } just Runs

        resumeOrStartSocket(socket, ADDRESS)

        verify(exactly = 1) { socket.start(ADDRESS, true) }
        verify(exactly = 0) { socket.resume() }
    }

    @Test
    fun `resume keeps existing socket instead of starting it twice`() {
        val socket = mockk<SocketService>()
        every { socket.started() } returns true
        every { socket.resume() } just Runs

        resumeOrStartSocket(socket, ADDRESS)

        verify(exactly = 1) { socket.resume() }
        verify(exactly = 0) { socket.start(any(), any()) }
    }

    @Test
    fun `resume does not start without an admitted address`() {
        val socket = mockk<SocketService>()
        every { socket.started() } returns false

        resumeOrStartSocket(socket, null)

        verify(exactly = 0) { socket.resume() }
        verify(exactly = 0) { socket.start(any(), any()) }
    }

    private companion object {
        const val ADDRESS = "wss://example.invalid"
    }
}
