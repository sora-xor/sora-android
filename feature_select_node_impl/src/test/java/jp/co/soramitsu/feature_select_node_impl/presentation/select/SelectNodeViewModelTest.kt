/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_select_node_impl.presentation.select

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.NodeManagerEvent
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_select_node_impl.TestData.CUSTOM_NODES
import jp.co.soramitsu.feature_select_node_impl.TestData.DEFAULT_NODES
import jp.co.soramitsu.feature_select_node_impl.TestData.NODE_LIST
import jp.co.soramitsu.feature_select_node_impl.TestData.SELECTED_NODE
import jp.co.soramitsu.feature_select_node_impl.domain.SelectNodeInteractor
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.RemoveNodeAlertState
import jp.co.soramitsu.feature_select_node_impl.presentation.select.model.SwitchNodeAlertState
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever


@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class SelectNodeViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: SelectNodeInteractor

    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var selectNodeRouter: SelectNodeRouter

    @Mock
    private lateinit var mainRouter: MainRouter

    @Mock
    private lateinit var nodeManager: NodeManager

    private lateinit var selectNodeViewModel: SelectNodeViewModel

    private val nodeManagerEvents = MutableStateFlow<NodeManagerEvent>(NodeManagerEvent.ConnectionFailed(SELECTED_NODE.address))

    @Before
    fun setUp() = runTest {
        whenever(resourceManager.getString(R.string.common_select_node)).thenReturn("Title")
        whenever(resourceManager.getString(R.string.select_node_unable_join_node_title))
            .thenReturn("Title")
        whenever(resourceManager.getString(R.string.select_node_unable_join_node_message))
            .thenReturn("Message")
        whenever(resourceManager.getString(R.string.select_node_switch_succeed)).thenReturn("Success")
        whenever(resourceManager.getString(R.string.common_error_invalid_parameters_body)).thenReturn("Error")
        whenever(interactor.subscribeNodes()).thenReturn(flowOf(NODE_LIST))
        whenever(nodeManager.events).thenReturn(nodeManagerEvents)

        selectNodeViewModel = SelectNodeViewModel(
            interactor,
            resourceManager,
            selectNodeRouter,
            mainRouter,
            nodeManager
        )
    }

    @Test
    fun `initialize EXPECT init toolbar state`() = runTest {
        advanceUntilIdle()

        assertEquals(selectNodeViewModel.toolbarState.value?.title, "Title")
    }

    @Test
    fun `initialize EXPECT subscribe to default nodes`() = runTest {
        advanceUntilIdle()

        verify(interactor).subscribeNodes()
    }

    @Test
    fun `get nodes EXPECT update state`() = runTest {
        advanceUntilIdle()

        assertEquals(DEFAULT_NODES, selectNodeViewModel.state.defaultNodes)
        assertEquals(CUSTOM_NODES, selectNodeViewModel.state.customNodes)
    }

    @Test
    fun `on node selected EXPECT switch alert`() = runTest {
        selectNodeViewModel.onNodeSelected(SELECTED_NODE)
        advanceUntilIdle()

        assertEquals(
            SwitchNodeAlertState(SELECTED_NODE),
            selectNodeViewModel.state.switchNodeAlertState
        )
    }

    @Test
    fun `onNodeSwitchConfirmed EXPECT try to connect`() = runTest {
        selectNodeViewModel.onNodeSelected(SELECTED_NODE)
        selectNodeViewModel.onNodeSwitchConfirmed()
        advanceUntilIdle()

        verify(nodeManager).tryToConnect(SELECTED_NODE)
    }

    @Test
    fun `onNodeSwitchConfirmed EXPECT switchNodeAlertState null`() = runTest {
        selectNodeViewModel.onNodeSelected(SELECTED_NODE)
        selectNodeViewModel.onNodeSwitchConfirmed()
        advanceUntilIdle()

        assertNull(selectNodeViewModel.state.switchNodeAlertState)
    }


    @Test
    fun `onNodeSwitchCanceled EXPECT switchNodeAlertState null`() = runTest {
        selectNodeViewModel.onNodeSwitchCanceled()

        assertNull(selectNodeViewModel.state.switchNodeAlertState)
    }

    @Test
    fun `get ConnectionFailed event EXPECT show dialog`() = runTest {
        nodeManagerEvents.emit(NodeManagerEvent.ConnectionFailed(SELECTED_NODE.address))
        advanceUntilIdle()

        assertEquals(
            "Title",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.first
        )

        assertEquals(
            "Message",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.second
        )
    }

    @Test
    fun `get Connected event EXPECT show dialog`() = runTest {
        nodeManagerEvents.emit(NodeManagerEvent.Connected(SELECTED_NODE.address))
        advanceUntilIdle()

        assertEquals(
            "Success",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.first
        )

        assertEquals(
            "",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.second
        )
    }

    @Test
    fun `get NoConnection event EXPECT show dialog`() = runTest {
        nodeManagerEvents.emit(NodeManagerEvent.NoConnection)
        advanceUntilIdle()

        assertEquals(
            "Title",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.first
        )

        assertEquals(
            "Error",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.second
        )
    }

    @Test
    fun `on add custom node clicked EXPECT navigate to add custom node screen`() {
        selectNodeViewModel.onAddCustomNode()

        verify(selectNodeRouter).showAddCustomNode()
    }

    @Test
    fun `remove connected node EXPECT error message`() {
        selectNodeViewModel.onRemoveNode(SELECTED_NODE)

        assertEquals(
            "Title",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.first
        )

        assertEquals(
            "Message",
            selectNodeViewModel.alertDialogLiveData.value?.peekContent()?.second
        )
    }

    @Test
    fun `remove node EXPECT alert`() {
        selectNodeViewModel.onRemoveNode(CUSTOM_NODES.last())

        assertEquals(
            RemoveNodeAlertState(CUSTOM_NODES.last()),
            selectNodeViewModel.state.removeAlertState
        )
    }

    @Test
    fun `cancel node removing EXPECT set alert state to null`() {
        selectNodeViewModel.onRemoveNodeCanceled()

        assertNull(selectNodeViewModel.state.removeAlertState)
    }

    @Test
    fun `remove node confirmed EXPECT remove node from local storage`() = runTest {
        advanceUntilIdle()
        selectNodeViewModel.onRemoveNode(CUSTOM_NODES.last())
        selectNodeViewModel.onRemoveNodeConfirmed()

        advanceUntilIdle()

        verify(interactor).removeNode(CUSTOM_NODES.last())
    }

    @Test
    fun `node removed EXPECT set alert state to null`() = runTest {
        selectNodeViewModel.onRemoveNode(CUSTOM_NODES.last())
        selectNodeViewModel.onRemoveNodeConfirmed()

        advanceUntilIdle()

        assertNull(selectNodeViewModel.state.removeAlertState)
    }

    @Test
    fun `select custom node EXPECT check pin code`() = runTest {
        selectNodeViewModel.onNodeSelected(CUSTOM_NODES.first())
        selectNodeViewModel.onNodeSwitchConfirmed()

        advanceUntilIdle()

        verify(mainRouter).showPin(PinCodeAction.SELECT_NODE)
    }

    @Test
    fun `pin code checked EXPECT try to connect`() = runTest {
        selectNodeViewModel.onNodeSelected(CUSTOM_NODES.first())
        selectNodeViewModel.onNodeSwitchConfirmed()
        selectNodeViewModel.onPinCodeChecked()

        advanceUntilIdle()

        verify(nodeManager).tryToConnect(CUSTOM_NODES.first())
        assertNull(selectNodeViewModel.state.switchNodeAlertState)
    }
}
