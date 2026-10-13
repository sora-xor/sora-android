package jp.co.soramitsu.common.nexus

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TairaToriiQueriesTest {
    private val network = NexusNetworks.taira
    private val account = IrohaAddressCodec.encode(ByteArray(32) { 1 }, 369)
    private val asset = TairaTestnetContract.XOR_ASSET_DEFINITION_ID

    @Test
    fun `asset collection uses an encoded public account and current query contract`() {
        val uri = URI(NexusToriiRoutes.accountAssetPage(network, account, asset, 25, "next_page-1"))

        assertEquals("https", uri.scheme)
        assertEquals("taira.sora.org", uri.host)
        assertEquals("/v1/accounts/$account/assets", uri.path)
        assertTrue(uri.rawPath.all { it.code < 128 })
        assertEquals(
            mapOf(
                "filter" to "asset = \"$asset\" and scope = \"global\"",
                "limit" to "25",
                "include_total" to "true",
                "cursor" to "next_page-1",
            ),
            query(uri),
        )
    }

    @Test
    fun `history queries successful XOR transfers without retired pagination fields`() {
        val uri = URI(NexusToriiRoutes.accountHistory(network, account, asset))

        assertEquals("/v1/accounts/$account/history", uri.path)
        assertEquals(
            mapOf(
                "filter" to "asset_definition_id = \"$asset\" and source = \"transaction\"" +
                    " and type = \"TRANSFER\" and status = \"SUCCESS\" and result_ok = true",
                "limit" to "100",
            ),
            query(uri),
        )
    }

    @Test
    fun `history hash filter canonicalizes the hash and preserves the continuation cursor`() {
        val uri = URI(NexusToriiRoutes.accountHistory(network, account, asset,
            limit = 1, cursor = "page_2-", transactionHash = "0X" + "AB".repeat(32)))
        val query = query(uri)

        assertTrue(query.getValue("filter").endsWith(" and tx_hash = \"${"ab".repeat(32)}\""))
        assertEquals("page_2-", query["cursor"])
        assertEquals("1", query["limit"])
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.accountHistory(network, account, asset, transactionHash = "x\" or true")
        }
    }

    @Test
    fun `collection queries reject invalid cursors and page sizes before requesting`() {
        listOf("", "a b", "a&limit=500", "a/b", "a=", "a".repeat(4097)).forEach { cursor ->
            assertThrows(IllegalArgumentException::class.java) {
                NexusToriiRoutes.accountAssetPage(network, account, asset, cursor = cursor)
            }
            assertThrows(IllegalArgumentException::class.java) {
                NexusToriiRoutes.accountHistory(network, account, asset, cursor = cursor)
            }
        }
        listOf(0, 501).forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) {
                NexusToriiRoutes.accountAssetPage(network, account, asset, limit = limit)
            }
        }
        listOf(0, 101).forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) {
                NexusToriiRoutes.accountHistory(network, account, asset, limit = limit)
            }
        }
    }

    @Test
    fun `current query contract binds the admitted Taira network and pinned asset`() {
        listOf("xor#universal", "another\" or result_ok = true").forEach { selector ->
            assertThrows(IllegalArgumentException::class.java) {
                NexusToriiRoutes.accountAssetPage(network, account, selector)
            }
            assertThrows(IllegalArgumentException::class.java) {
                NexusToriiRoutes.accountHistory(network, account, selector)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.accountAssetPage(NexusNetworks.minamoto, account, asset)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.accountHistory(NexusNetworks.minamoto, account, asset)
        }
        val otherNetworkAccount = IrohaAddressCodec.encode(ByteArray(32) { 1 }, 753)
        assertThrows(IrohaAddressCodec.AddressException::class.java) {
            NexusToriiRoutes.accountHistory(network, otherNetworkAccount, asset)
        }
    }

    @Test
    fun `direct definition and global pipeline status use canonical public Torii paths`() {
        assertEquals("https://taira.sora.org/v1/assets/definitions/$asset",
            NexusToriiRoutes.assetDefinition(network, asset))
        assertEquals("https://taira.sora.org/v1/pipeline/transactions/status?hash=${"ab".repeat(32)}&scope=global",
            NexusToriiRoutes.transactionStatus(network, "ab".repeat(32)))
    }

    @Test
    fun `public provenance exception is restricted to canonical Taira HTTPS origin`() {
        assertTrue(NexusToriiRoutes.isPublicTairaRoute(NexusToriiRoutes.accountHistory(network, account, asset)))
        assertTrue(NexusToriiRoutes.isPublicTairaRoute(TairaTestnetContract.MCP_ENDPOINT))
        listOf(
            "http://taira.sora.org/v1/accounts",
            "https://taira.sora.org.attacker.invalid/v1/accounts",
            "https://attacker.invalid@taira.sora.org/v1/accounts",
            "https://taira.sora.org:444/v1/accounts",
            "https://taira.sora.org:443/v1/accounts",
            "https://node-1.taira.sora.org/v1/accounts",
            "https://minamoto.sora.org/v1/accounts",
            "https://taira.sora.org/v1/accounts#fragment",
            "not a URL",
        ).forEach { url -> assertFalse(url, NexusToriiRoutes.isPublicTairaRoute(url)) }
    }

    private fun query(uri: URI): Map<String, String> = uri.rawQuery.split('&').associate { field ->
        val (key, value) = field.split('=', limit = 2)
        URLDecoder.decode(key, Charsets.UTF_8.name()) to URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
