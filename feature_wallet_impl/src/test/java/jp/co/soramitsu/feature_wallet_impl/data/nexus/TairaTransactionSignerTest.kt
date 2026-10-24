package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.*
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.nexus.*
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TairaTransactionSignerTest {
    private val phrase = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private val credentials = mockk<CredentialsRepository>()
    private val users = mockk<UserRepository>()
    private val wallet = mockk<SoraAccount>()
    private var now = 1_700_000_000_000L
    private var requests = 0
    private val client = TairaSigningClient(transport = { request ->
        requests++
        val body = Json.parseToJsonElement(checkNotNull(request.body).toString(Charsets.UTF_8)).jsonObject.getValue("payload").jsonObject
        assertFalse(request.body!!.toString(Charsets.UTF_8).contains(phrase))
        assertTrue(request.headers.getValue("X-Iroha-Account").startsWith("0x"))
        val authority = body.getValue("authority").jsonPrimitive.content
        buildJsonObject {
            putJsonObject("intent") { put("payer", "authority"); putJsonObject("value") { put("charge_limits", JsonArray(emptyList())); put("gas_limit", JsonNull) } }
            put("components", JsonArray(emptyList())); put("capacities", JsonArray(emptyList()))
            putJsonObject("observation") { put("ledger_time_ms", now); put("next_block_height", 3); put("route_dataspace_id", 0) }
            putJsonObject("decision") { put("status", "accepted"); putJsonObject("value") { put("program_revision", JsonNull); putJsonObject("debit_source") { put("kind", "account"); put("value", authority) } } }
        }.toString().toByteArray()
    }, now = { now })
    private fun request(): NexusTransferSigningRequest {
        val derived = IrohaKeyDerivation.derive(phrase, NexusNetworks.taira)
        return try {
            NexusTransferSigningRequest(NexusNetworks.taira, NetworkAccountLocal("wallet", "taira", TairaTransferCodec.hex(derived.publicKey),
                derived.address, NexusNetworks.taira.derivationPath, 1, true), derived.address, TairaTestnetContract.XOR_ASSET_DEFINITION_ID, "1")
        } finally { derived.destroy() }
    }
    private fun signer(): TairaTransactionSigner {
        every { wallet.substrateAddress } returns "wallet"
        coEvery { users.getSoraAccount("wallet") } returns wallet
        coEvery { credentials.retrieveMnemonic(wallet) } returns phrase
        coEvery { credentials.isExplicitWatchOnly(wallet) } returns false
        return TairaTransactionSigner(credentials, users, client) { now }
    }
    @Test fun `requoted draft is stable and can be signed only once`() = runBlocking {
        val signer = signer(); val request = request()
        val quote = signer.quote(request)
        now += 1_000
        assertEquals(quote, signer.quote(request))
        assertEquals("0", quote.fee)
        val signed = signer.sign(request, quote)
        assertEquals(64, signed.transactionHash.length)
        assertTrue(signed.noritoBytes.size > 100)
        assertEquals(2, requests)
        assertTrue(runCatching { signer.sign(request, quote) }.isFailure)
    }
    @Test fun `changed amount expired quote and production cannot sign`() = runBlocking {
        val signer = signer(); val request = request(); val quote = signer.quote(request)
        assertFalse(signer.isQualifiedFor(NexusNetworks.minamoto))
        assertTrue(runCatching { signer.sign(request.copy(amount = "2"), quote) }.isFailure)
        now += 80_000
        assertTrue(runCatching { signer.sign(request, quote) }.isFailure)
        assertTrue(runCatching { signer.quote(request.copy(network = NexusNetworks.minamoto)) }.isFailure)
    }
    @Test fun `watch only wallet cannot authenticate fee request`() = runBlocking {
        val signer = signer(); coEvery { credentials.isExplicitWatchOnly(wallet) } returns true
        assertTrue(runCatching { signer.quote(request()) }.isFailure)
        assertEquals(0, requests)
        coVerify(exactly = 0) { credentials.retrieveMnemonic(any()) }
    }
}
