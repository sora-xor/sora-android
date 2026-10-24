package jp.co.soramitsu.sora.substrate.substrate

/** Test double with the same merge rules as the file-backed store and no I/O. */
class InMemorySora2SubmissionDetailsStore : Sora2SubmissionDetailsStore {
    val entries = linkedMapOf<Pair<String, String>, Sora2SubmissionDetails>()

    override suspend fun get(walletId: String, transactionHash: String) =
        entries[walletId to transactionHash]

    override suspend fun list(walletId: String) =
        entries.values.filter { it.walletId == walletId }

    override suspend fun recordActivity(
        walletId: String,
        transactionHash: String,
        activity: Sora2SubmissionActivity,
    ) = update(walletId, transactionHash) {
        it.copy(activity = activity.copy(duplicateConfirmed = false))
    }

    override suspend fun recordNodeRefusal(
        walletId: String,
        transactionHash: String,
        refusal: Sora2NodeRefusal,
    ) = update(walletId, transactionHash) { it.copy(nodeRefusal = refusal) }

    override suspend fun recordChainFailure(
        walletId: String,
        transactionHash: String,
        detail: String,
    ) = update(walletId, transactionHash) { it.copy(chainFailure = detail) }

    override suspend fun remove(walletId: String, transactionHash: String) {
        entries.remove(walletId to transactionHash)
    }

    override suspend fun retainOnly(
        walletId: String,
        transactionHashes: Set<String>,
        createdBefore: Long,
    ) {
        entries.values.removeAll {
            it.walletId == walletId &&
                it.createdAt < createdBefore &&
                it.transactionHash !in transactionHashes
        }
    }

    private fun update(
        walletId: String,
        transactionHash: String,
        change: (Sora2SubmissionDetails) -> Sora2SubmissionDetails,
    ) {
        val key = walletId to transactionHash
        entries[key] = change(
            entries[key] ?: Sora2SubmissionDetails(walletId, transactionHash, createdAt = 1L)
        )
    }
}
