package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.Gson
import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.mockk.*
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.Type
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.metadata.*
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Module
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Event
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.Storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.xsubstrate.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.response.RpcResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import java.math.BigInteger

/** RPC and decoded runtime values are controlled; the app's selection and projection stay real. */
class SubstrateDecodedStateContractTest {
    private val socket = mockk<SocketService>()
    private val runtime = mockk<RuntimeManager>()
    private val subject = SubstrateCalls(socket, runtime, mockk())
    private val requests = mutableListOf<RuntimeRequest>()
    private val block = "0x" + "ab".repeat(32)
    private var response: (RuntimeRequest) -> Any? = { if (it.method == "chain_getHeader") mapOf("parentHash" to "parent", "number" to "0x10", "stateRoot" to "state", "extrinsicsRoot" to "ext") else if (it.method == "chain_getFinalizedHead") block else "0x00" }
    @Before fun setup() {
        every { socket.jsonMapper } returns Gson()
        every { socket.executeRequest(any(), any(), any()) } answers {
            val request = firstArg<RuntimeRequest>(); requests += request
            val listener = thirdArg<SocketService.ResponseListener<RpcResponse>>()
            try { listener.onNext(RpcResponse("2.0", response(request), request.id, null)) }
            catch (error: Throwable) { listener.onError(error) }
            mockk<SocketService.Cancellable>(relaxed = true)
        }
    }
    private class Decoded(private val decoded: Any) : Type<Any>("FixtureDecoded") {
        override val isFullyResolved = true
        override fun decode(scaleCodecReader: ScaleCodecReader, runtime: RuntimeSnapshot) = decoded
        override fun encode(scaleCodecWriter: ScaleCodecWriter, runtime: RuntimeSnapshot, value: Any) = error("read-only fixture")
        override fun isValidInstance(instance: Any?) = instance != null
    }
    private fun decoded(moduleName: String, storageName: String, value: Any) {
        val original = TestRuntimeSnapshots.mainnet133
        val module = original.metadata.module(moduleName)
        val originalType = module.storage?.entries?.get(storageName)?.type
        val type = if (originalType is StorageEntryType.NMap) StorageEntryType.NMap(originalType.keys, originalType.hashers, Decoded(value)) else StorageEntryType.Plain(Decoded(value))
        val entry = StorageEntry(storageName, StorageEntryModifier.values().first(), type, byteArrayOf(), emptyList(), moduleName)
        val changed = Module(moduleName, Storage(moduleName, mapOf(storageName to entry)), module.calls, module.events, module.constants, module.errors, module.index)
        coEvery { runtime.getRuntimeSnapshot() } returns RuntimeSnapshot(original.typeRegistry,
            RuntimeMetadata(original.metadata.runtimeVersion, original.metadata.modules + (moduleName to changed), original.metadata.extrinsic))
    }
    private fun market(status: String, close: Long) = Struct.Instance(mapOf("status" to DictEnum.Entry(status, null), "close_block" to close.toBigInteger()))
    @Test fun marketLocksAtTheFinalizedCloseBlock() = runTest {
        decoded("Polkamarkt", "Markets", market("Open", 16))
        val result = subject.polkamarktAuthoritativeMarket(42)
        assertEquals(block, result.blockHash); assertEquals("Locked", result.value!!.status)
        assertEquals(42L, result.value!!.marketId); assertEquals(16.toBigInteger(), result.value!!.observedBlockNumber)
        assertEquals(listOf(block), requests.single { it.method == "chain_getHeader" }.params)
        assertEquals(block, requests.single { it.method == "state_getStorage" }.params.last())
    }
    @Test fun marketBeforeCloseStaysOpenAndExplicitCheckpointAvoidsAnotherHeadRead() = runTest {
        decoded("Polkamarkt", "Markets", market("Open", 17))
        assertEquals("Open", subject.polkamarktAuthoritativeMarket(42, block).value!!.status)
        assertFalse(requests.any { it.method == "chain_getFinalizedHead" })
    }
    @Test fun resolvedStatusIsNeverOverwrittenByTheClosingRule() = runTest {
        decoded("Polkamarkt", "Markets", market("Resolved", 1))
        assertEquals("Resolved", subject.polkamarktAuthoritativeMarket(42, block).value!!.status)
    }
    @Test fun absentMarketDoesNotInventRuntimeStateOrReadAHeader() = runTest {
        decoded("Polkamarkt", "Markets", market("Open", 17)); response = { null }
        assertNull(subject.polkamarktAuthoritativeMarket(42, block).value)
        assertEquals(1, requests.size)
    }
    @Test fun incompleteMarketStateNeverBecomesAuthoritative() = runTest {
        for (fields in listOf(mapOf("status" to DictEnum.Entry("Open", null)), mapOf("close_block" to BigInteger.ONE))) {
            decoded("Polkamarkt", "Markets", Struct.Instance(fields))
            val failure = runCatching { subject.polkamarktAuthoritativeMarket(42, block) }.exceptionOrNull()
            assertEquals("POLKAMARKT_RUNTIME_MARKET_INVALID", failure?.message)
        }
    }
    private fun record(phase: String, event: Any?) = Struct.Instance(mapOf("phase" to DictEnum.Entry(phase, 3.toBigInteger()), "event" to event))
    @Test fun finalizedEventsKeepOnlyExtrinsicRecordsAndMapBothRuntimeEventShapes() = runTest {
        val system = TestRuntimeSnapshots.mainnet133.metadata.module("System")
        val generic = GenericEvent.Instance(system, Event("Fixture", 0 to 7, emptyList(), emptyList()), emptyList())
        decoded("System", "Events", listOf(record("ApplyExtrinsic", generic), record("ApplyExtrinsic", DictEnum.Entry("Assets", DictEnum.Entry("Transferred", null))),
            record("Finalization", generic), record("Initialization", generic), record("Unknown", generic), record("ApplyExtrinsic", null), "invalid-record"))
        val events = subject.checkEvents(block)
        assertEquals(2, events.size)
        assertEquals(jp.co.soramitsu.sora.substrate.models.BlockEvent(system.index.toInt(), 7, 3), events[0])
        assertEquals(jp.co.soramitsu.sora.substrate.models.BlockEvent("Assets", "Transferred", 3), events[1])
    }
    @Test fun nonListEventProjectionDoesNotInventEvents() = runTest {
        decoded("System", "Events", "unexpected")
        assertTrue(subject.checkEvents(block).isEmpty())
    }
    @Test fun unavailableEventsRecordOnlyPrivacySafeCategory() = runTest {
        decoded("System", "Events", emptyList<Any>())
        mockkObject(FirebaseWrapper)
        try {
            every { FirebaseWrapper.recordErrorClass(any()) } just Runs
            response = { throw IllegalStateException("sensitive fixture server error") }
            assertTrue(subject.checkEvents(block).isEmpty())
            verify(exactly = 1) { FirebaseWrapper.recordErrorClass(FirebaseWrapper.PrivacySafeErrorClass.FINALIZED_EVENT_READ) }
        } finally { unmockkObject(FirebaseWrapper) }
    }
    @Test fun legacyDualReferenceFlagPreservesFalseAndTrue() = runTest {
        decoded("System", "UpgradedToDualRefCount", true)
        for ((raw, expected) in listOf("0x00" to false, "0x01" to true)) {
            response = { raw }
            assertEquals(expected, subject.isUpgradedToDualRefCount())
        }
        assertTrue(requests.all { it.method == "state_getStorage" && it.params.size == 1 })
    }

}
