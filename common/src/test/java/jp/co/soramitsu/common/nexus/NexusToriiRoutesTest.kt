package jp.co.soramitsu.common.nexus

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusToriiRoutesTest {

    @Test
    fun `Nexus response admission rejects duplicate escaped and lenient JSON`() {
        assertEquals(
            "{\"total\":1,\"has_more\":false}",
            admitNexusJsonResponse(
                "{\"total\":1,\"has_more\":false}".encodeToByteArray()
            ),
        )
        listOf(
            "{\"total\":1,\"total\":2}",
            "{\"total\":1,\"\\u0074otal\":2}",
            "{'total':1}",
            "{total:1}",
            "{\"total\":1,}",
            "{\"value\":\"\\uD800\"}",
            "{\"total\":1.0}",
            "{\"total\":1e0}",
            "{\"total\":1E+0}",
            "{\"total\":-0}",
            "{\"total\":18446744073709551616}",
            "{\"total\":-9223372036854775809}",
        ).forEach { document ->
            val error = assertThrows(NexusToriiException::class.java) {
                admitNexusJsonResponse(document.encodeToByteArray())
            }
            assertEquals("NEXUS_INVALID_RESPONSE", error.safeCode)
        }
        val invalidUtf8 = assertThrows(NexusToriiException::class.java) {
            admitNexusJsonResponse(byteArrayOf(0x7b, 0x22, 0x78, 0x22, 0x3a, 0xff.toByte(), 0x7d))
        }
        assertEquals("NEXUS_INVALID_RESPONSE", invalidUtf8.safeCode)
    }

    @Test
    fun `qualified network uses exact outer response and typed request media`() {
        NexusNetworks.admitted.forEach { network ->
            val mcp = NexusToriiRoutes.mcp(network)
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
        }
        val health = NexusToriiRoutes.health(NexusNetworks.minamoto)
        val submission = NexusToriiRoutes.submitTransaction(NexusNetworks.minamoto)
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
            NexusToriiRoutes.NORITO_TRANSACTION_REQUEST_MEDIA_TYPE,
            NexusToriiRoutes.requestContentType("POST", submission),
        )
        assertEquals(
            NexusToriiRoutes.JSON_RESPONSE_MEDIA_TYPE,
            NexusToriiRoutes.responseAccept(submission),
        )
        listOf(
            { NexusToriiRoutes.health(NexusNetworks.taira) },
            { NexusToriiRoutes.submitTransaction(NexusNetworks.taira) },
        ).forEach { rawTairaRoute ->
            assertEquals(
                "TAIRA_MCP_REQUIRED",
                assertThrows(IllegalArgumentException::class.java) {
                    rawTairaRoute()
                }.message,
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
    fun `routes reject a forged copy of an admitted network`() {
        val forged = NexusNetworks.minamoto.copy(
            toriiBaseUrl = "https://attacker.invalid",
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.health(forged)
        }
        assertEquals("NEXUS_NETWORK_NOT_ADMITTED", error.message)
    }

    @Test
    fun `Taira MCP route is immutable and alternate topology is rejected`() {
        assertEquals(
            TairaTestnetContract.MCP_ENDPOINT,
            NexusToriiRoutes.mcp(NexusNetworks.taira),
        )
        assertEquals(
            "TAIRA_CONTRACT_MISMATCH",
            assertThrows(IllegalArgumentException::class.java) {
                NexusNetworks.taira.copy(toriiBaseUrl = "https://node-1.taira.sora.org")
            }.message,
        )
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
        assertThrows(NexusToriiException::class.java) {
            NexusToriiResponseContract.validateFanout(
                mapOf(
                    "x-iroha-routed-by" to "proxy",
                    "x-iroha-route-lane-id" to "4294967295",
                    "x-iroha-route-dataspace-id" to "18446744073709551615",
                )::get
            )
        }
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    mapOf("x-iroha-routed-by" to "Proxy")::get
                )
            }.also {
                assertEquals(NexusToriiFailureCategory.PROTOCOL, it.category)
            }.safeCode,
        )
        assertEquals(
            "NEXUS_FANOUT_EVIDENCE_MISSING",
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
            "NEXUS_FANOUT_EVIDENCE_MISSING",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    headerValue = mapOf("x-iroha-routed-by" to "proxy")::get,
                    requireFanout = true,
                )
            }.safeCode,
        )

        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    (complete - "x-iroha-routed-by")::get
                )
            }.safeCode,
        )
        NexusToriiResponseContract.validateFanout(
            headerValue = mapOf(
                "x-iroha-routed-by" to "local",
                "x-iroha-route-lane-id" to "0",
                "x-iroha-route-dataspace-id" to "0",
                "x-iroha-fanout-routes-attempted" to "1",
                "x-iroha-fanout-routes-succeeded" to "1",
                "x-iroha-fanout-routes-failed" to "0",
                "x-iroha-fanout-routes-unavailable" to "0",
                "x-iroha-fanout-routes-denied" to "0",
                "x-iroha-fanout-routes-not-found" to "0",
            )::get,
            requireFanout = true,
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    mapOf("x-iroha-route-lane-id" to "1")::get
                )
            }.safeCode,
        )
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
            assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(
                    mapOf(
                        "x-iroha-routed-by" to "local",
                        "x-iroha-route-lane-id" to "1",
                        "x-iroha-route-dataspace-id" to "2",
                    )::get
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
        val unavailable = assertThrows(NexusToriiException::class.java) {
            NexusToriiResponseContract.validateFanout(partial::get)
        }
        assertEquals("NEXUS_FANOUT_ROUTE_UNAVAILABLE", unavailable.safeCode)
        assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, unavailable.category)
        assertEquals(NexusFanoutFailureReason.ROUTE_UNAVAILABLE, unavailable.fanoutDiagnostic?.reason)
        assertEquals(4, unavailable.fanoutDiagnostic?.attemptedRoutes)
        assertEquals(2, unavailable.fanoutDiagnostic?.succeededRoutes)
        assertEquals(2, unavailable.fanoutDiagnostic?.unavailableRoutes)

        val inferredUnavailable = assertThrows(NexusToriiException::class.java) {
            NexusToriiResponseContract.validateFanout(
                (partial - "x-iroha-fanout-first-failure")::get
            )
        }
        assertEquals("NEXUS_FANOUT_ROUTE_UNAVAILABLE", inferredUnavailable.safeCode)
        assertEquals(
            NexusFanoutFailureReason.ROUTE_UNAVAILABLE,
            inferredUnavailable.fanoutDiagnostic?.reason,
        )
        listOf(
            Triple("permission_denied", "NEXUS_FANOUT_PERMISSION_DENIED", "denied"),
            Triple("not_found", "NEXUS_FANOUT_ROUTE_NOT_FOUND", "not-found"),
            Triple("error", "NEXUS_FANOUT_UPSTREAM_ERROR", "generic"),
            Triple("new_server_failure", "NEXUS_FANOUT_UNKNOWN_FAILURE", "generic"),
        ).forEach { (wireReason, safeCode, counter) ->
            val reasonHeaders = partial.toMutableMap().apply {
                this["x-iroha-fanout-first-failure"] = wireReason
                this["x-iroha-fanout-routes-unavailable"] = "0"
                this["x-iroha-fanout-routes-denied"] = if (counter == "denied") "2" else "0"
                this["x-iroha-fanout-routes-not-found"] =
                    if (counter == "not-found") "2" else "0"
            }
            val failure = assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(reasonHeaders::get)
            }
            assertEquals(safeCode, failure.safeCode)
            assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, failure.category)
        }
        listOf(
            partial + ("x-iroha-fanout-routes-unavailable" to "0"),
            partial + ("x-iroha-fanout-first-failure" to "permission_denied"),
            partial + ("x-iroha-fanout-first-failure" to "not_found"),
            partial + ("x-iroha-fanout-first-failure" to "error"),
            partial + ("x-iroha-fanout-first-failure" to "new_server_failure"),
        ).forEach { contradictory ->
            val failure = assertThrows(NexusToriiException::class.java) {
                NexusToriiResponseContract.validateFanout(contradictory::get)
            }
            assertEquals("NEXUS_FANOUT_HEADERS_INVALID", failure.safeCode)
            assertEquals(NexusToriiFailureCategory.PROTOCOL, failure.category)
        }
        assertEquals(
            "NEXUS_FANOUT_HEADERS_INVALID",
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
    fun `HTTP error envelope preserves deployment health without fanout headers`() {
        val json = Json { }
        val unavailable = NexusToriiHttpErrorContract.deploymentHealthFailure(
            httpStatus = 503,
            rejectCode = null,
            body = """{"code":"route_unavailable","message":"no route"}"""
                .encodeToByteArray(),
            json = json,
        )
        assertEquals("NEXUS_FANOUT_ROUTE_UNAVAILABLE", unavailable?.safeCode)
        assertEquals(503, unavailable?.httpStatus)
        assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, unavailable?.category)
        assertEquals(
            NexusFanoutFailureReason.ROUTE_UNAVAILABLE,
            unavailable?.fanoutDiagnostic?.reason,
        )

        val denied = NexusToriiHttpErrorContract.deploymentHealthFailure(
            httpStatus = 403,
            rejectCode = "permission_denied",
            body = ByteArray(0),
            json = json,
        )
        assertEquals("NEXUS_FANOUT_PERMISSION_DENIED", denied?.safeCode)

        val marker = NexusToriiHttpErrorContract.deploymentHealthFailure(
            httpStatus = 503,
            rejectCode = null,
            body = "route_unavailable".encodeToByteArray(),
            json = json,
        )
        assertEquals("NEXUS_FANOUT_ROUTE_UNAVAILABLE", marker?.safeCode)

        val mismatch = NexusToriiHttpErrorContract.deploymentHealthFailure(
            httpStatus = 503,
            rejectCode = "permission_denied",
            body = """{"code":"route_unavailable","message":"no route"}"""
                .encodeToByteArray(),
            json = json,
        )
        assertEquals("NEXUS_HTTP_ERROR_IDENTITY_MISMATCH", mismatch?.safeCode)
        assertEquals(NexusToriiFailureCategory.PROTOCOL, mismatch?.category)

        listOf(502, 503).forEach { status ->
            val ingress = NexusToriiHttpErrorContract.deploymentHealthFailure(
                httpStatus = status,
                rejectCode = null,
                body = ByteArray(0),
                json = json,
            )
            assertEquals("NEXUS_PUBLIC_INGRESS_UNAVAILABLE", ingress?.safeCode)
            assertEquals(status, ingress?.httpStatus)
            assertEquals(NexusToriiFailureCategory.DEPLOYMENT_HEALTH, ingress?.category)
        }

        assertNull(
            NexusToriiHttpErrorContract.deploymentHealthFailure(
                httpStatus = 422,
                rejectCode = null,
                body = """{"code":"invalid_request","message":"bad input"}"""
                    .encodeToByteArray(),
                json = json,
            )
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
            NexusAssetDefinitionIdentity.isQualifiedXorDefinition(
                definition,
                NexusNetworks.taira,
            )
        )
        assertTrue(
            NexusAssetDefinitionIdentity.isQualifiedXorDefinition(
                NexusAssetDefinition(id = TAIRA_XOR_ASSET_DEFINITION_ID),
                NexusNetworks.taira,
            )
        )
        assertFalse(
            NexusAssetDefinitionIdentity.isQualifiedXorDefinition(
                NexusAssetDefinition(id = "61CtjvNd9T3THAR65GsMVHr82Bjc"),
                NexusNetworks.taira,
            )
        )
        assertFalse(
            NexusAssetDefinitionIdentity.isQualifiedXorDefinition(
                definition.copy(
                    aliasBinding = definition.aliasBinding?.copy(
                        status = "leased_grace"
                    )
                ),
                NexusNetworks.taira,
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
            NexusToriiRoutes.transactionStatus(NexusNetworks.minamoto, "not-a-hash")
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusToriiRoutes.transactionStatus(NexusNetworks.minamoto, "0".repeat(64))
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
