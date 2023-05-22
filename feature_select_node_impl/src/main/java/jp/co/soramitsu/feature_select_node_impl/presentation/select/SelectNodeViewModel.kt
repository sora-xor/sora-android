/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_select_node_impl.domain.SelectNodeInteractor
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.RemoveNodeAlertState
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.SelectNodeState
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.SwitchNodeAlertState
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
internal class SelectNodeViewModel @Inject constructor(
    private val interactor: SelectNodeInteractor,
    private val resourceManager: ResourceManager,
    private val router: SelectNodeRouter,
    private val mainRouter: MainRouter,
    private val nodeManager: NodeManager
) : BaseViewModel() {

    var state by mutableStateOf(SelectNodeState())
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.common_select_node,
        )

        subscribeNodes()
        subscribeNodeManagerEvents()
    }

    fun onNodeSelected(node: ChainNode) {
        state = state.copy(switchNodeAlertState = SwitchNodeAlertState(node))
    }

    fun onNodeSwitchConfirmed() {
        viewModelScope.launch {
            val customNode = state.customNodes.firstOrNull {
                it.address == state.switchNodeAlertState?.node?.address
            }
                ?.let { true }
                ?: false

            if (customNode) {
                mainRouter.showPin(PinCodeAction.SELECT_NODE)
            } else {
                switchNode()
            }
        }
    }

    fun onPinCodeChecked() {
        switchNode()
    }

    private fun switchNode() {
        state.switchNodeAlertState?.node?.let {
            nodeManager.tryToConnect(it)
        }

        state = state.copy(
            switchNodeAlertState = null,
        )
    }

    fun onNodeSwitchCanceled() {
        state = state.copy(
            switchNodeAlertState = null
        )
    }

    private fun subscribeNodes() {
        interactor.subscribeNodes()
            .catch {
                onError(it)
            }
            .onEach { nodes ->
                state = state.copy(
                    defaultNodes = nodes.filter { it.isDefault },
                    customNodes = nodes.filter { !it.isDefault }
                )
            }
            .launchIn(viewModelScope)
    }

    private fun subscribeNodeManagerEvents() {
        nodeManager.events
            .catch {
                onError(it)
            }
            .onEach { event ->
                when (event) {
                    is NodeManagerEvent.ConnectionFailed -> {
                        showUnableJoinNodeDialog()
                    }
                    is NodeManagerEvent.Connected -> {
                        showSwitchedNodeDialog()
                    }
                    is NodeManagerEvent.NoConnection -> {
                        showNoConnectionDialog()
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    private fun showUnableJoinNodeDialog() {
        alertDialogLiveData.value =
            resourceManager.getString(R.string.select_node_unable_join_node_title) to
            resourceManager.getString(R.string.select_node_unable_join_node_message)
    }

    private fun showSwitchedNodeDialog() {
        alertDialogLiveData.value =
            resourceManager.getString(R.string.select_node_switch_succeed) to ""
    }

    fun onAddCustomNode() {
        router.showAddCustomNode()
    }

    fun onRemoveNode(node: ChainNode) {
        state.customNodes.firstOrNull {
            it.isSelected && it.address == node.address
        }
            ?.let {
                showUnableToRemoveConnectedNode()
                return
            }

        state = state.copy(removeAlertState = RemoveNodeAlertState(node))
    }

    fun onRemoveNodeCanceled() {
        state = state.copy(removeAlertState = null)
    }

    fun onRemoveNodeConfirmed() {
        viewModelScope.launch {
            state.removeAlertState?.node?.let {
                interactor.removeNode(it)
            }
            state = state.copy(removeAlertState = null)
        }
    }

    private fun showUnableToRemoveConnectedNode() {
        alertDialogLiveData.value =
            resourceManager.getString(R.string.remove_node_error_title) to
            resourceManager.getString(R.string.remove_node_error_message)
    }

    fun onEditNode(node: ChainNode) {
        router.showEditNode(node.name, node.address)
    }

    private fun showNoConnectionDialog() {
        alertDialogLiveData.value =
            resourceManager.getString(R.string.select_node_unable_join_node_title) to
            resourceManager.getString(R.string.common_error_invalid_parameters_body)
    }
}
