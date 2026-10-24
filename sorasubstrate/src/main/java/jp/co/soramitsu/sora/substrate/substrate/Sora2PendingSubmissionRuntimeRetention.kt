package jp.co.soramitsu.sora.substrate.substrate

import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.sora.substrate.runtime.Sora2RecoveryRuntimeIdentity
import jp.co.soramitsu.sora.substrate.runtime.Sora2RecoveryRuntimeRetention
import kotlinx.coroutines.CancellationException

/**
 * Keeps the runtime cache generation of every unresolved SORA2 pending submission. Without it, a
 * mainnet runtime upgrade published while a submission is still unresolved would prune the only
 * local copy of the runtime that can decode its status. The journal is read only (never
 * changed); while the upgrade gate is closed or the read fails, pruning is skipped entirely.
 */
@Singleton
class Sora2PendingSubmissionRuntimeRetention @Inject constructor(
    private val database: Lazy<AppDatabase>,
) : Sora2RecoveryRuntimeRetention {

    override suspend fun retainedRecoveryRuntimes(): Set<Sora2RecoveryRuntimeIdentity>? {
        if (WalletUpgradeBackup.blockingFailure() != null) return null
        return try {
            database.get().walletIdentityDao()
                .getUnresolvedSora2PendingSubmissions()
                .mapTo(linkedSetOf()) { row ->
                    Sora2RecoveryRuntimeIdentity(
                        genesisHash = row.genesisHash,
                        specVersion = row.specVersion,
                        metadataSha256 = row.metadataSha256,
                        typesSha256 = row.typesSha256,
                    )
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            FirebaseWrapper.recordException(error)
            null
        }
    }
}
