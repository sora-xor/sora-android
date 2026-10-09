package jp.co.soramitsu.feature_account_impl.upgrade

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.File
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.util.Calendar
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.bouncycastle.util.Arrays
import org.bouncycastle.util.encoders.Base64
import org.json.JSONObject

/*
 * Storage writers copied from released SORA Wallet builds so device tests produce exactly the
 * bytes real users have on their phones. Do not modernize or "fix" anything in this file.
 *
 * Sources (read with `git show <rev>:<path>`):
 *
 * Play 3.8.6.3 = b7b0f0fcde09a11758f6802723ae556ea40878fd
 *   common/src/main/java/jp/co/soramitsu/common/util/EncryptionUtil.kt          -> [Release3863EncryptionUtil]
 *   common/src/main/java/jp/co/soramitsu/common/data/SoraPreferences.kt          -> [Release3863SoraPreferences]
 *   common/src/main/java/jp/co/soramitsu/common/data/EncryptedPreferences.kt     -> [Release3863EncryptedPreferences]
 *   feature_account_impl/.../datasource/PrefsCredentialsDatasource.kt           -> [Release3863PrefsCredentialsDatasource]
 *   feature_account_impl/.../datasource/PrefsUserDatasource.kt                  -> [Release3863PrefsUserDatasource]
 *
 * 2.3.3 = release/sora/2.3.3
 *   common/.../credentials/repository/datasource/PrefsCredentialsDatasource.kt  -> [Release233PrefsCredentialsDatasource]
 *   feature_account_impl/.../datasource/PrefsUserDatasource.kt                  -> [Release233PrefsUserDatasource]
 *
 * 2.4.0 = release/sora/2.4.0 (first multi-account release, Room 58)
 *   app/src/main/java/jp/co/soramitsu/sora/splash/domain/MigrationManager.kt    -> [Release240MigrationManager]
 *   common/.../credentials/repository/CredentialsRepositoryImpl.kt (migration functions)
 *                                                                               -> [Release240CredentialsRepository]
 *   common/.../credentials/repository/datasource/PrefsCredentialsDatasource.kt  -> [Release240PrefsCredentialsDatasource]
 *   feature_account_impl/.../repository/UserRepositoryImpl.kt (migration functions)
 *                                                                               -> [Release240UserRepository]
 *   feature_account_impl/.../datasource/PrefsUserDatasource.kt                  -> [Release240PrefsUserDatasource]
 *   common/src/main/java/jp/co/soramitsu/common/data/EncryptedPreferences.kt and SoraPreferences.kt
 *   (read halves)                                                               -> [Release240EncryptedPreferences], [Release240SoraPreferences]
 *   EncryptionUtil at 2.4.0 is byte-identical to 2.3.3 ([Release3863EncryptionUtil]).
 *
 * 3.8.6.3 account removal (b7b0f0fc): PrefsCredentialsDatasource.clearAllDataForAddress,
 *   EncryptedPreferences.clear, SoraPreferences.clear and AccountDao.clearAccount
 *                                                                               -> [Release3863AccountRemoval]
 *   common/src/main/java/jp/co/soramitsu/common/util/EncryptionUtil.kt differs from the 3.8.6.3
 *   file only in imports (spongycastle Arrays/Base64 instead of bouncycastle, same encodings), the
 *   Hilt annotations and line wrapping, so both releases share [Release3863EncryptionUtil].
 *   SoraPreferences and EncryptedPreferences write identically in both releases.
 *
 * Mechanical changes only, each marked "HARNESS:": class names, removed interfaces/`override`
 * and Hilt annotations, the DataStore is passed in (the released class built the same DataStore
 * through the `preferencesDataStore` delegate; a test needs a cancellable owner to model the end of
 * the old app's process), and 2.3.3's spongycastle `Hex.toHexString` is replaced by an equivalent
 * lower-case hex encoder because spongycastle is not on the test compile classpath.
 */

/**
 * HARNESS: builds the DataStore exactly like the released `preferencesDataStore(name =
 * "sora_prefs_datastore", produceMigrations = { listOf(SharedPreferencesMigration(it,
 * "sora_prefs")) })` delegate, but owned by [scope] so the test can end the "old process".
 */
fun historicalDataStore(context: Context, scope: CoroutineScope): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        migrations = listOf(SharedPreferencesMigration(context, "sora_prefs")),
        scope = scope,
        produceFile = { context.preferencesDataStoreFile("sora_prefs_datastore") },
    )

/** Verbatim copy of `EncryptionUtil` at b7b0f0fc (Play 3.8.6.3). HARNESS: class name only. */
class Release3863EncryptionUtil(
    private val context: Context
) {

    companion object {
        private const val RSA = "RSA"
        private const val AES = "AES"
        private const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val KEY_ALIAS = "key_alias"
        private const val BLOCK_SIZE = 16
        private const val AES_KEY_LENGTH = 256
        private var second = false

        private var privateKey: PrivateKey? = null
        private var publicKey: PublicKey? = null

        private const val SECRET_KEY = "secret_key"
        private val secureRandom = SecureRandom()
        private var keyStore: KeyStore? = null
    }

    init {
        initKeystore()
    }

    private fun getPrerenceAesKey(): Key {
        val secretKey: SecretKey
        val encryptedKey =
            context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE).getString(SECRET_KEY, "")
        if (encryptedKey!!.isEmpty()) {
            val keyGenerator = KeyGenerator.getInstance(AES)
            keyGenerator.init(AES_KEY_LENGTH, secureRandom)
            secretKey = keyGenerator.generateKey()
            context.getSharedPreferences(KEY_ALIAS, Context.MODE_PRIVATE).edit()
                .putString(SECRET_KEY, encryptRsa(secretKey.encoded)).apply()
        } else {
            val key = decryptRsa(encryptedKey)
            secretKey = SecretKeySpec(key, 0, key!!.size, AES)
        }
        return secretKey
    }

    private fun initKeystore() {
        try {
            keyStore = KeyStore.getInstance(KEY_STORE_PROVIDER)
            keyStore!!.load(null)

            if (keyStore!!.getKey(KEY_ALIAS, null) == null) {
                createKeys()
            }

            privateKey = keyStore!!.getKey(KEY_ALIAS, null) as PrivateKey
            publicKey = keyStore!!.getCertificate(KEY_ALIAS).publicKey
        } catch (e: Exception) {
            if (!second) {
                second = true
                initKeystore()
            }
            e.printStackTrace()
        }
    }

    private fun createKeys() {
        val startDate = Calendar.getInstance()
        val endDate = Calendar.getInstance()
        endDate.add(Calendar.YEAR, 25)

        val spec: AlgorithmParameterSpec

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(KEY_ALIAS)
                .setSubject(X500Principal("CN=Sora"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(startDate.time)
                .setEndDate(endDate.time)
                .build()
        } else {
            spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setCertificateSubject(X500Principal("CN=Sora"))
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(startDate.time)
                .setCertificateNotAfter(endDate.time)
                .build()
        }
        val keyPairGenerator = KeyPairGenerator.getInstance(RSA, KEY_STORE_PROVIDER)
        keyPairGenerator.initialize(spec)
        keyPairGenerator.generateKeyPair()
    }

    fun encrypt(cleartext: String?): String {
        if (cleartext != null && cleartext.isNotEmpty()) {
            try {
                return encrypt(getPrerenceAesKey().encoded, cleartext)
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            }
        }
        return ""
    }

    fun encrypt(key: ByteArray, cleartext: String): String {
        try {
            val result = encrypt(key, cleartext.toByteArray())
            return Base64.toBase64String(result)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }

    fun decrypt(encryptedBase64: String): String {
        try {
            return decrypt(getPrerenceAesKey().encoded, encryptedBase64)
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }

        return ""
    }

    fun decrypt(key: ByteArray, encryptedBase64: String): String {
        try {
            val encrypted = Base64.decode(encryptedBase64)
            val result = decrypt(key, encrypted)
            return String(result)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return ""
    }

    @Throws(Exception::class)
    private fun encrypt(key: ByteArray, clear: ByteArray): ByteArray {
        val skeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, IvParameterSpec(generateIVBytes()), secureRandom)
        return Arrays.concatenate(cipher.iv, cipher.doFinal(clear))
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    private fun decrypt(key: ByteArray, encrypted: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
            IvParameterSpec(Arrays.copyOfRange(encrypted, 0, BLOCK_SIZE)), secureRandom
        )
        return cipher.doFinal(Arrays.copyOfRange(encrypted, BLOCK_SIZE, encrypted.size))
    }

    private fun encryptRsa(input: ByteArray): String {
        val cipher: Cipher

        try {
            cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            return Base64.toBase64String(cipher.doFinal(input))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: BadPaddingException) {
            e.printStackTrace()
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
        }

        return ""
    }

    private fun decryptRsa(encrypted: String): ByteArray? {
        val cipher: Cipher

        try {
            cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, privateKey)
            return cipher.doFinal(Base64.decode(encrypted))
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        } catch (e: NoSuchPaddingException) {
            e.printStackTrace()
        } catch (e: InvalidKeyException) {
            e.printStackTrace()
        } catch (e: BadPaddingException) {
            e.printStackTrace()
        } catch (e: IllegalBlockSizeException) {
            e.printStackTrace()
        }

        return null
    }

    private fun generateIVBytes(): ByteArray {
        val ivBytes = ByteArray(BLOCK_SIZE)
        secureRandom.nextBytes(ivBytes)
        return ivBytes
    }
}

/**
 * Write half of `SoraPreferences` at b7b0f0fc (identical at 2.3.3).
 * HARNESS: class name; the DataStore is passed in instead of built by the delegate.
 */
class Release3863SoraPreferences(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun putString(field: String, value: String) {
        dataStore.edit {
            it[stringPreferencesKey(field)] = value
        }
    }

    suspend fun putBoolean(field: String, value: Boolean) {
        dataStore.edit {
            it[booleanPreferencesKey(field)] = value
        }
    }
}

/** Write half of `EncryptedPreferences` at b7b0f0fc (identical at 2.3.3). HARNESS: names. */
class Release3863EncryptedPreferences(
    private val soraPreferences: Release3863SoraPreferences,
    private val encryptionUtil: Release3863EncryptionUtil
) {

    suspend fun putEncryptedString(field: String, value: String) {
        soraPreferences.putString(field, encryptionUtil.encrypt(value))
    }
}

/** Write half of `PrefsCredentialsDatasource` at b7b0f0fc. HARNESS: name, no interface. */
class Release3863PrefsCredentialsDatasource constructor(
    private val encryptedPreferences: Release3863EncryptedPreferences,
    private val soraPreferences: Release3863SoraPreferences,
) {

    companion object {
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_KEY_NONCE = "prefs_key_nonce"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
        private const val PREFS_SEED = "prefs_seed"
        private const val PREFS_ADDRESS = "prefs_address_pure"
    }

    suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY + suffixAddress, keyPair.privateKey.toHexString())
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY + suffixAddress, keyPair.publicKey.toHexString())
        encryptedPreferences.putEncryptedString(PREFS_KEY_NONCE + suffixAddress, keyPair.nonce.toHexString())
    }

    suspend fun saveMnemonic(mnemonic: String, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC + suffixAddress, mnemonic)
    }

    suspend fun saveSeed(seed: String, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_SEED + suffixAddress, seed)
    }
}

/** Write half of `PrefsUserDatasource` at b7b0f0fc. HARNESS: name, no interface. */
class Release3863PrefsUserDatasource(
    private val soraPreferences: Release3863SoraPreferences,
    private val encryptedPreferences: Release3863EncryptedPreferences
) {

    companion object {
        private const val PREFS_PIN_CODE = "user_pin_code"
        private const val PREFS_REGISTRATION_STATE = "registration_state"
        private const val KEY_NEEDS_MIGRATION = "needs_migration"
        private const val KEY_IS_MIGRATION_FETCHED = "is_migration_fetched"
        private const val KEY_CUR_ACCOUNT_ADDRESS = "cur_account_address"
    }

    suspend fun setCurAccountAddress(accountAddress: String) =
        soraPreferences.putString(KEY_CUR_ACCOUNT_ADDRESS, accountAddress)

    suspend fun savePin(pin: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pin)
    }

    // HARNESS: the released signature takes OnboardingState; its toString() is the enum name.
    suspend fun saveRegistrationState(onboardingState: String) {
        soraPreferences.putString(PREFS_REGISTRATION_STATE, onboardingState.toString())
    }

    suspend fun saveNeedsMigration(it: Boolean, suffixAddress: String) {
        soraPreferences.putBoolean(KEY_NEEDS_MIGRATION + suffixAddress, it)
    }

    suspend fun saveIsMigrationFetched(it: Boolean, suffixAddress: String) {
        soraPreferences.putBoolean(KEY_IS_MIGRATION_FETCHED + suffixAddress, it)
    }
}

/** Write half of `PrefsCredentialsDatasource` at release/sora/2.3.3. HARNESS: name, Hex. */
class Release233PrefsCredentialsDatasource(
    private val encryptedPreferences: Release3863EncryptedPreferences,
    private val soraPreferences: Release3863SoraPreferences,
) {

    companion object {
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_KEY_NONCE = "prefs_key_nonce"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
        private const val PREFS_ADDRESS = "prefs_address_pure"
    }

    suspend fun saveAddress(address: String) {
        soraPreferences.putString(PREFS_ADDRESS, address)
    }

    suspend fun saveKeys(keyPair: Sr25519Keypair) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY, Hex.toHexString(keyPair.privateKey))
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY, Hex.toHexString(keyPair.publicKey))
        encryptedPreferences.putEncryptedString(PREFS_KEY_NONCE, Hex.toHexString(keyPair.nonce))
    }

    suspend fun saveMnemonic(mnemonic: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC, mnemonic)
    }

    /** HARNESS: spongycastle `Hex.toHexString` (lower-case, no prefix). */
    private object Hex {
        fun toHexString(bytes: ByteArray): String =
            bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

/** Write half of `PrefsUserDatasource` at release/sora/2.3.3. HARNESS: name, no interface. */
class Release233PrefsUserDatasource(
    private val soraPreferences: Release3863SoraPreferences,
    private val encryptedPreferences: Release3863EncryptedPreferences
) {

    companion object {
        private const val PREFS_PIN_CODE = "user_pin_code"
        private const val PREFS_REGISTRATION_STATE = "registration_state"
        private const val KEY_ACCOUNT_NAME = "key_account_name"
        private const val KEY_NEEDS_MIGRATION = "needs_migration"
        private const val KEY_IS_MIGRATION_FETCHED = "is_migration_fetched"
    }

    suspend fun savePin(pin: String) {
        encryptedPreferences.putEncryptedString(PREFS_PIN_CODE, pin)
    }

    // HARNESS: the released signature takes OnboardingState; its toString() is the enum name.
    suspend fun saveRegistrationState(onboardingState: String) {
        soraPreferences.putString(PREFS_REGISTRATION_STATE, onboardingState.toString())
    }

    suspend fun saveAccountName(accountName: String) {
        return soraPreferences.putString(KEY_ACCOUNT_NAME, accountName)
    }

    suspend fun saveNeedsMigration(it: Boolean) {
        soraPreferences.putBoolean(KEY_NEEDS_MIGRATION, it)
    }

    suspend fun saveIsMigrationFetched(it: Boolean) {
        soraPreferences.putBoolean(KEY_IS_MIGRATION_FETCHED, it)
    }
}

/** Read half of `SoraPreferences` at release/sora/2.4.0. HARNESS: name, DataStore passed in. */
class Release240SoraPreferences(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun putString(field: String, value: String) {
        dataStore.edit {
            it[stringPreferencesKey(field)] = value
        }
    }

    suspend fun getString(field: String): String =
        dataStore.data.map {
            it[stringPreferencesKey(field)] ?: ""
        }.first()

    suspend fun getBoolean(field: String, defaultValue: Boolean = false): Boolean =
        dataStore.data.map {
            it[booleanPreferencesKey(field)] ?: defaultValue
        }.first()

    suspend fun putBoolean(field: String, value: Boolean) {
        dataStore.edit {
            it[booleanPreferencesKey(field)] = value
        }
    }
}

/** `EncryptedPreferences` at release/sora/2.4.0. HARNESS: names. */
class Release240EncryptedPreferences(
    private val soraPreferences: Release240SoraPreferences,
    private val encryptionUtil: Release3863EncryptionUtil
) {

    suspend fun putEncryptedString(field: String, value: String) {
        soraPreferences.putString(field, encryptionUtil.encrypt(value))
    }

    suspend fun getDecryptedString(field: String): String {
        val encryptedString = soraPreferences.getString(field)

        return if (encryptedString.isEmpty()) "" else encryptionUtil.decrypt(encryptedString)
    }
}

/** `PrefsCredentialsDatasource` at release/sora/2.4.0. HARNESS: name, no interface, Hex. */
class Release240PrefsCredentialsDatasource constructor(
    private val encryptedPreferences: Release240EncryptedPreferences,
    private val soraPreferences: Release240SoraPreferences,
) {

    companion object {
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_KEY_NONCE = "prefs_key_nonce"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
        private const val PREFS_ADDRESS = "prefs_address_pure"
    }

    suspend fun getAddress(): String {
        return soraPreferences.getString(PREFS_ADDRESS)
    }

    suspend fun saveKeys(keyPair: Sr25519Keypair, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_PRIVATE_KEY + suffixAddress, Hex.toHexString(keyPair.privateKey))
        encryptedPreferences.putEncryptedString(PREFS_PUBLIC_KEY + suffixAddress, Hex.toHexString(keyPair.publicKey))
        encryptedPreferences.putEncryptedString(PREFS_KEY_NONCE + suffixAddress, Hex.toHexString(keyPair.nonce))
    }

    suspend fun retrieveKeys(suffixAddress: String): Sr25519Keypair? {
        val privateKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PRIVATE_KEY + suffixAddress))
        val publicKeyBytes = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_PUBLIC_KEY + suffixAddress))
        val nonce = Hex.decode(encryptedPreferences.getDecryptedString(PREFS_KEY_NONCE + suffixAddress))

        return if (privateKeyBytes.isEmpty() || publicKeyBytes.isEmpty()) null else Sr25519Keypair(privateKeyBytes, publicKeyBytes, nonce)
    }

    suspend fun saveMnemonic(mnemonic: String, suffixAddress: String) {
        encryptedPreferences.putEncryptedString(PREFS_MNEMONIC + suffixAddress, mnemonic)
    }

    suspend fun retrieveMnemonic(suffixAddress: String): String {
        return encryptedPreferences.getDecryptedString(PREFS_MNEMONIC + suffixAddress)
    }

    /** HARNESS: spongycastle `Hex` (lower-case, no prefix). */
    private object Hex {
        fun toHexString(bytes: ByteArray): String =
            bytes.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

        fun decode(value: String): ByteArray =
            value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

/** `PrefsUserDatasource` at release/sora/2.4.0 (migration functions). HARNESS: name. */
class Release240PrefsUserDatasource(
    private val soraPreferences: Release240SoraPreferences,
) {

    companion object {
        private const val PREFS_REGISTRATION_STATE = "registration_state"
        private const val KEY_ACCOUNT_NAME = "key_account_name"
        private const val KEY_NEEDS_MIGRATION = "needs_migration"
        private const val KEY_IS_MIGRATION_FETCHED = "is_migration_fetched"
        private const val KEY_CUR_ACCOUNT_ADDRESS = "cur_account_address"
    }

    suspend fun setCurAccountAddress(accountAddress: String) =
        soraPreferences.putString(KEY_CUR_ACCOUNT_ADDRESS, accountAddress)

    // HARNESS: OnboardingState is compared by its enum name.
    suspend fun retrieveRegistratrionState(): String =
        soraPreferences.getString(PREFS_REGISTRATION_STATE).ifEmpty { "INITIAL" }

    suspend fun getAccountName(): String {
        return soraPreferences.getString(KEY_ACCOUNT_NAME)
    }

    suspend fun saveNeedsMigration(it: Boolean, suffixAddress: String) {
        soraPreferences.putBoolean(KEY_NEEDS_MIGRATION + suffixAddress, it)
    }

    suspend fun needsMigration(suffixAddress: String): Boolean {
        return soraPreferences.getBoolean(KEY_NEEDS_MIGRATION + suffixAddress)
    }

    suspend fun saveIsMigrationFetched(it: Boolean, suffixAddress: String) {
        soraPreferences.putBoolean(KEY_IS_MIGRATION_FETCHED + suffixAddress, it)
    }

    suspend fun isMigrationStatusFetched(suffixAddress: String): Boolean {
        return soraPreferences.getBoolean(KEY_IS_MIGRATION_FETCHED + suffixAddress, false)
    }
}

/**
 * The migration functions of `CredentialsRepositoryImpl` at release/sora/2.4.0.
 * HARNESS: `toSoraAddress()` (SS58, prefix 69) is [toSoraAddress]; `SoraAccount` is a pair.
 */
class Release240CredentialsRepository(
    private val credentialsPrefs: Release240PrefsCredentialsDatasource,
    private val toSoraAddress: (ByteArray) -> String,
) {

    suspend fun saveMnemonic(mnemonic: String, soraAccount: Pair<String, String>) {
        credentialsPrefs.saveMnemonic(mnemonic, soraAccount.first)
    }

    suspend fun retrieveMnemonic(soraAccount: Pair<String, String>): String {
        return credentialsPrefs.retrieveMnemonic(soraAccount.first)
    }

    suspend fun retrieveKeyPair(soraAccount: Pair<String, String>): Sr25519Keypair {
        return credentialsPrefs.retrieveKeys(soraAccount.first)
            ?: throw IllegalStateException("Keypair not found")
    }

    suspend fun saveKeyPair(key: Sr25519Keypair, soraAccount: Pair<String, String>) {
        credentialsPrefs.saveKeys(key, soraAccount.first)
    }

    suspend fun getAddressForMigration(): String {
        var address = credentialsPrefs.getAddress()
        if (address.isEmpty()) {
            address =
                credentialsPrefs.retrieveKeys("")?.publicKey?.let(toSoraAddress).orEmpty()
        }
        return address
    }
}

/**
 * The migration functions of `UserRepositoryImpl` at release/sora/2.4.0.
 * HARNESS: the Room DAO calls are the same SQL on the 2.4.0 `accounts` table
 * (`@Insert(onConflict = REPLACE)` and `select count(*) from accounts`).
 */
class Release240UserRepository(
    private val context: Context,
    private val userDatasource: Release240PrefsUserDatasource,
) {

    suspend fun getRegistrationState(): String = userDatasource.retrieveRegistratrionState()

    fun getSoraAccountsCount(): Int =
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("app.db").path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { db ->
            db.rawQuery("select count(*) from accounts", null).use { it.moveToFirst(); it.getInt(0) }
        }

    fun insertSoraAccount(soraAccount: Pair<String, String>) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("app.db").path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.execSQL(
                "INSERT OR REPLACE INTO `accounts` (`substrateAddress`,`accountName`) VALUES (?,?)",
                arrayOf(soraAccount.first, soraAccount.second),
            )
        }
    }

    suspend fun setCurSoraAccount(soraAccount: Pair<String, String>) {
        userDatasource.setCurAccountAddress(soraAccount.first)
    }

    suspend fun getAccountNameForMigration(): String {
        return userDatasource.getAccountName()
    }

    suspend fun saveNeedsMigration(it: Boolean, soraAccount: Pair<String, String>) {
        userDatasource.saveNeedsMigration(it, soraAccount.first)
    }

    suspend fun needsMigration(soraAccount: Pair<String, String>): Boolean {
        return userDatasource.needsMigration(soraAccount.first)
    }

    suspend fun saveIsMigrationFetched(it: Boolean, soraAccount: Pair<String, String>) {
        userDatasource.saveIsMigrationFetched(it, soraAccount.first)
    }

    suspend fun isMigrationFetched(soraAccount: Pair<String, String>): Boolean {
        return userDatasource.isMigrationStatusFetched(soraAccount.first) &&
            !userDatasource.needsMigration(soraAccount.first)
    }
}

/** `MigrationManager.start` at release/sora/2.4.0, verbatim. HARNESS: types above. */
class Release240MigrationManager(
    private val userRepository: Release240UserRepository,
    private val credentialsRepository: Release240CredentialsRepository,
) {

    suspend fun start(): Boolean {
        if (userRepository.getRegistrationState() == "REGISTRATION_FINISHED") {
            if (userRepository.getSoraAccountsCount() == 0) {
                val emptySoraAccount = "" to ""
                val address = credentialsRepository.getAddressForMigration()
                val name = userRepository.getAccountNameForMigration()
                val soraAccount = address to name
                val needsIrohaMigration = userRepository.needsMigration(emptySoraAccount)
                val isIrohaFetched = userRepository.isMigrationFetched(emptySoraAccount)
                userRepository.saveNeedsMigration(needsIrohaMigration, soraAccount)
                userRepository.saveIsMigrationFetched(isIrohaFetched, soraAccount)
                val mnemonic = credentialsRepository.retrieveMnemonic(emptySoraAccount)
                credentialsRepository.saveMnemonic(mnemonic, soraAccount)
                val soraKeys = credentialsRepository.retrieveKeyPair(emptySoraAccount)
                credentialsRepository.saveKeyPair(soraKeys, soraAccount)
                userRepository.insertSoraAccount(soraAccount)
                userRepository.setCurSoraAccount(soraAccount)
            }
        }
        return true
    }
}

/**
 * Account removal at b7b0f0fc (Play 3.8.6.3): `UserRepositoryImpl.clearAccountData` ->
 * `PrefsCredentialsDatasource.clearAllDataForAddress` (one DataStore edit) and
 * `AccountDao.clearAccount` + `ReferralsDao.clearTable` in a Room transaction.
 * HARNESS: the DAO calls are the same SQL; [killedAfterPreferences] stops where a process kill
 * between the two steps would.
 */
class Release3863AccountRemoval(
    private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {

    companion object {
        private const val PREFS_PRIVATE_KEY = "prefs_priv_key"
        private const val PREFS_PUBLIC_KEY = "prefs_pub_key"
        private const val PREFS_KEY_NONCE = "prefs_key_nonce"
        private const val PREFS_MNEMONIC = "prefs_mnemonic"
        private const val PREFS_SEED = "prefs_seed"
        private const val PREFS_ADDRESS = "prefs_address_pure"
    }

    suspend fun clearAccountData(address: String, killedAfterPreferences: Boolean = false) {
        clearAllDataForAddress(address)
        if (killedAfterPreferences) return
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("app.db").path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { db ->
            db.beginTransaction()
            try {
                db.execSQL("delete from accounts where substrateAddress = ?", arrayOf(address))
                if (db.rawQuery("SELECT 1 FROM sqlite_master WHERE name = 'referrals'", null)
                        .use { it.moveToFirst() }
                ) {
                    db.execSQL("delete from referrals")
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private suspend fun clearAllDataForAddress(suffixAddress: String) {
        val fields =
            listOf(PREFS_ADDRESS, PREFS_PRIVATE_KEY, PREFS_PUBLIC_KEY, PREFS_KEY_NONCE, PREFS_MNEMONIC, PREFS_SEED)
                .map { it + suffixAddress }
        dataStore.edit {
            fields.forEach { field ->
                it.remove(stringPreferencesKey(field))
            }
        }
    }
}

/**
 * Creates `app.db` at a released Room version.
 *
 * Versions 58 and later come from the exported schemas in `core_db/schemas` (the same JSON that
 * Room's MigrationTestHelper reads): every entity `createSql`, index and setup query, then
 * `user_version`. Version 50 (2.3.x) predates exported schemas; its tables are reproduced from the
 * `@Entity` classes at release/sora/2.3.3 (tokens, assets, pools, extrinsics, extrinsic_params,
 * BigDecimal stored as TEXT through BigDecimalConverter, enums as INTEGER).
 */
object HistoricalRoom {

    fun create(context: Context, instrumentationContext: Context, version: Int) {
        val file = context.getDatabasePath("app.db")
        file.parentFile?.mkdirs()
        check(!file.exists()) { "app.db already exists" }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
            if (version == 50) {
                room50Statements.forEach(sqlite::execSQL)
            } else {
                schemaStatements(instrumentationContext, version).forEach(sqlite::execSQL)
            }
            sqlite.version = version
        }
    }

    fun insertAccounts(context: Context, accounts: List<Pair<String, String>>) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath("app.db").path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            accounts.forEach { (address, name) ->
                sqlite.execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(address, name),
                )
            }
        }
    }

    private fun schemaStatements(instrumentationContext: Context, version: Int): List<String> {
        val json = instrumentationContext.assets
            .open("jp.co.soramitsu.core_db.AppDatabase/$version.json")
            .bufferedReader()
            .use { it.readText() }
        val database = JSONObject(json).getJSONObject("database")
        return buildList {
            val entities = database.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                add(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) {
                    for (i in 0 until indices.length()) {
                        add(
                            indices.getJSONObject(i).getString("createSql")
                                .replace("\${TABLE_NAME}", table)
                        )
                    }
                }
            }
            val views = database.optJSONArray("views")
            if (views != null) {
                for (index in 0 until views.length()) {
                    val view = views.getJSONObject(index)
                    add(
                        view.getString("createSql")
                            .replace("\${VIEW_NAME}", view.getString("viewName"))
                    )
                }
            }
            val setup = database.getJSONArray("setupQueries")
            for (index in 0 until setup.length()) add(setup.getString(index))
        }
    }

    private val room50Statements = listOf(
        "CREATE TABLE IF NOT EXISTS `extrinsic_params` (`extrinsicId` TEXT NOT NULL, `paramName` TEXT NOT NULL, `paramValue` TEXT NOT NULL, PRIMARY KEY(`extrinsicId`, `paramName`), FOREIGN KEY(`extrinsicId`) REFERENCES `extrinsics`(`txHash`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `extrinsics` (`txHash` TEXT NOT NULL, `blockHash` TEXT, `fee` TEXT NOT NULL, `status` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `type` INTEGER NOT NULL, `eventSuccess` INTEGER, `localPending` INTEGER NOT NULL, PRIMARY KEY(`txHash`))",
        "CREATE TABLE IF NOT EXISTS `assets` (`tokenId` TEXT NOT NULL, `accountAddress` TEXT NOT NULL, `displayAsset` INTEGER NOT NULL, `position` INTEGER NOT NULL, `free` TEXT NOT NULL, `reserved` TEXT NOT NULL, `miscFrozen` TEXT NOT NULL, `feeFrozen` TEXT NOT NULL, `bonded` TEXT NOT NULL, `redeemable` TEXT NOT NULL, `unbonding` TEXT NOT NULL, PRIMARY KEY(`tokenId`, `accountAddress`), FOREIGN KEY(`tokenId`) REFERENCES `tokens`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_assets_accountAddress` ON `assets` (`accountAddress`)",
        "CREATE TABLE IF NOT EXISTS `tokens` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `symbol` TEXT NOT NULL, `precision` INTEGER NOT NULL, `isMintable` INTEGER NOT NULL, `whitelistName` TEXT NOT NULL, `isHidable` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE IF NOT EXISTS `pools` (`assetId` TEXT NOT NULL, `reservesFirst` TEXT NOT NULL, `reservesSecond` TEXT NOT NULL, `totalIssuance` TEXT NOT NULL, `poolProvidersBalance` TEXT NOT NULL, PRIMARY KEY(`assetId`))",
        "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
        "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'release-2.3.3-room-50')",
        "INSERT INTO tokens VALUES('0x0200000000000000000000000000000000000000000000000000000000000000', 'SORA', 'XOR', 18, 1, 'whitelist', 0)",
    )
}

/** Deletes everything a test install could hold, including the test package's Keystore alias. */
fun resetWalletStorage(context: Context) {
    context.deleteDatabase("app.db")
    listOf("-wal", "-shm", "-journal").forEach {
        File(context.getDatabasePath("app.db").path + it).delete()
    }
    File(context.filesDir, "datastore").deleteRecursively()
    val sharedPrefs = File(context.applicationInfo.dataDir, "shared_prefs")
    listOf("sora_prefs", "key_alias").forEach { name ->
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        File(sharedPrefs, "$name.xml").delete()
        File(sharedPrefs, "$name.xml.bak").delete()
    }
    context.noBackupFilesDir.listFiles().orEmpty().forEach { it.deleteRecursively() }
    context.cacheDir.listFiles().orEmpty()
        .filter { it.name.startsWith("wallet-") }
        .forEach { it.deleteRecursively() }
    runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("key_alias")
    }
}
