package jp.co.soramitsu.core_db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.data.network.dto.PolkamarktPendingAssetId
import jp.co.soramitsu.common.nexus.NexusAssetDefinitionIdentity
import jp.co.soramitsu.common.nexus.NexusQuantityContract
import jp.co.soramitsu.common.nexus.NexusTransactionHash
import jp.co.soramitsu.common.nexus.TairaDeployment
import jp.co.soramitsu.common.nexus.WalletNetworkChainIdentity
import jp.co.soramitsu.common.nexus.WalletNetworkId
import jp.co.soramitsu.core_db.WalletDeletionIntegrity
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.PendingNetworkTransactionLocal
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.core_db.model.WalletDeletionContract
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletDeletionTargetLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletIdentityDao {

    @Query("select * from walletIdentities order by displayName, walletId")
    suspend fun getWallets(): List<WalletIdentityLocal>

    @Query("select * from walletIdentities where walletId = :walletId")
    suspend fun getWallet(walletId: String): WalletIdentityLocal?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWallet(wallet: WalletIdentityLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertWalletForUpsert(wallet: WalletIdentityLocal): Long

    @Query(
        """
        update walletIdentities
        set displayName = :displayName,
            secretSource = :secretSource,
            migrationState = :migrationState,
            derivationVersion = :derivationVersion
        where walletId = :walletId
        """
    )
    suspend fun updateWalletForUpsert(
        walletId: String,
        displayName: String,
        secretSource: String,
        migrationState: String,
        derivationVersion: Int,
    ): Int

    @Transaction
    suspend fun upsertWallet(wallet: WalletIdentityLocal) {
        if (insertWalletForUpsert(wallet) == -1L) {
            check(
                updateWalletForUpsert(
                    walletId = wallet.walletId,
                    displayName = wallet.displayName,
                    secretSource = wallet.secretSource,
                    migrationState = wallet.migrationState,
                    derivationVersion = wallet.derivationVersion,
                ) == 1
            ) { "WALLET_IDENTITY_UPSERT_MISMATCH" }
        }
    }

    @Query("update walletIdentities set migrationState = :state, secretSource = :secretSource where walletId = :walletId")
    suspend fun updateWalletVerification(walletId: String, state: String, secretSource: String)

    @Query("update walletIdentities set displayName = :displayName where walletId = :walletId")
    suspend fun updateWalletName(walletId: String, displayName: String)

    @Query("select * from networkAccounts where walletId = :walletId order by networkId")
    suspend fun getNetworkAccounts(walletId: String): List<NetworkAccountLocal>

    @Query("select * from networkAccounts where walletId = :walletId and networkId = :networkId")
    suspend fun getNetworkAccount(walletId: String, networkId: String): NetworkAccountLocal?

    @Query("select * from networkAccounts order by walletId, networkId")
    suspend fun getAllNetworkAccounts(): List<NetworkAccountLocal>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNetworkAccountForUpsert(account: NetworkAccountLocal): Long

    @Query(
        """
        update networkAccounts
        set publicKey = :publicKey,
            address = :address,
            derivationPath = :derivationPath,
            derivationVersion = :derivationVersion,
            enabled = :enabled
        where walletId = :walletId and networkId = :networkId
        """
    )
    suspend fun updateNetworkAccountForUpsert(
        walletId: String,
        networkId: String,
        publicKey: String,
        address: String,
        derivationPath: String,
        derivationVersion: Int,
        enabled: Boolean,
    ): Int

    @Transaction
    suspend fun upsertNetworkAccount(account: NetworkAccountLocal) {
        if (insertNetworkAccountForUpsert(account) == -1L) {
            check(
                updateNetworkAccountForUpsert(
                    walletId = account.walletId,
                    networkId = account.networkId,
                    publicKey = account.publicKey,
                    address = account.address,
                    derivationPath = account.derivationPath,
                    derivationVersion = account.derivationVersion,
                    enabled = account.enabled,
                ) == 1
            ) { "NETWORK_ACCOUNT_UPSERT_MISMATCH" }
        }
    }

    @Transaction
    suspend fun upsertNetworkAccounts(accounts: List<NetworkAccountLocal>) {
        accounts.forEach { upsertNetworkAccount(it) }
    }

    @Query(
        """
        select networkAccounts.* from networkAccounts
        inner join walletIdentities
            on walletIdentities.walletId = networkAccounts.walletId
        where networkAccounts.enabled = 1
            and walletIdentities.migrationState = 'VERIFIED'
            and not exists (
                select 1 from walletMigrationJournal
                where migrationId = 'wallet-network-v1'
                    and state != 'VERIFIED'
            )
            and not exists (select 1 from walletDeletionOperations)
        order by networkAccounts.networkId
        """
    )
    fun observeEnabledNetworkAccounts(): Flow<List<NetworkAccountLocal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMigrationJournal(journal: WalletMigrationJournalLocal)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMigrationFailureJournal(
        journal: WalletMigrationJournalLocal,
    ): Long

    @Query("select * from walletMigrationJournal where migrationId = :migrationId")
    suspend fun getMigrationJournal(migrationId: String): WalletMigrationJournalLocal?

    @Query(
        """
        update walletMigrationJournal
        set state = 'VERIFIED',
            verifiedAccountCount = :verifiedAccountCount,
            failureCode = null,
            completedAt = :completedAt
        where migrationId = :migrationId
            and state = 'VERIFYING'
            and legacyAccountCount = :legacyAccountCount
            and verifiedAccountCount = :verifiedAccountCount
            and selectedWalletId = :selectedWalletId
            and integrityHash = :integrityHash
            and startedAt = :startedAt
        """
    )
    suspend fun activateMigrationJournal(
        migrationId: String,
        legacyAccountCount: Int,
        verifiedAccountCount: Int,
        selectedWalletId: String,
        integrityHash: String,
        startedAt: Long,
        completedAt: Long,
    ): Int

    /**
     * Advances a VERIFIED wallet checkpoint after an explicitly verified live-wallet
     * lifecycle change (for example account creation, rename, selection, or removal).
     *
     * Every field from the previously observed checkpoint participates in the compare-and-set. A
     * caller may use this only after re-verifying all current secrets and the exact dual-read
     * wallet/network model in the same Room transaction.
     */
    @Query(
        """
        update walletMigrationJournal
        set legacyAccountCount = :currentAccountCount,
            verifiedAccountCount = :currentAccountCount,
            selectedWalletId = :currentSelectedWalletId,
            integrityHash = :currentIntegrityHash,
            failureCode = null,
            completedAt = :refreshedAt
        where migrationId = :migrationId
            and state = 'VERIFIED'
            and legacyAccountCount = :receiptAccountCount
            and verifiedAccountCount = :receiptVerifiedAccountCount
            and selectedWalletId = :receiptSelectedWalletId
            and integrityHash = :receiptIntegrityHash
            and failureCode is null
            and startedAt = :receiptStartedAt
            and completedAt = :receiptCompletedAt
        """
    )
    suspend fun refreshVerifiedMigrationJournal(
        migrationId: String,
        receiptAccountCount: Int,
        receiptVerifiedAccountCount: Int,
        receiptSelectedWalletId: String,
        receiptIntegrityHash: String,
        receiptStartedAt: Long,
        receiptCompletedAt: Long,
        currentAccountCount: Int,
        currentSelectedWalletId: String,
        currentIntegrityHash: String,
        refreshedAt: Long,
    ): Int

    @Query(
        """
        update walletMigrationJournal
        set state = 'RECOVERY_REQUIRED',
            verifiedAccountCount = 0,
            failureCode = :failureCode,
            completedAt = :completedAt
        where migrationId = :migrationId
            and state in ('VERIFYING', 'VERIFIED', 'RECOVERY_REQUIRED')
            and legacyAccountCount = :legacyAccountCount
            and selectedWalletId = :selectedWalletId
            and integrityHash = :integrityHash
            and startedAt = :startedAt
        """
    )
    suspend fun markMigrationRecoveryRequired(
        migrationId: String,
        legacyAccountCount: Int,
        selectedWalletId: String,
        integrityHash: String,
        startedAt: Long,
        failureCode: String,
        completedAt: Long,
    ): Int

    @Query(
        """
        update walletMigrationJournal
        set state = 'RECOVERY_REQUIRED',
            legacyAccountCount = :currentAccountCount,
            verifiedAccountCount = 0,
            selectedWalletId = :currentSelectedWalletId,
            integrityHash = :currentIntegrityHash,
            failureCode = :failureCode,
            completedAt = :completedAt
        where migrationId = :migrationId
            and state = 'VERIFIED'
            and legacyAccountCount = :legacyAccountCount
            and verifiedAccountCount = :receiptVerifiedAccountCount
            and selectedWalletId = :receiptSelectedWalletId
            and integrityHash = :receiptIntegrityHash
            and failureCode is null
            and startedAt = :startedAt
            and completedAt = :receiptCompletedAt
        """
    )
    suspend fun markActivatedMigrationRecoveryRequired(
        migrationId: String,
        legacyAccountCount: Int,
        receiptVerifiedAccountCount: Int,
        receiptSelectedWalletId: String,
        receiptIntegrityHash: String,
        startedAt: Long,
        receiptCompletedAt: Long,
        currentAccountCount: Int,
        currentSelectedWalletId: String,
        currentIntegrityHash: String,
        failureCode: String,
        completedAt: Long,
    ): Int

    @Transaction
    suspend fun recordMigrationFailure(
        journal: WalletMigrationJournalLocal,
    ): Boolean {
        val failureCode = requireNotNull(journal.failureCode)
        val completedAt = requireNotNull(journal.completedAt)
        check(
            journal.state == "RECOVERY_REQUIRED" &&
                journal.verifiedAccountCount == 0
        ) { "MIGRATION_FAILURE_JOURNAL_INVALID" }
        if (
            markMigrationRecoveryRequired(
                migrationId = journal.migrationId,
                legacyAccountCount = journal.legacyAccountCount,
                selectedWalletId = journal.selectedWalletId,
                integrityHash = journal.integrityHash,
                startedAt = journal.startedAt,
                failureCode = failureCode,
                completedAt = completedAt,
            ) == 1
        ) {
            return true
        }
        return insertMigrationFailureJournal(journal) != -1L
    }

    @Query("select * from walletDeletionOperations where activeSlot = 1")
    suspend fun getActiveDeletionOperation(): WalletDeletionOperationLocal?

    @Query("select exists(select 1 from walletDeletionOperations)")
    suspend fun hasActiveDeletionOperation(): Boolean

    @Query(
        """
        select * from walletDeletionTargets
        where operationId = :operationId
        order by walletId
        """
    )
    suspend fun getDeletionTargets(
        operationId: String,
    ): List<WalletDeletionTargetLocal>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeletionOperation(operation: WalletDeletionOperationLocal)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeletionTargets(targets: List<WalletDeletionTargetLocal>)

    @Transaction
    suspend fun beginDeletionOperation(
        operation: WalletDeletionOperationLocal,
        targets: List<WalletDeletionTargetLocal>,
    ) {
        val targetIds = targets.map(WalletDeletionTargetLocal::walletId).toSet()
        check(!hasActiveDeletionOperation()) {
            "WALLET_DELETION_ALREADY_ACTIVE"
        }
        check(
            operation.activeSlot == WalletDeletionContract.ACTIVE_SLOT &&
                operation.formatVersion == WalletDeletionContract.FORMAT_VERSION &&
                operation.phase == WalletDeletionContract.PHASE_CONFIRMED &&
                operation.scope in setOf(
                    WalletDeletionContract.SCOPE_SINGLE,
                    WalletDeletionContract.SCOPE_ALL,
                ) &&
                operation.expectedWalletCount >= operation.targetCount &&
                operation.expectedWalletCount <=
                WalletDeletionContract.MAX_TARGET_WALLETS &&
                operation.targetCount > 0 &&
                operation.targetCount == targets.size &&
                targetIds.size == targets.size &&
                targets.all { it.operationId == operation.operationId } &&
                WalletDeletionIntegrity.isSha256(operation.beforeSnapshotHash) &&
                WalletDeletionIntegrity.isSha256(operation.afterSnapshotHash) &&
                WalletPreferenceIntegrity.isSha256(operation.beforePreferencesHash) &&
                WalletPreferenceIntegrity.isSha256(operation.afterPreferencesHash) &&
                operation.failureCode == null &&
                operation.requestedAt > 0L &&
                operation.updatedAt >= operation.requestedAt &&
                (
                    operation.scope == WalletDeletionContract.SCOPE_ALL &&
                        operation.targetCount == operation.expectedWalletCount &&
                        operation.selectedAfter.isEmpty() ||
                        operation.scope == WalletDeletionContract.SCOPE_SINGLE &&
                        operation.targetCount == 1 &&
                        operation.selectedAfter.isNotBlank() &&
                        operation.selectedAfter !in targetIds
                    ) &&
                operation.requestDigest ==
                WalletDeletionIntegrity.requestDigest(operation, targetIds)
        ) { "WALLET_DELETION_REQUEST_INVALID" }
        check(
            countUnresolvedTransactionsForJournal(targetIds.toList()) == 0
        ) { "WALLET_DELETION_PENDING_TRANSACTIONS" }
        insertDeletionOperation(operation)
        insertDeletionTargets(targets)
    }

    @Query(
        """
        update walletDeletionOperations
        set phase = 'DATABASE_COMMITTED',
            updatedAt = :updatedAt,
            failureCode = null
        where operationId = :operationId
            and phase = 'CONFIRMED'
            and requestDigest = :requestDigest
        """
    )
    suspend fun markDeletionDatabaseCommitted(
        operationId: String,
        requestDigest: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        update walletDeletionOperations
        set phase = 'PREFERENCES_COMMITTED',
            updatedAt = :updatedAt,
            failureCode = null
        where operationId = :operationId
            and phase = 'DATABASE_COMMITTED'
            and requestDigest = :requestDigest
        """
    )
    suspend fun markDeletionPreferencesCommitted(
        operationId: String,
        requestDigest: String,
        updatedAt: Long,
    ): Int

    @Query(
        """
        update walletDeletionOperations
        set failureCode = :failureCode,
            updatedAt = :updatedAt
        where operationId = :operationId
        """
    )
    suspend fun recordDeletionFailure(
        operationId: String,
        failureCode: String,
        updatedAt: Long,
    ): Int

    @Query("delete from walletDeletionOperations where operationId = :operationId")
    suspend fun deleteDeletionOperation(operationId: String): Int

    @Query(
        """
        select count(*) from pendingNetworkTransactions
        where walletId in (:walletIds)
        """
    )
    suspend fun countPendingTransactionRowsForJournal(walletIds: List<String>): Int

    @Query(
        """
        select * from pendingNetworkTransactions
        where walletId in (:walletIds)
        order by walletId, localId
        """
    )
    suspend fun getPendingTransactionsForJournal(
        walletIds: List<String>,
    ): List<PendingNetworkTransactionLocal>

    @Query(
        """
        select * from sora2PendingSubmissions
        where walletId in (:walletIds)
        order by walletId, localId
        """
    )
    suspend fun getSora2PendingSubmissionsForJournal(
        walletIds: List<String>,
    ): List<Sora2PendingSubmissionLocal>

    /**
     * A terminal-looking row is deletion authority only after the complete durable record has
     * passed the same validation as a newly inserted Nexus/Polkamarkt journal entry. Reading just
     * `state` would let a corrupt restart record masquerade as an empty unresolved set.
     */
    @Transaction
    suspend fun countUnresolvedTransactionsForJournal(walletIds: List<String>): Int {
        check(
            walletIds.isNotEmpty() &&
                walletIds.size <= WalletDeletionContract.MAX_TARGET_WALLETS &&
                walletIds.toSet().size == walletIds.size
        ) { "WALLET_DELETION_PENDING_TARGETS_INVALID" }
        val transactions = getPendingTransactionsForJournal(walletIds)
        transactions.forEach(::validatePendingTransaction)
        val sora2Submissions = getSora2PendingSubmissionsForJournal(walletIds)
        sora2Submissions.forEach(::validateSora2PendingSubmission)
        return transactions.count {
            it.state !in TERMINAL_PENDING_STATES || !hasCurrentChainIdentity(it)
        } +
            sora2Submissions.count { !it.isAuthoritativelyTerminal }
    }

    @Query(
        """
        delete from pendingNetworkTransactions
        where walletId in (:walletIds)
        """
    )
    suspend fun deletePendingTransactionsForJournal(walletIds: List<String>): Int

    @Query(
        """
        select count(*) from sora2PendingSubmissions
        where walletId in (:walletIds)
        """
    )
    suspend fun countSora2PendingSubmissionRowsForJournal(walletIds: List<String>): Int

    @Query(
        """
        delete from sora2PendingSubmissions
        where walletId in (:walletIds)
        """
    )
    suspend fun deleteSora2PendingSubmissionsForJournal(walletIds: List<String>): Int

    @Query("delete from networkAccounts where walletId in (:walletIds)")
    suspend fun deleteNetworkAccountsForJournal(walletIds: List<String>): Int

    @Query("delete from walletIdentities where walletId in (:walletIds)")
    suspend fun deleteWalletIdentitiesForJournal(walletIds: List<String>): Int

    @Query(
        """
        update walletIdentities
        set migrationState = 'RECOVERY_REQUIRED'
        where walletId in (:walletIds)
        """
    )
    suspend fun markWalletsRecoveryRequired(walletIds: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPendingTransactionInternal(transaction: PendingNetworkTransactionLocal)

    @Query(
        """
        select count(*) from pendingNetworkTransactions
        where state not in ('FINALIZED', 'REJECTED')
        """
    )
    suspend fun countUnresolvedTransactions(): Int

    @Query(
        """
        select count(*) from pendingNetworkTransactions
        where chainId is null or not (
            (networkId = 'sora2' and chainId =
                '0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5')
            or (networkId = 'minamoto' and chainId =
                '00000000-0000-0000-0000-000000000753')
            or (
                networkId = 'taira'
                and chainId = :tairaChainId
                and substr(localId, 1, length(:tairaPendingJournalPrefix)) =
                    :tairaPendingJournalPrefix
            )
        )
        """
    )
    suspend fun countPendingTransactionsRequiringChainRecoveryForChain(
        tairaChainId: String?,
        tairaPendingJournalPrefix: String,
    ): Int

    suspend fun countPendingTransactionsRequiringChainRecovery(): Int {
        val tairaBinding = TairaDeployment.binding
        return countPendingTransactionsRequiringChainRecoveryForChain(
            tairaBinding?.currentChainId,
            tairaBinding?.pendingJournalPrefix.orEmpty(),
        )
    }

    @Query(
        """
        select count(*) from pendingNetworkTransactions
        where state in ('FINALIZED', 'REJECTED')
            and (
                (networkId = 'sora2' and chainId =
                    '0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5')
                or (networkId = 'minamoto' and chainId =
                    '00000000-0000-0000-0000-000000000753')
                or (
                    networkId = 'taira'
                    and chainId = :tairaChainId
                    and substr(localId, 1, length(:tairaPendingJournalPrefix)) =
                        :tairaPendingJournalPrefix
                )
            )
        """
    )
    suspend fun countAuthoritativelyTerminalTransactionsInternal(
        tairaChainId: String?,
        tairaPendingJournalPrefix: String,
    ): Int

    @Query(
        """
        select * from pendingNetworkTransactions
        where state in ('FINALIZED', 'REJECTED')
            and (
                (networkId = 'sora2' and chainId =
                    '0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5')
                or (networkId = 'minamoto' and chainId =
                    '00000000-0000-0000-0000-000000000753')
                or (
                    networkId = 'taira'
                    and chainId = :tairaChainId
                    and substr(localId, 1, length(:tairaPendingJournalPrefix)) =
                        :tairaPendingJournalPrefix
                )
            )
        order by updatedAt, createdAt, localId
        limit :limit
        """
    )
    suspend fun getOldestAuthoritativelyTerminalTransactionsInternal(
        limit: Int,
        tairaChainId: String?,
        tairaPendingJournalPrefix: String,
    ): List<PendingNetworkTransactionLocal>

    @Query(
        """
        delete from pendingNetworkTransactions
        where localId in (:localIds)
            and state in ('FINALIZED', 'REJECTED')
            and (
                (networkId = 'sora2' and chainId =
                    '0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5')
                or (networkId = 'minamoto' and chainId =
                    '00000000-0000-0000-0000-000000000753')
                or (
                    networkId = 'taira'
                    and chainId = :tairaChainId
                    and substr(localId, 1, length(:tairaPendingJournalPrefix)) =
                        :tairaPendingJournalPrefix
                )
            )
        """
    )
    suspend fun deleteAuthoritativelyTerminalTransactionsInternal(
        localIds: List<String>,
        tairaChainId: String?,
        tairaPendingJournalPrefix: String,
    ): Int

    /**
     * Bounds only fully validated, exact-current-chain terminal rows. Any signed, submitted,
     * unknown, committed-pending-reconciliation, null-chain, or retired-chain row remains durable
     * regardless of age or storage pressure.
     */
    @Transaction
    suspend fun pruneAuthoritativelyTerminalTransactions() {
        val tairaBinding = TairaDeployment.binding
        val tairaChainId = tairaBinding?.currentChainId
        val tairaPendingJournalPrefix = tairaBinding?.pendingJournalPrefix.orEmpty()
        var terminalCount = countAuthoritativelyTerminalTransactionsInternal(
            tairaChainId,
            tairaPendingJournalPrefix,
        )
        while (terminalCount > MAX_RETAINED_TERMINAL_TRANSACTIONS) {
            val batchSize = minOf(
                terminalCount - MAX_RETAINED_TERMINAL_TRANSACTIONS,
                MAX_TERMINAL_PRUNE_BATCH,
            )
            val candidates = getOldestAuthoritativelyTerminalTransactionsInternal(
                batchSize,
                tairaChainId,
                tairaPendingJournalPrefix,
            )
            check(candidates.size == batchSize) {
                "PENDING_TRANSACTION_TERMINAL_PRUNE_MISMATCH"
            }
            candidates.forEach(::validatePendingTransactionForWrite)
            check(candidates.all {
                it.state in TERMINAL_PENDING_STATES && !it.submissionIsAmbiguous
            }) { "PENDING_TRANSACTION_TERMINAL_PRUNE_UNSAFE" }
            val localIds = candidates.map(PendingNetworkTransactionLocal::localId)
            check(
                deleteAuthoritativelyTerminalTransactionsInternal(
                    localIds,
                    tairaChainId,
                    tairaPendingJournalPrefix,
                ) == localIds.size
            ) { "PENDING_TRANSACTION_TERMINAL_PRUNE_MISMATCH" }
            terminalCount = countAuthoritativelyTerminalTransactionsInternal(
                tairaChainId,
                tairaPendingJournalPrefix,
            )
        }
    }

    @Transaction
    suspend fun insertPendingTransaction(transaction: PendingNetworkTransactionLocal) {
        validatePendingTransactionForWrite(transaction)
        check(getActiveDeletionOperation() == null) {
            "WALLET_DELETION_ACTIVE"
        }
        check(countPendingTransactionsRequiringChainRecovery() == 0) {
            "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
        }
        check(countUnresolvedTransactions() < MAX_ACTIVE_PENDING_TRANSACTIONS) {
            "PENDING_TRANSACTION_LIMIT_EXCEEDED"
        }
        insertPendingTransactionInternal(transaction)
        pruneAuthoritativelyTerminalTransactions()
    }

    @Query(
        """
        select * from pendingNetworkTransactions
        where walletId = :walletId
            and (
                state not in ('FINALIZED', 'REJECTED')
                or chainId is null
                or not (
                    (networkId = 'sora2' and chainId =
                        '0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5')
                    or (networkId = 'minamoto' and chainId =
                        '00000000-0000-0000-0000-000000000753')
                    or (
                        networkId = 'taira'
                        and chainId = :tairaChainId
                        and substr(localId, 1, length(:tairaPendingJournalPrefix)) =
                            :tairaPendingJournalPrefix
                    )
                )
            )
        order by createdAt desc
        """
    )
    fun observePendingTransactionsForChain(
        walletId: String,
        tairaChainId: String?,
        tairaPendingJournalPrefix: String,
    ): Flow<List<PendingNetworkTransactionLocal>>

    fun observePendingTransactions(walletId: String): Flow<List<PendingNetworkTransactionLocal>> {
        val tairaBinding = TairaDeployment.binding
        return observePendingTransactionsForChain(
            walletId,
            tairaBinding?.currentChainId,
            tairaBinding?.pendingJournalPrefix.orEmpty(),
        )
    }

    @Query(
        """
        select * from pendingNetworkTransactions
        where state not in ('FINALIZED', 'REJECTED')
            or chainId is null
            or not (
                (networkId = 'sora2' and chainId =
                    '0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5')
                or (networkId = 'minamoto' and chainId =
                    '00000000-0000-0000-0000-000000000753')
                or (
                    networkId = 'taira'
                    and chainId = :tairaChainId
                    and substr(localId, 1, length(:tairaPendingJournalPrefix)) =
                        :tairaPendingJournalPrefix
                )
            )
        order by createdAt
        limit :limit
        """
    )
    suspend fun getUnresolvedTransactionsInternal(
        limit: Int,
        tairaChainId: String?,
        tairaPendingJournalPrefix: String,
    ): List<PendingNetworkTransactionLocal>

    @Transaction
    suspend fun getUnresolvedTransactions(): List<PendingNetworkTransactionLocal> {
        val tairaBinding = TairaDeployment.binding
        val transactions = getUnresolvedTransactionsInternal(
            MAX_ACTIVE_PENDING_TRANSACTIONS + 1,
            tairaBinding?.currentChainId,
            tairaBinding?.pendingJournalPrefix.orEmpty(),
        )
        check(transactions.size <= MAX_ACTIVE_PENDING_TRANSACTIONS) {
            "PENDING_TRANSACTION_LIMIT_EXCEEDED"
        }
        transactions.forEach(::validatePendingTransaction)
        return transactions
    }

    @Query("select * from pendingNetworkTransactions where localId = :localId")
    suspend fun getPendingTransaction(localId: String): PendingNetworkTransactionLocal?

    @Query(
        """
        update pendingNetworkTransactions
        set transactionHash = :transactionHash,
            state = :state,
            submissionIsAmbiguous = :ambiguous,
            updatedAt = :updatedAt
        where localId = :localId
            and (
                chainId = :chainId
                or (chainId is null and :chainId is null)
            )
        """
    )
    suspend fun updatePendingTransactionInternal(
        localId: String,
        chainId: String?,
        transactionHash: String?,
        state: String,
        ambiguous: Boolean,
        updatedAt: Long,
    ): Int

    @Transaction
    suspend fun updatePendingTransaction(
        localId: String,
        transactionHash: String?,
        state: String,
        ambiguous: Boolean,
        updatedAt: Long,
    ) {
        val current = checkNotNull(getPendingTransaction(localId)) {
            "PENDING_TRANSACTION_MISSING"
        }
        validatePendingTransactionForWrite(current)
        if (current.state in TERMINAL_PENDING_STATES) {
            check(current.transactionHash == transactionHash) {
                "PENDING_TRANSACTION_HASH_CHANGED"
            }
            return
        }
        val monotonicUpdatedAt = maxOf(updatedAt, current.updatedAt)
        val replacement = current.copy(
            transactionHash = transactionHash,
            state = state,
            submissionIsAmbiguous = ambiguous,
            updatedAt = monotonicUpdatedAt,
        )
        validatePendingTransactionForWrite(replacement)
        check(
            updatePendingTransactionInternal(
                localId = localId,
                chainId = current.chainId,
                transactionHash = transactionHash,
                state = state,
                ambiguous = ambiguous,
                updatedAt = monotonicUpdatedAt,
            ) == 1
        ) { "PENDING_TRANSACTION_UPDATE_MISMATCH" }
        if (state in TERMINAL_PENDING_STATES) {
            pruneAuthoritativelyTerminalTransactions()
        }
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSora2PendingSubmissionInternal(
        submission: Sora2PendingSubmissionLocal,
    )

    @Query(
        """
        select count(*) from sora2PendingSubmissions
        where state not in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
        """
    )
    suspend fun countUnresolvedSora2PendingSubmissionsInternal(): Int

    @Query(
        """
        select count(*) from sora2PendingSubmissions
        where state in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
        """
    )
    suspend fun countTerminalSora2PendingSubmissionsInternal(): Int

    @Query(
        """
        select count(*) from sora2PendingSubmissions as sora2
        where sora2.state in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
            and not (
                sora2.operationKind = 'POLKAMARKT_SORA2_MUTATION'
                and exists (
                    select 1 from pendingNetworkTransactions as pending
                    where pending.networkId = 'sora2'
                        and pending.transactionHash = sora2.transactionHash
                        and pending.state not in ('FINALIZED', 'REJECTED')
                )
            )
        """
    )
    suspend fun countPrunableTerminalSora2PendingSubmissionsInternal(): Int

    @Query(
        """
        select sora2.* from sora2PendingSubmissions as sora2
        where sora2.state in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
            and not (
                sora2.operationKind = 'POLKAMARKT_SORA2_MUTATION'
                and exists (
                    select 1 from pendingNetworkTransactions as pending
                    where pending.networkId = 'sora2'
                        and pending.transactionHash = sora2.transactionHash
                        and pending.state not in ('FINALIZED', 'REJECTED')
                )
            )
        order by sora2.updatedAt, sora2.createdAt, sora2.localId
        limit :limit
        """
    )
    suspend fun getOldestTerminalSora2PendingSubmissionsInternal(
        limit: Int,
    ): List<Sora2PendingSubmissionLocal>

    @Query(
        """
        delete from sora2PendingSubmissions
        where localId in (:localIds)
            and state in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
        """
    )
    suspend fun deleteTerminalSora2PendingSubmissionsInternal(
        localIds: List<String>,
    ): Int

    @Transaction
    suspend fun pruneTerminalSora2PendingSubmissions() {
        var terminalCount = countPrunableTerminalSora2PendingSubmissionsInternal()
        while (terminalCount > MAX_RETAINED_TERMINAL_SORA2_SUBMISSIONS) {
            val batchSize = minOf(
                terminalCount - MAX_RETAINED_TERMINAL_SORA2_SUBMISSIONS,
                MAX_TERMINAL_PRUNE_BATCH,
            )
            val candidates = getOldestTerminalSora2PendingSubmissionsInternal(batchSize)
            check(candidates.size == batchSize) { "SORA2_PENDING_TERMINAL_PRUNE_MISMATCH" }
            candidates.forEach(::validateSora2PendingSubmission)
            check(candidates.all { it.isAuthoritativelyTerminal }) {
                "SORA2_PENDING_TERMINAL_PRUNE_UNSAFE"
            }
            val localIds = candidates.map(Sora2PendingSubmissionLocal::localId)
            check(deleteTerminalSora2PendingSubmissionsInternal(localIds) == localIds.size) {
                "SORA2_PENDING_TERMINAL_PRUNE_MISMATCH"
            }
            terminalCount = countPrunableTerminalSora2PendingSubmissionsInternal()
        }
    }

    @Transaction
    suspend fun insertSora2PendingSubmission(submission: Sora2PendingSubmissionLocal) {
        validateSora2PendingSubmission(submission)
        check(getActiveDeletionOperation() == null) { "WALLET_DELETION_ACTIVE" }
        check(
            countUnresolvedSora2PendingSubmissionsInternal() <
                MAX_ACTIVE_SORA2_PENDING_SUBMISSIONS
        ) { "SORA2_PENDING_SUBMISSION_LIMIT_EXCEEDED" }
        insertSora2PendingSubmissionInternal(submission)
        pruneTerminalSora2PendingSubmissions()
    }

    @Query(
        """
        select * from sora2PendingSubmissions
        where state not in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
        order by updatedAt, createdAt, localId
        limit :limit
        """
    )
    suspend fun getUnresolvedSora2PendingSubmissionsInternal(
        limit: Int,
    ): List<Sora2PendingSubmissionLocal>

    @Transaction
    suspend fun getUnresolvedSora2PendingSubmissions(): List<Sora2PendingSubmissionLocal> {
        val submissions = getUnresolvedSora2PendingSubmissionsInternal(
            MAX_ACTIVE_SORA2_PENDING_SUBMISSIONS + 1
        )
        check(submissions.size <= MAX_ACTIVE_SORA2_PENDING_SUBMISSIONS) {
            "SORA2_PENDING_SUBMISSION_LIMIT_EXCEEDED"
        }
        submissions.forEach(::validateSora2PendingSubmission)
        return submissions
    }

    @Query(
        """
        select * from sora2PendingSubmissions
        where walletId = :walletId
            and state not in ('FINALIZED_SUCCESS', 'FINALIZED_FAILURE', 'EXPIRED_NOT_INCLUDED')
        order by createdAt desc, localId
        """
    )
    fun observeUnresolvedSora2PendingSubmissions(
        walletId: String,
    ): Flow<List<Sora2PendingSubmissionLocal>>

    @Query(
        """
        select * from sora2PendingSubmissions
        where walletId = :walletId
        order by createdAt desc, localId
        limit 1501
        """
    )
    suspend fun getSora2SubmissionOverlayRowsInternal(
        walletId: String,
    ): List<Sora2PendingSubmissionLocal>

    /**
     * Returns the bounded durable activity overlay without changing or pruning recovery evidence.
     * Querying both operation kinds before filtering prevents a corrupt operation value from
     * being silently hidden by a SQL predicate.
     */
    @Transaction
    suspend fun getSora2SubmissionOverlayRows(
        walletId: String,
    ): List<Sora2PendingSubmissionLocal> {
        check(walletId.isNotBlank() && walletId.length <= MAX_WALLET_ID_LENGTH) {
            "SORA2_PENDING_OVERLAY_WALLET_INVALID"
        }
        val rows = getSora2SubmissionOverlayRowsInternal(walletId)
        check(
            rows.size <=
                MAX_ACTIVE_SORA2_PENDING_SUBMISSIONS +
                MAX_RETAINED_TERMINAL_SORA2_SUBMISSIONS +
                MAX_ACTIVE_PENDING_TRANSACTIONS
        ) { "SORA2_PENDING_OVERLAY_LIMIT_EXCEEDED" }
        rows.forEach(::validateSora2PendingSubmission)
        check(rows.all { it.walletId == walletId }) {
            "SORA2_PENDING_OVERLAY_SCOPE_MISMATCH"
        }
        return rows
    }

    @Query("select * from sora2PendingSubmissions where localId = :localId")
    suspend fun getSora2PendingSubmission(localId: String): Sora2PendingSubmissionLocal?

    @Query(
        """
        update sora2PendingSubmissions
        set state = :state,
            submissionIsAmbiguous = :ambiguous,
            terminalBlockNumber = :terminalBlockNumber,
            terminalBlockHash = :terminalBlockHash,
            terminalFinalizedHeight = :terminalFinalizedHeight,
            updatedAt = :updatedAt
        where localId = :localId
            and transactionHash = :transactionHash
            and state = :expectedState
            and updatedAt = :expectedUpdatedAt
        """
    )
    suspend fun updateSora2PendingSubmissionInternal(
        localId: String,
        transactionHash: String,
        expectedState: String,
        expectedUpdatedAt: Long,
        state: String,
        ambiguous: Boolean,
        terminalBlockNumber: Long?,
        terminalBlockHash: String?,
        terminalFinalizedHeight: Long?,
        updatedAt: Long,
    ): Int

    @Transaction
    suspend fun updateSora2PendingSubmission(
        expected: Sora2PendingSubmissionLocal,
        state: String,
        ambiguous: Boolean,
        terminalBlockNumber: Long? = null,
        terminalBlockHash: String? = null,
        terminalFinalizedHeight: Long? = null,
        updatedAt: Long,
    ): Sora2PendingSubmissionLocal {
        val current = checkNotNull(getSora2PendingSubmission(expected.localId)) {
            "SORA2_PENDING_SUBMISSION_MISSING"
        }
        validateSora2PendingSubmission(current)
        check(current == expected) { "SORA2_PENDING_SUBMISSION_CHANGED" }
        val monotonicUpdatedAt = maxOf(updatedAt, current.updatedAt)
        val replacement = current.copy(
            state = state,
            submissionIsAmbiguous = ambiguous,
            terminalBlockNumber = terminalBlockNumber,
            terminalBlockHash = terminalBlockHash,
            terminalFinalizedHeight = terminalFinalizedHeight,
            updatedAt = monotonicUpdatedAt,
        )
        validateSora2PendingTransition(current, replacement)
        validateSora2PendingSubmission(replacement)
        check(
            updateSora2PendingSubmissionInternal(
                localId = current.localId,
                transactionHash = current.transactionHash,
                expectedState = current.state,
                expectedUpdatedAt = current.updatedAt,
                state = replacement.state,
                ambiguous = replacement.submissionIsAmbiguous,
                terminalBlockNumber = replacement.terminalBlockNumber,
                terminalBlockHash = replacement.terminalBlockHash,
                terminalFinalizedHeight = replacement.terminalFinalizedHeight,
                updatedAt = replacement.updatedAt,
            ) == 1
        ) { "SORA2_PENDING_SUBMISSION_UPDATE_MISMATCH" }
        check(getSora2PendingSubmission(current.localId) == replacement) {
            "SORA2_PENDING_SUBMISSION_READBACK_MISMATCH"
        }
        if (replacement.isAuthoritativelyTerminal) {
            pruneTerminalSora2PendingSubmissions()
        }
        return replacement
    }

    @Query(
        """
        delete from sora2PendingSubmissions
        where localId = :localId
            and transactionHash = :transactionHash
            and state = 'SIGNED_BEFORE_TRANSPORT'
            and updatedAt = :expectedUpdatedAt
        """
    )
    suspend fun deleteSora2DefinitelyNotSubmittedInternal(
        localId: String,
        transactionHash: String,
        expectedUpdatedAt: Long,
    ): Int

    @Transaction
    suspend fun deleteSora2DefinitelyNotSubmitted(
        expected: Sora2PendingSubmissionLocal,
    ) {
        val current = checkNotNull(getSora2PendingSubmission(expected.localId)) {
            "SORA2_PENDING_SUBMISSION_MISSING"
        }
        validateSora2PendingSubmission(current)
        check(
            current == expected &&
                current.state == Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT
        ) { "SORA2_PENDING_SUBMISSION_NOT_DEFINITIVE" }
        check(
            deleteSora2DefinitelyNotSubmittedInternal(
                localId = current.localId,
                transactionHash = current.transactionHash,
                expectedUpdatedAt = current.updatedAt,
            ) == 1
        ) { "SORA2_PENDING_SUBMISSION_DELETE_MISMATCH" }
    }

    @Query("delete from walletMigrationJournal")
    suspend fun deleteMigrationJournal()

    @Transaction
    suspend fun installDerivedAccounts(
        walletId: String,
        secretSource: String,
        accounts: List<NetworkAccountLocal>,
    ) {
        accounts.forEach { upsertNetworkAccount(it) }
        updateWalletVerification(walletId, "VERIFIED", secretSource)
    }

    companion object {
        private const val MAX_ACTIVE_PENDING_TRANSACTIONS = 500
        private const val MAX_RETAINED_TERMINAL_TRANSACTIONS = 500
        private const val MAX_ACTIVE_SORA2_PENDING_SUBMISSIONS = 500
        private const val MAX_RETAINED_TERMINAL_SORA2_SUBMISSIONS = 500
        private const val MAX_TERMINAL_PRUNE_BATCH = 100
        private const val MAX_PENDING_ID_LENGTH = 128
        private const val MAX_WALLET_ID_LENGTH = 256
        private const val MAX_ASSET_ID_LENGTH = 512
        private const val MAX_RECIPIENT_LENGTH = 512
        private val NETWORK_IDS = setOf("sora2", "minamoto", "taira")
        private val STATES = setOf(
            "SIGNED",
            "SUBMITTED",
            "UNKNOWN",
            "COMMITTED_PENDING_RECONCILIATION",
            "FINALIZED",
            "REJECTED",
        )
        private val TERMINAL_PENDING_STATES = setOf("FINALIZED", "REJECTED")
        private val LOWER_HEX_64 = Regex("^[0-9a-f]{64}$")
        private val CANONICAL_HASH = Regex("^0x[0-9a-f]{64}$")

        fun validatePendingTransaction(transaction: PendingNetworkTransactionLocal) {
            check(
                transaction.localId.isNotBlank() &&
                    transaction.localId.length <= MAX_PENDING_ID_LENGTH
            ) { "PENDING_TRANSACTION_ID_INVALID" }
            check(
                transaction.walletId.isNotBlank() &&
                    transaction.walletId.length <= MAX_WALLET_ID_LENGTH
            ) { "PENDING_TRANSACTION_WALLET_INVALID" }
            check(transaction.networkId in NETWORK_IDS) { "PENDING_TRANSACTION_NETWORK_INVALID" }
            check(
                transaction.assetId.isNotBlank() &&
                    transaction.assetId.length <= MAX_ASSET_ID_LENGTH
            ) { "PENDING_TRANSACTION_ASSET_INVALID" }
            if (hasCurrentChainIdentity(transaction)) {
                check(isCanonicalPendingHash(transaction)) {
                    "PENDING_TRANSACTION_HASH_INVALID"
                }
            }
            check(
                NexusQuantityContract.isWireQuantity(transaction.amount)
            ) { "PENDING_TRANSACTION_AMOUNT_INVALID" }
            check(
                transaction.recipient.isNotBlank() &&
                    transaction.recipient.length <= MAX_RECIPIENT_LENGTH
            ) { "PENDING_TRANSACTION_RECIPIENT_INVALID" }
            check(transaction.state in STATES) { "PENDING_TRANSACTION_STATE_INVALID" }
            check(
                transaction.state !in setOf("FINALIZED", "REJECTED") ||
                    !transaction.submissionIsAmbiguous
            ) { "PENDING_TRANSACTION_TERMINAL_STATE_AMBIGUOUS" }
            check(
                transaction.createdAt > 0 &&
                transaction.updatedAt >= transaction.createdAt
            ) { "PENDING_TRANSACTION_TIMESTAMP_INVALID" }
        }

        /**
         * Structural reads deliberately accept a null or retired chain ID as immutable recovery
         * evidence. Every new insert and every transition is stricter and requires the exact
         * reviewed identity configured for that wallet network.
         */
        fun validatePendingTransactionForWrite(
            transaction: PendingNetworkTransactionLocal,
        ) {
            validatePendingTransaction(transaction)
            check(hasCurrentChainIdentity(transaction)) {
                "PENDING_TRANSACTION_CHAIN_IDENTITY_RECOVERY_REQUIRED"
            }
        }

        fun hasCurrentChainIdentity(transaction: PendingNetworkTransactionLocal): Boolean {
            val tairaBinding = TairaDeployment.binding
            return hasCurrentChainIdentityForBinding(
                transaction = transaction,
                tairaChainId = tairaBinding?.currentChainId,
                tairaPendingJournalPrefix = tairaBinding?.pendingJournalPrefix.orEmpty(),
            )
        }

        /**
         * Explicit projection used by migration tests and SQL parity checks. Taira authority is
         * the conjunction of the signed current chain UUID and the exact manifest namespace;
         * legacy same-UUID rows intentionally fail this predicate.
         */
        fun hasCurrentChainIdentityForBinding(
            transaction: PendingNetworkTransactionLocal,
            tairaChainId: String?,
            tairaPendingJournalPrefix: String,
        ): Boolean = when (WalletNetworkId.fromWireId(transaction.networkId)) {
            WalletNetworkId.SORA2 ->
                transaction.chainId == WalletNetworkChainIdentity.SORA2
            WalletNetworkId.MINAMOTO ->
                transaction.chainId == WalletNetworkChainIdentity.MINAMOTO
            WalletNetworkId.TAIRA ->
                tairaChainId != null &&
                    tairaPendingJournalPrefix.isNotEmpty() &&
                    transaction.chainId == tairaChainId &&
                    transaction.localId.startsWith(tairaPendingJournalPrefix)
            null -> false
        }

        fun validateSora2PendingSubmission(submission: Sora2PendingSubmissionLocal) {
            check(
                submission.recoverySchemaVersion ==
                    Sora2PendingSubmissionLocal.RECOVERY_SCHEMA_VERSION
            ) { "SORA2_PENDING_SCHEMA_INVALID" }
            check(
                submission.walletId.isNotBlank() &&
                    submission.walletId.length <= MAX_WALLET_ID_LENGTH &&
                    submission.networkId == Sora2PendingSubmissionLocal.NETWORK_SORA2
            ) { "SORA2_PENDING_SCOPE_INVALID" }
            check(
                isCanonicalNonZeroHash(submission.transactionHash) &&
                    submission.localId ==
                    "sora2:${submission.transactionHash.removePrefix("0x")}" &&
                    submission.localId.length <= MAX_PENDING_ID_LENGTH
            ) { "SORA2_PENDING_HASH_INVALID" }
            check(
                LOWER_HEX_64.matches(submission.accountId) &&
                    LOWER_HEX_64.matches(submission.publicKey) &&
                    submission.accountId.any { it != '0' } &&
                    submission.accountId == submission.publicKey
            ) { "SORA2_PENDING_ACCOUNT_INVALID" }
            check(isCanonicalNonZeroHash(submission.genesisHash)) {
                "SORA2_PENDING_GENESIS_INVALID"
            }
            check(
                submission.specVersion > 0 &&
                    submission.transactionVersion > 0 &&
                    LOWER_HEX_64.matches(submission.metadataSha256) &&
                    LOWER_HEX_64.matches(submission.typesSha256) &&
                    submission.metadataSha256.any { it != '0' } &&
                    submission.typesSha256.any { it != '0' }
            ) { "SORA2_PENDING_RUNTIME_INVALID" }
            val expectedDeath = runCatching {
                Math.addExact(submission.eraBirthBlock, submission.eraPeriod.toLong())
            }.getOrNull()
            check(
                submission.eraBirthBlock >= 0L &&
                    submission.eraPeriod == 64 &&
                    submission.eraPhase ==
                    (submission.eraBirthBlock % submission.eraPeriod).toInt() &&
                    expectedDeath == submission.eraDeathBlockExclusive &&
                    isCanonicalNonZeroHash(submission.eraBirthBlockHash)
            ) { "SORA2_PENDING_ERA_INVALID" }
            check(submission.operationKind in Sora2PendingSubmissionLocal.OPERATION_KINDS) {
                "SORA2_PENDING_OPERATION_INVALID"
            }
            check(
                submission.state in
                    Sora2PendingSubmissionLocal.UNRESOLVED_STATES +
                    Sora2PendingSubmissionLocal.TERMINAL_STATES &&
                    submission.submissionIsAmbiguous ==
                    (submission.state ==
                        Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN)
            ) { "SORA2_PENDING_STATE_INVALID" }
            when (submission.state) {
                Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
                Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE -> {
                    check(
                        submission.terminalBlockNumber != null &&
                            submission.terminalBlockNumber >= submission.eraBirthBlock &&
                            submission.terminalBlockNumber <
                            submission.eraDeathBlockExclusive &&
                            submission.terminalBlockHash?.let(::isCanonicalNonZeroHash) == true &&
                            submission.terminalFinalizedHeight != null &&
                            submission.terminalFinalizedHeight >=
                            submission.terminalBlockNumber
                    ) { "SORA2_PENDING_TERMINAL_PROOF_INVALID" }
                }
                Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED -> {
                    check(
                        submission.terminalBlockNumber == null &&
                            submission.terminalBlockHash == null &&
                            submission.terminalFinalizedHeight != null &&
                            submission.terminalFinalizedHeight >=
                            submission.eraDeathBlockExclusive
                    ) { "SORA2_PENDING_EXPIRY_PROOF_INVALID" }
                }
                else -> check(
                    submission.terminalBlockNumber == null &&
                        submission.terminalBlockHash == null &&
                        submission.terminalFinalizedHeight == null
                ) { "SORA2_PENDING_PRETERMINAL_PROOF_INVALID" }
            }
            check(
                submission.createdAt > 0L &&
                    submission.updatedAt >= submission.createdAt
            ) { "SORA2_PENDING_TIMESTAMP_INVALID" }
        }

        private fun validateSora2PendingTransition(
            current: Sora2PendingSubmissionLocal,
            replacement: Sora2PendingSubmissionLocal,
        ) {
            val allowed = when (current.state) {
                Sora2PendingSubmissionLocal.STATE_SIGNED_BEFORE_TRANSPORT ->
                    replacement.state ==
                        Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN ||
                        replacement.state in Sora2PendingSubmissionLocal.TERMINAL_STATES
                Sora2PendingSubmissionLocal.STATE_SUBMISSION_UNKNOWN ->
                    replacement.state in setOf(
                        Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY,
                        Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS,
                        Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE,
                        Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED,
                    )
                Sora2PendingSubmissionLocal.STATE_SUBMITTED_AWAITING_FINALITY ->
                    replacement.state in Sora2PendingSubmissionLocal.TERMINAL_STATES
                else -> false
            }
            check(allowed) { "SORA2_PENDING_TRANSITION_INVALID" }
            check(
                current.copy(
                    state = replacement.state,
                    submissionIsAmbiguous = replacement.submissionIsAmbiguous,
                    terminalBlockNumber = replacement.terminalBlockNumber,
                    terminalBlockHash = replacement.terminalBlockHash,
                    terminalFinalizedHeight = replacement.terminalFinalizedHeight,
                    updatedAt = replacement.updatedAt,
                ) == replacement
            ) { "SORA2_PENDING_IDENTITY_CHANGED" }
        }

        private fun isCanonicalNonZeroHash(value: String): Boolean =
            CANONICAL_HASH.matches(value) && value.drop(2).any { it != '0' }

        private fun isCanonicalPendingHash(
            transaction: PendingNetworkTransactionLocal,
        ): Boolean {
            val hash = transaction.transactionHash ?: return false
            val normalized = NexusTransactionHash.normalized(hash) ?: return false
            return when {
                PolkamarktPendingAssetId.parse(transaction.assetId) != null ->
                    transaction.networkId == "sora2" &&
                        hash.startsWith("0x") &&
                        normalized == hash.removePrefix("0x")
                transaction.networkId in setOf("minamoto", "taira") &&
                    NexusAssetDefinitionIdentity.hasCanonicalWireShape(
                        transaction.assetId
                    ) -> normalized == hash
                else -> false
            }
        }
    }
}
