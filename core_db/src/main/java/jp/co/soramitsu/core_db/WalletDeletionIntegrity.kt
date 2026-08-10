package jp.co.soramitsu.core_db

import java.security.MessageDigest
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal

/**
 * Length-prefixed canonical hashes used to reject stale or corrupted wallet-deletion requests.
 * Inputs contain public wallet metadata only.
 */
object WalletDeletionIntegrity {
    private val HASH = Regex("^[0-9a-f]{64}$")

    fun snapshotHash(
        accounts: List<SoraAccountLocal>,
        identities: List<WalletIdentityLocal>,
        networkAccounts: List<NetworkAccountLocal>,
        selectedWalletId: String,
        scope: String,
        targetWalletIds: Set<String>,
    ): String {
        val accountIds = accounts.map(SoraAccountLocal::substrateAddress)
        check(accountIds.toSet().size == accountIds.size) {
            "WALLET_DELETION_ACCOUNT_DUPLICATE"
        }
        check(
            identities.size == accounts.size &&
                identities.map(WalletIdentityLocal::walletId).toSet() == accountIds.toSet()
        ) {
            "WALLET_DELETION_IDENTITY_SET_MISMATCH"
        }
        check(networkAccounts.all { it.walletId in accountIds }) {
            "WALLET_DELETION_NETWORK_OWNER_MISMATCH"
        }
        check(
            networkAccounts.map { it.walletId to it.networkId }.toSet().size ==
                networkAccounts.size
        ) { "WALLET_DELETION_NETWORK_DUPLICATE" }
        check(selectedWalletId.isEmpty() || selectedWalletId in accountIds) {
            "WALLET_DELETION_SELECTION_MISMATCH"
        }

        return hashFields {
            field("wallet-deletion-snapshot-v1")
            field(scope)
            field(selectedWalletId)
            field(targetWalletIds.size.toString())
            targetWalletIds.sorted().forEach { target ->
                field("target")
                field(target)
            }
            accounts.sortedBy(SoraAccountLocal::substrateAddress).forEach { account ->
                field("account")
                field(account.substrateAddress)
                field(account.accountName)
            }
            identities.sortedBy(WalletIdentityLocal::walletId).forEach { identity ->
                field("identity")
                field(identity.walletId)
                field(identity.displayName)
                field(identity.secretSource)
                field(identity.migrationState)
                field(identity.derivationVersion.toString())
            }
            networkAccounts
                .sortedWith(
                    compareBy(
                        NetworkAccountLocal::walletId,
                        NetworkAccountLocal::networkId,
                    )
                )
                .forEach { account ->
                    field("network")
                    field(account.walletId)
                    field(account.networkId)
                    field(account.publicKey)
                    field(account.address)
                    field(account.derivationPath)
                    field(account.derivationVersion.toString())
                    field(if (account.enabled) "1" else "0")
                }
        }
    }

    fun requestDigest(
        operation: WalletDeletionOperationLocal,
        targetWalletIds: Set<String>,
    ): String = hashFields {
        field("wallet-deletion-request-v1")
        field(operation.operationId)
        field(operation.activeSlot.toString())
        field(operation.formatVersion.toString())
        field(operation.scope)
        field(operation.expectedWalletCount.toString())
        field(operation.targetCount.toString())
        field(operation.selectedBefore)
        field(operation.selectedAfter)
        field(operation.beforeSnapshotHash)
        field(operation.afterSnapshotHash)
        field(operation.beforePreferencesHash)
        field(operation.afterPreferencesHash)
        field(if (operation.removeLegacyUnsuffixed) "1" else "0")
        field(operation.requestedAt.toString())
        targetWalletIds.sorted().forEach { target ->
            field("target")
            field(target)
        }
    }

    fun isSha256(value: String): Boolean = HASH.matches(value)

    private inline fun hashFields(block: CanonicalDigest.() -> Unit): String =
        CanonicalDigest().apply(block).finish()

    private class CanonicalDigest {
        private val digest = MessageDigest.getInstance("SHA-256")

        fun field(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            var length = bytes.size.toLong()
            val lengthBytes = ByteArray(Long.SIZE_BYTES)
            for (index in lengthBytes.indices.reversed()) {
                lengthBytes[index] = (length and 0xff).toByte()
                length = length ushr 8
            }
            digest.update(lengthBytes)
            digest.update(bytes)
        }

        fun finish(): String = digest.digest().joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }
}
