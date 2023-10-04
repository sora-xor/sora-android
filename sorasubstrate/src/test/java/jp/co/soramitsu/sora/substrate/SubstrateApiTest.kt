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

package jp.co.soramitsu.sora.substrate

import jp.co.soramitsu.xsubstrate.extensions.fromHex
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApiImpl
import jp.co.soramitsu.test_shared.RealRuntimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class SubstrateApiTest {

    private val socket: SocketService = mock()

    private val runtimeManager: RuntimeManager = mock()

    private lateinit var api: SubstrateApiImpl
    private lateinit var dev: RuntimeSnapshot
    private lateinit var stage: RuntimeSnapshot

    private fun setUpApi() {
        api = SubstrateApiImpl(socket, runtimeManager)
    }

    @Before
    fun before() {
        dev = RealRuntimeProvider.buildRuntime(networkName = "sora2", suffix = "_dev")
        stage = RealRuntimeProvider.buildRuntime(networkName = "sora2", suffix = "_soralution")
    }

    @Test
    @Ignore
    fun `dev env subscribe getPoolReserveAccount`() = runTest {
        //whenever(socket.executeAsync(request = any(), mapper = scale(PoolPropertiesResponse))).thenReturn()
        whenever(runtimeManager.getRuntimeSnapshot()).thenReturn(dev)
        setUpApi()

        val baseTokenId = "0x0200000000000000000000000000000000000000000000000000000000000000"
        val tokenId = "0x0200050000000000000000000000000000000000000000000000000000000000"
        val t = api.getPoolReserveAccount(baseTokenId, tokenId.fromHex())
        assertEquals(byteArrayOf(12, 12, 14), t)
    }

    @Test
    @Ignore
    fun `soralution env subscribe getPoolReserveAccount`() = runTest {
        whenever(runtimeManager.getRuntimeSnapshot()).thenReturn(stage)
        setUpApi()

        val baseTokenId = "0x0200000000000000000000000000000000000000000000000000000000000000"
        val tokenId = "0x0200050000000000000000000000000000000000000000000000000000000000"
        val t = api.getPoolReserveAccount(baseTokenId, tokenId.fromHex())
        assertEquals(byteArrayOf(12, 12, 14), t)
    }
}
