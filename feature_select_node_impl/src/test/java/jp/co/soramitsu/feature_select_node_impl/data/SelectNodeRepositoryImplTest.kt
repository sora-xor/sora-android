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
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
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

    @Test
    fun `refresh replaces blocked defaults and deselects a blocked saved custom node`() = runTest {
        val blockedCustom = NodeLocal(
            address = "wss://ws.mof.sora.org/", chain = "sora", name = "Old custom",
            isDefault = false, isSelected = true,
        )
        val insertedNodes = slot<List<NodeLocal>>()
        stubSuccessfulDatabaseRefresh()
        every { nodeDao.getSelectedNode() } returns blockedCustom
        every { nodeDao.getNodes() } returns listOf(blockedCustom)
        coEvery { nodeDao.insertNodes(capture(insertedNodes)) } just Runs
        coEvery { soraConfigManager.getNodes() } returns listOf(
            remoteNode("wss://mof.sora.org"),
            remoteNode("wss://mof3.sora.org"),
        )

        val result = repository.fetchDefaultNodes()

        assertEquals(listOf(FlavorOptionsProvider.wsHostUrl), result.map { it.address })
        assertTrue(result.single().isSelected)
        assertEquals(blockedCustom.copy(isSelected = false), insertedNodes.captured.last())
        assertEquals(
            listOf(FlavorOptionsProvider.wsHostUrl),
            insertedNodes.captured.filter { it.isSelected }.map { it.address },
        )
    }

    @Test
    fun `refresh ignores blocked remote nodes while retaining an allowed selection`() = runTest {
        val selected = NodeLocal(
            address = "wss://mof2.sora.org/", chain = "sora", name = "Mainnet",
            isDefault = true, isSelected = true,
        )
        val insertedNodes = slot<List<NodeLocal>>()
        stubSuccessfulDatabaseRefresh()
        every { nodeDao.getSelectedNode() } returns selected
        every { nodeDao.getNodes() } returns listOf(selected)
        coEvery { nodeDao.insertNodes(capture(insertedNodes)) } just Runs
        coEvery { soraConfigManager.getNodes() } returns listOf(
            remoteNode("wss://ws.mof.sora.org"),
            remoteNode(selected.address),
        )

        val result = repository.fetchDefaultNodes()

        assertEquals(listOf(selected.address), result.map { it.address })
        assertTrue(result.single().isSelected)
        assertEquals(listOf(selected.address), insertedNodes.captured.map { it.address })
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
