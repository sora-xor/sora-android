package jp.co.soramitsu.common.nexus

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NexusAccountTransactionProofTest {

    @Test
    fun `proof accepts only an exact successful transaction from the account authority`() {
        assertEquals(
            NexusAccountTransactionProofResult.FOUND,
            proof().accept(
                page(
                    1,
                    item(hash = TARGET_HASH),
                )
            ),
        )
        assertEquals(
            NexusAccountTransactionProofResult.ABSENT,
            proof().accept(
                page(
                    1,
                    item(hash = OTHER_HASH),
                )
            ),
        )

        listOf(
            item(hash = TARGET_HASH, succeeded = false),
            item(hash = TARGET_HASH, authority = OTHER_AUTHORITY),
            item(hash = TARGET_HASH, authority = null),
        ).forEach { invalid ->
            assertEquals(
                "NEXUS_TRANSACTION_HISTORY_IDENTITY_MISMATCH",
                assertThrows(IllegalStateException::class.java) {
                    proof().accept(page(1, invalid))
                }.message,
            )
        }
    }

    @Test
    fun `proof rejects exact-count drift and impossible page boundaries`() {
        assertThrows(SerializationException::class.java) {
            Json.decodeFromString(
                NexusAccountTransactionPage.serializer(),
                """{"total":0,"has_more":false,"count_mode":"exact"}""",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            NexusAccountTransactionProof(
                network = NexusNetworks.minamoto,
                accountId = ACCOUNT,
                transactionHash = "0".repeat(64),
                pageSize = 100,
                maxPages = 20,
            )
        }
        val drift = proof()
        assertEquals(
            NexusAccountTransactionProofResult.CONTINUE,
            drift.accept(page(2, item(hash = OTHER_HASH))),
        )
        assertEquals(
            "NEXUS_TRANSACTION_HISTORY_INVALID",
            assertThrows(IllegalStateException::class.java) {
                drift.accept(page(3, item(hash = THIRD_HASH)))
            }.message,
        )
        listOf(
            NexusAccountTransactionPage(
                items = listOf(item(hash = TARGET_HASH)),
                total = 1,
                hasMore = false,
                countMode = "bounded",
            ),
            NexusAccountTransactionPage(
                items = listOf(item(hash = TARGET_HASH)),
                total = 1,
                hasMore = true,
                countMode = "exact",
            ),
        ).forEach { invalidMetadata ->
            assertEquals(
                "NEXUS_TRANSACTION_HISTORY_INVALID",
                assertThrows(IllegalStateException::class.java) {
                    proof().accept(invalidMetadata)
                }.message,
            )
        }

        assertEquals(
            "NEXUS_TRANSACTION_HISTORY_INVALID",
            assertThrows(IllegalStateException::class.java) {
                proof(pageSize = 1).accept(
                    page(
                        2,
                        item(hash = OTHER_HASH),
                        item(hash = THIRD_HASH),
                    )
                )
            }.message,
        )
    }

    @Test
    fun `proof distinguishes repeated pages from duplicate hashes across different pages`() {
        val repeated = proof()
        val firstPage = page(3, item(hash = OTHER_HASH))
        assertEquals(
            NexusAccountTransactionProofResult.CONTINUE,
            repeated.accept(firstPage),
        )
        assertEquals(
            "NEXUS_TRANSACTION_HISTORY_REPEATED_PAGE",
            assertThrows(IllegalStateException::class.java) {
                repeated.accept(firstPage)
            }.message,
        )

        val duplicate = proof(pageSize = 2)
        assertEquals(
            NexusAccountTransactionProofResult.CONTINUE,
            duplicate.accept(
                page(
                    4,
                    item(hash = OTHER_HASH),
                    item(hash = THIRD_HASH),
                )
            ),
        )
        assertEquals(
            "NEXUS_TRANSACTION_HISTORY_DUPLICATE_HASH",
            assertThrows(IllegalStateException::class.java) {
                duplicate.accept(
                    NexusAccountTransactionPage(
                        items = listOf(
                            item(hash = OTHER_HASH),
                            item(hash = FOURTH_HASH),
                        ),
                        total = 4,
                        hasMore = false,
                        countMode = "exact",
                    )
                )
            }.message,
        )
    }

    @Test
    fun `proof rejects empty continuation and fails closed at its page bound`() {
        assertEquals(
            "NEXUS_TRANSACTION_HISTORY_EMPTY_PAGE",
            assertThrows(IllegalStateException::class.java) {
                proof().accept(page(1))
            }.message,
        )

        assertEquals(
            "NEXUS_TRANSACTION_HISTORY_PAGE_LIMIT_EXCEEDED",
            assertThrows(NexusToriiException::class.java) {
                proof(maxPages = 1).accept(
                    page(2, item(hash = OTHER_HASH))
                )
            }.safeCode,
        )
    }

    private fun proof(
        pageSize: Int = 100,
        maxPages: Int = 20,
    ) = NexusAccountTransactionProof(
        network = NexusNetworks.minamoto,
        accountId = ACCOUNT,
        transactionHash = TARGET_HASH,
        pageSize = pageSize,
        maxPages = maxPages,
    )

    private fun page(
        total: Long,
        vararg items: NexusAccountTransactionItem,
    ) = NexusAccountTransactionPage(
        items = items.toList(),
        total = total,
        hasMore = items.size.toLong() < total,
        countMode = "exact",
    )

    private fun item(
        hash: String,
        authority: String? = ACCOUNT,
        succeeded: Boolean = true,
    ) = NexusAccountTransactionItem(
        authority = authority,
        timestampMillis = 1,
        entrypointHash = hash,
        succeeded = succeeded,
    )

    private companion object {
        val TARGET_HASH = "aa".repeat(32)
        val OTHER_HASH = "bb".repeat(32)
        val THIRD_HASH = "cc".repeat(32)
        val FOURTH_HASH = "dd".repeat(32)
        const val ACCOUNT =
            "sorauﾛ1Pcﾅ2ﾗtﾉaﾘLﾕｽ2MヱﾐﾎｳﾓヱｷﾆｲMﾒSﾏｱヱｷJヱFmJﾇMs6YN687Y"
        const val OTHER_AUTHORITY =
            "sorauﾛ1NﾍﾖﾁﾘﾗoEuKﾗﾁK2ｴA9ｸxmxBﾈｴDﾋﾐﾐﾅｴjuXvｾﾍｵn5FAXTS3"
    }
}
