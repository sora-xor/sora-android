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

package jp.co.soramitsu.feature_select_node_impl.data

import androidx.room.withTransaction
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import java.io.IOException
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.NodeDao
import jp.co.soramitsu.core_db.model.NodeLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_blockexplorer_api.data.models.SoraConfigNode
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SelectNodeRepositoryImplTest {

    private lateinit var database: AppDatabase
    private lateinit var nodeDao: NodeDao
    private lateinit var soraConfigManager: SoraConfigManager
    private lateinit var repository: SelectNodeRepositoryImpl

    @Before
    fun setUp() {
        database = mockk()
        nodeDao = mockk()
        soraConfigManager = mockk()

        mockkStatic("androidx.room.RoomDatabaseKt")
        val transaction = slot<suspend () -> List<ChainNode>>()
        coEvery {
            database.withTransaction(capture(transaction))
        } coAnswers {
            transaction.captured.invoke()
        }
        every { database.nodeDao() } returns nodeDao

        repository = SelectNodeRepositoryImpl(
            db = database,
            converter = NodeConverter(),
            calls = mockk<SubstrateCalls>(relaxed = true),
            soraConfigManager = soraConfigManager,
        )
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `suspended remote node fetch does not start a Room transaction`() = runTest {
        val remoteStarted = CompletableDeferred<Unit>()
        val remoteResult = CompletableDeferred<List<SoraConfigNode>>()
        coEvery { soraConfigManager.getNodes() } coAnswers {
            remoteStarted.complete(Unit)
            remoteResult.await()
        }
        stubSuccessfulDatabaseRefresh()

        val fetch = async { repository.fetchDefaultNodes() }
        remoteStarted.await()

        coVerify(exactly = 0) {
            database.withTransaction<List<ChainNode>>(any())
        }

        remoteResult.complete(listOf(remoteNode(address = "wss://default")))
        fetch.await()

        coVerify(exactly = 1) {
            database.withTransaction<List<ChainNode>>(any())
        }
    }

    @Test
    fun `remote node failure leaves database state unchanged`() = runTest {
        coEvery { soraConfigManager.getNodes() } throws IOException("offline")

        assertTrue(repository.fetchDefaultNodes().isEmpty())

        coVerify(exactly = 0) {
            database.withTransaction<List<ChainNode>>(any())
        }
        verify(exactly = 0) { database.nodeDao() }
        coVerify(exactly = 0) { nodeDao.clearTable() }
        coVerify(exactly = 0) { nodeDao.insertNodes(any()) }
        coVerify(exactly = 0) { nodeDao.selectNode(any()) }
    }

    @Test
    fun `refresh preserves current custom node and its selection`() = runTest {
        val selectedCustomNode = NodeLocal(
            address = "wss://custom/",
            chain = "sora",
            name = "Custom",
            isDefault = false,
            isSelected = true,
        )
        val staleDefaultNode = NodeLocal(
            address = "wss://stale/",
            chain = "sora",
            name = "Stale",
            isDefault = true,
            isSelected = false,
        )
        val remoteNodes = listOf(
            remoteNode(address = "wss://first", name = "First"),
            remoteNode(address = "wss://second", name = "Second"),
        )
        val insertedNodes = slot<List<NodeLocal>>()

        coEvery { soraConfigManager.getNodes() } returns remoteNodes
        every { nodeDao.getSelectedNode() } returns selectedCustomNode
        every { nodeDao.getNodes() } returns listOf(staleDefaultNode, selectedCustomNode)
        coEvery { nodeDao.clearTable() } just Runs
        coEvery { nodeDao.insertNodes(capture(insertedNodes)) } just Runs
        coEvery { nodeDao.selectNode(any()) } just Runs

        val result = repository.fetchDefaultNodes()

        assertEquals(
            listOf(
                NodeLocal("wss://first/", "sora", "First", true, false),
                NodeLocal("wss://second/", "sora", "Second", true, false),
                selectedCustomNode,
            ),
            insertedNodes.captured,
        )
        assertEquals(
            listOf(
                ChainNode("sora", "First", "wss://first/", false, true),
                ChainNode("sora", "Second", "wss://second/", false, true),
            ),
            result,
        )
        coVerify(exactly = 1) { nodeDao.clearTable() }
        coVerify(exactly = 1) { nodeDao.insertNodes(any()) }
        coVerify(exactly = 0) { nodeDao.selectNode(any()) }
    }

    private fun stubSuccessfulDatabaseRefresh() {
        every { nodeDao.getSelectedNode() } returns null
        every { nodeDao.getNodes() } returns emptyList()
        coEvery { nodeDao.clearTable() } just Runs
        coEvery { nodeDao.insertNodes(any()) } just Runs
        coEvery { nodeDao.selectNode(any()) } just Runs
    }

    private fun remoteNode(
        address: String,
        name: String = "Default",
    ) = SoraConfigNode(
        chain = "sora",
        name = name,
        address = address,
    )
}
