package jp.co.soramitsu.feature_account_impl.domain.keys

import java.security.MessageDigest
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialField
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsDatasource
import jp.co.soramitsu.feature_account_api.domain.interfaces.StoredCredential
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair

/**
 * Stand-in for the native sr25519 library, which JVM tests cannot load.
 *
 * Key pairs are real: [generateKeypair] is `MiniSecretKey::expand_to_keypair(ExpansionMode::Ed25519)`
 * of schnorrkel written out in Kotlin (SHA-512 of the seed, Ed25519 clamping, division by the
 * cofactor; the upper half is the nonce), and the public key comes from the production
 * [Sr25519PublicKeyDerivation]. `HistoricalSr25519VectorsTest` checks both against the vectors of
 * the released native library, so every address used by the JVM tests is a real SORA address.
 *
 * Only the signature itself is stood in for: [signAndVerify] answers what a Schnorr signature
 * does (it verifies only under the public key of the scalar that made it, whatever the nonce).
 * The real sign and verify run on a device (WalletUpgradeScenarioTest, Sr25519NativeContractTest).
 */
class FakeStoredKeyCrypto : StoredKeyCrypto {
    /** The native call throws (as it does for input it rejects). */
    var throwOnSign = false

    /** The native verification answers false although the key pair belongs together. */
    var signatureVerifies = true
    val challenges = mutableListOf<ByteArray>()

    override fun generateKeypair(seed: ByteArray): Sr25519Keypair = realSr25519Keypair(seed)

    override fun signAndVerify(
        keypair: Sr25519Keypair,
        challenge: ByteArray,
        expectedPublicKey: ByteArray,
    ): Boolean {
        if (throwOnSign) throw IllegalStateException("Provided private key is invalid.")
        challenges += challenge.copyOf()
        val public = Sr25519PublicKeyDerivation.publicKeyOrNull(keypair.privateKey)
        return signatureVerifies && public != null &&
            MessageDigest.isEqual(public, expectedPublicKey) &&
            MessageDigest.isEqual(keypair.publicKey, expectedPublicKey)
    }
}

/** schnorrkel `MiniSecretKey::expand_to_keypair(ExpansionMode::Ed25519).to_bytes()` in Kotlin. */
fun realSr25519Keypair(seed: ByteArray): Sr25519Keypair {
    require(seed.size == 32) { "seed must be 32 bytes" }
    val hash = MessageDigest.getInstance("SHA-512").digest(seed)
    val key = hash.copyOfRange(0, 32)
    key[0] = (key[0].toInt() and 248).toByte()
    key[31] = (key[31].toInt() and 63).toByte()
    key[31] = (key[31].toInt() or 64).toByte()
    // divide_scalar_bytes_by_cofactor: shift the little-endian value right by three bits.
    var low = 0
    for (index in 31 downTo 0) {
        val byte = key[index].toInt() and 0xff
        key[index] = ((byte ushr 3) or low).toByte()
        low = (byte and 0b111) shl 5
    }
    val public = requireNotNull(Sr25519PublicKeyDerivation.publicKeyOrNull(key))
    return Sr25519Keypair(key, public, hash.copyOfRange(32, 64))
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
