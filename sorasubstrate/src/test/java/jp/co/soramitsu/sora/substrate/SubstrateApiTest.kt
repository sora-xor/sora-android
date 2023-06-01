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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.mockk.MockKException
import io.mockk.coEvery
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApiImpl
import jp.co.soramitsu.test_shared.RealRuntimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

@OptIn(ExperimentalCoroutinesApi::class)
class SubstrateApiTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    lateinit var socket: SocketService

    @MockK
    lateinit var runtimeManager: RuntimeManager

    private lateinit var api: SubstrateApiImpl

    private fun setUpApi() {
        api = SubstrateApiImpl(socket, runtimeManager)
    }

    @Test(expected = MockKException::class)
    fun `dev env subscribe getPoolReserveAccount`() = runTest {
        val n = RealRuntimeProvider.buildRuntime(networkName = "sora2", suffix = "_dev")
        coEvery { runtimeManager.getRuntimeSnapshot() } returns n
        setUpApi()

        val baseTokenId = "0x0200000000000000000000000000000000000000000000000000000000000000"
        val tokenId = "0x0200050000000000000000000000000000000000000000000000000000000000"
        val t = api.getPoolReserveAccount(baseTokenId, tokenId.fromHex())
        assertEquals(byteArrayOf(12, 12, 14), t)
    }

    @Test(expected = MockKException::class)
    fun `soralution env subscribe getPoolReserveAccount`() = runTest {
        val n = RealRuntimeProvider.buildRuntime(networkName = "sora2", suffix = "_soralution")
        coEvery { runtimeManager.getRuntimeSnapshot() } returns n
        setUpApi()

        val baseTokenId = "0x0200000000000000000000000000000000000000000000000000000000000000"
        val tokenId = "0x0200050000000000000000000000000000000000000000000000000000000000"
        val t = api.getPoolReserveAccount(baseTokenId, tokenId.fromHex())
        assertEquals(byteArrayOf(12, 12, 14), t)
    }
}
