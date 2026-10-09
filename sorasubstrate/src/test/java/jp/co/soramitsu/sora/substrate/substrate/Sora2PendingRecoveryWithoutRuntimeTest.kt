package jp.co.soramitsu.sora.substrate.substrate

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.account.Sora2AddressCodec
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.AccountDao
import jp.co.soramitsu.core_db.dao.WalletIdentityDao
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.runtime.Sora2CanonicalBlock
import jp.co.soramitsu.sora.substrate.runtime.Sora2FinalizedRuntimeIdentity
import jp.co.soramitsu.sora.substrate.runtime.Sora2RuntimeContract
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.chain.RuntimeVersion
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A pending submission must reach a final state even when the runtime it was signed with is no
 * longer available locally (mainnet upgraded, or the OS cleared the cache). Finding the exact
 * hash in the blocks of its mortal era needs no runtime; only decoding the events of an
 * included transaction does, and an included transaction is never reported as expired.
 */
class Sora2PendingRecoveryWithoutRuntimeTest {

    private val codec = Sora2AddressCodec()
    private val publicKey = ByteArray(32) { 7 }
    private val walletId = checkNotNull(codec.toSoraAddressOrNull(publicKey))
    private val publicKeyHex = publicKey.toHexString(withPrefix = false).lowercase()

    private val row = Sora2PendingSubmissionLocal(
        localId = "sora2:${"ab".repeat(32)}",
        recoverySchemaVersion = Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION,
        walletId = walletId,
        networkId = Sora2PendingSubmissionLocal.NETWORK_SORA2,
        transactionHash = TX_HASH,
        accountId = publicKeyHex,
        publicKey = publicKeyHex,
        genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
        specVersion = 133,
        transactionVersion = 131,
        metadataSha256 = "1".repeat(64),
        typesSha256 = "2".repeat(64),
        eraBirthBlock = BIRTH,
        eraDeathBlockExclusive = BIRTH + 64,
        eraPeriod = 64,
        eraPhase = (BIRTH % 64).toInt(),
        eraBirthBlockHash = BIRTH_HASH,
        operationKind = Sora2PendingSubmissionLocal.OPERATION_GENERIC,
        state = Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN,
        submissionIsAmbiguous = true,
        terminalBlockNumber = null,
        terminalBlockHash = null,
        terminalFinalizedHeight = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private var current = row
    private val writtenStates = mutableListOf<String>()

    private val dao = mockk<WalletIdentityDao>().also { dao ->
        coEvery { dao.getUnresolvedSora2PendingSubmissions() } answers {
            listOf(current).filterNot { it.isAuthoritativelyTerminal }
        }
        coEvery { dao.hasActiveDeletionOperation() } returns false
        coEvery { dao.getMigrationJournal(any()) } returns null
        coEvery { dao.getWallet(walletId) } returns WalletIdentityLocal(
            walletId = walletId,
            displayName = "Primary",
            secretSource = "MNEMONIC",
            migrationState = "VERIFIED",
            derivationVersion = 1,
        )
        coEvery { dao.getNetworkAccount(walletId, "sora2") } returns NetworkAccountLocal(
            walletId = walletId,
            networkId = "sora2",
            publicKey = publicKeyHex,
            address = walletId,
            derivationPath = "",
            derivationVersion = 0,
            enabled = true,
        )
        coEvery { dao.getSora2PendingSubmission(row.localId) } answers { current }
        coEvery {
            dao.updateSora2PendingSubmission(any(), any(), any(), any(), any(), any(), any())
        } answers {
            current = current.copy(
                state = secondArg(),
                submissionIsAmbiguous = thirdArg(),
                terminalFinalizedHeight = arg(5),
                updatedAt = arg(6),
            )
            writtenStates += current.state
            current
        }
    }
    private val accountDao = mockk<AccountDao>().also {
        coEvery { it.getAccount(walletId) } returns SoraAccountLocal(walletId, "Primary")
    }
    private val database = mockk<AppDatabase>().also {
        every { it.walletIdentityDao() } returns dao
        every { it.accountDao() } returns accountDao
    }

    // The runtime the submission was signed with cannot be found anywhere.
    private val runtimeManager = mockk<RuntimeManager>().also {
        coEvery {
            it.getReviewedRecoveryRuntimeSnapshotOrNull(any(), any(), any(), any(), any(), any())
        } returns null
    }
    private val rpc = mockk<Sora2BoundedRuntimeRpcClient>()

    private fun chainAt(finalizedHeight: Long, includedAt: Long? = null) {
        coEvery { rpc.getFinalizedIdentity() } returns Sora2FinalizedRuntimeIdentity(
            genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
            finalizedHash = blockHash(finalizedHeight),
            runtimeVersion = RuntimeVersion(134, 131),
            finalizedBlockNumber = finalizedHeight,
        )
        coEvery { rpc.getCanonicalBlock(any(), any(), any()) } answers {
            val height = firstArg<Long>()
            Sora2CanonicalBlock(
                blockHash = if (height == BIRTH) BIRTH_HASH else blockHash(height),
                blockNumber = height,
                matchingExtrinsicIndices = if (height == includedAt) {
                    mapOf(TX_HASH to 2)
                } else {
                    emptyMap()
                },
            )
        }
    }

    private fun coordinator() = Sora2PendingSubmissionCoordinator(
        database = database,
        runtimeManager = runtimeManager,
        runtimeRpcClient = rpc,
        addressCodec = codec,
        recoveryKick = {},
    )

    @Test
    fun `a submission that was never included expires without its signing runtime`() = runTest {
        chainAt(finalizedHeight = BIRTH + 100)

        val pass = coordinator().recoverStatusOnly()

        assertEquals(1, pass.resolved)
        assertEquals(0, pass.failures)
        assertEquals(listOf(Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED), writtenStates)
        // No runtime is needed (or downloaded) to prove the hash is absent from its era.
        coVerify(exactly = 0) {
            runtimeManager.getReviewedRecoveryRuntimeSnapshotOrNull(
                any(), any(), any(), any(), any(), any(),
            )
        }
    }

    @Test
    fun `an included submission is never reported as expired when its runtime is missing`() =
        runTest {
            chainAt(finalizedHeight = BIRTH + 100, includedAt = BIRTH + 10)
            val coordinator = coordinator()

            val first = coordinator.recoverStatusOnly()
            val second = coordinator.recoverStatusOnly()

            assertEquals(0, first.resolved)
            assertEquals(1, first.failures)
            assertEquals(0, second.resolved)
            assertEquals(1, second.failures)
            assertEquals(emptyList<String>(), writtenStates)
            // The second pass scans the era again from its birth and finds the inclusion again.
            coVerify(exactly = 2) { rpc.getCanonicalBlock(BIRTH + 10, any(), any()) }
            // The lookup carries the era-birth block, where the signing runtime can be rebuilt.
            coVerify {
                runtimeManager.getReviewedRecoveryRuntimeSnapshotOrNull(
                    genesisHash = Sora2RuntimeContract.SORA_MAINNET_GENESIS_HASH,
                    specVersion = 133,
                    transactionVersion = 131,
                    metadataSha256 = "1".repeat(64),
                    typesSha256 = "2".repeat(64),
                    eraBirthBlockHash = BIRTH_HASH,
                )
            }
        }

    @Test
    fun `a submission still inside its era stays pending`() = runTest {
        chainAt(finalizedHeight = BIRTH + 20)

        val pass = coordinator().recoverStatusOnly()

        assertEquals(0, pass.resolved)
        assertEquals(1, pass.unresolved)
        assertEquals(emptyList<String>(), writtenStates)
    }

    private companion object {
        const val BIRTH = 27_973_000L
        val TX_HASH = "0x${"ab".repeat(32)}"
        val BIRTH_HASH = "0x${"cd".repeat(32)}"

        fun blockHash(height: Long): String =
            "0x" + height.toString(16).padStart(64, '0').replaceFirst('0', 'e')
    }
}
