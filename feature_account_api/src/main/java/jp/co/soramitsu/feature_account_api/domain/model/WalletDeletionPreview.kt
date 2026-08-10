package jp.co.soramitsu.feature_account_api.domain.model

enum class WalletDeletionScope {
    SINGLE,
    ALL,
}

data class WalletDeletionTarget(
    val walletId: String,
    val displayName: String,
)

/**
 * Non-authorizing preview created only after PIN/biometric authentication. The repository keeps
 * the matching one-time capability in memory; constructing or retaining this value alone cannot
 * authorize deletion.
 */
data class WalletDeletionPreview(
    val previewId: String,
    val scope: WalletDeletionScope,
    val targets: List<WalletDeletionTarget>,
    val selectedBefore: String,
    val selectedAfter: String,
    val snapshotHash: String,
    val beforePreferencesHash: String,
    val afterPreferencesHash: String,
    val removeLegacyUnsuffixed: Boolean,
    val expiresAtElapsedRealtime: Long,
)

data class WalletDeletionResult(
    val scope: WalletDeletionScope,
    val selectedAfter: String,
)
