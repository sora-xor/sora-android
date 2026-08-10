package jp.co.soramitsu.feature_account_impl.data.repository.datasource

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.util.WalletDecryptionException
import jp.co.soramitsu.core_db.WalletUpgradeBackup
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.deriveSeed32
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.encrypt.MultiChainEncryption
import jp.co.soramitsu.xcrypto.encryption.sr25519.Sr25519JNI
import jp.co.soramitsu.xsubstrate.encrypt.SignWrapper
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.xsubstrate.encrypt.keypair.substrate.SubstrateKeypairFactory
import jp.co.soramitsu.xsubstrate.encrypt.seed.substrate.SubstrateSeedFactory
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production qualification runs each numbered method in a separate instrumentation invocation.
 * The workflow force-stops the test application between invocations, so phases 02 and 03 prove
 * Android Keystore/DataStore readback after actual process death rather than constructing two
 * active DataStore instances for the same file.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedWalletMigrationStorageTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun phase01SeedRetainedSharedPreferencesAndVerifiedBackup() {
        resetQualificationStorage()
        assertFalse(dataStoreFile().exists())

        // This only creates the test UID's retained RSA alias. The known AES fixture key is
        // independently wrapped below, matching the release-era key_alias/secret_key format.
        val legacyEncryption = EncryptionUtil(context)
        val wrappedAesKeyCiphertext = wrapKnownAesKeyWithRetainedAlias()
        assertTrue(
            keyPreferences().edit()
                .putString(WRAPPED_AES_KEY, wrappedAesKeyCiphertext)
                .commit()
        )

        val legacyKeyPair = derivePinnedMnemonicKeyPair()
        val legacyValues = try {
            generateLegacySharedPreferenceValues(legacyEncryption, legacyKeyPair)
        } finally {
            legacyKeyPair.privateKey.fill(0)
            legacyKeyPair.nonce.fill(0)
        }
        val legacyPreferences = walletPreferences()
        val editor = legacyPreferences.edit().clear()
        legacyValues.forEach { (field, value) ->
            editor.putString(field, value)
        }
        assertTrue(editor.commit())
        assertExactLegacyPreferenceValues(legacyValues, legacyPreferences.all)

        createRetainedV73Database()
        val sourceDatabase = context.getDatabasePath(DATABASE_NAME)
        val sourceWalletPreferences = walletPreferencesFile()
        val sourceKeyPreferences = keyPreferencesFile()
        assertTrue(sourceDatabase.isFile)
        assertTrue(sourceWalletPreferences.isFile)
        assertTrue(sourceKeyPreferences.isFile)
        assertFalse(dataStoreFile().exists())

        val databaseBytes = sourceDatabase.readBytes()
        val walletPreferenceBytes = sourceWalletPreferences.readBytes()
        val keyPreferenceBytes = sourceKeyPreferences.readBytes()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertArrayEquals(databaseBytes, sourceDatabase.readBytes())
        assertArrayEquals(walletPreferenceBytes, sourceWalletPreferences.readBytes())
        assertArrayEquals(keyPreferenceBytes, sourceKeyPreferences.readBytes())
        assertOpaqueStringEquals(wrappedAesKeyCiphertext, wrappedAesKey())

        val backup = retainedBackupDirectory()
        assertEquals("verified", File(backup, ".complete").readText())
        assertArrayEquals(
            databaseBytes,
            File(backup, "databases/$DATABASE_NAME").readBytes(),
        )
        assertArrayEquals(
            walletPreferenceBytes,
            File(backup, "shared_prefs/$SHARED_PREFERENCES_NAME.xml").readBytes(),
        )
        assertArrayEquals(
            keyPreferenceBytes,
            File(backup, "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml").readBytes(),
        )
        JSONObject(File(backup, "manifest.json").readText()).also { manifest ->
            assertEquals(RETAINED_DATABASE_VERSION, manifest.getInt("sourceDatabaseVersion"))
            assertEquals(CURRENT_DATABASE_VERSION, manifest.getInt("targetDatabaseVersion"))
            assertEquals(1, manifest.getInt("legacyAccountCount"))
        }

        val qualificationEditor = qualificationState().edit().clear()
            .putString(STAGE, STAGE_SHARED_PREFERENCES_BACKED_UP)
            .putString(SOURCE_DATABASE_SHA256, sha256(databaseBytes))
            .putString(SOURCE_WALLET_PREFERENCES_SHA256, sha256(walletPreferenceBytes))
            .putString(SOURCE_KEY_PREFERENCES_SHA256, sha256(keyPreferenceBytes))
            .putString(KEYSTORE_CERTIFICATE_SHA256, retainedAliasCertificateSha256())
        // Legacy CBC envelopes carry randomized IVs. Persist only SHA-256 commitments: they bind
        // the exact ciphertext across forced restarts without duplicating or logging key material.
        LEGACY_ENCRYPTED_FIELDS.forEach { field ->
            qualificationEditor.putString(
                legacyCiphertextSha256Key(field),
                sha256Utf8(requireNotNull(legacyValues[field])),
            )
        }
        assertTrue(qualificationEditor.commit())
    }

    @Test
    fun phase02ImportLegacyCiphertextAfterProcessRestart() = runBlocking {
        requireStage(STAGE_SHARED_PREFERENCES_BACKED_UP)
        assertFalse(dataStoreFile().exists())
        assertLegacyPreferencesMatchPersistedExpectations(walletPreferences().all)
        assertBackupHashesMatchQualificationState()
        val wrappedAesKeyBeforeImport = wrappedAesKey()

        val soraPreferences = SoraPreferences(context)
        val datasource = PrefsCredentialsDatasource(
            EncryptedPreferences(soraPreferences, EncryptionUtil(context)),
            soraPreferences,
        )

        assertLegacyWalletRoundTrip(datasource)
        assertLegacyCiphertextValuesUnchanged(soraPreferences)
        assertTrue(dataStoreFile().isFile)
        assertOpaqueStringEquals(wrappedAesKeyBeforeImport, wrappedAesKey())
        assertEquals(
            qualificationState().getString(KEYSTORE_CERTIFICATE_SHA256, ""),
            retainedAliasCertificateSha256(),
        )
        assertTrue(
            walletPreferences().all.keys.none { key ->
                key in LEGACY_PREFERENCE_FIELDS
            }
        )

        val dataStoreBytes = dataStoreFile().readBytes()
        assertTrue(
            qualificationState().edit()
                .putString(STAGE, STAGE_DATASTORE_IMPORTED)
                .putString(DATASTORE_SHA256, sha256(dataStoreBytes))
                .commit()
        )
    }

    @Test
    fun phase03ReadRetainedDataStoreAfterSecondProcessRestart() = runBlocking {
        requireStage(STAGE_DATASTORE_IMPORTED)
        assertTrue(dataStoreFile().isFile)
        val dataStoreBytesBeforeRead = dataStoreFile().readBytes()
        assertEquals(
            qualificationState().getString(DATASTORE_SHA256, ""),
            sha256(dataStoreBytesBeforeRead),
        )
        val wrappedAesKeyBeforeRead = wrappedAesKey()

        val soraPreferences = SoraPreferences(context)
        val datasource = PrefsCredentialsDatasource(
            EncryptedPreferences(soraPreferences, EncryptionUtil(context)),
            soraPreferences,
        )

        assertLegacyWalletRoundTrip(datasource)
        assertLegacyCiphertextValuesUnchanged(soraPreferences)
        assertArrayEquals(dataStoreBytesBeforeRead, dataStoreFile().readBytes())
        assertOpaqueStringEquals(wrappedAesKeyBeforeRead, wrappedAesKey())
        assertEquals(
            qualificationState().getString(KEYSTORE_CERTIFICATE_SHA256, ""),
            retainedAliasCertificateSha256(),
        )
        assertBackupHashesMatchQualificationState()
        assertTrue(
            qualificationState().edit()
                .putString(STAGE, STAGE_DATASTORE_REOPENED)
                .commit()
        )
    }

    @Test
    fun phase04AuthenticatedEnvelopeAndWrappedKeyCorruptionFailClosed() = runBlocking {
        requireStage(STAGE_DATASTORE_REOPENED)
        resetQualificationStorage()

        val soraPreferences = SoraPreferences(context)
        val encryptionUtil = EncryptionUtil(context)
        val datasource = PrefsCredentialsDatasource(
            EncryptedPreferences(soraPreferences, encryptionUtil),
            soraPreferences,
        )
        val keyPair = derivePinnedMnemonicKeyPair()

        try {
            datasource.saveKeys(keyPair, "")
            datasource.saveMnemonic(PINNED_MNEMONIC, "")
            datasource.saveSeed(PINNED_SORA2_SEED, "")
            soraPreferences.putString(LEGACY_ADDRESS_KEY, PINNED_SORA2_ADDRESS)
            soraPreferences.putString(CURRENT_ACCOUNT_KEY, PINNED_SORA2_ADDRESS)
            assertCurrentEnvelopeRoundTrip(datasource, keyPair)

            ENCRYPTED_FIELDS.forEach { field ->
                val storedEnvelope = soraPreferences.getString(field)
                assertTrue(storedEnvelope.startsWith(AUTHENTICATED_ENVELOPE_PREFIX))
                assertFalse(storedEnvelope.contains(PINNED_MNEMONIC))
                assertFalse(storedEnvelope.contains(PINNED_SORA2_SEED))
            }

            val originalWrappedKey = wrappedAesKey()
            val corruptedWrappedKey = originalWrappedKey.corruptWrappedRsaCiphertext()
            assertTrue(
                keyPreferences().edit()
                    .putString(WRAPPED_AES_KEY, corruptedWrappedKey)
                    .commit()
            )
            val wrappedKeyFailure = runCatching {
                EncryptionUtil(context).decrypt(soraPreferences.getString(MNEMONIC_KEY))
            }
            assertTrue(wrappedKeyFailure.exceptionOrNull() is KeyMaterialUnavailableException)
            assertOpaqueStringEquals(corruptedWrappedKey, wrappedAesKey())
            assertTrue(retainedAliasExists())
            assertTrue(
                keyPreferences().edit()
                    .putString(WRAPPED_AES_KEY, originalWrappedKey)
                    .commit()
            )

            val encryptedMnemonic = soraPreferences.getString(MNEMONIC_KEY)
            val corruptedEnvelope = encryptedMnemonic.corruptAuthenticatedEnvelope()
            soraPreferences.putString(MNEMONIC_KEY, corruptedEnvelope)
            val corruptRead = runCatching { datasource.retrieveMnemonic("") }
            assertTrue(corruptRead.exceptionOrNull() is WalletDecryptionException)
            assertOpaqueStringEquals(
                corruptedEnvelope,
                soraPreferences.getString(MNEMONIC_KEY),
            )
            assertEquals("", soraPreferences.getString("$MNEMONIC_KEY$PINNED_SORA2_ADDRESS"))
            assertTrue(
                qualificationState().edit()
                    .putString(STAGE, STAGE_V2_TAMPERED)
                    .commit()
            )
        } finally {
            keyPair.privateKey.fill(0)
            keyPair.nonce.fill(0)
        }
    }

    @Test
    fun phase05MissingKeystoreAliasNeverCreatesReplacement() = runBlocking {
        requireStage(STAGE_V2_TAMPERED)
        val wrappedKeyBeforeFailure = wrappedAesKey()
        val soraPreferences = SoraPreferences(context)
        val encryptedMnemonicBeforeFailure = soraPreferences.getString(MNEMONIC_KEY)
        deleteRetainedAlias()
        assertFalse(retainedAliasExists())

        // This phase is a fresh instrumentation process. Construct exactly one DataStore owner;
        // the missing alias must close the wallet rather than manufacture a replacement identity.
        val unavailableEncryption = EncryptionUtil(context)
        val failure = runCatching {
            unavailableEncryption.decrypt(soraPreferences.getString(MNEMONIC_KEY))
        }
        assertTrue(failure.exceptionOrNull() is KeyMaterialUnavailableException)
        assertFalse(retainedAliasExists())
        assertOpaqueStringEquals(wrappedKeyBeforeFailure, wrappedAesKey())
        assertOpaqueStringEquals(
            encryptedMnemonicBeforeFailure,
            soraPreferences.getString(MNEMONIC_KEY),
        )
        assertTrue(
            qualificationState().edit()
                .putString(STAGE, STAGE_COMPLETE)
                .commit()
        )
    }

    private suspend fun assertLegacyWalletRoundTrip(
        datasource: PrefsCredentialsDatasource,
    ) {
        val recovered = requireNotNull(datasource.retrieveKeys(""))
        val expected = derivePinnedMnemonicKeyPair()
        try {
            assertSecretBytesEqual(expected.privateKey, recovered.privateKey)
            assertArrayEquals(expected.publicKey, recovered.publicKey)
            assertSecretBytesEqual(expected.nonce, recovered.nonce)
            assertRecoveredKeySigningParity(recovered)
        } finally {
            expected.privateKey.fill(0)
            expected.nonce.fill(0)
            recovered.privateKey.fill(0)
            recovered.nonce.fill(0)
        }
        assertOpaqueStringEquals(PINNED_MNEMONIC, datasource.retrieveMnemonic(""))
        // Release-created mnemonic wallets commonly had no eagerly persisted seed. Migration
        // must preserve that absence and derive only in memory at the identity verifier.
        assertEquals("", datasource.retrieveSeed(""))
        assertNull(datasource.retrieveKeys(PINNED_SORA2_ADDRESS))
        assertEquals("", datasource.retrieveMnemonic(PINNED_SORA2_ADDRESS))
        assertEquals("", datasource.retrieveSeed(PINNED_SORA2_ADDRESS))
    }

    private suspend fun assertLegacyCiphertextValuesUnchanged(
        soraPreferences: SoraPreferences,
    ) {
        LEGACY_ENCRYPTED_FIELDS.forEach { field ->
            val ciphertext = soraPreferences.getString(field)
            assertFalse(ciphertext.startsWith(AUTHENTICATED_ENVELOPE_PREFIX))
            assertPersistedLegacyCiphertext(field, ciphertext)
        }
        ENCRYPTED_FIELDS.forEach { field ->
            assertEquals("", soraPreferences.getString("$field$PINNED_SORA2_ADDRESS"))
        }
        assertEquals(PINNED_SORA2_ADDRESS, soraPreferences.getString(LEGACY_ADDRESS_KEY))
        assertEquals(PINNED_SORA2_ADDRESS, soraPreferences.getString(CURRENT_ACCOUNT_KEY))
        assertEquals("", soraPreferences.getString(SEED_KEY))
    }

    private suspend fun assertCurrentEnvelopeRoundTrip(
        datasource: PrefsCredentialsDatasource,
        keyPair: Sr25519Keypair,
    ) {
        val recovered = requireNotNull(datasource.retrieveKeys(""))
        try {
            assertSecretBytesEqual(keyPair.privateKey, recovered.privateKey)
            assertArrayEquals(keyPair.publicKey, recovered.publicKey)
            assertSecretBytesEqual(keyPair.nonce, recovered.nonce)
        } finally {
            recovered.privateKey.fill(0)
            recovered.nonce.fill(0)
        }
        assertOpaqueStringEquals(PINNED_MNEMONIC, datasource.retrieveMnemonic(""))
        assertOpaqueStringEquals(PINNED_SORA2_SEED, datasource.retrieveSeed(""))
        assertNull(datasource.retrieveKeys(PINNED_SORA2_ADDRESS))
    }

    private fun generateLegacySharedPreferenceValues(
        encryptionUtil: EncryptionUtil,
        keyPair: Sr25519Keypair,
    ): Map<String, String> {
        val aesKey = LEGACY_AES_KEY_HEX.fromHex()
        return try {
            linkedMapOf(
                PRIVATE_KEY to encryptionUtil.encrypt(
                    aesKey,
                    keyPair.privateKey.toHexString(),
                ),
                PUBLIC_KEY to encryptionUtil.encrypt(
                    aesKey,
                    keyPair.publicKey.toHexString(),
                ),
                NONCE_KEY to encryptionUtil.encrypt(
                    aesKey,
                    keyPair.nonce.toHexString(),
                ),
                MNEMONIC_KEY to encryptionUtil.encrypt(aesKey, PINNED_MNEMONIC),
                LEGACY_ADDRESS_KEY to PINNED_SORA2_ADDRESS,
                CURRENT_ACCOUNT_KEY to PINNED_SORA2_ADDRESS,
            )
        } finally {
            aesKey.fill(0)
        }
    }

    private fun derivePinnedMnemonicKeyPair(): Sr25519Keypair {
        val seed = SubstrateSeedFactory.deriveSeed32(PINNED_MNEMONIC, null).seed
        return try {
            val expectedSeed = PINNED_SORA2_SEED.fromHex()
            try {
                assertSecretBytesEqual(expectedSeed, seed)
            } finally {
                expectedSeed.fill(0)
            }
            val keyPair = SubstrateKeypairFactory.generate(
                SubstrateOptionsProvider.encryptionType,
                seed,
            ) as Sr25519Keypair
            try {
                assertEquals(PINNED_SORA2_PUBLIC_KEY, keyPair.publicKey.toHexString())
                assertEquals(PINNED_SORA2_ADDRESS, keyPair.publicKey.toAddress(SORA2_PREFIX))
                keyPair
            } catch (error: Throwable) {
                keyPair.privateKey.fill(0)
                keyPair.nonce.fill(0)
                throw error
            }
        } finally {
            seed.fill(0)
        }
    }

    private fun assertRecoveredKeySigningParity(keyPair: Sr25519Keypair) {
        // Sr25519 signatures are randomized, so a fixed public challenge is verified instead of
        // persisting signature bytes or exposing any recovered private material in assertions.
        val challenge = LEGACY_SIGNING_CHALLENGE.encodeToByteArray()
        try {
            val signature = SignWrapper.sign(
                MultiChainEncryption.Substrate(SubstrateOptionsProvider.encryptionType),
                challenge,
                keyPair,
            ).signature
            try {
                assertTrue(
                    Sr25519JNI.verify(
                        signature,
                        challenge,
                        keyPair.publicKey,
                    )
                )
            } finally {
                signature.fill(0)
            }
        } finally {
            challenge.fill(0)
        }
    }

    private fun assertExactLegacyPreferenceValues(
        expected: Map<String, String>,
        actual: Map<String, *>,
    ) {
        assertEquals(expected.keys, actual.keys)
        LEGACY_ENCRYPTED_FIELDS.forEach { field ->
            val expectedCiphertext = requireNotNull(expected[field])
            assertFalse(expectedCiphertext.startsWith(AUTHENTICATED_ENVELOPE_PREFIX))
            assertOpaqueStringEquals(
                expectedCiphertext,
                requireNotNull(actual[field] as? String),
            )
        }
        assertEquals(PINNED_SORA2_ADDRESS, actual[LEGACY_ADDRESS_KEY])
        assertEquals(PINNED_SORA2_ADDRESS, actual[CURRENT_ACCOUNT_KEY])
    }

    private fun assertLegacyPreferencesMatchPersistedExpectations(
        actual: Map<String, *>,
    ) {
        assertEquals(LEGACY_PREFERENCE_FIELDS.toSet(), actual.keys)
        LEGACY_ENCRYPTED_FIELDS.forEach { field ->
            val ciphertext = requireNotNull(actual[field] as? String)
            assertFalse(ciphertext.startsWith(AUTHENTICATED_ENVELOPE_PREFIX))
            assertPersistedLegacyCiphertext(
                field,
                ciphertext,
            )
        }
        assertEquals(PINNED_SORA2_ADDRESS, actual[LEGACY_ADDRESS_KEY])
        assertEquals(PINNED_SORA2_ADDRESS, actual[CURRENT_ACCOUNT_KEY])
    }

    private fun assertPersistedLegacyCiphertext(field: String, ciphertext: String) {
        val expectedSha256 = qualificationState().getString(
            legacyCiphertextSha256Key(field),
            "",
        ).orEmpty()
        assertEquals(SHA256_HEX_LENGTH, expectedSha256.length)
        assertEquals(expectedSha256, sha256Utf8(ciphertext))
    }

    private fun assertSecretBytesEqual(expected: ByteArray, actual: ByteArray) {
        assertTrue(MessageDigest.isEqual(expected, actual))
    }

    private fun assertOpaqueStringEquals(expected: String, actual: String) {
        val expectedBytes = expected.encodeToByteArray()
        val actualBytes = actual.encodeToByteArray()
        try {
            assertTrue(MessageDigest.isEqual(expectedBytes, actualBytes))
        } finally {
            expectedBytes.fill(0)
            actualBytes.fill(0)
        }
    }

    private fun legacyCiphertextSha256Key(field: String): String =
        "$LEGACY_CIPHERTEXT_SHA256_PREFIX$field"

    private fun createRetainedV73Database() {
        context.openOrCreateDatabase(
            DATABASE_NAME,
            Context.MODE_PRIVATE,
            null,
        ).use { database ->
            database.execSQL(
                """
                CREATE TABLE accounts(
                    substrateAddress TEXT NOT NULL PRIMARY KEY,
                    accountName TEXT NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(PINNED_SORA2_ADDRESS, "Retained 12 words"),
            )
            database.execSQL("PRAGMA user_version = $RETAINED_DATABASE_VERSION")
        }
    }

    private fun wrapKnownAesKeyWithRetainedAlias(): String {
        val aesKey = LEGACY_AES_KEY_HEX.fromHex()
        return try {
            val keyStore = loadedKeyStore()
            val publicKey = requireNotNull(keyStore.getCertificate(KEY_ALIAS)).publicKey
            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            Base64.getEncoder().encodeToString(cipher.doFinal(aesKey))
        } finally {
            aesKey.fill(0)
        }
    }

    private fun assertBackupHashesMatchQualificationState() {
        val state = qualificationState()
        val backup = retainedBackupDirectory()
        assertEquals("verified", File(backup, ".complete").readText())
        assertEquals(
            state.getString(SOURCE_DATABASE_SHA256, ""),
            sha256(File(backup, "databases/$DATABASE_NAME").readBytes()),
        )
        assertEquals(
            state.getString(SOURCE_WALLET_PREFERENCES_SHA256, ""),
            sha256(
                File(
                    backup,
                    "shared_prefs/$SHARED_PREFERENCES_NAME.xml",
                ).readBytes()
            ),
        )
        assertEquals(
            state.getString(SOURCE_KEY_PREFERENCES_SHA256, ""),
            sha256(
                File(
                    backup,
                    "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml",
                ).readBytes()
            ),
        )
    }

    private fun requireStage(expected: String) {
        assertEquals(expected, qualificationState().getString(STAGE, ""))
    }

    private fun retainedBackupDirectory(): File = File(
        context.noBackupFilesDir,
        "$BACKUP_PREFIX$RETAINED_DATABASE_VERSION-to-$CURRENT_DATABASE_VERSION",
    )

    private fun walletPreferences() = context.getSharedPreferences(
        SHARED_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun keyPreferences() = context.getSharedPreferences(
        KEYSTORE_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    private fun qualificationState() = context.getSharedPreferences(
        QUALIFICATION_STATE_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    private fun wrappedAesKey(): String =
        keyPreferences().getString(WRAPPED_AES_KEY, "").orEmpty()

    private fun walletPreferencesFile(): File = File(
        context.applicationInfo.dataDir,
        "shared_prefs/$SHARED_PREFERENCES_NAME.xml",
    )

    private fun keyPreferencesFile(): File = File(
        context.applicationInfo.dataDir,
        "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml",
    )

    private fun dataStoreFile(): File = File(
        context.filesDir,
        "datastore/$DATASTORE_FILE_NAME",
    )

    private fun loadedKeyStore(): KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun retainedAliasExists(): Boolean = loadedKeyStore().containsAlias(KEY_ALIAS)

    private fun retainedAliasCertificateSha256(): String =
        sha256(requireNotNull(loadedKeyStore().getCertificate(KEY_ALIAS)).encoded)

    private fun deleteRetainedAlias() {
        loadedKeyStore().deleteEntry(KEY_ALIAS)
    }

    private fun String.corruptAuthenticatedEnvelope(): String {
        check(startsWith(AUTHENTICATED_ENVELOPE_PREFIX))
        return corruptAt(AUTHENTICATED_ENVELOPE_PREFIX.length + 4)
    }

    private fun String.corruptWrappedRsaCiphertext(): String {
        val ciphertext = Base64.getDecoder().decode(this)
        return try {
            // RSA/PKCS#1 v1.5 cannot accept the all-zero encoded message. Keeping the exact
            // ciphertext length and valid Base64 makes this a deterministic unwrap failure,
            // rather than relying on a one-character mutation to fail padding by probability.
            ciphertext.fill(0)
            Base64.getEncoder().encodeToString(ciphertext)
        } finally {
            ciphertext.fill(0)
        }
    }

    private fun String.corruptAt(index: Int): String {
        check(index in indices)
        val replacement = if (this[index] == 'A') 'B' else 'A'
        return replaceRange(index, index + 1, replacement.toString())
    }

    private fun sha256(value: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value)
        return digest.joinToString("") {
            (it.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun sha256Utf8(value: String): String {
        val bytes = value.encodeToByteArray()
        return try {
            sha256(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun resetQualificationStorage() {
        context.deleteDatabase(DATABASE_NAME)
        listOf(
            SHARED_PREFERENCES_NAME,
            KEYSTORE_PREFERENCES_NAME,
            QUALIFICATION_STATE_PREFERENCES,
        ).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit().clear().commit()
            File(
                context.applicationInfo.dataDir,
                "shared_prefs/$name.xml.bak",
            ).delete()
        }
        dataStoreFile().delete()
        listOf(
            File("${context.getDatabasePath(DATABASE_NAME).path}-wal"),
            File("${context.getDatabasePath(DATABASE_NAME).path}-shm"),
            File("${context.getDatabasePath(DATABASE_NAME).path}-journal"),
        ).forEach { it.delete() }
        context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith(BACKUP_PREFIX) }
            .forEach { it.deleteRecursively() }
        if (retainedAliasExists()) deleteRetainedAlias()
    }

    private companion object {
        const val DATABASE_NAME = "app.db"
        const val SHARED_PREFERENCES_NAME = "sora_prefs"
        const val KEYSTORE_PREFERENCES_NAME = "key_alias"
        const val QUALIFICATION_STATE_PREFERENCES = "wallet_migration_qualification_state"
        const val DATASTORE_FILE_NAME = "sora_prefs_datastore.preferences_pb"
        const val BACKUP_PREFIX = "wallet-upgrade-backup-v"
        const val RETAINED_DATABASE_VERSION = 73
        const val CURRENT_DATABASE_VERSION = 77
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "key_alias"
        const val WRAPPED_AES_KEY = "secret_key"
        const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        const val AUTHENTICATED_ENVELOPE_PREFIX = "v2:"
        const val LEGACY_CIPHERTEXT_SHA256_PREFIX = "legacy_ciphertext_sha256_"
        const val SHA256_HEX_LENGTH = 64
        const val LEGACY_ADDRESS_KEY = "prefs_address_pure"
        const val CURRENT_ACCOUNT_KEY = "cur_account_address"
        const val PRIVATE_KEY = "prefs_priv_key"
        const val PUBLIC_KEY = "prefs_pub_key"
        const val NONCE_KEY = "prefs_key_nonce"
        const val MNEMONIC_KEY = "prefs_mnemonic"
        const val SEED_KEY = "prefs_seed"
        const val SORA2_PREFIX: Short = 69
        const val PINNED_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                "abandon about"
        const val PINNED_SORA2_SEED =
            "4ed8d4b17698ddeaa1f1559f152f87b5d472f725ca86d341bd0276f1b61197e2"
        const val PINNED_SORA2_PUBLIC_KEY =
            "66933bd1f37070ef87bd1198af3dacceb095237f803f3d32b173e6b425ed7972"
        const val PINNED_SORA2_ADDRESS =
            "cnTon6cV8Ze8ATHT1tZdHregCc3wBc8vfg7P5o2TtwdUoRf4m"
        const val LEGACY_SIGNING_CHALLENGE =
            "sora-wallet-retained-ciphertext-signing-parity-v1"
        const val LEGACY_AES_KEY_HEX =
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        const val STAGE = "stage"
        const val STAGE_SHARED_PREFERENCES_BACKED_UP = "shared-preferences-backed-up"
        const val STAGE_DATASTORE_IMPORTED = "datastore-imported"
        const val STAGE_DATASTORE_REOPENED = "datastore-reopened"
        const val STAGE_V2_TAMPERED = "v2-tampered"
        const val STAGE_COMPLETE = "complete"
        const val SOURCE_DATABASE_SHA256 = "source_database_sha256"
        const val SOURCE_WALLET_PREFERENCES_SHA256 = "source_wallet_preferences_sha256"
        const val SOURCE_KEY_PREFERENCES_SHA256 = "source_key_preferences_sha256"
        const val DATASTORE_SHA256 = "datastore_sha256"
        const val KEYSTORE_CERTIFICATE_SHA256 = "keystore_certificate_sha256"

        val LEGACY_ENCRYPTED_FIELDS = listOf(
            PRIVATE_KEY,
            PUBLIC_KEY,
            NONCE_KEY,
            MNEMONIC_KEY,
        )
        val LEGACY_PREFERENCE_FIELDS = LEGACY_ENCRYPTED_FIELDS + listOf(
            LEGACY_ADDRESS_KEY,
            CURRENT_ACCOUNT_KEY,
        )
        val ENCRYPTED_FIELDS = LEGACY_ENCRYPTED_FIELDS + SEED_KEY
    }
}
