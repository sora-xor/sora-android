package jp.co.soramitsu.feature_account_impl.domain.keys

import java.security.MessageDigest
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

/**
 * Stand-in for the native sr25519 library, which JVM tests cannot load. A key pair is derived
 * from a 32-byte seed by hashing, and a signature "verifies" only when the private half is the
 * one that belongs to the expected public key. The nonce plays no part, as in the real library
 * (see Sr25519NativeContractTest, which runs the real one on a device).
 */
class FakeStoredKeyCrypto : StoredKeyCrypto {
    var throwOnSign = false
    val challenges = mutableListOf<ByteArray>()

    override fun generateKeypair(seed: ByteArray): Sr25519Keypair {
        require(seed.size == 32) { "seed must be 32 bytes" }
        val public = hash("public", seed)
        return Sr25519Keypair(privateOf(public), public, hash("nonce", public))
    }

    override fun signAndVerify(
        keypair: Sr25519Keypair,
        challenge: ByteArray,
        expectedPublicKey: ByteArray,
    ): Boolean {
        if (throwOnSign) throw IllegalStateException("Provided private key is invalid.")
        challenges += challenge.copyOf()
        return MessageDigest.isEqual(keypair.privateKey, privateOf(expectedPublicKey))
    }

    private fun privateOf(public: ByteArray) = hash("private", public)

    private fun hash(label: String, material: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(label.encodeToByteArray() + byteArrayOf(0) + material)
}

/**
 * The credential preferences in memory: plain values per preference key, with the production
 * rules that matter to the repository (add-only writes, unreadable values, the one replacing
 * write of the confirmed repair). Every write is recorded.
 */
class FakeCredentialsDatasource : CredentialsDatasource {
    val values = linkedMapOf<String, String>()
    val unreadable = mutableSetOf<String>()
    val watchOnly = mutableSetOf<String>()
    val addOnlyWrites = mutableListOf<Map<CredentialField, String>>()
    val replacingWrites = mutableListOf<Map<CredentialField, String>>()
    var legacyAddress = ""

    fun put(field: CredentialField, address: String, value: String) {
        values[key(field, address)] = value
    }

    fun putKeys(address: String, keys: Sr25519Keypair) {
        put(CredentialField.PRIVATE_KEY, address, keys.privateKey.toHex())
        put(CredentialField.PUBLIC_KEY, address, keys.publicKey.toHex())
        put(CredentialField.KEY_NONCE, address, keys.nonce.toHex())
    }

    fun markUnreadable(field: CredentialField, address: String) {
        values[key(field, address)] = "ciphertext-that-does-not-decrypt"
        unreadable += key(field, address)
    }

    fun snapshot(): Map<String, String> = values.toMap()

    private fun key(field: CredentialField, address: String) = when (field) {
        CredentialField.PRIVATE_KEY -> "prefs_priv_key"
        CredentialField.PUBLIC_KEY -> "prefs_pub_key"
        CredentialField.KEY_NONCE -> "prefs_key_nonce"
        CredentialField.MNEMONIC -> "prefs_mnemonic"
        CredentialField.SEED -> "prefs_seed"
    } + address

    private fun read(field: CredentialField, address: String): String {
        val key = key(field, address)
        if (key in unreadable) {
            throw jp.co.soramitsu.common.util.WalletDecryptionException("unreadable $key")
        }
        return values[key].orEmpty()
    }

    override suspend fun getAddress(): String = legacyAddress

    override suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String) =
        error("saveKeys overwrites: never called")

    override suspend fun retrieveKeys(suffixAddress: String): Sr25519Keypair? {
        val private = read(CredentialField.PRIVATE_KEY, suffixAddress).hexToBytes()
        val public = read(CredentialField.PUBLIC_KEY, suffixAddress).hexToBytes()
        val nonce = read(CredentialField.KEY_NONCE, suffixAddress).hexToBytes()
        return if (private.isEmpty() || public.isEmpty()) null else Sr25519Keypair(private, public, nonce)
    }

    override suspend fun saveMnemonic(mnemonic: String, suffixAddress: String) =
        error("saveMnemonic overwrites: never called")

    override suspend fun retrieveMnemonic(suffixAddress: String): String =
        read(CredentialField.MNEMONIC, suffixAddress)

    override suspend fun saveSeed(seed: String, suffixAddress: String) =
        error("saveSeed overwrites: never called")

    override suspend fun retrieveSeed(suffixAddress: String): String =
        read(CredentialField.SEED, suffixAddress)

    override suspend fun readStoredCredential(
        field: CredentialField,
        address: String,
    ): StoredCredential {
        val key = key(field, address)
        return when {
            values[key].isNullOrEmpty() -> StoredCredential.Absent
            key in unreadable -> StoredCredential.Unreadable
            else -> StoredCredential.Present(values.getValue(key))
        }
    }

    override suspend fun writeCredentialsIfAbsent(
        address: String,
        values: Map<CredentialField, String>,
    ) {
        values.keys.forEach { field ->
            check(this.values[key(field, address)].isNullOrEmpty()) { "WALLET_CREDENTIAL_ALREADY_PRESENT" }
        }
        values.forEach { (field, value) -> this.values[key(field, address)] = value }
        addOnlyWrites += values
    }

    override suspend fun replaceCredentialsForRepair(
        address: String,
        values: Map<CredentialField, String>,
    ) {
        values.forEach { (field, value) ->
            this.values[key(field, address)] = value
            unreadable -= key(field, address)
        }
        replacingWrites += values
    }

    override suspend fun isExplicitWatchOnly(suffixAddress: String): Boolean = suffixAddress in watchOnly

    override suspend fun setExplicitWatchOnly(suffixAddress: String, watchOnly: Boolean) {
        if (watchOnly) this.watchOnly += suffixAddress else this.watchOnly -= suffixAddress
    }

    override suspend fun requireKeyMaterialReadable() = Unit

    override suspend fun requireWalletPreferenceCoverage(
        walletIds: Set<String>,
        selectedAddress: String,
        requiredCoverage: Set<String>,
    ) = Unit

    override suspend fun previewWalletDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
    ): WalletPreferenceIntegrity.Hashes = error("not used")

    override suspend fun commitWalletDeletionPreferences(
        walletIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
        expectedBeforeHash: String,
        expectedAfterHash: String,
    ) = error("not used")
}

fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun String.hexToBytes(): ByteArray {
    val body = removePrefix("0x").removePrefix("0X")
    return ByteArray(body.length / 2) { body.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
