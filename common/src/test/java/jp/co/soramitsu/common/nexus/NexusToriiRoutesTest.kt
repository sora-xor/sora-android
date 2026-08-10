package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusToriiRoutesTest {

    @Test
    fun `qualified network uses exact outer response and typed request media`() {
        listOf(NexusNetworks.minamoto).forEach { network ->
            val health = NexusToriiRoutes.health(network)
            val mcp = NexusToriiRoutes.mcp(network)
            val submission = NexusToriiRoutes.submitTransaction(network)
            assertEquals(
                NexusToriiRoutes.HEALTH_RESPONSE_MEDIA_TYPE,
                NexusToriiRoutes.responseAccept(health),
            )
            assertNull(NexusToriiRoutes.requestContentType("GET", health))
            assertTrue(
                NexusToriiRoutes.isExpectedResponseContentType(
                    health,
                    "text/plain; charset=UTF-8",
                )
            )
            assertFalse(
                NexusToriiRoutes.isExpectedResponseContentType(
                    health,
                    "application/json",
                )
            )
            assertEquals(
                NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,
                NexusToriiRoutes.responseAccept(mcp),
            )
            assertEquals(
                NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,
                NexusToriiRoutes.requestContentType("POST", mcp),
            )
            assertTrue(
                NexusToriiRoutes.isExpectedResponseContentType(
                    mcp,
                    "application/json",
                )
            )
            assertFalse(
                NexusToriiRoutes.isExpectedResponseContentType(
                    mcp,
                    "text/plain",
                )
            )
            assertFalse(NexusToriiRoutes.isExpectedResponseContentType(mcp, null))
            assertFalse(
                NexusToriiRoutes.isExpectedResponseContentType(
                    mcp,
                    "application/json; profile=unexpected",
                )
            )
            assertFalse(
                NexusToriiRoutes.isExpectedResponseContentType(
                    mcp,
                    "application/json, text/plain",
                )
            )
            assertEquals(
                NexusToriiRoutes.NORITO_TRANSACTION_REQUEST_MEDIA_TYPE,
                NexusToriiRoutes.requestContentType("POST", submission),
            )
            assertEquals(
                NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,
                NexusToriiRoutes.responseAccept(submission),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.requestContentType(
                "POST",
                NexusToriiRoutes.health(NexusNetworks.minamoto),
            )
        }
        assertTrue(NexusToriiRoutes.isHealthyResponse("Healthy"))
        assertFalse(NexusToriiRoutes.isHealthyResponse("Healthy\n"))
        assertFalse(NexusToriiRoutes.isHealthyResponse("healthy"))
    }

    @Test
    fun `Taira routes fail before transport when deployment manifest is absent`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.health(NexusNetworks.taira)
        }
        assertEquals("TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED", error.message)
        listOf<(NexusNetwork) -> String>(
            NexusToriiRoutes::mcp,
            NexusToriiRoutes::submitTransaction,
            { network -> NexusToriiRoutes.assetDefinitions(network) },
            { network -> NexusToriiRoutes.assetDefinition(network, "xor#universal") },
        ).forEach { route ->
            assertEquals(
                "TAIRA_DEPLOYMENT_MANIFEST_NOT_QUALIFIED",
                assertThrows(IllegalArgumentException::class.java) {
                    route(NexusNetworks.taira)
                }.message,
            )
        }
    }

    @Test
    fun `partial Torii fanout cannot masquerade as an authoritative success`() {
        NexusToriiResponseContract.validateFanout(headerValue = { null })
        NexusToriiResponseContract.validateFanout(
            mapOf("x-iroha-routed-by" to "proxy")::get
        )
        NexusToriiResponseContract.validateFanout(
            mapOf("x-iroha-routed-by" to "local")::get
        )
        NexusToriiResponseContract.validateFanout(
            mapOf(
                "x-iroha-routed-by" to "proxy",
                "x-iroha-route-lane-id" to "4294967295",
                "x-iroha-route-dataspace-id" to "18446744073709551615",
            )::get
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    mapOf("x-iroha-routed-by" to "Proxy")::get
                )
            }.safeCode,
        )
        assertEquals(
            "NEXUS_PARTIAL_FANOUT",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    headerValue = { null },
                    requireFanout = true,
                )
            }.safeCode,
        )
        val complete = mapOf(
            "x-iroha-routed-by" to "local",
            "x-iroha-fanout-routes-attempted" to "4",
            "x-iroha-fanout-routes-succeeded" to "4",
            "x-iroha-fanout-routes-failed" to "0",
            "x-iroha-fanout-routes-unavailable" to "0",
            "x-iroha-fanout-routes-denied" to "0",
            "x-iroha-fanout-routes-not-found" to "0",
        )
        NexusToriiResponseContract.validateFanout(
            headerValue = complete::get,
            requireFanout = true,
        )
        NexusToriiResponseContract.validateFanout(
            headerValue = (complete + ("x-iroha-routed-by" to "proxy"))::get,
            requireFanout = true,
        )

        assertEquals(
            "NEXUS_PARTIAL_FANOUT",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    headerValue = mapOf("x-iroha-routed-by" to "proxy")::get,
                    requireFanout = true,
                )
            }.safeCode,
        )

        assertEquals(
            "NEXUS_PARTIAL_FANOUT",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    (complete - "x-iroha-routed-by")::get
                )
            }.safeCode,
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    (complete + mapOf(
                        "x-iroha-route-lane-id" to "1",
                        "x-iroha-route-dataspace-id" to "2",
                    ))::get
                )
            }.safeCode,
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    mapOf("x-iroha-route-lane-id" to "1")::get
                )
            }.safeCode,
        )
        listOf(
            mapOf(
                "x-iroha-routed-by" to "proxy",
                "x-iroha-route-lane-id" to "01",
                "x-iroha-route-dataspace-id" to "2",
            ),
            mapOf(
                "x-iroha-routed-by" to "proxy",
                "x-iroha-route-lane-id" to "4294967296",
                "x-iroha-route-dataspace-id" to "2",
            ),
            mapOf(
                "x-iroha-routed-by" to "proxy",
                "x-iroha-route-lane-id" to "1",
                "x-iroha-route-dataspace-id" to "18446744073709551616",
            ),
        ).forEach { invalidRoute ->
            assertEquals(
                "NEXUS_FANOUT_HEADERS_INVALID",
                assertThrows(NexusToriiException::class.java) {
                    NexusToriiResponseContract.validateFanout(invalidRoute::get)
                }.safeCode,
            )
        }

        val partial = complete + mapOf(
            "x-iroha-fanout-first-failure" to "route_unavailable",
            "x-iroha-fanout-routes-succeeded" to "2",
            "x-iroha-fanout-routes-failed" to "2",
            "x-iroha-fanout-routes-unavailable" to "2",
        )
        assertEquals(
            "NEXUS_PARTIAL_FANOUT",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(partial::get)
            }.safeCode,
        )
        assertEquals(
            "NEXUS_PARTIAL_FANOUT",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    mapOf("x-iroha-fanout-routes-attempted" to "4")::get
                )
            }.safeCode,
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    (complete + ("x-iroha-fanout-routes-attempted" to "04"))::get
                )
            }.safeCode,
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    (complete + mapOf(
                        "x-iroha-fanout-routes-attempted" to "1025",
                        "x-iroha-fanout-routes-succeeded" to "1025",
                    ))::get
                )
            }.safeCode,
        )
    }

    @Test
    fun `routes Minamoto account assets with a canonical network-scoped address`() {
        assertEquals(
            "https://minamoto.sora.org/v1/accounts/$ENCODED_MINAMOTO/assets" +
                "?limit=25&offset=50&count_mode=exact&scope=global" +
                "&asset=$TAIRA_XOR_ASSET_DEFINITION_ID",
            NexusToriiRoutes.accountAssets(
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                limit = 25,
                offset = 50,
                asset = TAIRA_XOR_ASSET_DEFINITION_ID,
            )
        )
        assertEquals(
            "https://minamoto.sora.org/v1/accounts/$ENCODED_MINAMOTO/transactions" +
                "?limit=25&offset=50&asset_id=$TAIRA_XOR_ASSET_DEFINITION_ID" +
                "&count_mode=exact",
            NexusToriiRoutes.accountTransactions(
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = TAIRA_XOR_ASSET_DEFINITION_ID,
                limit = 25,
                offset = 50,
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.accountTransactions(
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = NexusToriiRoutes.XOR_ASSET_ALIAS,
            )
        }
        assertEquals(
            "https://minamoto.sora.org/v1/assets/definitions/xor%23universal",
            NexusToriiRoutes.assetDefinition(
                NexusNetworks.minamoto,
                NexusToriiRoutes.XOR_ASSET_ALIAS,
            ),
        )
        assertTrue(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(
                TAIRA_XOR_ASSET_DEFINITION_ID
            )
        )
        assertFalse(
            NexusAssetDefinitionIdentity.hasCanonicalWireShape(
                NexusToriiRoutes.XOR_ASSET_ALIAS
            )
        )
        val definition = NexusAssetDefinition(
            id = TAIRA_XOR_ASSET_DEFINITION_ID,
            name = "xor",
            alias = NexusToriiRoutes.XOR_ASSET_ALIAS,
            aliasBinding = NexusAssetAliasBinding(
                alias = NexusToriiRoutes.XOR_ASSET_ALIAS,
                status = "permanent",
                boundAtMillis = 0,
            ),
        )
        assertTrue(
            NexusAssetDefinitionIdentity.isQualifiedXorDefinition(definition)
        )
        assertFalse(
            NexusAssetDefinitionIdentity.isQualifiedXorDefinition(
                definition.copy(
                    aliasBinding = definition.aliasBinding?.copy(
                        status = "leased_grace"
                    )
                )
            )
        )
    }

    @Test
    fun `rejects a Taira address on a Minamoto route`() {
        assertThrows(IrohaAddressCodec.AddressException::class.java) {
            NexusToriiRoutes.account(
                network = NexusNetworks.minamoto,
                accountId = TAIRA_ADDRESS,
            )
        }
    }

    @Test
    fun `transaction status rejects malformed and all-zero hashes`() {
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.transactionStatus(NexusNetworks.taira, "not-a-hash")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.transactionStatus(NexusNetworks.taira, "0".repeat(64))
        }
        assertEquals(
            "ab".repeat(32),
            NexusTransactionHash.normalized("0X" + "AB".repeat(32)),
        )
    }

    @Test
    fun `terminal pipeline status requires authoritative global state`() {
        val rejected = NexusTransactionStatus(
            hash = "ab".repeat(32),
            status = NexusTransactionStatusValue(kind = "rejected"),
            scope = "global",
            resolvedFrom = "cache",
        )

        assertFalse(rejected.hasAuthoritativeGlobalResolution)
        assertTrue(
            rejected.copy(resolvedFrom = "state")
                .hasAuthoritativeGlobalResolution
        )
        assertFalse(
            rejected.copy(scope = "account", resolvedFrom = "state")
                .hasAuthoritativeGlobalResolution
        )
    }

    @Test
    fun `applied pipeline status requires state and a positive block`() {
        val applied = NexusTransactionStatus(
            hash = "ab".repeat(32),
            status = NexusTransactionStatusValue(
                kind = "applied",
                blockHeight = 1,
            ),
            scope = "global",
            resolvedFrom = "state",
        )

        assertTrue(applied.hasAuthoritativeGlobalResolution)
        assertFalse(
            applied.copy(resolvedFrom = "cache")
                .hasAuthoritativeGlobalResolution
        )
        assertFalse(
            applied.copy(status = applied.status.copy(blockHeight = 0))
                .hasAuthoritativeGlobalResolution
        )
    }

    @Test
    fun `committed pipeline status requires state positive block and no rejection`() {
        val committed = NexusTransactionStatus(
            hash = "ab".repeat(32),
            status = NexusTransactionStatusValue(
                kind = "committed",
                blockHeight = 1,
            ),
            scope = "global",
            resolvedFrom = "state",
        )

        assertTrue(committed.hasAuthoritativeGlobalResolution)
        assertFalse(committed.copy(resolvedFrom = "queue").hasAuthoritativeGlobalResolution)
        assertFalse(committed.copy(resolvedFrom = "cache").hasAuthoritativeGlobalResolution)
        assertFalse(
            committed.copy(status = committed.status.copy(blockHeight = 0))
                .hasAuthoritativeGlobalResolution
        )
        assertFalse(
            committed.copy(
                status = committed.status.copy(rejectionReason = JsonPrimitive("rejected"))
            ).hasAuthoritativeGlobalResolution
        )
    }

    private companion object {
        const val MINAMOTO_ADDRESS =
            "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val TAIRA_ADDRESS =
            "testuﾛ1Q1ﾘﾚxgﾁﾃﾀdRZﾀWｿfXLGﾜﾘPﾐﾉﾉkﾃ7ﾖｶBｹssﾙﾈjｷｹUNYWHP"
        const val ENCODED_MINAMOTO =
            "sorau%EF%BE%9B1Pc%EF%BE%852%EF%BE%97t%EF%BE%89a%EF%BE%98L%EF%BE%95" +
                "%EF%BD%BD2M%E3%83%B1%EF%BE%90%EF%BE%8E%EF%BD%B3%EF%BE%93%E3%83%B1" +
                "%EF%BD%B7%EF%BE%86%EF%BD%B2M%EF%BE%92S%EF%BE%8F%EF%BD%B1%E3%83%B1" +
                "%EF%BD%B7J%E3%83%B1FmJ%EF%BE%87Ms6YN687Y"
        const val TAIRA_XOR_ASSET_DEFINITION_ID =
            "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
    }
}
