package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.every
import io.mockk.mockk
import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiSoraCalls
import jp.co.soramitsu.sora.substrate.runtime.Sora2CallShape
import jp.co.soramitsu.sora.substrate.runtime.Sora2CallTable
import jp.co.soramitsu.sora.substrate.testing.TestRuntimeSnapshots
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericCall
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.toHex
import jp.co.soramitsu.xsubstrate.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.xsubstrate.runtime.metadata.call
import jp.co.soramitsu.xsubstrate.runtime.metadata.event
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `ethBridge.transfer_to_sidechain` as the app signs it: pinned in the call table against live
 * runtime 142, encoded byte for byte as the golden vectors, and its `RequestRegistered` event read
 * back from `System.Events`.
 */
class EthBridgeTransferCallTest {

    /** Mainnet runtime 142 (transaction version 134), `state_getMetadata` on 2026-11-24. */
    private val mainnet142: RuntimeSnapshot by lazy {
        TestRuntimeSnapshots.build(
            TestRuntimeSnapshots.resource("sora2_metadata_mainnet_142"),
            TestRuntimeSnapshots.packagedTypes,
            runtimeVersion = 142,
        )
    }

    private val vectors = Json.parseToJsonElement(TestRuntimeSnapshots.resource("hashi/sora.json")).jsonObject

    @Test
    fun `the pinned call table matches live runtime 142, transfer_to_sidechain included`() {
        val module = mainnet142.metadata.module("EthBridge")
        val function = module.call("transfer_to_sidechain")
        assertEquals(
            "31:31:3(asset_id:{code:[u8;32]},to:bytes[20],amount:u128,network_id:u32)",
            Sora2CallShape.of(module, function),
        )
        assertEquals(emptyList<String>(), Sora2CallTable.mismatches(mainnet142))
        assertEquals(HashiBridgeContract.PALLET_INDEX, module.index.toInt())
        assertEquals(HashiBridgeContract.TRANSFER_TO_SIDECHAIN_CALL_INDEX, function.index.second)
    }

    @Test
    fun `the call the builder makes encodes to the vector bytes`() {
        val examples = vectors.getValue("transferToSidechain").jsonObject.getValue("examples").jsonArray
        examples.forEach { element ->
            val example = element.jsonObject
            var captured: Map<String, Any?>? = null
            val builder = mockk<ExtrinsicBuilder>()
            every { builder.call("EthBridge", "transfer_to_sidechain", any()) } answers {
                captured = thirdArg()
                builder
            }
            builder.transferToSidechain(
                assetId = example.getValue("assetId").jsonPrimitive.content,
                ethereumRecipient = EthHex.decode(example.getValue("to").jsonPrimitive.content),
                amount = BigInteger(example.getValue("amount").jsonPrimitive.content),
                networkId = example.getValue("networkId").jsonPrimitive.int,
            )
            val module = mainnet142.metadata.module("EthBridge")
            val call = GenericCall.Instance(module, module.call("transfer_to_sidechain"), checkNotNull(captured))
            val expected = example.getValue("callHex").jsonPrimitive.content
            assertEquals(expected, GenericCall.toHex(mainnet142, call))
            // The independent hand encoding agrees.
            assertEquals(
                expected,
                EthHex.encode(
                    HashiSoraCalls.transferToSidechain(
                        EthHex.decode(example.getValue("assetId").jsonPrimitive.content),
                        EthHex.decode(example.getValue("to").jsonPrimitive.content),
                        BigInteger(example.getValue("amount").jsonPrimitive.content),
                        example.getValue("networkId").jsonPrimitive.int,
                    )
                ),
            )
        }
    }

    @Test
    fun `the builder refuses a bad recipient, amount or network`() {
        val builder = mockk<ExtrinsicBuilder>(relaxed = true)
        listOf(
            { builder.transferToSidechain(HashiBridgeContract.XOR_ASSET_ID, ByteArray(19), BigInteger.ONE, 0) },
            { builder.transferToSidechain(HashiBridgeContract.XOR_ASSET_ID, ByteArray(20), BigInteger.ZERO, 0) },
            { builder.transferToSidechain(HashiBridgeContract.XOR_ASSET_ID, ByteArray(20), BigInteger.ONE.shiftLeft(128), 0) },
            { builder.transferToSidechain(HashiBridgeContract.XOR_ASSET_ID, ByteArray(20), BigInteger.ONE, -1) },
        ).forEach { attempt -> assertEquals(true, runCatching { attempt() }.exceptionOrNull() is IllegalArgumentException) }
    }

    @Test
    fun `the RequestRegistered event of the extrinsic is found in System Events`() {
        val requestHash = ByteArray(32) { (it + 1).toByte() }
        // System.Events as the chain stores it: Vec<EventRecord { phase, event, topics }>, written
        // by hand so the test does not depend on how the registry models the event:
        // phase ApplyExtrinsic(2) = 0x00 ‖ u32 LE; event = pallet 31, event 0, H256; no topics.
        val record = byteArrayOf(0x00) + byteArrayOf(2, 0, 0, 0) +
            byteArrayOf(HashiBridgeContract.PALLET_INDEX.toByte(), HashiBridgeContract.REQUEST_REGISTERED_EVENT_INDEX.toByte()) +
            requestHash + byteArrayOf(0x00)
        val events = EthHex.encode(byteArrayOf(0x04) + record)
        assertArrayEquals(requestHash, EthBridgeEvents.requestRegistered(mainnet142, events, 2))
        assertNull(EthBridgeEvents.requestRegistered(mainnet142, events, 0))
        assertNull(EthBridgeEvents.requestRegistered(mainnet142, events, 1))
        // The names the runtime gives these indices are the ones the reader matches.
        val ethBridge = mainnet142.metadata.module("EthBridge")
        assertEquals(HashiBridgeContract.PALLET_INDEX, ethBridge.index.toInt())
        assertEquals(HashiBridgeContract.REQUEST_REGISTERED_EVENT_INDEX, ethBridge.event("RequestRegistered").index.second)
    }
}
