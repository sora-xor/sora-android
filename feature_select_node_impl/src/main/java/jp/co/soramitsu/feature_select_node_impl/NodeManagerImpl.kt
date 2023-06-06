/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl

import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.removeHexPrefix
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.shared_utils.wsrpc.state.SocketStateMachine
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
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
    appDatabase: AppDatabase,
    private val soraConfigManager: SoraConfigManager,
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

    init {
        coroutineManager.applicationScope.launch {
            val address =
                appDatabase.nodeDao().getSelectedNode()?.address ?: FlavorOptionsProvider.wsHostUrl
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
                availableNodes = it
            }
            .launchIn(coroutineManager.applicationScope)

        selectNodeRepository.getSelectedNode()
            .distinctUntilChanged()
            .filterNotNull()
            .onEach {
                selectedNode = it
            }
            .launchIn(coroutineManager.applicationScope)
    }

    override fun tryToConnect(node: ChainNode) {
        if (!connectionManager.isNetworkAvailable) {
            coroutineManager.applicationScope.launch {
                _events.emit(NodeManagerEvent.NoConnection)
            }
        } else {
            previousNode = selectedNode
            newNode = node
            stateObserverEnabled = true
            connectionManager.switchUrl(node.address)
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
                    connectionManager.switchUrl(it)
                }
            }

            else -> {}
        }
    }

    override fun checkGenesisHash(url: String) {
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
        connectionManager.switchUrl(url)
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
                        val newHash = selectNodeRepository.getBlockHash().removeHexPrefix()
                        if (BuildUtils.isFlavors(
                                Flavor.DEVELOP,
                                Flavor.TESTING,
                                Flavor.SORALUTION
                            )
                        ) {
                            _events.emit(NodeManagerEvent.GenesisValidated(result = true))
                        } else {
                            _events.emit(NodeManagerEvent.GenesisValidated(result = soraConfigManager.getGenesis() == newHash))
                        }
                    } catch (e: Throwable) {
                        _events.emit(NodeManagerEvent.GenesisValidated(result = false))
                    }
                    previousNode?.address?.let { connectionManager.switchUrl(it) }
                    customNodeUrl = ""
                }
            }

            is SocketStateMachine.State.WaitingForReconnect -> {
                if (state.url == customNodeUrl) {
                    customNodeUrl = ""
                    blockHashCheckObserverEnabled = false
                    _events.emit(NodeManagerEvent.ConnectionFailed(customNodeUrl))
                    previousNode?.address?.let { connectionManager.switchUrl(it) }
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

            connectionManager.switchUrl(nextNode.address)
            selectNodeRepository.selectNode(nextNode)
        }
    }
}
