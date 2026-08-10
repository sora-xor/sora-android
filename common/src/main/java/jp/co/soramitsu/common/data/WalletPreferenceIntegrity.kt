package jp.co.soramitsu.common.data

import java.security.MessageDigest

/**
 * Canonical, privacy-preserving fingerprints for wallet-bearing preferences.
 *
 * The digest is used only as a crash-recovery precondition. It lets the deletion journal prove
 * that preferences are exactly in the state previewed by the authenticated user, or exactly in
 * the state produced by the journaled edit, without persisting or logging any plaintext secret.
 */
object WalletPreferenceIntegrity {

    private val SHA256 = Regex("^[0-9a-f]{64}$")

    data class Hashes(
        val before: String,
        val after: String,
    )

    fun isSha256(value: String): Boolean = SHA256.matches(value)

    fun hash(
        strings: Map<String, String>,
        booleans: Map<String, Boolean>,
    ): String {
        check(strings.keys.intersect(booleans.keys).isEmpty()) {
            "WALLET_PREFERENCE_TYPE_COLLISION"
        }
        check((strings.keys + booleans.keys).all(WalletPreferenceKeys::isWalletStateKey)) {
            "NON_WALLET_PREFERENCE_IN_FINGERPRINT"
        }
        return CanonicalDigest().apply {
            field("wallet-preference-snapshot-v1")
            field((strings.size + booleans.size).toString())
            strings.toSortedMap().forEach { (key, value) ->
                check(key.isNotBlank()) { "WALLET_PREFERENCE_KEY_INVALID" }
                entry(key, "string", value)
            }
            booleans.toSortedMap().forEach { (key, value) ->
                check(key.isNotBlank()) { "WALLET_PREFERENCE_KEY_INVALID" }
                entry(key, "boolean", if (value) "1" else "0")
            }
        }.finish()
    }

    private class CanonicalDigest {
        private val digest = MessageDigest.getInstance("SHA-256")

        fun entry(key: String, type: String, value: String) {
            field("entry")
            field(key)
            field(type)
            field(value)
        }

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
