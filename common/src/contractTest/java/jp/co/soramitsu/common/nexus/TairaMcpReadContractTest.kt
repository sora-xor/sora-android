package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Local wire contracts: no credentials, signing, or network access. */
class TairaMcpReadContractTest {
    private val definition = TairaTestnetContract.XOR_ASSET_DEFINITION_ID
    private val hash = "ab".repeat(32)
    private fun arguments(request: NexusMcpRequest) = request.params!!.getValue("arguments").jsonObject
    private fun health(status: JsonElement = JsonPrimitive(200), contentType: JsonElement = JsonPrimitive("text/plain"), body: JsonElement = JsonPrimitive("Healthy")) = buildJsonObject {
        put("isError", false)
        putJsonObject("structuredContent") { put("status", status); put("content_type", contentType); put("body", body) }
    }
    @Test fun nonTairaAssetIdentityRequiresAnActiveExactAliasBinding() {
        val valid = NexusAssetDefinition(definition, "xor", "xor#universal", NexusAssetAliasBinding("xor#universal", "permanent", boundAtMillis = 1))
        assertTrue(NexusAssetDefinitionIdentity.isQualifiedXorDefinition(valid, NexusNetworks.minamoto))
        assertTrue(NexusAssetDefinitionIdentity.isQualifiedXorDefinition(valid.copy(aliasBinding = valid.aliasBinding!!.copy(status = "leased_active")), NexusNetworks.minamoto))
        for (bad in listOf(valid.copy(name = "XOR"), valid.copy(alias = null), valid.copy(aliasBinding = null), valid.copy(aliasBinding = valid.aliasBinding!!.copy(status = "expired")))) {
            assertFalse(NexusAssetDefinitionIdentity.isQualifiedXorDefinition(bad, NexusNetworks.minamoto))
        }
    }
}
