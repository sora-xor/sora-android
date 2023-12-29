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

package jp.co.soramitsu.feature_select_node_impl.presentation.details

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.TextFieldValue
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_impl.TestData.CUSTOM_NODES
import jp.co.soramitsu.feature_select_node_impl.TestData.NODE_DETAILS_ADDRESS
import jp.co.soramitsu.feature_select_node_impl.TestData.NODE_DETAILS_NAME
import jp.co.soramitsu.feature_select_node_impl.TestData.NODE_DETAIL_NODE
import jp.co.soramitsu.feature_select_node_impl.TestData.NODE_LIST
import jp.co.soramitsu.feature_select_node_impl.TestData.SELECTED_NODE
import jp.co.soramitsu.feature_select_node_impl.domain.SelectNodeInteractor
import jp.co.soramitsu.feature_select_node_impl.domain.ValidationEvent
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.given
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class NodeDetailsViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var mainRouter: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var interactor: SelectNodeInteractor

    @Mock
    private lateinit var nodeManager: NodeManager

    private lateinit var viewModel: NodeDetailsViewModel

    private val nodeManagerEvents =
        MutableStateFlow<NodeManagerEvent>(NodeManagerEvent.GenesisValidated(false))

    @Before
    fun setUp() {
        given(resourceManager.getString(R.string.select_node_node_name)).willReturn("Node name")
        given(resourceManager.getString(R.string.select_node_node_address)).willReturn("Node address")
        whenever(resourceManager.getString(R.string.select_node_unable_join_node_title))
            .thenReturn("Title")
        whenever(resourceManager.getString(R.string.select_node_unable_join_node_message))
            .thenReturn("Message")
        whenever(resourceManager.getString(R.string.common_error_general_title))
            .thenReturn("Title")
        whenever(
            resourceManager.getString(
                R.string.node_details_node_already_added,
                NODE_LIST.last().name,
                SELECTED_NODE.address
            )
        )
            .thenReturn("Message")
        whenever(resourceManager.getString(R.string.common_error_general_title))
            .thenReturn("Title")
        whenever(resourceManager.getString(R.string.common_error_invalid_parameters_body))
            .thenReturn("Error")
        given(nodeManager.events).willReturn(
            nodeManagerEvents
        )
        given(interactor.subscribeSelectedNode()).willReturn(flowOf(CUSTOM_NODES.first()))
        given(interactor.subscribeNodes()).willReturn(flowOf(NODE_LIST))
    }

    private fun addNodeViewModel() {
        viewModel = NodeDetailsViewModel(
            interactor,
            mainRouter,
            resourceManager,
            nodeManager,
            nodeAddress = null,
            nodeName = null
        )
    }

    private fun nodeDetailsViewModel(node: ChainNode) {
        viewModel = NodeDetailsViewModel(
            interactor,
            mainRouter,
            resourceManager,
            nodeManager,
            nodeAddress = node.address,
            nodeName = node.name
        )
    }

    @Test
    fun `init EXPECT set up toolbar state title`() {
        addNodeViewModel()

        assertEquals(R.string.select_node_node_details, viewModel.toolbarState.getOrAwaitValue().basic.title)
    }

    @Test
    fun `init EXPECT set up node name label`() {
        addNodeViewModel()

        assertEquals("Node name", viewModel.state.nameState.label)
    }

    @Test
    fun `init EXPECT set up node address label`() {
        addNodeViewModel()

        assertEquals("Node address", viewModel.state.addressState.label)
    }

    @Test
    fun `init EXPECT subscribeNodeManagerEvents`() = runTest {
        addNodeViewModel()

        advanceUntilIdle()

        verify(nodeManager).events
    }

    @Test
    fun `init EXPECT subscribeSelectedNode`() = runTest {
        addNodeViewModel()

        advanceUntilIdle()

        verify(interactor).subscribeSelectedNode()
    }

    @Test
    fun `init EXPECT subscribeNodes`() = runTest {
        addNodeViewModel()

        advanceUntilIdle()

        verify(interactor).subscribeNodes()
    }

    @Test
    fun `submit button clicked EXPECT show pin code`() {
        addNodeViewModel()

        viewModel.onSubmit()

        verify(mainRouter).showPin(PinCodeAction.CUSTOM_NODE)
    }

    @Test
    fun `on how to run a node EXPECT open web view`() {
        addNodeViewModel()

        val title = "How to run a node"
        given(resourceManager.getString(R.string.select_node_how_to_run_node)).willReturn(title)

        viewModel.onHowToRunNode()

        verify(mainRouter).showWebView(
            title = title,
            url = Const.HOW_TO_RUN_A_NODE_PAGE
        )
    }

    @Test
    fun `onNameChanged EXPECT change state`() {
        addNodeViewModel()

        assertEquals(TextFieldValue(), viewModel.state.nameState.value)

        viewModel.onNameChanged(NODE_DETAILS_ADDRESS)

        assertEquals(NODE_DETAILS_ADDRESS, viewModel.state.nameState.value)
    }

    @Test
    fun `name is empty EXPECT submit button disabled`() {
        addNodeViewModel()

        viewModel.onNameChanged(TextFieldValue())

        assertFalse(viewModel.state.submitButtonEnabled)
    }

    @Test
    fun `onAddressChanged EXPECT submit button disabled`() {
        addNodeViewModel()

        viewModel.onAddressChanged(TextFieldValue("text"))

        assertFalse(viewModel.state.submitButtonEnabled)
    }

    @Test
    fun `onAddressChanged EXPECT change state`() {
        addNodeViewModel()

        assertEquals(TextFieldValue(), viewModel.state.addressState.value)

        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)

        assertEquals(NODE_DETAILS_ADDRESS, viewModel.state.addressState.value)
    }

    @Test
    fun `onAddressChanged EXPECT validate url`() = runTest {
        addNodeViewModel()

        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        advanceUntilIdle()

        verify(interactor).validateNodeAddress(NODE_DETAILS_ADDRESS.text)
    }

    @Test
    fun `url validation succeed EXPECT check genesis hash url`() = runTest {
        given(interactor.validateNodeAddress(NODE_DETAILS_ADDRESS.text)).willReturn(ValidationEvent.Succeed)

        addNodeViewModel()
        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        advanceUntilIdle()

        verify(nodeManager).checkGenesisHash(NODE_DETAILS_ADDRESS.text)
    }

    @Test
    fun `protocol validation failed EXPECT error text`() = runTest {
        given(interactor.validateNodeAddress(NODE_DETAILS_ADDRESS.text)).willReturn(ValidationEvent.ProtocolValidationFailed)
        given(resourceManager.getString(R.string.node_details_protocol_validation_failed)).willReturn(
            "error"
        )

        addNodeViewModel()
        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        advanceUntilIdle()

        assertEquals("error", viewModel.state.addressState.descriptionText)
        assertTrue(viewModel.state.addressState.error)
    }

    @Test
    fun `address validation failed EXPECT error text`() = runTest {
        given(interactor.validateNodeAddress(NODE_DETAILS_ADDRESS.text)).willReturn(ValidationEvent.AddressValidationFailed)
        given(resourceManager.getString(R.string.node_details_address_validation_failed)).willReturn(
            "error"
        )

        addNodeViewModel()
        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        advanceUntilIdle()

        assertEquals("error", viewModel.state.addressState.descriptionText)
        assertTrue(viewModel.state.addressState.error)
        assertFalse(viewModel.state.submitButtonEnabled)
    }

    @Test
    fun `genesis validation failed EXPECT error text`() = runTest {
        addNodeViewModel()

        given(resourceManager.getString(R.string.node_details_genesis_validation_failed)).willReturn(
            "error"
        )

        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        advanceUntilIdle()

        assertEquals("error", viewModel.state.addressState.descriptionText)
        assertTrue(viewModel.state.addressState.error)
        assertFalse(viewModel.state.submitButtonEnabled)
    }

    @Test
    fun `pin code checked EXPECT add custom node`() = runTest {
        addNodeViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged(NODE_DETAILS_NAME)
        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        viewModel.onPinCodeChecked(checked = true)
        advanceUntilIdle()

        verify(interactor).addCustomNode(NODE_DETAIL_NODE)
        verify(mainRouter).popBackStack()
    }

    @Test
    fun `pin code checked for node details EXPECT update existing node`() = runTest {
        nodeDetailsViewModel(NODE_DETAIL_NODE.copy(address = "old address"))
        advanceUntilIdle()

        viewModel.onAddressChanged(TextFieldValue(NODE_DETAIL_NODE.address))
        viewModel.onPinCodeChecked(true)
        advanceUntilIdle()

        verify(interactor).updateCustomNode("old address", NODE_DETAIL_NODE)
        verify(mainRouter).popBackStack()
    }

    @Test
    fun `connection to custom node failed EXPECT error dialog`() = runTest {
        nodeManagerEvents.emit(NodeManagerEvent.ConnectionFailed(NODE_DETAILS_ADDRESS.text))

        addNodeViewModel()

        viewModel.onNameChanged(NODE_DETAILS_NAME)
        viewModel.onAddressChanged(NODE_DETAILS_ADDRESS)
        viewModel.onPinCodeChecked(checked = true)
        advanceUntilIdle()

        assertEquals(
            "Title",
            viewModel.alertDialogLiveData.getOrAwaitValue().first
        )

        assertEquals(
            "Message",
            viewModel.alertDialogLiveData.getOrAwaitValue().second
        )
    }

    @Test
    fun `node already existed EXPECT error dialog`() = runTest {
        nodeManagerEvents.emit(
            NodeManagerEvent.NodeExisting(
                NODE_LIST.last().name,
                SELECTED_NODE.address
            )
        )

        addNodeViewModel()

        viewModel.onAddressChanged(TextFieldValue(NODE_LIST.last().address))
        advanceUntilIdle()

        assertFalse(viewModel.state.submitButtonEnabled)
        assertEquals(
            "Title",
            viewModel.alertDialogLiveData.getOrAwaitValue().first
        )

        assertEquals(
            "Message",
            viewModel.alertDialogLiveData.getOrAwaitValue().second
        )
    }

    @Test
    fun `open node details EXPECT set up name and address`() {
        nodeDetailsViewModel(CUSTOM_NODES.first())

        assertEquals(CUSTOM_NODES.first().name, viewModel.state.nameState.value.text)
        assertEquals(CUSTOM_NODES.first().address, viewModel.state.addressState.value.text)
    }

    @Test
    fun `open node details EXPECT submit button enabled`() {
        nodeDetailsViewModel(CUSTOM_NODES.first())

        assertTrue(viewModel.state.submitButtonEnabled)
    }

    @Test
    fun `open node details EXPECT subscribe selected node`() = runTest {
        nodeDetailsViewModel(CUSTOM_NODES.first())
        advanceUntilIdle()

        verify(interactor).subscribeSelectedNode()
    }

    @Test
    fun `open connected node details EXPECT address field disabled`() = runTest {
        nodeDetailsViewModel(CUSTOM_NODES.first())
        advanceUntilIdle()

        assertFalse(viewModel.state.addressState.enabled)
    }

    @Test
    fun `get NoConnection event EXPECT show dialog`() = runTest {
        nodeManagerEvents.emit(NodeManagerEvent.NoConnection)

        nodeDetailsViewModel(CUSTOM_NODES.first())
        advanceUntilIdle()

        assertEquals(
            "Title",
            viewModel.alertDialogLiveData.getOrAwaitValue().first
        )

        assertEquals(
            "Error",
            viewModel.alertDialogLiveData.getOrAwaitValue().second
        )
    }

    @Test
    fun `enter existing name EXPECT node name error`() = runTest {
        given(resourceManager.getString(R.string.add_node_name_already_existing)).willReturn("Error")

        addNodeViewModel()
        advanceUntilIdle()

        viewModel.onNameChanged(TextFieldValue(NODE_LIST.first().name))

        assertEquals("Error", viewModel.state.nameState.descriptionText)
        assertTrue(viewModel.state.nameState.error)
    }

    @Test
    fun `node name is existing with current node EXPECT node name error`() = runTest {
        nodeDetailsViewModel(NODE_DETAIL_NODE)
        advanceUntilIdle()

        viewModel.onNameChanged(TextFieldValue(NODE_DETAIL_NODE.name))

        assertNull("Error", viewModel.state.nameState.descriptionText)
        assertFalse(viewModel.state.nameState.error)
    }
}
