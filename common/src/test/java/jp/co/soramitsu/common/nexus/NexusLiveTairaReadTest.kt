package jp.co.soramitsu.common.nexus

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in, anonymous public reads only. Never signs, creates accounts or submits transactions. */
class NexusLiveTairaReadTest {
    @Test
    fun `canonical Taira HTTPS checkpoint binds a fresh applied block`() = runBlocking {
        assumeTrue(System.getenv("SORA_LIVE_TAIRA_READS") == "1")
        val checkpoint = TairaSigningClient().checkpoint()
        assertTrue(checkpoint.height > 0)
        assertEquals(checkpoint.hash, NexusTransactionHash.normalized(checkpoint.hash))
    }

    @Test
    fun `canonical public Taira serves health XOR balance and account movements`() = runBlocking {
        assumeTrue(System.getenv("SORA_LIVE_TAIRA_READS") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val fixture = json.parseToJsonElement(checkNotNull(javaClass.getResource("/nexus/taira-2026-10-13/balance.json")).readText()).jsonObject
        val account = fixture.getValue("result").jsonObject.getValue("structuredContent").jsonObject.getValue("body").jsonObject
            .getValue("items").jsonArray.single().jsonObject.getValue("account_id").jsonPrimitive.content
        val client = NexusToriiClient(json)
        assertEquals("Healthy", client.health(NexusNetworks.taira))
        assertEquals(TairaTestnetContract.XOR_ASSET_DEFINITION_ID, client.getXorBalance(NexusNetworks.taira, account).asset)
        val page = client.committedXorTransferPage(NexusNetworks.taira, account, TairaTestnetContract.XOR_ASSET_DEFINITION_ID, null)
        assertTrue(page.items.size <= 100)
    }
}
