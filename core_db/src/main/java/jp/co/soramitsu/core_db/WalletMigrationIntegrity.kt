package jp.co.soramitsu.core_db

import java.security.MessageDigest
import jp.co.soramitsu.core_db.model.SoraAccountLocal

/**
 * Canonical hash shared by pre-Room recovery inspection and the application migration.
 */
object WalletMigrationIntegrity {

    fun snapshotHash(
        accounts: List<SoraAccountLocal>,
        selectedWalletId: String,
    ): String = snapshotHashPairs(
        accounts = accounts.map { it.substrateAddress to it.accountName },
        selectedWalletId = selectedWalletId,
    )

    fun snapshotHashPairs(
        accounts: List<Pair<String, String>>,
        selectedWalletId: String,
    ): String {
        val canonical = buildString {
            accounts.sortedBy { it.first }.forEach { (address, name) ->
                append(address)
                append('\u0000')
                append(name)
                append('\n')
            }
            append("selected\u0000")
            append(selectedWalletId)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
    }
}
