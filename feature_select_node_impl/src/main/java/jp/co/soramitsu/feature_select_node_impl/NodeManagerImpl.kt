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

package jp.co.soramitsu.feature_select_node_impl

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.removeHexPrefix
import jp.co.soramitsu.common.account.WalletMutationCoordinator
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.common.network.SoraNodeEndpointPolicy
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import jp.co.soramitsu.xsubstrate.wsrpc.state.SocketStateMachine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class NodeManagerImpl(
    private val connectionManager: ConnectionManager,
    private val selectNodeRepository: SelectNodeRepository,
    private val coroutineManager: CoroutineManager,
    private val autoSwitch: Boolean,
    private val appDatabase: AppDatabase,
    private val soraConfigManager: SoraConfigManager,
    private val walletDatabaseReady: () -> Boolean = {
        WalletUpgradeBackup.blockingFailure() == null
    },
) : NodeManager {

    private companion object {
        const val NODE_SWITCHING_FREQUENCY = 4 // switch node every n attempt
    }

    private var availableNodes: List<ChainNode> = emptyList()

    private var previousNode: ChainNode? = null
    private var selectedNode: ChainNode? = null
    private var newNode: ChainNode? = null

    private val _events: MutableSharedFlow<NodeManagerEvent> = MutableSharedFlow()
    override val events: Flow<NodeManagerEvent> = _events

    private var stateObserverEnabled: Boolean = false
    private var blockHashCheckObserverEnabled: Boolean = false

    private var customNodeUrl: String = ""

    private val attemptedNodes = mutableSetOf<String>()
    private var allNodesUnavailableReported = false
    private val requestGeneration = AtomicLong()

    private val started = AtomicBoolean(false)

    init {
        ensureStarted()
    }

    /**
     * The node address is stored in the wallet database, which stays closed while the upgrade
     * gate blocks startup. Recovery can clear the gate in the same process (Try again, Check
     * again, Continue read-only), so a blocked start is not final: the next call starts it.
     */
    override fun ensureStarted(): Boolean {
        if (started.get()) return true
        if (!walletDatabaseReady()) return false
        if (!started.compareAndSet(false, true)) return true
        start()
        return true
    }

    private fun start() {
        coroutineManager.applicationScope.launch {
            val address =
                appDatabase.nodeDao().getSelectedNode()?.address?.takeIf(SoraNodeEndpointPolicy::isAllowed)
                    ?: FlavorOptionsProvider.wsHostUrl
            connectionManager.setAddress(address)
            connectionManager.observeAppState()

            selectNodeRepository.fetchDefaultNodes()
        }

        connectionManager.networkState
            .onEach {
                val handlingRequest = stateObserverEnabled || blockHashCheckObserverEnabled
                if (stateObserverEnabled) {
                    handleState(it)
                }
                if (blockHashCheckObserverEnabled) {
                    handleConnectionStateForBlockHashCheck(it)
                }
                if (!handlingRequest) autoSwitch(it)
            }
            .launchIn(coroutineManager.applicationScope)

        selectNodeRepository.getNodes()
            .distinctUntilChanged()
            .onEach {
                availableNodes = it.filter { node -> SoraNodeEndpointPolicy.isAllowed(node.address) }
            }
            .launchIn(coroutineManager.applicationScope)

        selectNodeRepository.getSelectedNode()
            .distinctUntilChanged()
            .onEach {
                selectedNode = it?.takeIf { node -> SoraNodeEndpointPolicy.isAllowed(node.address) }
            }
            .launchIn(coroutineManager.applicationScope)
    }

    override fun tryToConnect(node: ChainNode) {
        if (!SoraNodeEndpointPolicy.isAllowed(node.address)) {
            coroutineManager.applicationScope.launch {
                _events.emit(NodeManagerEvent.ConnectionFailed(node.address))
            }
            return
        }
        if (!connectionManager.isNetworkAvailable) {
            coroutineManager.applicationScope.launch {
                _events.emit(NodeManagerEvent.NoConnection)
            }
        } else {
            beginRequest()
            previousNode = selectedNode
            newNode = node
            stateObserverEnabled = true
            switchUrlAfterWalletMutations(node.address)
        }
    }

    override val connectionState: Flow<Boolean> = connectionManager.connectionState

    private suspend fun handleState(state: SocketStateMachine.State) {
        val candidate = newNode ?: return
        val generation = requestGeneration.get()
        when (state) {
            is SocketStateMachine.State.Connected -> {
                if (state.url != candidate.address) return
                stateObserverEnabled = false
                try {
                    selectNodeRepository.selectNode(candidate)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failManualSelection(candidate, generation)
                    return
                }
                if (generation != requestGeneration.get()) return
                selectedNode = candidate
                newNode = null
                _events.emit(NodeManagerEvent.Connected(candidate.address))
            }
            is SocketStateMachine.State.WaitingForReconnect -> {
                if (state.url == candidate.address) failManualSelection(candidate, generation)
            }
            else -> Unit
        }
    }

    private suspend fun failManualSelection(candidate: ChainNode, generation: Long) {
        if (generation != requestGeneration.get()) return
        stateObserverEnabled = false
        newNode = null
        val restore = previousNode?.address
        _events.emit(NodeManagerEvent.ConnectionFailed(candidate.address))
        restore?.let { switchUrlWithWalletMutationBarrier(it, generation) }
    }

    override fun checkGenesisHash(url: String) {
        if (!SoraNodeEndpointPolicy.isAllowed(url)) {
            coroutineManager.applicationScope.launch {
                _events.emit(NodeManagerEvent.GenesisValidated(result = false))
            }
            return
        }
        if (!connectionManager.isNetworkAvailable) {
            coroutineManager.applicationScope.launch {
                _events.emit(NodeManagerEvent.NoConnection)
            }
            return
        }

        if (isNodeAlreadyExisting(url)) {
            return
        }

        beginRequest()
        previousNode = selectedNode
        customNodeUrl = url

        blockHashCheckObserverEnabled = true
        switchUrlAfterWalletMutations(url)
    }

    private fun isNodeAlreadyExisting(url: String): Boolean {
        availableNodes.firstOrNull {
            it.address.compareWithUrl(url)
        }
            ?.let {
                coroutineManager.applicationScope.launch {
                    _events.emit(
                        NodeManagerEvent.NodeExisting(
                            existedNodeName = it.name,
                            currentNodeUrl = selectedNode?.address ?: ""
                        )
                    )
                }
                return true
            }

        return false
    }

    private suspend fun handleConnectionStateForBlockHashCheck(state: SocketStateMachine.State) {
        val url = customNodeUrl
        val generation = requestGeneration.get()
        val restore = previousNode?.address
        when (state) {
            is SocketStateMachine.State.Connected -> {
                if (state.url != url) return
                blockHashCheckObserverEnabled = false
                val valid = try {
                    val newHash = selectNodeRepository.getBlockHash()
                    BuildUtils.isFlavors(Flavor.DEVELOP, Flavor.SORALUTION) ||
                        soraConfigManager.getGenesis().removeHexPrefix() == newHash.removeHexPrefix()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                // A different selection may have arrived while the RPC was in flight.
                if (generation != requestGeneration.get()) return
                customNodeUrl = ""
                _events.emit(NodeManagerEvent.GenesisValidated(result = valid))
                restore?.let { switchUrlWithWalletMutationBarrier(it, generation) }
            }
            is SocketStateMachine.State.WaitingForReconnect -> {
                if (state.url != url) return
                customNodeUrl = ""
                blockHashCheckObserverEnabled = false
                _events.emit(NodeManagerEvent.ConnectionFailed(url))
                restore?.let { switchUrlWithWalletMutationBarrier(it, generation) }
            }
            else -> Unit
        }
    }

    private suspend fun autoSwitch(currentState: SocketStateMachine.State) {
        if (!autoSwitch || availableNodes.isEmpty()) return
        val current = selectedNode ?: return
        if (currentState is SocketStateMachine.State.Connected) {
            if (currentState.url == current.address) resetAutoSwitch()
            return
        }
        if (currentState !is SocketStateMachine.State.WaitingForReconnect ||
            currentState.url != current.address || currentState.attempt % NODE_SWITCHING_FREQUENCY != 0
        ) return
        if (allNodesUnavailableReported) return
        val currentIndex = availableNodes.indexOfFirst { it.address == current.address }
        if (currentIndex < 0) return
        val next = availableNodes[(currentIndex + 1) % availableNodes.size]
        if (next.address in attemptedNodes) {
            allNodesUnavailableReported = true
            _events.emit(NodeManagerEvent.AllNodesUnavailable)
            return
        }
        val generation = requestGeneration.get()
        var failed = false
        WalletMutationCoordinator.withLock {
            if (generation != requestGeneration.get()) return@withLock
            try {
                connectionManager.switchUrl(next.address)
                selectNodeRepository.selectNode(next)
                selectedNode = next
                attemptedNodes += next.address
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == requestGeneration.get()) connectionManager.switchUrl(current.address)
                failed = true
            }
        }
        if (failed && generation == requestGeneration.get()) {
            _events.emit(NodeManagerEvent.ConnectionFailed(next.address))
        }
    }

    private fun resetAutoSwitch() {
        attemptedNodes.clear()
        allNodesUnavailableReported = false
    }

    private fun beginRequest() {
        requestGeneration.incrementAndGet()
        stateObserverEnabled = false
        blockHashCheckObserverEnabled = false
        newNode = null
        customNodeUrl = ""
        resetAutoSwitch()
    }

    private fun switchUrlAfterWalletMutations(url: String) {
        val generation = requestGeneration.get()
        coroutineManager.applicationScope.launch {
            WalletMutationCoordinator.withLock {
                if (generation != requestGeneration.get()) return@withLock
                // Another admitted selection may finish while this request waits for the lock.
                previousNode = selectedNode
                connectionManager.switchUrl(url)
            }
        }
    }

    /**
     * A selected-node change cannot cut across runtime qualification, nonce/header reads, signing,
     * durable hash journaling, or the final transport handoff guarded by this same process-wide
     * coordinator.
     */
    private suspend fun switchUrlWithWalletMutationBarrier(url: String, generation: Long) {
        if (!SoraNodeEndpointPolicy.isAllowed(url)) return
        WalletMutationCoordinator.withLock {
            if (generation != requestGeneration.get()) return@withLock
            connectionManager.switchUrl(url)
        }
    }
}
