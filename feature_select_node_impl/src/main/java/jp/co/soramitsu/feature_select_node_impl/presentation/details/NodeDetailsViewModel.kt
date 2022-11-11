/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import jp.co.soramitsu.feature_select_node_impl.R
import jp.co.soramitsu.feature_select_node_impl.domain.SelectNodeInteractor
import jp.co.soramitsu.feature_select_node_impl.domain.ValidationEvent
import jp.co.soramitsu.ui_core.component.input.InputTextState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
internal class NodeDetailsViewModel @AssistedInject constructor(
    private val interactor: SelectNodeInteractor,
    private val mainRouter: MainRouter,
    private val resourceManager: ResourceManager,
    private val nodeManager: NodeManager,
    @Assisted("nodeName") private val nodeName: String?,
    @Assisted("nodeAddress") private val nodeAddress: String?,
) : BaseViewModel() {

    @AssistedFactory
    interface NodeDetailsViewModelFactory {
        fun create(
            @Assisted("nodeName") nodeName: String?,
            @Assisted("nodeAddress") nodeAddress: String?
        ): NodeDetailsViewModel
    }

    var state by mutableStateOf(
        NodeDetailsState(
            nameState = InputTextState(
                value = TextFieldValue(nodeName ?: ""),
                label = resourceManager.getString(R.string.select_node_node_name)
            ),
            addressState = InputTextState(
                value = TextFieldValue(nodeAddress ?: ""),
                label = resourceManager.getString(R.string.select_node_node_address)
            )
        )
    )
        private set

    private val address: MutableStateFlow<String> = MutableStateFlow("")

    private var addressIsValid: Boolean = false
    private var nameIsValid: Boolean = false
    private var genesisHashIsValid = false
    private var pinCodeChecked: Boolean = false
    private var node: Node? = null
    private var selectedNode: Node? = null
    private var nodes: List<Node> = emptyList()

    init {
        _toolbarState.value = ToolbarState(
            title = resourceManager.getString(R.string.select_node_node_details)
        )

        subscribeAddressChanges()
        subscribeNodeManagerEvents()

        if (nodeName != null && nodeAddress != null) {
            addressIsValid = true
            nameIsValid = true
            genesisHashIsValid = true

            state = state.copy(submitButtonEnabled = submitButtonEnabled())
        }

        interactor.subscribeSelectedNode()
            .distinctUntilChanged()
            .filterNotNull()
            .catch { onError(it) }
            .onEach {
                selectedNode = it
                state = state.copy(
                    addressState = state.addressState.copy(
                        enabled = !isCurrentNodeConnected()
                    )
                )
            }
            .launchIn(viewModelScope)

        interactor.subscribeNodes()
            .distinctUntilChanged()
            .filterNotNull()
            .catch { onError(it) }
            .onEach {
                nodes = it
            }
            .launchIn(viewModelScope)
    }

    private fun isCurrentNodeConnected(): Boolean {
        return selectedNode?.address == nodeAddress || selectedNode?.address == "$nodeAddress/"
    }

    fun onSubmit() {
        mainRouter.showPin(action = PinCodeAction.CUSTOM_NODE)
    }

    fun onHowToRunNode() {
        mainRouter.showWebView(
            title = resourceManager.getString(R.string.select_node_how_to_run_node),
            url = Const.HOW_TO_RUN_A_NODE_PAGE
        )
    }

    fun onNameChanged(text: TextFieldValue) {
        pinCodeChecked = false
        val errorMessage = validateName(text.text)
        state = state.copy(
            nameState = state.nameState.copy(
                value = text,
                descriptionText = errorMessage,
                error = errorMessage != null
            ),
            submitButtonEnabled = submitButtonEnabled()
        )
    }

    private fun validateName(name: String): String? {
        if (name.isEmpty()) {
            nameIsValid = false
            return null
        }

        val nameExisting = nodes.firstOrNull {
            it.name.trim() == name.trim()
        }
            ?.let { true }
            ?: false

        val nameIsOccupied = nameExisting && name.trim() != nodeName

        if (nameIsOccupied) {
            nameIsValid = false
            return resourceManager.getString(R.string.add_node_name_already_existing)
        }

        nameIsValid = name.isNotEmpty() && !nameIsOccupied
        return null
    }

    fun onAddressChanged(address: TextFieldValue) {
        if (address.text != state.addressState.value.text) {
            pinCodeChecked = false
            addressIsValid = false
            genesisHashIsValid = false
            this.address.value = address.text
        }
        state = state.copy(
            addressState = state.addressState.copy(
                value = address,
                descriptionText = null,
                error = false
            ),
            submitButtonEnabled = submitButtonEnabled()
        )
    }

    fun onNameFocusChanged(focusState: FocusState) {
        state = state.copy(
            nameState = state.nameState.copy(
                focused = focusState.isFocused
            )
        )
    }

    fun onAddressFocusChanged(focusState: FocusState) {
        state = state.copy(
            addressState = state.addressState.copy(
                focused = focusState.isFocused
            )
        )
    }

    private fun subscribeAddressChanges() {
        address
            .debounce(800)
            .catch { onError(it) }
            .onEach { url ->
                if (url.isNotEmpty()) {
                    validateAddress(url)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun validateAddress(url: String) {
        val event = interactor.validateNodeAddress(url)
        when (event) {
            ValidationEvent.Succeed -> {
                addressIsValid = true
                state = state.copy(
                    addressState = state.addressState.copy(
                        descriptionText = null,
                        error = false
                    ),
                    submitButtonEnabled = submitButtonEnabled(),
                    loading = true
                )
                checkGenesisHash(url)
            }
            ValidationEvent.ProtocolValidationFailed -> {
                addressIsValid = false
                state = state.copy(
                    addressState = state.addressState.copy(
                        descriptionText = resourceManager.getString(R.string.node_details_protocol_validation_failed),
                        error = true
                    ),
                    submitButtonEnabled = submitButtonEnabled(),
                )
            }

            ValidationEvent.AddressValidationFailed -> {
                addressIsValid = false
                state = state.copy(
                    addressState = state.addressState.copy(
                        descriptionText = resourceManager.getString(R.string.node_details_address_validation_failed),
                        error = true
                    ),
                    submitButtonEnabled = submitButtonEnabled(),
                )
            }
        }
    }

    private fun checkGenesisHash(url: String) {
        nodeManager.checkGenesisHash(url)
    }

    private fun subscribeNodeManagerEvents() {
        nodeManager.events
            .catch {
                onError(it)
            }
            .onEach { event ->
                when (event) {
                    is NodeManagerEvent.NodeExisting -> {
                        state = state.copy(loading = false)
                        showNodeAlreadyAddedDialog(event.existedNodeName, event.currentNodeUrl)
                    }

                    is NodeManagerEvent.GenesisValidated -> {
                        genesisHashIsValid = event.result
                        state = if (!genesisHashIsValid) {
                            state.copy(
                                addressState = state.addressState.copy(
                                    descriptionText = resourceManager.getString(R.string.node_details_genesis_validation_failed),
                                    error = true
                                ),
                                submitButtonEnabled = submitButtonEnabled(),
                                loading = false
                            )
                        } else {
                            state.copy(
                                submitButtonEnabled = submitButtonEnabled(),
                                loading = false
                            )
                        }
                    }

                    is NodeManagerEvent.ConnectionFailed -> {
                        state = state.copy(loading = false)
                        showUnableJoinNodeDialog()
                    }

                    is NodeManagerEvent.NoConnection -> {
                        state = state.copy(loading = false)
                        showNoConnectionDialog()
                    }

                    is NodeManagerEvent.Connected -> {
                        if (pinCodeChecked) {
                            submitChanges()
                        }
                    }

                    else -> {
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun submitButtonEnabled(): Boolean = addressIsValid && nameIsValid && genesisHashIsValid

    fun onPinCodeChecked(checked: Boolean) {
        pinCodeChecked = checked
        val nodeConnected = isCurrentNodeConnected()
        node = Node(
            chain = Const.SORA,
            name = state.nameState.value.text.trim(),
            address = state.addressState.value.text.trim(),
            isSelected = nodeConnected,
            isDefault = false
        )
        if (nodeConnected) {
            state = state.copy(loading = true)
            node?.let(nodeManager::tryToConnect)
        } else {
            submitChanges()
        }
    }

    private fun submitChanges() {
        if (nodeAddress == null) {
            addNode()
        } else {
            updateCustomNode()
        }
    }

    private fun addNode() {
        viewModelScope.launch {
            node?.let { node ->
                interactor.addCustomNode(node)
                mainRouter.popBackStack()
            }
        }
    }

    private fun updateCustomNode() {
        viewModelScope.launch {
            if (nodeAddress == null) {
                return@launch
            }
            node?.let { node ->
                interactor.updateCustomNode(nodeAddress, node)
                mainRouter.popBackStack()
            }
        }
    }

    private fun showUnableJoinNodeDialog() {
        alertDialogLiveData.value =
            Event(
                resourceManager.getString(R.string.select_node_unable_join_node_title) to
                    resourceManager.getString(R.string.select_node_unable_join_node_message)
            )
    }

    private fun showNodeAlreadyAddedDialog(
        existedNodeName: String,
        currentNodeUrl: String
    ) {
        alertDialogLiveData.value =
            Event(
                resourceManager.getString(R.string.common_error_general_title) to
                    resourceManager.getString(
                        R.string.node_details_node_already_added,
                        existedNodeName,
                        currentNodeUrl
                    )
            )
    }

    private fun showNoConnectionDialog() {
        alertDialogLiveData.value =
            Event(
                resourceManager.getString(R.string.common_error_general_title) to
                    resourceManager.getString(R.string.common_error_invalid_parameters_body)
            )
    }
}
