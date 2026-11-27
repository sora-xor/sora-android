package jp.co.soramitsu.sora.substrate.substrate

import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiSoraRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.xsubstrate.runtime.RuntimeSnapshot
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.generics.GenericEvent
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray

/**
 * The SORA runtime pieces the HASHI bridge needs beyond plain storage: the request hash an
 * extrinsic registered (its `ethBridge.RequestRegistered` event) and names of module errors.
 */
@Singleton
class EthBridgeChainSupport @Inject constructor(
    private val runtimeManager: RuntimeManager,
) {

    /**
     * The request hash registered by [extrinsicHash] in the finalized block [blockHash]: the
     * `RequestRegistered(H256)` event of that extrinsic. Null when the extrinsic is not in the
     * block or registered nothing.
     */
    suspend fun requestHashOf(rpc: HashiSoraRpcClient, blockHash: String, extrinsicHash: String): ByteArray? {
        val block = (rpc.request("chain_getBlock", buildJsonArray { add(blockHash) }) as? JsonObject)
            ?.get("block") as? JsonObject ?: throw IllegalArgumentException("ETH_BRIDGE_BLOCK_INVALID")
        val extrinsics = block["extrinsics"] as? JsonArray ?: throw IllegalArgumentException("ETH_BRIDGE_BLOCK_INVALID")
        val wanted = extrinsicHash.canonicalExtrinsicHash()
        val index = extrinsics.indexOfFirst { (it as? JsonPrimitive)?.content?.extrinsicHash() == wanted }
        if (index < 0) return null
        val snapshot = runtimeManager.getRuntimeSnapshot()
        val key = snapshot.metadata.module(Pallete.SYSTEM.palletName).storage(EVENTS).storageKey()
        val events = (rpc.request("state_getStorage", buildJsonArray { add(key); add(blockHash) }) as? JsonPrimitive)
            ?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("ETH_BRIDGE_EVENTS_MISSING")
        return EthBridgeEvents.requestRegistered(snapshot, events, index)
    }

    /** Names module errors (`Pallet.Error`) from the current runtime's metadata. */
    suspend fun moduleErrorNamer(): (Int, Int) -> String? {
        val metadata = runtimeManager.getRuntimeSnapshot().metadata
        return { moduleIndex, errorIndex ->
            metadata.modules.values.firstOrNull { it.index.toInt() == moduleIndex }?.let { module ->
                module.errors.values.elementAtOrNull(errorIndex)?.name?.let { "${module.name}.$it" }
            }
        }
    }

    private companion object {
        const val EVENTS = "Events"
    }
}

internal object EthBridgeEvents {

    private const val EVENT = "RequestRegistered"

    /** The H256 of the `EthBridge.RequestRegistered` event of extrinsic [index], if there is one. */
    fun requestRegistered(snapshot: RuntimeSnapshot, eventsHex: String, index: Int): ByteArray? {
        val type = checkNotNull(
            snapshot.metadata.module(Pallete.SYSTEM.palletName).storage("Events").type.value
        ) { "ETH_BRIDGE_EVENTS_TYPE_MISSING" }
        val records = type.fromHex(snapshot, eventsHex) as? List<*> ?: throw IllegalArgumentException("ETH_BRIDGE_EVENTS_INVALID")
        val found = records.mapNotNull { record ->
            val struct = record as? Struct.Instance ?: throw IllegalArgumentException("ETH_BRIDGE_EVENTS_INVALID")
            val phase = struct.get<DictEnum.Entry<*>>("phase")
            if (phase?.name != "ApplyExtrinsic" || (phase.value as? BigInteger)?.toInt() != index) return@mapNotNull null
            val generic = struct.get<GenericEvent.Instance>("event")
            val named = struct.get<DictEnum.Entry<*>>("event")
            when {
                generic != null &&
                    generic.module.index.toInt() == HashiBridgeContract.PALLET_INDEX &&
                    generic.event.name == EVENT -> generic.arguments.firstOrNull()
                named != null && named.name == Pallete.ETH_BRIDGE.palletName &&
                    (named.value as? DictEnum.Entry<*>)?.name == EVENT ->
                    (named.value as DictEnum.Entry<*>).value.let { if (it is List<*>) it.firstOrNull() else it }
                else -> null
            }
        }
        if (found.isEmpty()) return null
        check(found.size == 1) { "ETH_BRIDGE_REQUEST_EVENT_AMBIGUOUS" }
        return h256(found.single())
    }

    /** An `H256` as the registry decodes it: bytes, a list of 32 byte values, or a one-field wrapper. */
    private fun h256(value: Any?): ByteArray {
        val bytes = when (value) {
            is ByteArray -> value
            is List<*> -> if (value.size == 1) return h256(value.single()) else ByteArray(value.size) { index ->
                when (val item = value[index]) {
                    is Byte -> item
                    is Number -> item.toInt().also { require(it in 0..255) { "ETH_BRIDGE_REQUEST_EVENT_INVALID" } }.toByte()
                    else -> throw IllegalArgumentException("ETH_BRIDGE_REQUEST_EVENT_INVALID")
                }
            }
            is Struct.Instance -> return h256(value.mapping.values.singleOrNull())
            else -> throw IllegalArgumentException("ETH_BRIDGE_REQUEST_EVENT_INVALID")
        }
        require(bytes.size == 32) { "ETH_BRIDGE_REQUEST_EVENT_INVALID" }
        return bytes
    }
}
