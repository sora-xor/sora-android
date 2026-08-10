package jp.co.soramitsu.feature_blockexplorer_api.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PiHistoryCheckpointValidatorTest {

    @Test
    fun `history binds exact account and canonical hashes at indexed checkpoint`() = runTest {
        val canonicalHashes = mapOf(
            40 to BLOCK_HASH_A,
            41 to BLOCK_HASH_B,
        )
        val resolvedHeights = mutableListOf<Int>()
        val elements = listOf(
            historyElement(
                id = TRANSACTION_HASH_A,
                blockHash = BLOCK_HASH_A,
                blockHeight = 40,
                address = ACCOUNT,
            ),
            historyElement(
                id = TRANSACTION_HASH_B,
                blockHash = BLOCK_HASH_A,
                blockHeight = 40,
                address = null,
                dataFrom = ACCOUNT,
            ),
            historyElement(
                id = TRANSACTION_HASH_C,
                blockHash = BLOCK_HASH_B,
                blockHeight = 41,
                address = null,
                dataTo = ACCOUNT,
            ),
        )

        PiHistoryCheckpointValidator.validate(
            elements = elements,
            expectedAddress = ACCOUNT,
            health = healthyCheckpoint(indexed = "41", finalized = "45"),
        ) { height ->
            resolvedHeights += height
            canonicalHashes.getValue(height)
        }

        assertThat(resolvedHeights).containsExactly(40, 41)
    }

    @Test
    fun `history rejects a record from another account before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(
                    historyElement(
                        address = "cnOther",
                        dataFrom = "cnSender",
                        dataTo = "cnReceiver",
                    )
                ),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_HISTORY_ACCOUNT_MISMATCH")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects an unknown execution result before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(historyElement(executionKnown = false)),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_EXECUTION_INVALID")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects a malformed fee before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(historyElement(networkFee = "-1")),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_FEE_INVALID")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects a malformed timestamp before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(historyElement(timestamp = "not-a-time")),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_TIMESTAMP_INVALID")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects a block above the indexed checkpoint before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(historyElement(blockHeight = 43)),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(indexed = "42", finalized = "45"),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_HISTORY_ABOVE_CHECKPOINT")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects a missing block height before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(historyElement(blockHeight = null)),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_HISTORY_BLOCK_MISSING")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects a canonical rpc hash mismatch`() = runTest {
        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(historyElement()),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                BLOCK_HASH_B
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_HISTORY_CHECKPOINT_FORK")
    }

    @Test
    fun `history binds optional indexed checkpoint hash even for an empty page`() = runTest {
        val checkedHeights = mutableListOf<Int>()

        PiHistoryCheckpointValidator.validate(
            elements = emptyList(),
            expectedAddress = ACCOUNT,
            health = healthyCheckpoint(indexedHash = BLOCK_HASH_A),
        ) { height ->
            checkedHeights += height
            BLOCK_HASH_A
        }

        assertThat(checkedHeights).containsExactly(42)

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = emptyList(),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(indexedHash = BLOCK_HASH_A),
            ) {
                BLOCK_HASH_B
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_HISTORY_CHECKPOINT_FORK")
    }

    @Test
    fun `history rejects malformed and zero canonical rpc hashes`() = runTest {
        listOf(
            "block-42",
            ZERO_HASH,
        ).forEach { canonicalHash ->
            val error = runCatching {
                PiHistoryCheckpointValidator.validate(
                    elements = listOf(historyElement()),
                    expectedAddress = ACCOUNT,
                    health = healthyCheckpoint(),
                ) {
                    canonicalHash
                }
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_CHECKPOINT_FORK")
        }
    }

    @Test
    fun `history rejects missing malformed and zero block hashes before rpc`() = runTest {
        listOf(
            null,
            "block-42",
            ZERO_HASH,
        ).forEach { invalidHash ->
            var rpcCalled = false

            val error = runCatching {
                PiHistoryCheckpointValidator.validate(
                    elements = listOf(historyElement(blockHash = invalidHash)),
                    expectedAddress = ACCOUNT,
                    health = healthyCheckpoint(),
                ) {
                    rpcCalled = true
                    BLOCK_HASH_A
                }
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_BLOCK_HASH_INVALID")
            assertThat(rpcCalled).isFalse()
        }
    }

    @Test
    fun `history accepts bounded synthetic event identifiers`() = runTest {
        val syntheticId = "$TRANSACTION_HASH_A-mint"

        val receipt = PiHistoryCheckpointValidator.validate(
            elements = listOf(historyElement(id = syntheticId)),
            expectedAddress = ACCOUNT,
            health = healthyCheckpoint(),
        ) { BLOCK_HASH_A }

        assertThat(receipt.elements.single().id).isEqualTo(syntheticId)
    }

    @Test
    fun `history rejects duplicate synthetic event identifiers before rpc`() = runTest {
        val syntheticId = "$TRANSACTION_HASH_A-mint"
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(
                    historyElement(id = syntheticId),
                    historyElement(id = syntheticId),
                ),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat()
            .isEqualTo("PI_INDEXER_HISTORY_TRANSACTION_INVALID")
        assertThat(rpcCalled).isFalse()
    }

    @Test
    fun `history rejects malformed event identifiers before rpc`() = runTest {
        listOf(
            "",
            " transaction-1",
            "transaction\u0000-1",
        ).forEach { invalidIdentifier ->
            var rpcCalled = false

            val error = runCatching {
                PiHistoryCheckpointValidator.validate(
                    elements = listOf(historyElement(id = invalidIdentifier)),
                    expectedAddress = ACCOUNT,
                    health = healthyCheckpoint(),
                ) {
                    rpcCalled = true
                    BLOCK_HASH_A
                }
            }.exceptionOrNull()

            assertThat(error).isInstanceOf(IllegalStateException::class.java)
            assertThat(error).hasMessageThat()
                .isEqualTo("PI_INDEXER_HISTORY_TRANSACTION_INVALID")
            assertThat(rpcCalled).isFalse()
        }
    }

    @Test
    fun `history rejects conflicting hashes at the same block height before rpc`() = runTest {
        var rpcCalled = false

        val error = runCatching {
            PiHistoryCheckpointValidator.validate(
                elements = listOf(
                    historyElement(
                        id = TRANSACTION_HASH_A,
                        blockHash = BLOCK_HASH_A,
                    ),
                    historyElement(
                        id = TRANSACTION_HASH_B,
                        blockHash = BLOCK_HASH_B,
                    ),
                ),
                expectedAddress = ACCOUNT,
                health = healthyCheckpoint(),
            ) {
                rpcCalled = true
                BLOCK_HASH_A
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(error).hasMessageThat().isEqualTo("PI_INDEXER_HISTORY_BLOCK_CONFLICT")
        assertThat(rpcCalled).isFalse()
    }

    private fun historyElement(
        id: String = TRANSACTION_HASH_A,
        blockHash: String? = BLOCK_HASH_A,
        blockHeight: Long? = 42,
        address: String? = ACCOUNT,
        dataFrom: String? = null,
        dataTo: String? = null,
        executionKnown: Boolean = true,
        networkFee: String = "0",
        timestamp: String = "1",
    ) = IndexerHistoryElement(
        id = id,
        blockHash = blockHash,
        blockHeight = blockHeight,
        module = "Assets",
        method = "transfer",
        address = address,
        dataFrom = dataFrom,
        dataTo = dataTo,
        timestamp = timestamp,
        networkFee = networkFee,
        success = true,
        data = emptyList(),
        nestedData = emptyList(),
        executionKnown = executionKnown,
    )

    private fun healthyCheckpoint(
        indexed: String = "42",
        finalized: String = "45",
        indexedHash: String? = null,
    ) = PiIndexerHealth(
        ok = true,
        repositoryReady = true,
        service = "polkaswap-indexer",
        serviceId = "pi.soramitsu.io",
        schemaVersion = 1,
        ecosystem = "sora2",
        chainId = "sora:mainnet",
        network = "mainnet",
        publicBaseUrl = "https://pi.soramitsu.io/graphql",
        readOnly = true,
        genesisHash = null,
        latestIndexedBlock = null,
        latestIndexedBlockHash = indexedHash,
        latestIndexedAt = null,
        workerAvailable = true,
        workerReady = true,
        workerReadinessReason = null,
        workerLifecycle = "running",
        workerStartupComplete = true,
        workerLatestFinalizedBlock = finalized,
        workerLatestIndexedBlock = indexed,
        workerLag = "3",
        workerLastSuccessfulIndexTimestamp = "1800000000",
        workerLastError = null,
        workerLastErrorTimestamp = null,
    )

    private companion object {
        const val ACCOUNT = "cnAccount"
        const val TRANSACTION_HASH_A =
            "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val TRANSACTION_HASH_B =
            "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
        const val TRANSACTION_HASH_C =
            "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
        const val BLOCK_HASH_A =
            "0x1111111111111111111111111111111111111111111111111111111111111111"
        const val BLOCK_HASH_B =
            "0x2222222222222222222222222222222222222222222222222222222222222222"
        const val ZERO_HASH =
            "0x0000000000000000000000000000000000000000000000000000000000000000"
    }
}
