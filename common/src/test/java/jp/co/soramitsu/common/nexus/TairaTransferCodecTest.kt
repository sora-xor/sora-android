package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class TairaTransferCodecTest {
    @Test fun `live canary receipt hash validates its checksum and preserves exact transaction`() {
        val literal = "hash:4A48384AD05A8E1669BAF4CD0ED83773F006F178E486AD421BCF2ABCADD658A1#EC45"
        assertEquals("4a48384ad05a8e1669baf4cd0ed83773f006f178e486ad421bcf2abcadd658a1", NexusTransactionHash.normalized(literal))
        assertNull(NexusTransactionHash.normalized(literal.dropLast(1) + "4"))
        assertNull(NexusTransactionHash.normalized(literal.lowercase()))
        assertEquals(TairaTestnetContract.NETWORK_HEX, NexusTransactionHash.normalized(TairaTestnetContract.NETWORK_LITERAL))
    }

    @Test fun `payload and both hashes match current upstream golden fixture`() {
        val fixture = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/nexus/nexus_connect_transfer_v1.json")).readText()).jsonObject
        val input = fixture.getValue("transfer_input").jsonObject
        val expected = fixture.getValue("expected").jsonObject
        fun text(key: String) = input.getValue(key).jsonPrimitive.content
        val payload = TairaTransferCodec.payload(
            TairaTransferCodec.unhex(text("network_id").substring(5, 69)),
            TairaTransferCodec.unhex(IrohaAddressCodec.parse(text("authority"), 369).publicKeyHex),
            TairaTransferCodec.unhex(IrohaAddressCodec.parse(text("destination_account_id"), 369).publicKeyHex),
            text("source_asset_id").substringBefore('#'), text("quantity"), text("creation_time_ms").toLong(),
            text("ttl_ms").toLong(), text("nonce").toLong(), metadata = mapOf("purpose" to "nexus-app-fixture"),
        )
        assertEquals(expected.getValue("payload_bytes_hex").jsonPrimitive.content, TairaTransferCodec.hex(payload))
        assertEquals(expected.getValue("payload_hash_hex").jsonPrimitive.content, TairaTransferCodec.hex(TairaTransferCodec.signingMessage(payload)))
        assertEquals(expected.getValue("signed_transaction_hash_hex").jsonPrimitive.content, TairaTransferCodec.transactionHash(payload))
    }

    @Test fun `signatures bind exact payload and key before envelope construction`() {
        val seed = ByteArray(32) { (it + 1).toByte() }
        val key = org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        val payload = byteArrayOf(1, 2, 3)
        val signature = TairaTransferCodec.sign(seed, TairaTransferCodec.signingMessage(payload))
        assertTrue(TairaTransferCodec.envelope(payload, signature, key).isNotEmpty())
        assertThrows(IllegalArgumentException::class.java) { TairaTransferCodec.envelope(byteArrayOf(1, 2, 4), signature, key) }
        seed.fill(0)
    }

    @Test fun `quantity bounds and asset checksum are enforced`() {
        assertEquals("1.01", TairaTransferCodec.canonicalQuantity("1.010000000"))
        assertEquals("0", TairaTransferCodec.canonicalQuantity("0.000", true))
        listOf("0", "01", "1e3", "1.0000000001", "-1").forEach { amount ->
            assertThrows(IllegalArgumentException::class.java) { TairaTransferCodec.canonicalQuantity(amount) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            TairaTransferCodec.instructionJSON(ByteArray(32) { 1 }, ByteArray(32) { 2 }, "6TEAJqbb8oEPmLncoNiMRbLEK6tx", "1")
        }
    }

    @Test fun `authenticated quote binds body path method timestamp nonce and genesis`() {
        val one = TairaSigningClient.authMessage("{}".toByteArray(), 12, "abc")
        val two = TairaSigningClient.authMessage("{ }".toByteArray(), 12, "abc")
        assertFalse(one.contentEquals(two))
        val prefix = "iroha.app.request.network.v1\u0000".toByteArray() + TairaTransferCodec.unhex(TairaTestnetContract.NETWORK_HEX)
        assertTrue(one.copyOf(prefix.size).contentEquals(prefix))
        assertTrue(one.toString(Charsets.UTF_8).endsWith("\n12\nabc"))
    }
}
