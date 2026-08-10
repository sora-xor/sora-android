package jp.co.soramitsu.feature_blockexplorer_api.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

typealias PiHistoryBlockHashResolver = suspend (Int) -> String

/**
 * Opaque outside this module and issued only after the supplied canonical RPC resolver succeeds.
 */
class PiCanonicalHistoryValidationReceipt internal constructor(
    internal val elements: List<IndexerHistoryElement>,
    internal val expectedAddress: String,
    internal val health: PiIndexerHealth,
    internal val maximumRecords: Int,
)

/**
 * Binds PI account history to a fresh, qualified PI checkpoint and to canonical SORA2 RPC.
 *
 * The indexer's account filter is an OR across [IndexerHistoryElement.address],
 * [IndexerHistoryElement.dataFrom], and [IndexerHistoryElement.dataTo], so at least one of those
 * exact fields must identify the requested account. Substring and case-insensitive matches are not
 * accepted. RPC checks are de-duplicated by block height and executed in bounded batches.
 */
object PiHistoryCheckpointValidator {
    private const val DEFAULT_MAXIMUM_RECORDS = 100
    private const val ABSOLUTE_MAXIMUM_RECORDS = 1_000
    private const val MAXIMUM_CONCURRENT_RPC_CHECKS = 8
    private const val MAXIMUM_ADDRESS_BYTES = 512
    private const val MAXIMUM_HISTORY_IDENTIFIER_BYTES = 512
    private const val MAXIMUM_QUANTITY_BYTES = 4_096
    private const val MAXIMUM_CHECKPOINT_DIGITS = 10
    private const val MAXIMUM_TIMESTAMP_DIGITS = 19
    private val unsignedInteger = Regex("^(?:0|[1-9][0-9]*)$")
    private val substrateHash = Regex("^0x[0-9a-fA-F]{64}$")
    private const val zeroHash =
        "0x0000000000000000000000000000000000000000000000000000000000000000"

    suspend fun validate(
        elements: List<IndexerHistoryElement>,
        expectedAddress: String,
        health: PiIndexerHealth,
        maximumRecords: Int = DEFAULT_MAXIMUM_RECORDS,
        canonicalBlockHash: PiHistoryBlockHashResolver,
    ): PiCanonicalHistoryValidationReceipt {
        val snapshot = elements.map { element ->
            element.copy(
                data = element.data?.toList(),
                nestedData = element.nestedData?.map { nested ->
                    nested.copy(data = nested.data.toList())
                },
            )
        }
        val expectedHashesByHeight = validatedExpectedHashes(
            elements = snapshot,
            expectedAddress = expectedAddress,
            health = health,
            maximumRecords = maximumRecords,
        )

        expectedHashesByHeight.entries
            .sortedBy { it.key }
            .chunked(MAXIMUM_CONCURRENT_RPC_CHECKS)
            .forEach { batch ->
                coroutineScope {
                    batch.map { (height, expectedHash) ->
                        async {
                            check(
                                normalizedHash(canonicalBlockHash(height)) == expectedHash
                            ) { "PI_INDEXER_HISTORY_CHECKPOINT_FORK" }
                        }
                    }.awaitAll()
                }
            }
        return PiCanonicalHistoryValidationReceipt(
            elements = snapshot,
            expectedAddress = expectedAddress,
            health = health,
            maximumRecords = maximumRecords,
        )
    }

    /**
     * Revalidates the complete identity/checkpoint structure of a history page that was persisted
     * only after [validate] succeeded against canonical RPC. This intentionally performs no network
     * access, so a previously canonical-qualified page remains available during a full outage.
     */
    fun validatePersisted(
        elements: List<IndexerHistoryElement>,
        expectedAddress: String,
        health: PiIndexerHealth,
        maximumRecords: Int = DEFAULT_MAXIMUM_RECORDS,
    ) {
        validatedExpectedHashes(
            elements = elements,
            expectedAddress = expectedAddress,
            health = health,
            maximumRecords = maximumRecords,
        )
    }

    private fun validatedExpectedHashes(
        elements: List<IndexerHistoryElement>,
        expectedAddress: String,
        health: PiIndexerHealth,
        maximumRecords: Int,
    ): Map<Int, String> {
        check(
            expectedAddress.isNotEmpty() &&
                expectedAddress == expectedAddress.trim() &&
                expectedAddress.toByteArray(Charsets.UTF_8).size <= MAXIMUM_ADDRESS_BYTES
        ) { "PI_INDEXER_HISTORY_ACCOUNT_INVALID" }
        require(maximumRecords in 1..ABSOLUTE_MAXIMUM_RECORDS) {
            "PI_INDEXER_HISTORY_LIMIT_INVALID"
        }
        check(elements.size <= maximumRecords) { "PI_INDEXER_HISTORY_LIMIT_EXCEEDED" }

        val indexedCheckpoint = parseCheckpoint(
            health.latestIndexedBlock ?: health.workerLatestIndexedBlock,
            "PI_INDEXER_HISTORY_CHECKPOINT_INVALID",
        )
        val finalizedCheckpoint = parseCheckpoint(
            health.workerLatestFinalizedBlock,
            "PI_INDEXER_HISTORY_CHECKPOINT_INVALID",
        )
        check(
            indexedCheckpoint > 0 &&
                indexedCheckpoint <= finalizedCheckpoint
        ) { "PI_INDEXER_HISTORY_CHECKPOINT_INVALID" }

        val expectedHashesByHeight = linkedMapOf<Int, String>()
        health.latestIndexedBlockHash?.let { rawCheckpointHash ->
            expectedHashesByHeight[indexedCheckpoint] =
                normalizedHash(rawCheckpointHash)
                    ?: throw IllegalStateException(
                        "PI_INDEXER_HISTORY_CHECKPOINT_INVALID"
                    )
        }
        val historyIdentifiers = mutableSetOf<String>()
        elements.forEach { element ->
            check(element.executionKnown) {
                "PI_INDEXER_HISTORY_EXECUTION_INVALID"
            }
            check(
                element.networkFee.length in 1..MAXIMUM_QUANTITY_BYTES &&
                    unsignedInteger.matches(element.networkFee)
            ) { "PI_INDEXER_HISTORY_FEE_INVALID" }
            check(
                element.timestamp.length in 1..MAXIMUM_TIMESTAMP_DIGITS &&
                    unsignedInteger.matches(element.timestamp) &&
                    element.timestamp.toLongOrNull()?.let {
                        it <= Long.MAX_VALUE / 1_000L
                    } == true
            ) { "PI_INDEXER_HISTORY_TIMESTAMP_INVALID" }
            check(
                element.address == expectedAddress ||
                    element.dataFrom == expectedAddress ||
                    element.dataTo == expectedAddress
            ) { "PI_INDEXER_HISTORY_ACCOUNT_MISMATCH" }
            check(
                isValidHistoryIdentifier(element.id) &&
                    historyIdentifiers.add(element.id)
            ) {
                "PI_INDEXER_HISTORY_TRANSACTION_INVALID"
            }

            val height = element.blockHeight
                ?: throw IllegalStateException("PI_INDEXER_HISTORY_BLOCK_MISSING")
            check(height in 0..indexedCheckpoint.toLong() && height <= Int.MAX_VALUE) {
                "PI_INDEXER_HISTORY_ABOVE_CHECKPOINT"
            }
            val expectedHash = normalizedHash(element.blockHash)
                ?: throw IllegalStateException("PI_INDEXER_HISTORY_BLOCK_HASH_INVALID")
            val previous = expectedHashesByHeight.putIfAbsent(height.toInt(), expectedHash)
            check(previous == null || previous == expectedHash) {
                "PI_INDEXER_HISTORY_BLOCK_CONFLICT"
            }
        }
        return expectedHashesByHeight
    }

    private fun parseCheckpoint(value: String?, code: String): Int {
        check(
            value != null &&
                value.length <= MAXIMUM_CHECKPOINT_DIGITS &&
                unsignedInteger.matches(value)
        ) { code }
        return value.toIntOrNull() ?: throw IllegalStateException(code)
    }

    private fun normalizedHash(value: String?): String? {
        if (
            value == null ||
            !substrateHash.matches(value) ||
            value.equals(zeroHash, ignoreCase = true)
        ) {
            return null
        }
        return value.lowercase()
    }

    private fun isValidHistoryIdentifier(value: String): Boolean =
        value.isNotEmpty() &&
            value == value.trim() &&
            value.toByteArray(Charsets.UTF_8).size <=
            MAXIMUM_HISTORY_IDENTIFIER_BYTES &&
            value.none(Char::isISOControl)
}
