package jp.co.soramitsu.common.nexus

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NexusBalanceValidatorTest {

    @Test
    fun `balance is bound to exact account asset and global scope`() {
        val balance = NexusAssetBalance(
            accountId = MINAMOTO_ADDRESS,
            asset = XOR_DEFINITION,
            assetId = XOR_DEFINITION,
            assetName = NexusAssetDefinitionIdentity.XOR_NAME,
            assetAlias = NexusToriiRoutes.XOR_ASSET_ALIAS,
            quantity = "2.000000000000000001",
            scope = "global",
        )

        assertEquals(
            balance,
            NexusBalanceValidator.exactXorBalance(
                balances = listOf(balance),
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = XOR_DEFINITION,
            ),
        )
        assertNull(
            NexusBalanceValidator.exactXorBalance(
                balances = emptyList(),
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = XOR_DEFINITION,
            )
        )
    }

    @Test
    fun `missing or mismatched balance identity fails closed`() {
        fun assertRejected(balance: NexusAssetBalance) {
            assertThrows(IllegalStateException::class.java) {
                NexusBalanceValidator.exactXorBalance(
                    balances = listOf(balance),
                    network = NexusNetworks.minamoto,
                    accountId = MINAMOTO_ADDRESS,
                    assetDefinitionId = XOR_DEFINITION,
                )
            }
        }

        assertRejected(validBalance().copy(accountId = null))
        assertRejected(validBalance().copy(scope = null))
        assertRejected(validBalance().copy(asset = OTHER_XOR_DEFINITION))
        assertRejected(validBalance().copy(assetName = null))
        assertRejected(validBalance().copy(assetAlias = null))
        assertRejected(validBalance().copy(assetAlias = "xor#sora"))
        assertRejected(
            validBalance().copy(
                accountId =
                    "sorauﾛ1NﾍﾖﾁﾘﾗoEuKﾗﾁK2ｴA9ｸxmxBﾈｴDﾋﾐﾐﾅｴjuXvｾﾍｵn5FAXTS3"
            )
        )
        assertThrows(IllegalStateException::class.java) {
            NexusBalanceValidator.exactXorBalance(
                balances = listOf(validBalance(), validBalance()),
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = XOR_DEFINITION,
            )
        }
        assertThrows(IllegalStateException::class.java) {
            NexusBalanceValidator.exactXorBalance(
                balances = emptyList(),
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = NexusToriiRoutes.XOR_ASSET_ALIAS,
            )
        }

        assertEquals(
            validBalance().copy(assetName = "renamed", assetAlias = null),
            NexusBalanceValidator.exactAssetBalance(
                balances = listOf(
                    validBalance().copy(assetName = "renamed", assetAlias = null)
                ),
                network = NexusNetworks.minamoto,
                accountId = MINAMOTO_ADDRESS,
                assetDefinitionId = XOR_DEFINITION,
            ),
        )
    }

    @Test
    fun `asset pages require explicit stable exact counts and bounded termination`() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(
                NexusAssetBalancePage.serializer(),
                """{"has_more":false,"count_mode":"exact","total":0}""",
            )
        }
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(
                NexusAssetBalancePage.serializer(),
                """{"items":[{"asset_id":"$XOR_DEFINITION","quantity":"1"}],"has_more":false,"count_mode":"exact","total":1}""",
            )
        }
        val complete = NexusExactAssetPageProof(
            pageSize = 100,
            maxPages = 20,
            maxItems = 2_000,
        )
        assertTrue(
            complete.accept(
                NexusAssetBalancePage(
                    items = listOf(validBalance()),
                    hasMore = false,
                    countMode = "exact",
                    total = 1,
                )
            )
        )
        assertEquals(listOf(validBalance()), complete.items)

        listOf(
            NexusAssetBalancePage(
                items = listOf(validBalance()),
                hasMore = false,
                countMode = "bounded",
                total = 1,
            ),
            NexusAssetBalancePage(
                items = listOf(validBalance()),
                hasMore = false,
                countMode = "exact",
                total = 2,
            ),
        ).forEach { invalid ->
            assertThrows(IllegalStateException::class.java) {
                NexusExactAssetPageProof(100, 20, 2_000).accept(invalid)
            }
        }

        val drift = NexusExactAssetPageProof(100, 20, 2_000)
        assertFalse(
            drift.accept(
                NexusAssetBalancePage(
                    items = listOf(validBalance()),
                    hasMore = true,
                    countMode = "exact",
                    total = 2,
                )
            )
        )
        assertThrows(IllegalStateException::class.java) {
            drift.accept(
                NexusAssetBalancePage(
                    items = listOf(validBalance().copy(quantity = "2")),
                    hasMore = true,
                    countMode = "exact",
                    total = 3,
                )
            )
        }

        val bounded = NexusExactAssetPageProof(100, 1, 2_000)
        assertEquals(
            "NEXUS_PAGE_LIMIT_EXCEEDED",
            assertThrows(NexusToriiException::class.java) {
                bounded.accept(
                    NexusAssetBalancePage(
                        items = listOf(validBalance()),
                        hasMore = true,
                        countMode = "exact",
                        total = 2,
                    )
                )
            }.safeCode,
        )
    }

    private fun validBalance() = NexusAssetBalance(
        accountId = MINAMOTO_ADDRESS,
        asset = XOR_DEFINITION,
        assetId = XOR_DEFINITION,
        assetName = NexusAssetDefinitionIdentity.XOR_NAME,
        assetAlias = NexusToriiRoutes.XOR_ASSET_ALIAS,
        quantity = "1",
        scope = "global",
    )

    private companion object {
        const val XOR_DEFINITION = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val OTHER_XOR_DEFINITION = "61CtjvNd9T3THAR65GsMVHr82Bjc"
        const val MINAMOTO_ADDRESS =
            "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
    }
}
