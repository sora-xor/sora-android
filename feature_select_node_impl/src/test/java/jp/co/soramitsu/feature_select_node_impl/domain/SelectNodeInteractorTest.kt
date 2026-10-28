package jp.co.soramitsu.feature_select_node_impl.domain

import io.mockk.*
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.common.domain.ChainNode
import jp.co.soramitsu.feature_select_node_api.data.SelectNodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SelectNodeInteractorTest {
    private val repository = mockk<SelectNodeRepository>()
    private val dispatcher = mockk<CoroutineManager> { every { io } returns Dispatchers.Unconfined }
    private val subject = SelectNodeInteractor(repository, NodeValidator(), dispatcher)
    private val original = ChainNode("SORA", "Original", "wss://node.example.org", false, false)
    @Test fun nodeListAndCurrentSelectionRemainSeparateStreams() = runTest {
        val nodes = MutableStateFlow(listOf(original)); val selected = MutableStateFlow<ChainNode?>(null)
        every { repository.getNodes() } returns nodes; every { repository.getSelectedNode() } returns selected
        assertEquals(listOf(original), subject.subscribeNodes().first()); assertNull(subject.subscribeSelectedNode().first())
        selected.value = original.copy(isSelected = true)
        assertEquals(original.address, subject.subscribeSelectedNode().first()!!.address)
        nodes.value = emptyList(); assertTrue(subject.subscribeNodes().first().isEmpty())
        assertEquals(original.address, subject.subscribeSelectedNode().first()!!.address)
    }
    @Test fun customNodeEditsKeepThePreviousAddressAsTheirLookupIdentity() = runTest {
        coEvery { repository.addCustomNode(any()) } just Runs
        coEvery { repository.updateCustomNode(any(), any()) } just Runs
        coEvery { repository.deleteNode(any()) } just Runs
        val updated = original.copy(name = "Edited", address = "wss://new.example.org")
        subject.addCustomNode(original); subject.updateCustomNode(original.address, updated); subject.removeNode(updated)
        coVerifySequence { repository.addCustomNode(original); repository.updateCustomNode(original.address, updated); repository.deleteNode(updated.address) }
    }
    @Test fun validatingNodeInputDoesNotWriteOrSubscribeToStorage() {
        assertEquals(ValidationEvent.ProtocolValidationFailed, subject.validateNodeAddress("https://node.example.org"))
        assertEquals(ValidationEvent.AddressValidationFailed, subject.validateNodeAddress("wss://user:secret@node.example.org"))
        assertEquals(ValidationEvent.Succeed, subject.validateNodeAddress("wss://node.example.org"))
        verify { repository wasNot Called }
    }
    @Test fun failedNodeMutationPropagatesWithoutTryingAnotherWrite() = runTest {
        coEvery { repository.updateCustomNode(any(), any()) } throws IllegalStateException("NODE_WRITE_FAILED")
        val error = runCatching { subject.updateCustomNode(original.address, original.copy(name = "Edited")) }.exceptionOrNull()
        assertEquals("NODE_WRITE_FAILED", error?.message)
        coVerify(exactly = 1) { repository.updateCustomNode(original.address, any()) }
        coVerify(exactly = 0) { repository.deleteNode(any()); repository.addCustomNode(any()) }
    }
}
