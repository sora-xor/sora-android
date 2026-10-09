/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2026 Polka Biome Ltd.
SPDX-License-Identifier: BSD-4-Clause
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
