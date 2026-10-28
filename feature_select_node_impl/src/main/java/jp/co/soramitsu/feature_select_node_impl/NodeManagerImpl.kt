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

package jp.co.soramitsu.feature_select_node_impl

import java.util.concurrent.atomic.AtomicBoolean
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
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

    private var autoSwitchingStarted: Boolean = false
    private val attemptedNodes = mutableListOf<ChainNode>()

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
                if (stateObserverEnabled) {
                    handleState(it)
                }
                if (blockHashCheckObserverEnabled) {
                    handleConnectionStateForBlockHashCheck(it)
                }
                autoSwitch(it)
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
            .filterNotNull()
            .onEach {
                selectedNode = it.takeIf { node -> SoraNodeEndpointPolicy.isAllowed(node.address) }
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
            previousNode = selectedNode
            newNode = node
            stateObserverEnabled = true
            switchUrlAfterWalletMutations(node.address)
        }
    }

    override val connectionState: Flow<Boolean> = connectionManager.connectionState

    private suspend fun handleState(state: SocketStateMachine.State) {
        when (state) {
            is SocketStateMachine.State.Connected -> {
                if (state.url == newNode?.address) {
                    newNode?.let { _events.emit(NodeManagerEvent.Connected(it.address)) }
                    newNode?.let { selectNodeRepository.selectNode(it) }
                    stateObserverEnabled = false
                }
            }

            is SocketStateMachine.State.WaitingForReconnect -> {
                newNode?.let { _events.emit(NodeManagerEvent.ConnectionFailed(it.address)) }
                newNode = null
                previousNode?.address?.let {
                    stateObserverEnabled = false
                    switchUrlWithWalletMutationBarrier(it)
                }
            }

            else -> {}
        }
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
        when (state) {
            is SocketStateMachine.State.Connected -> {
                if (state.url == customNodeUrl) {
                    blockHashCheckObserverEnabled = false
                    try {
                        val newHash = selectNodeRepository.getBlockHash()
                        if (BuildUtils.isFlavors(
                                Flavor.DEVELOP,
                                Flavor.SORALUTION
                            )
                        ) {
                            _events.emit(NodeManagerEvent.GenesisValidated(result = true))
                        } else {
                            _events.emit(NodeManagerEvent.GenesisValidated(result = soraConfigManager.getGenesis().removeHexPrefix() == newHash.removeHexPrefix()))
                        }
                    } catch (e: Throwable) {
                        _events.emit(NodeManagerEvent.GenesisValidated(result = false))
                    }
                    previousNode?.address?.let { switchUrlWithWalletMutationBarrier(it) }
                    customNodeUrl = ""
                }
            }

            is SocketStateMachine.State.WaitingForReconnect -> {
                if (state.url == customNodeUrl) {
                    customNodeUrl = ""
                    blockHashCheckObserverEnabled = false
                    _events.emit(NodeManagerEvent.ConnectionFailed(customNodeUrl))
                    previousNode?.address?.let { switchUrlWithWalletMutationBarrier(it) }
                }
            }

            else -> {}
        }
    }

    private suspend fun autoSwitch(currentState: SocketStateMachine.State) {
        if (!autoSwitch || availableNodes.isEmpty()) {
            return
        }

        if (currentState is SocketStateMachine.State.Connected) {
            autoSwitchingStarted = false
            attemptedNodes.clear()
            return
        }

        if (!autoSwitchingStarted && attemptedNodes.isNotEmpty()) {
            _events.emit(NodeManagerEvent.AllNodesUnavailable)
            return
        }

        if (currentState is SocketStateMachine.State.WaitingForReconnect && (currentState.attempt % NODE_SWITCHING_FREQUENCY) == 0) {
            if (!autoSwitchingStarted) {
                autoSwitchingStarted = true
            }

            val currentNodeIndex = availableNodes.indexOfFirst { it.isSelected }
            // if current selected node is the last, start from first node
            val nextNodeIndex = (currentNodeIndex + 1) % availableNodes.size
            val nextNode = availableNodes[nextNodeIndex]

            if (currentNodeIndex > 0) {
                if (attemptedNodes.firstOrNull { it.address == availableNodes[currentNodeIndex].address } != null) {
                    autoSwitchingStarted = false
                    return
                }

                attemptedNodes.add(availableNodes[currentNodeIndex])
            }

            switchUrlWithWalletMutationBarrier(nextNode.address)
            selectNodeRepository.selectNode(nextNode)
        }
    }

    private fun switchUrlAfterWalletMutations(url: String) {
        coroutineManager.applicationScope.launch {
            switchUrlWithWalletMutationBarrier(url)
        }
    }

    /**
     * A selected-node change cannot cut across runtime qualification, nonce/header reads, signing,
     * durable hash journaling, or the final transport handoff guarded by this same process-wide
     * coordinator.
     */
    private suspend fun switchUrlWithWalletMutationBarrier(url: String) {
        if (!SoraNodeEndpointPolicy.isAllowed(url)) return
        WalletMutationCoordinator.withLock {
            connectionManager.switchUrl(url)
        }
    }
}
