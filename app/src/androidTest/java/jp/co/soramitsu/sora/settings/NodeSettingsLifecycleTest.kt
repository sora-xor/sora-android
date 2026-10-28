@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelStore
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_select_node_api.*
import jp.co.soramitsu.feature_select_node_impl.domain.*
import jp.co.soramitsu.feature_select_node_impl.presentation.select.*
import jp.co.soramitsu.feature_select_node_impl.presentation.details.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real screen and view model with inert node transport and persistence boundaries. */
@RunWith(AndroidJUnit4::class)
class NodeSettingsLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val official = ChainNode("Sora", "Official fixture", "wss://official.example", false, true)
    private val custom = ChainNode("Sora", "Custom fixture", "wss://custom.example", false, false)
    private val nodes = MutableStateFlow(listOf(official, custom))
    private val selected = MutableStateFlow<ChainNode?>(null)
    private val events = MutableSharedFlow<NodeManagerEvent>(extraBufferCapacity = 8)
    private val interactor = mockk<SelectNodeInteractor>(relaxed = true)
    private val manager = mockk<NodeManager>(relaxed = true)
    private val router = mockk<SelectNodeRouter>(relaxed = true)
    private val main = mockk<MainRouter>(relaxed = true)
    private val resources = mockk<ResourceManager>()
    private val store = ViewModelStore()
    @Before fun setup() {
        every { interactor.subscribeNodes() } returns nodes
        every { interactor.subscribeSelectedNode() } returns selected
        every { manager.events } returns events
        every { resources.getString(any()) } answers { compose.activity.getString(firstArg()) }
    }
    @After fun cleanup() { compose.runOnUiThread { store.clear() } }
    private fun show(fragment: SoraBaseFragment<*>, vm: androidx.lifecycle.ViewModel) {
        store.put("fixture", vm)
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme { val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun selection(): SelectNodeViewModel {
        lateinit var vm: SelectNodeViewModel
        compose.runOnUiThread { vm = SelectNodeViewModel(interactor, resources, router, main, manager) }
        show(SelectNodeFragment(), vm); return vm
    }
    private fun details(edit: Boolean = false): NodeDetailsViewModel {
        selected.value = official
        lateinit var vm: NodeDetailsViewModel
        compose.runOnUiThread { vm = NodeDetailsViewModel(interactor, main, resources, manager, if (edit) custom.name else null, if (edit) custom.address else null) }
        show(NodeDetailsFragment(), vm); return vm
    }
    @Test fun officialNodeConfirmationConnectsTheExactChoiceAndCancellationDoesNothing() {
        val vm: SelectNodeViewModel = selection()
        compose.onNodeWithText(official.name).performClick()
        compose.runOnIdle { assertEquals(official, vm.state.switchNodeAlertState!!.node); vm.onNodeSwitchCanceled() }
        verify(exactly = 0) { manager.tryToConnect(any()) }
        compose.onNodeWithText(official.name).performClick()
        compose.runOnIdle { vm.onNodeSwitchConfirmed() }
        compose.runOnIdle { assertNull(vm.state.switchNodeAlertState) }
        verify(exactly = 1) { manager.tryToConnect(official) }
        verify(exactly = 0) { main.showPin(any()) }
    }
    @Test fun customNodeWaitsForPinAndThenConnectsWithoutSavingAnotherNode() {
        val vm: SelectNodeViewModel = selection()
        compose.onNodeWithText(custom.name).performClick()
        compose.runOnIdle { vm.onNodeSwitchConfirmed() }
        verify(exactly = 1) { main.showPin(PinCodeAction.SELECT_NODE) }
        verify(exactly = 0) { manager.tryToConnect(any()) }
        compose.runOnIdle { vm.onPinCodeChecked() }
        verify(exactly = 1) { manager.tryToConnect(custom) }
        coVerify(exactly = 0) { interactor.addCustomNode(any()); interactor.removeNode(any()) }
    }
    @Test fun connectedCustomNodeCannotBeRemovedButOtherNodesRequireConfirmation() {
        val vm: SelectNodeViewModel = selection()
        compose.runOnIdle { nodes.value = listOf(official, custom.copy(isSelected = true)) }
        compose.runOnIdle {
            vm.onRemoveNode(custom)
            assertNull(vm.state.removeAlertState)
            assertEquals(compose.activity.getString(R.string.remove_node_error_title), vm.alertDialogLiveData.value!!.first)
            nodes.value = listOf(official, custom)
        }
        compose.runOnIdle { vm.onRemoveNode(custom); vm.onRemoveNodeCanceled(); assertNull(vm.state.removeAlertState) }
        coVerify(exactly = 0) { interactor.removeNode(any()) }
        compose.runOnIdle { vm.onRemoveNode(custom); vm.onRemoveNodeConfirmed() }
        compose.runOnIdle { assertNull(vm.state.removeAlertState) }
        coVerify(exactly = 1) { interactor.removeNode(custom) }
    }
    @Test fun nodeEventsShowDistinctOfflineFailureAndSuccessMessages() {
        val vm: SelectNodeViewModel = selection()
        val cases = listOf(NodeManagerEvent.NoConnection to R.string.common_error_invalid_parameters_body,
            NodeManagerEvent.ConnectionFailed(custom.address) to R.string.select_node_unable_join_node_message,
            NodeManagerEvent.Connected(custom.address) to null)
        for ((event, message) in cases) {
            compose.runOnIdle { assertTrue(events.tryEmit(event)) }
            compose.runOnIdle { assertEquals(message?.let(compose.activity::getString).orEmpty(), vm.alertDialogLiveData.value!!.second) }
        }
        compose.runOnIdle { vm.onAddCustomNode(); vm.onEditNode(custom) }
        verify { router.showAddCustomNode(); router.showEditNode(custom.name, custom.address) }
    }
    @Test fun existingNodeEditorKeepsAddressAndIdentityUntilConfirmed() {
        val vm: NodeDetailsViewModel = details(edit = true)
        compose.onNodeWithText(custom.address).assertExists()
        compose.runOnIdle {
            assertTrue(vm.state.value.submitButtonEnabled)
            vm.onNameChanged(TextFieldValue("Renamed fixture"))
            vm.onSubmit()
        }
        verify { main.showPin(PinCodeAction.CUSTOM_NODE) }
        coVerify(exactly = 0) { interactor.updateCustomNode(any(), any()) }
        compose.runOnIdle { vm.onPinCodeChecked(true) }
        compose.runOnIdle { coVerify(exactly = 1) { interactor.updateCustomNode(custom.address, match { it.name == "Renamed fixture" && it.address == custom.address }) }; verify { main.popBackStack() } }
    }
    @Test fun newNodeCannotBeSubmittedBeforeAddressAndGenesisValidationComplete() {
        every { interactor.validateNodeAddress("wss://new.example") } returns ValidationEvent.Succeed
        val vm: NodeDetailsViewModel = details()
        compose.runOnIdle {
            vm.onNameChanged(TextFieldValue("New fixture")); vm.onAddressChanged(TextFieldValue("wss://new.example"))
            assertFalse(vm.state.value.submitButtonEnabled)
        }
        compose.waitUntil(5_000) { vm.state.value.loading }
        verify { manager.checkGenesisHash("wss://new.example") }
        compose.runOnIdle { events.tryEmit(NodeManagerEvent.GenesisValidated(false)) }
        compose.runOnIdle { assertFalse(vm.state.value.submitButtonEnabled); assertTrue(vm.state.value.addressState.error) }
        compose.runOnIdle { events.tryEmit(NodeManagerEvent.GenesisValidated(true)) }
        compose.runOnIdle { assertTrue(vm.state.value.submitButtonEnabled); vm.onPinCodeChecked(true) }
        compose.runOnIdle { coVerify(exactly = 1) { interactor.addCustomNode(match { it.name == "New fixture" && it.address == "wss://new.example" }) }; verify { main.popBackStack() } }
    }
}
