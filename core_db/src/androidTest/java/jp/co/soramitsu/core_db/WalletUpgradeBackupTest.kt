package jp.co.soramitsu.core_db

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletDeletionContract
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.migrations.migration_walletIdentity_73_74
import jp.co.soramitsu.core_db.migrations.migration_walletDeletionJournal_74_75
import jp.co.soramitsu.core_db.migrations.migration_sora2PendingSubmission_75_76
import jp.co.soramitsu.core_db.migrations.migration_pendingNetworkTransactionChain_76_77
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletUpgradeBackupTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java,
        emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun prepareSandbox() {
        clearTestStorage()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
    }

    @After
    fun clearSandbox() {
        clearTestStorage()
        // Reset the process-wide gate for the next instrumentation test without exposing a
        // production reset API. An actually empty sandbox is the only state allowed to clear it.
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
    }

    @Test
    fun preMultiaccountRegisteredWalletIsBackedUpWithoutInventingAccountRows() {
        listOf(50, LEGACY_VERSION).forEach { version ->
            clearTestStorage()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            val file = context.getDatabasePath(DATABASE_NAME)
            file.parentFile?.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { sqlite ->
                sqlite.execSQL("CREATE TABLE tokens(id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL)")
                sqlite.execSQL("INSERT INTO tokens VALUES('retained-token', 'Retained cache')")
                if (version >= 58) {
                    sqlite.execSQL("CREATE TABLE accounts(substrateAddress TEXT NOT NULL PRIMARY KEY, accountName TEXT NOT NULL)")
                }
                sqlite.version = version
            }
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            assertTrue(preferences.edit()
                .putString("registration_state", "REGISTRATION_FINISHED")
                .putString("prefs_priv_key", "encrypted-private")
                .putString("prefs_pub_key", "encrypted-public")
                .putString("prefs_key_nonce", "encrypted-nonce")
                .putString("prefs_mnemonic", "encrypted-mnemonic")
                .commit())
            val before = file.readBytes()
            val originalPreferences = preferences.all
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            assertArrayEquals(before, file.readBytes())
            assertEquals(originalPreferences, preferences.all)
            assertTrue(context.noBackupFilesDir.listFiles().orEmpty().any {
                it.name.startsWith("wallet-upgrade-backup-v$version-to-") && File(it, ".complete").isFile
            })
        }
    }

    @Test
    fun preMultiaccountWalletWithIncompleteUnsuffixedKeypairIsSetAsideNotBlocking() {
        createLegacyDatabase(withAccount = false)
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        assertTrue(preferences.edit()
            .putString("registration_state", "REGISTRATION_FINISHED")
            .putString("prefs_priv_key", "encrypted-private")
            .putString("prefs_pub_key", "encrypted-public")
            .commit())
        val before = context.getDatabasePath(DATABASE_NAME).readBytes()
        val preferencesBefore = preferences.all

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertArrayEquals(before, context.getDatabasePath(DATABASE_NAME).readBytes())
        assertEquals(preferencesBefore, preferences.all)
        assertTrue(WalletUpgradeBackup.quarantine().unscopedMaterialWithoutRecordedOwner)
    }

    @Test
    fun applicationAttachContextWithoutApplicationContextCanPrepareEmptyInstall() {
        clearTestStorage()
        val attachContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context? = null
        }

        val result = WalletUpgradeBackup.prepare(attachContext)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun missingDatabaseWithEncryptedWalletMarkerKeepsKeysForRestore() {
        putWalletMarker()
        val preferencesBefore = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
        assertEquals(WALLET_ID, WalletUpgradeBackup.quarantine().staleSelection)
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all,
        )
    }

    @Test
    fun missingDatabaseWithOnlyRetainedWrappedAesKeyKeepsTheKey() {
        val wrappedKeyPreferences = context.getSharedPreferences(
            KEYSTORE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertTrue(
            wrappedKeyPreferences.edit()
                .putString(WRAPPED_AES_KEY, "retained-wrapped-wallet-key")
                .commit()
        )
        val wrappedKeyFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml",
        )
        val wrappedKeyBytes = wrappedKeyFile.readBytes()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertTrue(WalletUpgradeBackup.quarantine().retainedWrappedKeyWithoutDatabase)
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
        assertArrayEquals(wrappedKeyBytes, wrappedKeyFile.readBytes())
    }

    @Test
    fun reinstallWithRestoredEncryptedWalletStateAndNoDatabaseKeepsEveryByte() {
        putWalletMarker()
        putDataStoreStringMarker("prefs_seed$WALLET_ID")
        val dataStore = File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        )
        val dataStoreBytesBefore = dataStore.readBytes()
        val preferencesBefore = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).all

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).all,
        )
        assertArrayEquals(dataStoreBytesBefore, dataStore.readBytes())
        assertTrue(
            context.noBackupFilesDir.listFiles()
                .orEmpty()
                .none { it.name.startsWith(BACKUP_PREFIX) }
        )
    }

    @Test
    fun missingDatabaseWithRetainedUpgradeBackupKeepsTheBackupUntouched() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .single { it.name.startsWith(BACKUP_PREFIX) }
        val backupBefore = backup.walkTopDown()
            .filter(File::isFile)
            .associate { file ->
                file.relativeTo(backup).invariantSeparatorsPath to file.readBytes()
            }
        assertTrue(context.deleteDatabase(DATABASE_NAME))
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertTrue(WalletUpgradeBackup.quarantine().retainedBackupWithoutDatabase)
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
        val backupAfter = backup.walkTopDown()
            .filter(File::isFile)
            .associate { file ->
                file.relativeTo(backup).invariantSeparatorsPath to file.readBytes()
            }
        assertEquals(backupBefore.keys, backupAfter.keys)
        backupBefore.forEach { (name, bytes) ->
            assertArrayEquals(bytes, requireNotNull(backupAfter[name]))
        }
    }

    @Test
    fun walSidecarsWithoutTheirDatabaseAreRenamedAsideNotDeleted() {
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        val wal = File("${database.path}-wal")
        val walBytes = ByteArray(1_024) { (it * 13).toByte() }
        wal.writeBytes(walBytes)

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertFalse(wal.exists())
        val setAside = File(WalletUpgradeBackup.quarantine().setAsidePaths.single())
        assertTrue(setAside.path.contains("/wallet-quarantine-"))
        assertArrayEquals(walBytes, setAside.readBytes())
    }

    @Test
    fun emptyDatabaseWithEncryptedWalletMarkerIsSetAsideForOnboarding() {
        createLegacyDatabase(withAccount = false)
        putWalletMarker()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
        assertEquals(0, legacyAccountCount())
    }

    @Test
    fun symlinkedAuthoritativeDatabaseFailsBeforeAnySQLiteOpen() {
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        val target = File(context.cacheDir, "wallet-database-symlink-target")
        target.writeText("this is deliberately not a SQLite database")
        Os.symlink(target.absolutePath, database.absolutePath)

        try {
            val result = WalletUpgradeBackup.prepare(context)

            assertTrue(result.isFailure)
            assertEquals(
                "DATABASE_PATH_INVALID",
                WalletUpgradeBackup.blockingFailure()?.code,
            )
            assertTrue(target.isFile)
            assertTrue(
                context.noBackupFilesDir.listFiles()
                    .orEmpty()
                    .none { it.name.startsWith(BACKUP_PREFIX) }
            )
        } finally {
            database.delete()
            target.delete()
        }
    }

    @Test
    fun corruptLiveDatabaseInspectionNeverDeletesOrRewritesRecoveryBytes() {
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        assertTrue(
            database.parentFile?.let { parent ->
                parent.isDirectory || parent.mkdirs()
            } == true
        )
        val recoveryBytes = ByteArray(4_096) { index ->
            (index * 31 + 17).toByte()
        }
        database.writeBytes(recoveryBytes)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertTrue(database.isFile)
        assertArrayEquals(recoveryBytes, database.readBytes())
        assertTrue(
            context.noBackupFilesDir.listFiles()
                .orEmpty()
                .none { it.name.startsWith(BACKUP_PREFIX) }
        )
    }

    @Test
    fun roomCorruptionCallbackKeepsTheDatabaseAndCopiesItAsideForSupport() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val database = context.getDatabasePath(DATABASE_NAME)
        // Damage the SQLite header so the next open reports corruption to the open helper.
        val corrupt = database.readBytes().also { bytes ->
            for (index in 0 until 16) bytes[index] = 0x42
        }
        database.writeBytes(corrupt)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(CURRENT_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = error("Unexpected schema upgrade $oldVersion -> $newVersion")
                }
            )
            .build()
        val helper = WalletUpgradeBackup.gatedOpenHelperFactory().create(configuration)

        val failure = runCatching { helper.writableDatabase }.exceptionOrNull()
        runCatching { helper.close() }

        assertTrue(failure != null)
        assertTrue(database.isFile)
        assertArrayEquals(corrupt, database.readBytes())
        assertEquals("DATABASE_CORRUPT", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.BLOCKED,
            WalletRecoveryCapabilityGate.mode(),
        )
        val copy = context.noBackupFilesDir.listFiles().orEmpty()
            .single { it.name.startsWith("wallet-corrupt-quarantine-") }
        assertArrayEquals(corrupt, File(copy, DATABASE_NAME).readBytes())
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertTrue(options.canRestoreFromPhrase && options.canExport)
    }

    @Test
    fun startupKeepsTheNewestThreeSnapshotsAndEveryPreMigrationBackup() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val first = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(first.isDirectory)

        // Earlier 3.9.0 development builds published one snapshot per account change.
        val preMigration = copyBackupAs(first, generation = 2L, sourceVersion = LEGACY_VERSION)
        val snapshots = (3L..6L).map { copyBackupAs(first, it, CURRENT_VERSION) }

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        // Every wallet in the pruned copies is a live account with verified keys, and the three
        // newest snapshots pass the full validation, so the older ones are removed.
        assertFalse(first.exists())
        assertFalse(snapshots.first().exists())
        assertTrue(preMigration.isDirectory)
        snapshots.drop(1).forEach { assertTrue(it.name, it.isDirectory) }
        assertTrue(
            context.noBackupFilesDir.listFiles().orEmpty()
                .none { it.name.startsWith("wallet-backup-quarantine-pruned-") }
        )
        assertEquals(1, legacyAccountCount())
        assertTrue(WalletUpgradeBackup.nonBlockingWarnings().isEmpty())
    }

    /**
     * Application.attachBaseContext runs prepare() on the main thread in every process. With no
     * schema migration pending it must stay on the cheap path: no backup is fully validated, no
     * file is hashed or copied, also when the accounts change.
     */
    @Test
    fun startupWithNoPendingMigrationHashesCopiesAndValidatesNothing() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val first = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(first.isDirectory)
        // Left by earlier builds: the pre-migration backup and four current-schema snapshots.
        copyBackupAs(first, generation = 2L, sourceVersion = LEGACY_VERSION)
        (3L..6L).forEach { copyBackupAs(first, it, CURRENT_VERSION) }
        // The first start after that prunes once (full validation of the candidates only).
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val kept = backupDirectories()
        assertEquals(4, kept.size)

        val timings = mutableListOf<Long>()
        fun startApp() {
            WalletUpgradeBackup.WorkCountersForTest.reset()
            WalletUpgradeBackup.simulateProcessStartForTest()
            val started = SystemClock.elapsedRealtime()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            timings += SystemClock.elapsedRealtime() - started
            assertEquals(0, WalletUpgradeBackup.WorkCountersForTest.fullBackupValidations.get())
            assertEquals(0L, WalletUpgradeBackup.WorkCountersForTest.hashedBytes.get())
            assertEquals(0, WalletUpgradeBackup.WorkCountersForTest.copiedFiles.get())
        }
        repeat(3) { startApp() }

        // A second verified account changes the pruning decision. Every backup beyond the
        // newest three is a pre-migration backup, which is never pruned, so nothing is validated.
        addVerifiedAccount(PARTIAL_WALLET_ID, "Second")
        repeat(2) { startApp() }

        assertEquals(kept, backupDirectories())
        assertTrue(WalletUpgradeBackup.nonBlockingWarnings().isEmpty())
        Log.i(
            "WalletUpgradeBackupTest",
            "steady-state prepare() in ms: ${timings.joinToString()}",
        )
    }

    private fun backupDirectories(): List<String> = context.noBackupFilesDir.listFiles().orEmpty()
        .map(File::getName)
        .filter { it.startsWith(BACKUP_PREFIX) }
        .sorted()

    /** Adds an account row with a verified identity and its (marker) key set, as 3.9.0 would. */
    private fun addVerifiedAccount(address: String, name: String) {
        updateCurrentDatabase(
            "INSERT INTO accounts(substrateAddress, accountName) VALUES('$address', '$name')"
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletIdentities(
                walletId, displayName, secretSource, migrationState, derivationVersion
            ) VALUES('$address', '$name', 'MNEMONIC', 'VERIFIED', 1)
            """.trimIndent()
        )
        updateCurrentDatabase(
            """
            INSERT INTO networkAccounts(
                walletId, networkId, publicKey, address, derivationPath, derivationVersion, enabled
            ) VALUES('$address', 'sora2', '', '$address', '', 1, 1)
            """.trimIndent()
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString("prefs_priv_key$address", "encrypted-private-$address")
                .putString("prefs_pub_key$address", "encrypted-public-$address")
                .putString("prefs_key_nonce$address", "encrypted-nonce-$address")
                .putString("prefs_mnemonic$address", "encrypted-$address")
                .commit()
        )
    }

    @Test
    fun newerDamagedSnapshotsNeverCauseAValidOlderOneToBePruned() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val first = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        val valid = listOf(first) + (2L..3L).map { copyBackupAs(first, it, CURRENT_VERSION) }
        // Newer snapshots whose completion marker and manifest look fine but whose copied
        // preferences no longer match their recorded hash (bit rot, interrupted copy).
        val damaged = (4L..6L).map { generation ->
            copyBackupAs(first, generation, CURRENT_VERSION).also { backup ->
                val copied = backup.walkTopDown().first { it.isFile && it.name == "sora_prefs.xml" }
                copied.appendText(" ")
            }
        }

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        (valid + damaged).forEach { assertTrue(it.name, it.isDirectory) }
    }

    @Test
    fun snapshotHoldingAWalletWithoutVerifiedLiveKeysIsNeverPruned() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val first = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        val copies = (2L..5L).map { copyBackupAs(first, it, CURRENT_VERSION) }
        // The live keys of this account could not be verified on this phone: the old copies
        // may be the only readable ones.
        updateCurrentDatabase(
            "UPDATE walletIdentities SET migrationState = 'UNAVAILABLE', " +
                "secretSource = 'SECRET_UNAVAILABLE'"
        )

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        (listOf(first) + copies).forEach { assertTrue(it.name, it.isDirectory) }
    }

    /** A byte-identical copy of [backup] published under another generation (and source). */
    private fun copyBackupAs(backup: File, generation: Long, sourceVersion: Int): File {
        val manifest = JSONObject(File(backup, "manifest.json").readText())
        val fingerprint = manifest.getString("sourceFingerprint")
        val target = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$sourceVersion-to-$CURRENT_VERSION" +
                "-g${generation.toString().padStart(20, '0')}-$fingerprint",
        )
        assertTrue(backup.copyRecursively(target))
        File(target, "manifest.json").writeText(
            manifest
                .put("backupGeneration", generation)
                .put("sourceDatabaseVersion", sourceVersion)
                .toString()
        )
        return target
    }

    @Test
    fun accountWithSecretButMissingSelectionIsAdmitted() {
        createLegacyDatabase(withAccount = true)
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_mnemonic$WALLET_ID", "encrypted-value")
                .commit()
        )

        // The repository selects an existing account; the gate does not block.
        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertTrue(WalletUpgradeBackup.quarantine().isEmpty)
    }

    @Test
    fun currentLegacySecretRequiresCompleteKeypairAndPreservesEncryptedStorageBytes() {
        createCurrentDatabase(
            accounts = listOf(SoraAccountLocal(WALLET_ID, "Legacy secret")),
            secretSource = "LEGACY_SECRET",
        )
        val preferences = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$PREFERENCES_NAME.xml",
        )
        val before = preferences.readBytes()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertArrayEquals(before, preferences.readBytes())
    }

    @Test
    fun currentLegacySecretWithIncompleteKeypairIsLeftToPerAccountVerification() {
        createCurrentDatabase(
            accounts = listOf(SoraAccountLocal(WALLET_ID, "Legacy secret")),
            secretSource = "LEGACY_SECRET",
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove("prefs_key_nonce$WALLET_ID")
                .commit()
        )
        val preferencesBefore = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().incompleteKeySetWalletIds)
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all,
        )
    }

    @Test
    fun currentLegacySecretWithMnemonicConflictIsLeftToPerAccountVerification() {
        assertLegacySecretConflictIsAdmitted(
            stringKey = "prefs_mnemonic$WALLET_ID"
        )
    }

    @Test
    fun currentLegacySecretWithSeedConflictIsLeftToPerAccountVerification() {
        assertLegacySecretConflictIsAdmitted(
            stringKey = "prefs_seed$WALLET_ID"
        )
    }

    @Test
    fun currentLegacySecretWithWatchOnlyConflictIsLeftToPerAccountVerification() {
        createCurrentDatabase(
            accounts = listOf(SoraAccountLocal(WALLET_ID, "Legacy secret")),
            secretSource = "LEGACY_SECRET",
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("wallet.watchOnly.$WALLET_ID", true)
                .commit()
        )

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
    }

    @Test
    fun currentMnemonicWithIncompleteKeypairIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("MNEMONIC") { editor ->
            editor.remove("prefs_key_nonce$WALLET_ID")
        }
    }

    @Test
    fun currentMnemonicWithWatchOnlyConflictIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("MNEMONIC") { editor ->
            editor.putBoolean("wallet.watchOnly.$WALLET_ID", true)
        }
    }

    @Test
    fun currentRawSeedWithIncompleteKeypairIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("RAW_SEED") { editor ->
            editor.remove("prefs_pub_key$WALLET_ID")
        }
    }

    @Test
    fun currentRawSeedWithMnemonicConflictIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("RAW_SEED") { editor ->
            editor.putString("prefs_mnemonic$WALLET_ID", "conflicting-encrypted-source")
        }
    }

    @Test
    fun currentRawSeedWithWatchOnlyConflictIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("RAW_SEED") { editor ->
            editor.putBoolean("wallet.watchOnly.$WALLET_ID", true)
        }
    }

    @Test
    fun currentWatchOnlyWithCompleteKeypairConflictIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("WATCH_ONLY") { editor ->
            editor
                .putString("prefs_priv_key$WALLET_ID", "conflicting-private")
                .putString("prefs_pub_key$WALLET_ID", "conflicting-public")
                .putString("prefs_key_nonce$WALLET_ID", "conflicting-nonce")
        }
    }

    @Test
    fun currentWatchOnlyWithMnemonicConflictIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("WATCH_ONLY") { editor ->
            editor.putString("prefs_mnemonic$WALLET_ID", "conflicting-encrypted-source")
        }
    }

    @Test
    fun currentWatchOnlyWithRawSeedConflictIsLeftToPerAccountVerification() {
        assertCurrentSourceMutationIsAdmitted("WATCH_ONLY") { editor ->
            editor.putString("prefs_seed$WALLET_ID", "conflicting-encrypted-source")
        }
    }

    @Test
    fun secondWalletSecretWithoutCommittedAccountRowIsSetAside() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker(PARTIAL_WALLET_ID)
        val preferencesBefore = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(PARTIAL_WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
        assertEquals(1, legacyAccountCount())
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all,
        )
    }

    @Test
    fun selectedWalletWithoutCommittedAccountRowIsAStaleSelection() {
        createLegacyDatabase(withAccount = true)
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("cur_account_address", PARTIAL_WALLET_ID)
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(PARTIAL_WALLET_ID, WalletUpgradeBackup.quarantine().staleSelection)
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun dataStoreSecondWalletSecretWithoutCommittedAccountRowIsSetAside() {
        createLegacyDatabase(withAccount = true)
        putDataStoreStringMarker("prefs_seed$PARTIAL_WALLET_ID")
        val dataStore = File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb")
        val bytes = dataStore.readBytes()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(PARTIAL_WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
        assertEquals(1, legacyAccountCount())
        assertArrayEquals(bytes, dataStore.readBytes())
    }

    @Test
    fun staleFalseWatchOnlyPreferenceDoesNotInventAnOrphanWallet() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("wallet.watchOnly.$PARTIAL_WALLET_ID", false)
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun emptySelectionAndInitialRegistrationDoNotInventAnOrphanWallet() {
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("cur_account_address", "")
                .putString("registration_state", "INITIAL")
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun finishedRegistrationWithoutDatabaseIsAdmitted() {
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("registration_state", "REGISTRATION_FINISHED")
                .commit()
        )

        // No account rows: Splash opens onboarding, which offers restore.
        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun emptyDataStoreSelectionDoesNotInventAnOrphanWallet() {
        putDataStoreStringMarker("cur_account_address", "")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun clearedEmptyDataStoreDoesNotInventAnOrphanWallet() {
        val destination = File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        )
        destination.parentFile?.mkdirs()
        assertTrue(destination.createNewFile())

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun retainedWalletIsBackedUpWithoutChangingItsLegacyRow() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertEquals(1, legacyAccountCount())
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(File(backup, ".complete").readText() == "verified")
        assertTrue(File(backup, "manifest.json").isFile)
        assertTrue(File(backup, "databases/$DATABASE_NAME").isFile)
        assertEquals(1L, Os.lstat(File(backup, ".complete").path).st_nlink)
        assertEquals(
            1L,
            Os.lstat(File(backup, "databases/$DATABASE_NAME").path).st_nlink,
        )
        assertTrue(
            File(
                backup,
                "shared_prefs/$PREFERENCES_NAME.xml",
            ).isFile
        )
        assertTrue(
            context.cacheDir.listFiles()
                .orEmpty()
                .none { it.name.startsWith("wallet-backup-validation-") }
        )
    }

    @Test
    fun publishedBackupWithAnExtraHardLinkFailsClosedAsNonImmutable() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        val publishedDatabase = File(backup, "databases/$DATABASE_NAME")
        val extraLink = File(
            context.noBackupFilesDir,
            ".wallet-backup-extra-link-${UUID.randomUUID()}",
        )
        try {
            try {
                Os.link(publishedDatabase.path, extraLink.path)
            } catch (error: ErrnoException) {
                if (
                    error.errno != OsConstants.EPERM &&
                    error.errno != OsConstants.EACCES
                ) {
                    throw error
                }
                // Android SELinux denies app-created hard links on current devices. That denial
                // is already the stronger outcome: the published inode remains single-linked and
                // the verified backup continues to admit without mutation.
                assertEquals(1L, Os.lstat(publishedDatabase.path).st_nlink)
                assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
                assertEquals(null, WalletUpgradeBackup.blockingFailure())
                return
            }
            assertEquals(2L, Os.lstat(publishedDatabase.path).st_nlink)
            assertEquals(2L, Os.lstat(extraLink.path).st_nlink)

            val result = WalletUpgradeBackup.prepare(context)

            assertTrue(result.isFailure)
            assertEquals(
                "EXISTING_BACKUP_INVALID",
                WalletUpgradeBackup.blockingFailure()?.code,
            )
            assertTrue(publishedDatabase.isFile)
            assertEquals(2L, Os.lstat(publishedDatabase.path).st_nlink)
        } finally {
            if (extraLink.exists()) {
                Os.remove(extraLink.path)
            }
        }
    }

    @Test
    fun partialPublishedBackupWithoutCompletionMarkerIsRenamedAsideNotBlocking() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val partialDestination = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(partialDestination.mkdir())
        File(partialDestination, "partial-publication").writeText("incomplete")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(1, legacyAccountCount())
        assertEquals("verified", File(partialDestination, ".complete").readText())
        assertFalse(File(partialDestination, "partial-publication").exists())
        assertEquals("incomplete", setAsideFile("partial-publication").readText())
    }

    @Test
    fun insufficientBackupStorageFailsBeforeChangingInstalledWalletOrCreatingStaging() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        val databaseBytesBefore = database.readBytes()
        val preferencesBefore = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).all

        val result = WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(
            context = context,
            availableBackupBytes = 0L,
        )

        assertTrue(result.isFailure)
        assertEquals(
            "INSUFFICIENT_BACKUP_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertArrayEquals(databaseBytesBefore, database.readBytes())
        assertEquals(1, legacyAccountCount())
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).all,
        )
        assertTrue(
            context.noBackupFilesDir.listFiles()
                .orEmpty()
                .none { it.name.startsWith(BACKUP_PREFIX) }
        )
    }

    @Test
    fun appVersionRollbackFromNewerSchemaFailsWithoutChangingInstalledWallet() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL("PRAGMA user_version = ${CURRENT_VERSION + 1}")
        }
        val databaseBytesBefore = database.readBytes()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "DATABASE_DOWNGRADE_UNSUPPORTED",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertArrayEquals(databaseBytesBefore, database.readBytes())
        assertEquals(1, legacyAccountCount())
        assertTrue(
            context.noBackupFilesDir.listFiles()
                .orEmpty()
                .none { it.name.startsWith(BACKUP_PREFIX) }
        )
    }

    @Test
    fun openWalSidecarsAreCopiedByteForByteBeforeMigrationCanOpenRoom() {
        createLegacyWalDatabase().use {
            putWalletMarker()
            val database = context.getDatabasePath(DATABASE_NAME)
            val installedFiles = listOf(
                database,
                File("${database.path}-wal"),
                File("${database.path}-shm"),
            )
            assertTrue(installedFiles.all { it.isFile })
            val installedBytesBefore = installedFiles.associateWith { it.readBytes() }

            val result = WalletUpgradeBackup.prepare(context)

            assertTrue(result.isSuccess)
            assertEquals(null, WalletUpgradeBackup.blockingFailure())
            val backup = File(
                context.noBackupFilesDir,
                "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
            )
            installedFiles.forEach { installed ->
                val expected = requireNotNull(installedBytesBefore[installed])
                val installedAfterPreparation = installed.readBytes()
                // Opening a WAL database read-only can advance reader marks in the volatile
                // wal-index shared-memory file. The authoritative database and WAL bytes must
                // remain unchanged; every backed-up sidecar must match the final admitted source.
                if (!installed.name.endsWith("-shm")) {
                    assertArrayEquals(
                        "installed ${installed.name} changed",
                        expected,
                        installedAfterPreparation,
                    )
                }
                assertArrayEquals(
                    "backup ${installed.name} changed",
                    installedAfterPreparation,
                    File(backup, "databases/${installed.name}").readBytes(),
                )
            }
            assertEquals("verified", File(backup, ".complete").readText())
        }
    }

    @Test
    fun currentSchemaInterruptedSiblingStagingIsRenamedAsideNotBlocking() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val staging = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX${CURRENT_VERSION - 1}-to-$CURRENT_VERSION.staging",
        ).apply {
            assertTrue(mkdir())
            File(this, "partial").writeText("incomplete")
        }

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertFalse(staging.exists())
        assertEquals("incomplete", setAsideFile("partial").readText())
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun currentSchemaBackupWithMismatchedGenerationIsRenamedAsideAndReplaced() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        val manifestFile = File(backup, "manifest.json")
        val tampered = JSONObject(manifestFile.readText())
            .put("backupGeneration", 2L)
            .toString()
        manifestFile.writeText(tampered)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(tampered, setAsideFile("manifest.json").readText())
        assertEquals("verified", File(backup, ".complete").readText())
        assertEquals(1L, JSONObject(manifestFile.readText()).getLong("backupGeneration"))
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun verifiedBackupRecoveryExportIsCompleteAndNeverMutatesRetainedSource() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        val originalManifest = File(backup, "manifest.json").readText()

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("README.txt") != null)
            assertTrue(zip.getEntry("recovery-manifest.json") != null)
            assertTrue(
                zip.getEntry("encrypted-backup/databases/$DATABASE_NAME") != null
            )
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "verified-upgrade-backup",
                recoveryManifest.getString("sourceType"),
            )
        }
        assertTrue(backup.isDirectory)
        assertEquals(originalManifest, File(backup, "manifest.json").readText())
        assertFalse(
            File(
                requireNotNull(archive.parentFile),
                ".${archive.name}.staging",
            ).exists()
        )
        assertEquals(1L, Os.lstat(archive.path).st_nlink)
    }

    @Test
    fun recoveryExportFinalNameCollisionNeverReplacesOrDeletesExistingFile() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val exportDirectory = File(
            context.cacheDir,
            "wallet-recovery-exports",
        )
        assertTrue(exportDirectory.mkdirs())
        val archiveName = "sora-wallet-recovery-collision.zip"
        val existingArchive = File(exportDirectory, archiveName)
        val sentinel = "existing-unrelated-export".toByteArray(Charsets.UTF_8)
        existingArchive.writeBytes(sentinel)

        val result = WalletUpgradeBackup.createRecoveryArchiveWithFileNameForTest(
            context = context,
            archiveFileName = archiveName,
        )

        assertTrue(result.isFailure)
        assertEquals(
            "RECOVERY_EXPORT_EXISTS",
            (result.exceptionOrNull() as WalletUpgradeBackupException).code,
        )
        assertArrayEquals(sentinel, existingArchive.readBytes())
        assertFalse(File(exportDirectory, ".$archiveName.staging").exists())
    }

    @Test
    fun sourceMutationImmediatelyBeforeActivationFailsClosedAfterBackupPublication() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()

        val result = WalletUpgradeBackup.prepareWithFinalSourceMutationForTest(
            context = context,
        ) {
            putWalletMarker(PARTIAL_WALLET_ID)
        }

        assertTrue(result.isFailure)
        assertEquals("SOURCE_CHANGED", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(1, legacyAccountCount())
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertEquals("verified", File(backup, ".complete").readText())
        assertFalse(File("${backup.path}.staging").exists())
    }

    @Test
    fun databaseBytesMutationImmediatelyBeforeActivationFailsClosedAfterBackupPublication() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)

        val result = WalletUpgradeBackup.prepareWithFinalSourceMutationForTest(
            context = context,
        ) {
            SQLiteDatabase.openDatabase(
                database.path,
                null,
                SQLiteDatabase.OPEN_READWRITE,
            ).use { sqlite ->
                sqlite.execSQL("CREATE TABLE final_source_mutation(value TEXT NOT NULL)")
                sqlite.execSQL(
                    "INSERT INTO final_source_mutation(value) VALUES('changed')"
                )
            }
        }

        assertTrue(result.isFailure)
        assertEquals("SOURCE_CHANGED", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(1, legacyAccountCount())
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertEquals("verified", File(backup, ".complete").readText())
        assertFalse(File("${backup.path}.staging").exists())
    }

    @Test
    fun recoveryExportFallsBackToUnopenedEncryptedStorageWhenBackupCannotPublish() {
        createLegacyDatabase(withAccount = true)
        writeSharedPreferencesBackup("<map><string")
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            assertTrue(
                zip.getEntry("live-encrypted-storage/databases/$DATABASE_NAME") != null
            )
            assertTrue(
                zip.getEntry(
                    "live-encrypted-storage/shared_prefs/$PREFERENCES_NAME.xml.bak"
                ) != null
            )
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "live-encrypted-storage",
                recoveryManifest.getString("sourceType"),
            )
        }
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun recoveryExportNeverPresentsInterruptedStagingAsVerifiedBackup() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION.staging",
        ).apply {
            assertTrue(mkdirs())
            File(this, "partial").writeText("incomplete")
        }
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            assertTrue(
                zip.getEntry("encrypted-backup/databases/$DATABASE_NAME") != null
            )
            assertTrue(zip.getEntry("encrypted-backup/partial") == null)
        }
        assertEquals("incomplete", setAsideFile("partial").readText())
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun unexpectedMigrationFailureCannotDemoteExistingBackupBlocker() {
        createLegacyDatabase(withAccount = true)
        writeSharedPreferencesBackup("<map><string")
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.BLOCKED,
            WalletRecoveryCapabilityGate.mode(),
        )

        WalletUpgradeBackup.noteUnexpectedMigrationFailure()

        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.BLOCKED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertFalse(WalletUpgradeBackup.canAuthorizeMigrationRetry())
        assertTrue(WalletUpgradeBackup.authorizeMigrationRetry(context).isFailure)
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertTrue(options.canRetry && options.canRestoreFromPhrase && options.canExport)
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun interruptedSuccessorForcesLiveFallbackBesideVerifiedBackup() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val staging = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION-g-pending.staging",
        )
        assertTrue(staging.mkdir())
        File(staging, "partial").writeText("incomplete")

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "live-encrypted-storage",
                recoveryManifest.getString("sourceType"),
            )
            assertTrue(
                zip.getEntry("live-encrypted-storage/databases/$DATABASE_NAME") != null
            )
            assertTrue(zip.getEntry("encrypted-backup/manifest.json") == null)
        }
    }

    @Test
    fun recoveryExportNeverIncludesUnmanifestedBackupFiles() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        File(backup, "unmanifested").writeText("must-not-export")

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            assertTrue(
                zip.getEntry("live-encrypted-storage/databases/$DATABASE_NAME") != null
            )
            assertTrue(zip.getEntry("encrypted-backup/unmanifested") == null)
        }
        assertTrue(File(backup, "unmanifested").isFile)
    }

    @Test
    fun recoveryExportRejectsUnexpectedEmptyBackupDirectory() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(File(backup, "unexpected-empty-directory").mkdir())

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "live-encrypted-storage",
                recoveryManifest.getString("sourceType"),
            )
            assertTrue(
                zip.getEntry("live-encrypted-storage/databases/$DATABASE_NAME") != null
            )
        }
    }

    @Test
    fun recoveryExportRejectsSymlinkedBackupEntryAndUsesLiveEncryptedStorage() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        val target = File(context.cacheDir, "wallet-backup-symlink-target")
        target.writeText("must-not-follow")
        val linkedEntry = File(backup, "linked-secret")
        Os.symlink(target.absolutePath, linkedEntry.absolutePath)

        try {
            val archive =
                WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

            ZipFile(archive).use { zip ->
                val recoveryManifest = JSONObject(
                    zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                        .bufferedReader()
                        .use { it.readText() }
                )
                assertEquals(
                    "live-encrypted-storage",
                    recoveryManifest.getString("sourceType"),
                )
                assertTrue(
                    zip.getEntry(
                        "live-encrypted-storage/databases/$DATABASE_NAME"
                    ) != null
                )
                assertTrue(zip.getEntry("encrypted-backup/linked-secret") == null)
            }
        } finally {
            linkedEntry.delete()
            target.delete()
        }
    }

    @Test
    fun corruptHigherGenerationBackupNeverFallsBackToOlderVerifiedSnapshot() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val original = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )

        insertLegacyAccount(PARTIAL_WALLET_ID, "Second")
        putWalletMarker(PARTIAL_WALLET_ID)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val published = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter {
                it.isDirectory &&
                    it.name.startsWith(
                        "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION"
                    ) &&
                    !it.name.endsWith(".staging")
            }
        assertEquals(2, published.size)
        val newest = published.single { it != original }
        val newestManifest = File(newest, "manifest.json")
        assertEquals(
            2L,
            JSONObject(newestManifest.readText()).getLong("backupGeneration"),
        )
        newestManifest.writeText(
            JSONObject(newestManifest.readText())
                .put("sourceFingerprint", "invalid")
                .toString()
        )
        val now = System.currentTimeMillis()
        assertTrue(original.setLastModified(now + 4_000L))
        assertTrue(newest.setLastModified(now - 4_000L))
        assertTrue(
            original.lastModified() > newest.lastModified()
        )

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "live-encrypted-storage",
                recoveryManifest.getString("sourceType"),
            )
            assertTrue(
                zip.getEntry("live-encrypted-storage/databases/$DATABASE_NAME") != null
            )
            assertTrue(zip.getEntry("encrypted-backup/manifest.json") == null)
        }
    }

    @Test
    fun recoveryExportUsesHighestVerifiedGenerationDespiteClockRollback() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val original = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )

        insertLegacyAccount(PARTIAL_WALLET_ID, "Second")
        putWalletMarker(PARTIAL_WALLET_ID)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val published = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter {
                it.isDirectory &&
                    it.name.startsWith(
                        "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION"
                    ) &&
                    !it.name.endsWith(".staging")
            }
        assertEquals(2, published.size)
        val higherGeneration = published.single { it != original }
        val now = System.currentTimeMillis()
        assertTrue(original.setLastModified(now + 4_000L))
        assertTrue(higherGeneration.setLastModified(now - 4_000L))
        assertTrue(original.lastModified() > higherGeneration.lastModified())

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "verified-upgrade-backup",
                recoveryManifest.getString("sourceType"),
            )
            val selectedManifest = JSONObject(
                zip.getInputStream(
                    zip.getEntry("encrypted-backup/manifest.json")
                ).bufferedReader().use { it.readText() }
            )
            assertEquals(2L, selectedManifest.getLong("backupGeneration"))
            assertEquals(2, selectedManifest.getInt("legacyAccountCount"))
        }
    }

    @Test
    fun generationManifestMustMatchImmutableBackupDirectoryName() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        insertLegacyAccount(PARTIAL_WALLET_ID, "Second")
        putWalletMarker(PARTIAL_WALLET_ID)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val higherGeneration = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .single {
                it.isDirectory &&
                    it.name.contains("-g00000000000000000002-")
            }
        val manifestFile = File(higherGeneration, "manifest.json")
        manifestFile.writeText(
            JSONObject(manifestFile.readText())
                .put("backupGeneration", 1L)
                .toString()
        )

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "live-encrypted-storage",
                recoveryManifest.getString("sourceType"),
            )
            assertTrue(zip.getEntry("encrypted-backup/manifest.json") == null)
        }
    }

    @Test
    fun missingGenerationIsAGapNotABlocker() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val generationOne = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )

        insertLegacyAccount(PARTIAL_WALLET_ID, "Second")
        putWalletMarker(PARTIAL_WALLET_ID)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertTrue(generationOne.deleteRecursively())

        // Generation 2 alone is still a verified backup (old snapshots may be pruned).
        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()
        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "verified-upgrade-backup",
                recoveryManifest.getString("sourceType"),
            )
        }

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(2, legacyAccountCount())
    }

    @Test
    fun duplicateGenerationAcrossSourceVersionsNeverBecomesTheArchiveSource() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val original = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        // A second generation-1 backup claiming another source version.
        val duplicate = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX${LEGACY_VERSION - 1}-to-$CURRENT_VERSION",
        )
        assertTrue(original.copyRecursively(duplicate))
        val manifestFile = File(duplicate, "manifest.json")
        manifestFile.writeText(
            JSONObject(manifestFile.readText())
                .put("sourceDatabaseVersion", LEGACY_VERSION - 1)
                .toString()
        )

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()
        ZipFile(archive).use { zip ->
            val recoveryManifest = JSONObject(
                zip.getInputStream(zip.getEntry("recovery-manifest.json"))
                    .bufferedReader()
                    .use { it.readText() }
            )
            assertEquals(
                "live-encrypted-storage",
                recoveryManifest.getString("sourceType"),
            )
            assertTrue(zip.getEntry("encrypted-backup/manifest.json") == null)
        }

        // Startup is not blocked: the exact, deeply verified original backup is reused.
        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertTrue(original.isDirectory)
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun symlinkedEncryptedKeyAliasBackupFailsBeforeBackupPublication() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val keyAlias = File(
            context.applicationInfo.dataDir,
            "shared_prefs/key_alias.xml.bak",
        )
        keyAlias.parentFile?.mkdirs()
        val retainedBytes = keyAlias
            .takeIf { it.isFile }
            ?.readBytes()
        if (keyAlias.exists()) assertTrue(keyAlias.delete())
        val target = File(context.cacheDir, "wallet-key-alias-symlink-target")
        target.writeText("encrypted-key-alias")
        Os.symlink(target.absolutePath, keyAlias.absolutePath)

        try {
            val result = WalletUpgradeBackup.prepare(context)

            assertTrue(result.isFailure)
            assertEquals(
                "PREFERENCES_INVENTORY_INVALID",
                WalletUpgradeBackup.blockingFailure()?.code,
            )
            assertTrue(
                context.noBackupFilesDir.listFiles()
                    .orEmpty()
                    .none {
                        it.name.startsWith(BACKUP_PREFIX) &&
                            !it.name.endsWith(".staging")
                    }
            )
        } finally {
            keyAlias.delete()
            target.delete()
            retainedBytes?.let {
                keyAlias.writeBytes(it)
                it.fill(0)
            }
        }
    }

    @Test
    fun manifestWalletCountMustMatchCopiedDatabaseInventory() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        val manifestFile = File(backup, "manifest.json")
        val manifest = JSONObject(manifestFile.readText())
            .put("legacyAccountCount", 99)
        manifestFile.writeText(manifest.toString())

        // The tampered backup is never reused; a new verified backup is made instead.
        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        val published = context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it.name.startsWith(BACKUP_PREFIX) && it != backup }
        assertEquals(1, published.size)
        assertEquals(
            1,
            JSONObject(File(published.single(), "manifest.json").readText())
                .getInt("legacyAccountCount"),
        )
    }

    @Test
    fun unrecognizedStagingIsPreservedAsideAndANewBackupIsMade() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val staging = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION.staging",
        ).apply {
            assertTrue(mkdirs())
            File(this, "partial").writeText("incomplete")
        }

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(1, legacyAccountCount())
        assertFalse(staging.exists())
        assertEquals("incomplete", setAsideFile("partial").readText())
        assertEquals(
            "verified",
            File(
                context.noBackupFilesDir,
                "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION/.complete",
            ).readText(),
        )
    }

    @Test
    fun interruptedBackupCopiesResumeBeforeRoomAndAcrossRestart() {
        // Start with a real Room 73 schema and bytes copied by the production backup writer.
        // Materialize the persisted files at each interruption boundary, then reopen through the
        // real preflight and Room migration paths. No recovery action rewrites credential bytes.
        listOf("empty", "directory", "source-prefix", "manifest-prefix", "completion-prefix",
            "staged", "published-prefix", "published").forEach { boundary ->
            clearTestStorage()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            migrationHelper.createDatabase(DATABASE_NAME, LEGACY_VERSION).use { sqlite ->
                sqlite.execSQL("INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(WALLET_ID, "Retained"))
            }
            putWalletMarker()
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            assertTrue(preferences.edit()
                .putString("prefs_priv_key$WALLET_ID", "encrypted-private")
                .putString("prefs_pub_key$WALLET_ID", "encrypted-public")
                .putString("prefs_key_nonce$WALLET_ID", "encrypted-nonce")
                .putString("prefs_seed$WALLET_ID", "encrypted-seed").commit())
            val originalPreferences = preferences.all
            val installed = context.getDatabasePath(DATABASE_NAME)
            val originalDatabase = installed.readBytes()
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            val published = File(context.noBackupFilesDir,
                "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION")
            val staging = File(published.parentFile, "${published.name}.staging")
            assertTrue(published.renameTo(staging))
            when (boundary) {
                "empty", "directory" -> {
                    assertTrue(staging.deleteRecursively())
                    assertTrue(staging.mkdirs())
                    if (boundary == "directory") assertTrue(File(staging, "databases").mkdir())
                }
                "source-prefix" -> {
                    assertTrue(staging.deleteRecursively())
                    val copied = File(staging, "databases/app.db")
                    assertTrue(copied.parentFile!!.mkdirs())
                    copied.writeBytes(originalDatabase.copyOf(originalDatabase.size / 2))
                }
                "manifest-prefix" -> {
                    assertTrue(File(staging, ".complete").delete())
                    val manifest = File(staging, "manifest.json")
                    manifest.writeBytes(manifest.readBytes().copyOf(37))
                }
                "completion-prefix" -> File(staging, ".complete").writeText("ver")
                "published-prefix" -> {
                    val copied = File(published, "databases/app.db")
                    assertTrue(copied.parentFile!!.mkdirs())
                    copied.writeBytes(originalDatabase.copyOf(originalDatabase.size / 2))
                }
                "published" -> assertTrue(staging.copyRecursively(published))
            }
            repeat(2) {
                val result = WalletUpgradeBackup.prepare(context)
                assertTrue("$boundary: ${result.exceptionOrNull()}", result.isSuccess)
                assertArrayEquals(originalDatabase, installed.readBytes())
                assertEquals(originalPreferences, preferences.all)
                assertFalse(staging.exists())
                assertEquals("verified", File(published, ".complete").readText())
            }
            repeat(2) {
                assertTrue("$boundary restart", WalletUpgradeBackup.prepare(context).isSuccess)
                val room = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
                    .openHelperFactory(WalletUpgradeBackup.gatedOpenHelperFactory())
                    .addMigrations(migration_walletIdentity_73_74, migration_walletDeletionJournal_74_75,
                        migration_sora2PendingSubmission_75_76, migration_pendingNetworkTransactionChain_76_77)
                    .build()
                try {
                    val sqlite = room.openHelper.writableDatabase
                    assertEquals(CURRENT_VERSION, sqlite.version)
                    sqlite.query("SELECT substrateAddress, accountName FROM accounts").use { rows ->
                        assertTrue(rows.moveToFirst())
                        assertEquals(WALLET_ID, rows.getString(0))
                        assertEquals("Retained", rows.getString(1))
                        assertFalse(rows.moveToNext())
                    }
                } finally {
                    room.close()
                }
                assertEquals(originalPreferences, preferences.all)
                assertArrayEquals(originalDatabase, File(published, "databases/app.db").readBytes())
            }
        }
    }

    @Test
    fun changedStagingCopyIsRetainedAsideAndNeverTrusted() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val published = File(context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION")
        val staging = File(published.parentFile, "${published.name}.staging")
        assertTrue(published.renameTo(staging))
        val copied = File(staging, "databases/app.db")
        val modified = copied.readBytes().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        copied.writeBytes(modified)
        val originalDatabase = context.getDatabasePath(DATABASE_NAME).readBytes()
        repeat(2) {
            assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
            assertArrayEquals(originalDatabase, context.getDatabasePath(DATABASE_NAME).readBytes())
            assertFalse(staging.exists())
            assertArrayEquals(
                originalDatabase,
                File(published, "databases/$DATABASE_NAME").readBytes(),
            )
        }
        assertArrayEquals(modified, setAsideFile("app.db").readBytes())
    }

    @Test
    fun sharedPreferencesBackupIsAuthoritativeForOrphanDetection() {
        writeSharedPreferencesBackup(
            """<?xml version="1.0" encoding="utf-8" standalone="yes" ?>""" +
                """<map><string name="prefs_mnemonic$WALLET_ID">encrypted</string>""" +
                """<string name="cur_account_address">$WALLET_ID</string></map>"""
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
    }

    @Test
    fun sharedPreferencesBackupOverridesCleanMainFile() {
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("registration_state", "INITIAL")
                .commit()
        )
        writeSharedPreferencesBackup(
            """<?xml version="1.0" encoding="utf-8" standalone="yes" ?>""" +
                """<map><string name="prefs_seed$WALLET_ID">encrypted</string></map>"""
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
    }

    @Test
    fun malformedSharedPreferencesBackupFailsClosed() {
        writeSharedPreferencesBackup("<map><string")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun nonMapSharedPreferencesRootFailsClosed() {
        writeSharedPreferencesBackup("<foo/>")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun malformedUtf8SharedPreferencesFailsClosed() {
        writeSharedPreferencesBackupBytes(
            byteArrayOf(
                '<'.code.toByte(),
                'm'.code.toByte(),
                'a'.code.toByte(),
                'p'.code.toByte(),
                '>'.code.toByte(),
                0xc3.toByte(),
                0x28,
            )
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun authoritativeDataStoreDirectoryFailsClosed() {
        val destination = File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        )
        destination.parentFile?.mkdirs()
        assertTrue(destination.mkdir())

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun symlinkedAuthoritativeDataStoreFailsClosedWithoutFollowingTarget() {
        val destination = File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        )
        destination.parentFile?.mkdirs()
        val target = File(context.cacheDir, "wallet-datastore-symlink-target")
        target.writeBytes(byteArrayOf())
        Os.symlink(target.absolutePath, destination.absolutePath)

        try {
            val result = WalletUpgradeBackup.prepare(context)

            assertTrue(result.isFailure)
            assertEquals(
                "PREFERENCES_INVENTORY_INVALID",
                WalletUpgradeBackup.blockingFailure()?.code,
            )
            assertEquals(0L, target.length())
            assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
        } finally {
            destination.delete()
            target.delete()
        }
    }

    @Test
    fun retainedSharedPreferencesBackupIsIncludedInVerifiedBackup() {
        createLegacyDatabase(withAccount = true)
        writeSharedPreferencesBackup(
            """<?xml version="1.0" encoding="utf-8" standalone="yes" ?>""" +
                """<map><string name="prefs_mnemonic$WALLET_ID">encrypted</string>""" +
                """<string name="cur_account_address">$WALLET_ID</string></map>"""
        )

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(
            File(
                backup,
                "shared_prefs/$PREFERENCES_NAME.xml.bak",
            ).isFile
        )
    }

    @Test
    fun staleVerifiedBackupIsPreservedAndNeverReusedForChangedSource() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val original = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        val originalManifest = File(original, "manifest.json").readText()

        insertLegacyAccount(PARTIAL_WALLET_ID, "Second")
        putWalletMarker(PARTIAL_WALLET_ID)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        val published = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter {
                it.isDirectory &&
                    it.name.startsWith(
                        "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION"
                    ) &&
                    !it.name.endsWith(".staging")
            }
        assertEquals(2, published.size)
        assertEquals(originalManifest, File(original, "manifest.json").readText())
        val updated = published.single { it != original }
        val updatedManifest = JSONObject(File(updated, "manifest.json").readText())
        assertEquals(1L, JSONObject(originalManifest).getLong("backupGeneration"))
        assertEquals(2L, updatedManifest.getLong("backupGeneration"))
        assertEquals(2, updatedManifest.getInt("legacyAccountCount"))
        assertTrue(updated.name.contains("-g00000000000000000002-"))
        assertTrue(updated.name.endsWith(updatedManifest.getString("sourceFingerprint")))
    }

    @Test
    fun malformedUtf8DataStoreKeyFailsClosedWithInventoryCode() {
        val value = preferenceStringValue("encrypted")
        writeDataStore(
            preferenceMapEntry(
                key = byteArrayOf(0xc3.toByte(), 0x28),
                value = value,
            )
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun dataStoreMapEntryWithoutValueFailsClosed() {
        val key = "prefs_seed$WALLET_ID".toByteArray(Charsets.UTF_8)
        val entryWithoutValue = ByteArrayOutputStream().apply {
            write(10)
            writeVarint(key.size)
            write(key)
        }.toByteArray()
        writeDataStore(entryWithoutValue)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun duplicateDataStoreMapKeyFailsClosed() {
        val key = "prefs_seed$WALLET_ID".toByteArray(Charsets.UTF_8)
        val entry = preferenceMapEntry(key, preferenceStringValue("encrypted"))
        val preferences = ByteArrayOutputStream().apply {
            repeat(2) {
                write(10)
                writeVarint(entry.size)
                write(entry)
            }
        }.toByteArray()
        writeRawDataStore(preferences)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun duplicateDataStoreValueOneofFailsClosed() {
        val malformedValue = ByteArrayOutputStream().apply {
            write(8)
            write(1)
            val text = "encrypted".toByteArray(Charsets.UTF_8)
            write(42)
            writeVarint(text.size)
            write(text)
        }.toByteArray()
        writeDataStore(
            preferenceMapEntry(
                "prefs_seed$WALLET_ID".toByteArray(Charsets.UTF_8),
                malformedValue,
            )
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun unknownDataStoreTopLevelFieldFailsClosed() {
        writeRawDataStore(byteArrayOf(16, 1))

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun walletPreferenceWithWrongWireValueTypeFailsClosed() {
        writeDataStore(
            preferenceMapEntry(
                "prefs_seed$WALLET_ID".toByteArray(Charsets.UTF_8),
                byteArrayOf(24, 1),
            )
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "PREFERENCES_INVENTORY_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun currentSchemaCreatesOneVerifiedSnapshotWithoutRepeatingIt() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )

        val firstResult = WalletUpgradeBackup.prepare(context)
        val secondResult = WalletUpgradeBackup.prepare(context)

        assertTrue(firstResult.isSuccess)
        assertTrue(secondResult.isSuccess)
        val backups = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter {
                it.isDirectory &&
                    it.name.startsWith(
                        "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION"
                    )
            }
        assertEquals(1, backups.size)
        assertEquals(
            "verified",
            File(backups.single(), ".complete").readText(),
        )
    }

    @Test
    fun currentSchemaStartupNeitherRehashesNorRepublishesBackups() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        val wrappedKeyPreferences = context.getSharedPreferences(
            KEYSTORE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        assertTrue(
            wrappedKeyPreferences.edit()
                .putString(WRAPPED_AES_KEY, "retained-wrapped-wallet-key")
                .commit()
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        val backupBefore = backup.walkTopDown().filter(File::isFile)
            .associate { it.relativeTo(backup).path to it.readBytes() }
        // Account set and selection change: no new full snapshot per change any more.
        insertLegacyAccount(PARTIAL_WALLET_ID, "Second")
        var finalSourceHookCalled = false

        val result = WalletUpgradeBackup.prepareWithFinalSourceMutationForTest(context) {
            finalSourceHookCalled = true
        }

        assertTrue(result.isSuccess)
        assertFalse(finalSourceHookCalled)
        val backups = context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it.name.startsWith(BACKUP_PREFIX) }
        assertEquals(listOf(backup), backups)
        val backupAfter = backup.walkTopDown().filter(File::isFile)
            .associate { it.relativeTo(backup).path to it.readBytes() }
        assertEquals(backupBefore.keys, backupAfter.keys)
        backupBefore.forEach { (name, bytes) -> assertArrayEquals(bytes, backupAfter[name]) }
    }

    @Test
    fun pristineCurrentSchemaNamespaceIsBackedUpBeforeActivation() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        updateCurrentDatabase(
            """
            UPDATE walletIdentities
            SET secretSource = 'UNKNOWN', migrationState = 'PENDING_VERIFICATION'
            """.trimIndent()
        )
        updateCurrentDatabase(
            "UPDATE networkAccounts SET enabled = 0"
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(backup.isDirectory)
        assertEquals("verified", File(backup, ".complete").readText())
        assertTrue(
            File(
                backup,
                "shared_prefs/$PREFERENCES_NAME.xml",
            ).isFile
        )
        val manifest = JSONObject(File(backup, "manifest.json").readText())
        assertEquals(CURRENT_VERSION, manifest.getInt("sourceDatabaseVersion"))
        assertEquals(CURRENT_VERSION, manifest.getInt("targetDatabaseVersion"))
        assertEquals(1, manifest.getInt("legacyAccountCount"))
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun verifiedMigrationReceiptDoesNotFreezeLaterAccountNameOrSelection() {
        createCurrentDatabase(
            listOf(
                SoraAccountLocal(WALLET_ID, "Renamed after migration"),
                SoraAccountLocal(PARTIAL_WALLET_ID, "Added after migration"),
            )
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("cur_account_address", PARTIAL_WALLET_ID)
                .commit()
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'VERIFIED', 1, 1,
                '$WALLET_ID', '${"0".repeat(64)}', NULL, 1, 2
            )
            """.trimIndent()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertEquals(2, legacyAccountCount())
    }

    @Test
    fun currentSchemaMissingMigrationJournalTableFailsClosed() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        updateCurrentDatabase("DROP TABLE walletMigrationJournal")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "WALLET_DELETION_JOURNAL_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun interruptedCurrentSchemaMigrationRequiresRecovery() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'VERIFYING', 1, 0,
                '$WALLET_ID', '${"0".repeat(64)}', NULL, 1, NULL
            )
            """.trimIndent()
        )
        exhaustAutomaticRetries()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "WALLET_MIGRATION_RECOVERY_REQUIRED",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        // Stale journal fields do not invalidate the recovery: a retry re-verifies from the
        // account rows and encrypted preferences.
        assertTrue(WalletUpgradeBackup.hasValidatedLegacyRecovery())
        assertTrue(WalletUpgradeBackup.canAuthorizeMigrationRetry())
    }

    @Test
    fun interruptedVerificationIsRetriedAutomaticallyAtMostThreeTimes() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'VERIFYING', 1, 0,
                '$WALLET_ID', '${WalletMigrationIntegrity.snapshotHash(listOf(account), WALLET_ID)}',
                NULL, 1, NULL
            )
            """.trimIndent()
        )
        repeat(3) { attempt ->
            // A background start (worker, receiver, boot) only prepares and never runs the
            // verification, so it never uses the automatic retry budget.
            repeat(2) {
                assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
                assertEquals(
                    attempt,
                    WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context),
                )
            }
            assertEquals(null, WalletUpgradeBackup.startupFailureToShow(context))
            // The app's screens start and retry the verification automatically.
            assertTrue(WalletUpgradeBackup.authorizeAutomaticMigrationRetry(context))
            assertEquals(null, WalletUpgradeBackup.blockingFailure())
            assertEquals(
                WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
                WalletRecoveryCapabilityGate.mode(),
            )
            assertEquals(
                attempt + 1,
                WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context),
            )
        }
        // The fourth interrupted start shows the options; Try again still works.
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertFalse(WalletUpgradeBackup.authorizeAutomaticMigrationRetry(context))
        assertEquals(
            "WALLET_MIGRATION_RECOVERY_REQUIRED",
            WalletUpgradeBackup.startupFailureToShow(context)?.code,
        )
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertTrue(options.canRetry && options.canReadOnly && options.canSetUpAgain)
        assertTrue(WalletUpgradeBackup.retry(context).isSuccess)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun setUpAgainWhenThisPhoneCannotUnlockTheKeysSetsAsideEveryWalletFileAndKeepsTheBytes() {
        createCurrentDatabase(listOf(SoraAccountLocal(WALLET_ID, "Retained")))
        assertTrue(
            context.getSharedPreferences(KEYSTORE_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putString(WRAPPED_AES_KEY, "wrapped-key-from-another-phone").commit()
        )
        putDataStoreStringMarker("prefs_mnemonic$WALLET_ID")
        // The wallet check found that this phone has no Keystore key for the stored keys.
        WalletUpgradeBackup.noteTransientVerificationFailure(
            WalletRecoveryOptions.KEYSTORE_KEY_UNAVAILABLE,
            accountsReadable = true,
        )
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertEquals(SetUpAgainScope.ALL_WALLET_FILES, options.setUpAgainScope)
        // The main screen would have to decrypt the app PIN: read-only is not offered.
        assertFalse(options.canReadOnly)
        assertEquals(
            WalletUpgradeBackup.SafeReadOnlyRoute.VAULT_ONLY,
            WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow(),
        )
        val dataDir = File(context.applicationInfo.dataDir)
        val originals = mapOf(
            "databases/app.db" to context.getDatabasePath(DATABASE_NAME),
            "files/datastore/sora_prefs_datastore.preferences_pb" to
                File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb"),
            "shared_prefs/sora_prefs.xml" to File(dataDir, "shared_prefs/sora_prefs.xml"),
            "shared_prefs/key_alias.xml" to File(dataDir, "shared_prefs/key_alias.xml"),
        )
        val bytes = originals.mapValues { (_, file) -> file.readBytes() }

        assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        assertTrue(WalletUpgradeBackup.isSetUpAgainRequested(context))
        assertEquals(
            SetUpAgainScope.ALL_WALLET_FILES,
            WalletUpgradeBackup.pendingSetUpAgainScope(context),
        )
        // Nothing moves in the process that asked: its DataStore and Room may be open. Try again
        // in this process is refused (the app restarts instead), so the files are not verified
        // and opened while they wait to be set aside.
        assertEquals(
            "SET_UP_AGAIN_INCOMPLETE",
            (WalletUpgradeBackup.retry(context).exceptionOrNull() as? WalletUpgradeBackupException)?.code,
        )
        assertTrue(WalletUpgradeBackup.isSetUpAgainRequested(context))
        originals.values.forEach { assertTrue(it.path, it.isFile) }

        WalletUpgradeBackup.simulateProcessStartForTest()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertFalse(WalletUpgradeBackup.isSetUpAgainRequested(context))
        originals.values.forEach { assertFalse(it.path, it.exists()) }
        val aside = context.noBackupFilesDir.listFiles().orEmpty()
            .single { it.name.startsWith("wallet-quarantine-") && it.name.endsWith("-set-up-again") }
        bytes.forEach { (relative, content) ->
            assertArrayEquals(relative, content, File(aside, relative).readBytes())
        }
        // The app now starts like a new install; nothing blocks onboarding.
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertFalse(WalletUpgradeBackup.quarantine().hasRetainedMaterial)
    }

    @Test
    fun setUpAgainForADamagedDatabaseMovesOnlyTheDatabaseAndKeepsEveryKeyInPlace() {
        createCurrentDatabase(listOf(SoraAccountLocal(WALLET_ID, "Retained")))
        assertTrue(
            context.getSharedPreferences(KEYSTORE_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().putString(WRAPPED_AES_KEY, "wrapped-key-of-this-phone").commit()
        )
        putDataStoreStringMarker("prefs_mnemonic$WALLET_ID")
        // SQLite reported corruption on the open database.
        WalletUpgradeBackup.noteDatabaseCorruption(context)
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertEquals(WalletRecoveryKind.INTEGRITY, options.kind)
        assertEquals(SetUpAgainScope.DATABASE_ONLY, options.setUpAgainScope)
        assertTrue(options.canSetUpAgain && options.canRestoreFromPhrase)
        val dataDir = File(context.applicationInfo.dataDir)
        val database = context.getDatabasePath(DATABASE_NAME)
        val databaseBytes = database.readBytes()
        val kept = listOf(
            File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb"),
            File(dataDir, "shared_prefs/key_alias.xml"),
        ).associateWith { it.readBytes() }

        assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        assertEquals(
            SetUpAgainScope.DATABASE_ONLY,
            WalletUpgradeBackup.pendingSetUpAgainScope(context),
        )
        WalletUpgradeBackup.simulateProcessStartForTest()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertFalse(WalletUpgradeBackup.isSetUpAgainRequested(context))
        assertFalse(database.exists())
        val aside = context.noBackupFilesDir.listFiles().orEmpty()
            .single { it.name.startsWith("wallet-quarantine-") && it.name.endsWith("-set-up-again") }
        assertArrayEquals(databaseBytes, File(aside, "databases/app.db").readBytes())
        assertFalse(File(aside, "files").exists())
        assertFalse(File(aside, "shared_prefs").exists())
        // Every key stays exactly where it was and is offered again.
        kept.forEach { (file, content) -> assertArrayEquals(file.path, content, file.readBytes()) }
        val quarantine = WalletUpgradeBackup.quarantine()
        assertTrue(WALLET_ID in quarantine.unlinkedWalletIds)
        assertTrue(quarantine.retainedWrappedKeyWithoutDatabase)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
    }

    @Test
    fun setUpAgainIsNotOfferedWhileAStateCanPassOnItsOwnOrNeedsSpace() {
        // No recovery state at all.
        assertEquals(null, WalletUpgradeBackup.recoveryOptions())
        assertEquals(
            "SET_UP_AGAIN_NOT_AVAILABLE",
            (
                WalletUpgradeBackup.requestSetUpAgain(context).exceptionOrNull()
                    as? WalletUpgradeBackupException
                )?.code,
        )
        // A busy Keystore: Try again (automatic) is the forward path.
        WalletUpgradeBackup.noteTransientVerificationFailure(
            WalletRecoveryOptions.KEYSTORE_TRANSIENT,
            accountsReadable = true,
        )
        val transient = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertFalse(transient.canSetUpAgain || transient.canRestoreFromPhrase || transient.canReadOnly)
        assertTrue(transient.canRetry && transient.retryAutomatically)
        assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isFailure)
        assertFalse(WalletUpgradeBackup.isSetUpAgainRequested(context))
        WalletUpgradeBackup.noteMigrationVerified()
        // Low storage before a schema step: free space and check again.
        clearTestStorage()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(
            WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(context, 0L).isFailure
        )
        val storage = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertEquals(WalletRecoveryKind.STORAGE, storage.kind)
        assertEquals(SetUpAgainScope.NONE, storage.setUpAgainScope)
        assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isFailure)
        assertFalse(WalletUpgradeBackup.isSetUpAgainRequested(context))
        assertTrue(context.getDatabasePath(DATABASE_NAME).isFile)
    }

    @Test
    fun aVerificationThatFailsInTheRunningAppOffersTheRestoreItShows() {
        createCurrentDatabase(listOf(SoraAccountLocal(WALLET_ID, "Retained")))
        putDataStoreStringMarker("prefs_mnemonic$WALLET_ID")
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertEquals(null, WalletUpgradeBackup.recoveryOptions())
        // The startup checks passed; MigrationManager then recorded a failure for this run.
        WalletUpgradeBackup.noteMigrationRecoveryRequired("POST_MIGRATION_MISMATCH")
        val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertEquals("POST_MIGRATION_MISMATCH", options.code)
        assertEquals(SetUpAgainScope.DATABASE_ONLY, options.setUpAgainScope)
        // The facts gathered at startup still apply: the phrase can be shown and exported.
        assertTrue(options.canRestoreFromPhrase && options.canShowPhraseAfterPin && options.canExport)
        val databaseBytes = context.getDatabasePath(DATABASE_NAME).readBytes()

        // The restore it offers is accepted, and only the request is written.
        assertTrue(WalletUpgradeBackup.requestSetUpAgain(context).isSuccess)
        assertEquals(
            SetUpAgainScope.DATABASE_ONLY,
            WalletUpgradeBackup.pendingSetUpAgainScope(context),
        )
        assertArrayEquals(databaseBytes, context.getDatabasePath(DATABASE_NAME).readBytes())
        // Try again in this process waits for the restart that sets the database aside.
        assertEquals(
            "SET_UP_AGAIN_INCOMPLETE",
            (WalletUpgradeBackup.retry(context).exceptionOrNull() as? WalletUpgradeBackupException)?.code,
        )
        assertTrue(WalletUpgradeBackup.isSetUpAgainRequested(context))

        // A verified run forgets the in-memory state.
        WalletUpgradeBackup.noteMigrationVerified()
        assertEquals(null, WalletUpgradeBackup.recoveryOptions())
    }

    @Test
    fun automaticRetryThatCannotBeSavedShowsTheRecoveryOptionsAtOnce() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'VERIFYING', 1, 0,
                '$WALLET_ID', '${WalletMigrationIntegrity.snapshotHash(listOf(account), WALLET_ID)}',
                NULL, 1, NULL
            )
            """.trimIndent()
        )
        // The retry counter cannot be saved (as on a full disk): its staging path is occupied.
        val blocker = File(context.noBackupFilesDir, ".wallet-migration-auto-retry.tmp")
        assertTrue(blocker.mkdirs() && File(blocker, "occupied").createNewFile())
        try {
            WalletUpgradeBackup.simulateProcessStartForTest()
            assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
            // The budget is unused, so the first screen waits for the automatic retry...
            assertEquals(null, WalletUpgradeBackup.startupFailureToShow(context))
            assertFalse(WalletUpgradeBackup.authorizeAutomaticMigrationRetry(context))
            assertEquals(0, WalletUpgradeBackup.automaticMigrationRetriesUsedForTest(context))
            // ...which could not happen, so the options are shown now, not after a crash.
            assertEquals(
                "WALLET_MIGRATION_RECOVERY_REQUIRED",
                WalletUpgradeBackup.startupFailureToShow(context)?.code,
            )
            assertFalse(WalletUpgradeBackup.isAutomaticMigrationRetryAvailable(context))
            assertEquals(
                WalletRecoveryCapabilityGate.Mode.BLOCKED,
                WalletRecoveryCapabilityGate.mode(),
            )
            val options = requireNotNull(WalletUpgradeBackup.recoveryOptions())
            assertTrue(options.canRetry && options.canReadOnly)
            // Try again does not need the counter.
            assertTrue(WalletUpgradeBackup.retry(context).isSuccess)
            assertEquals(
                WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
                WalletRecoveryCapabilityGate.mode(),
            )
            assertEquals(1, legacyAccountCount())
        } finally {
            blocker.deleteRecursively()
        }
    }

    @Test
    fun currentSchemaSnapshotOnALowStoragePhoneIsSkippedAndNotRetriedEveryStart() {
        createCurrentDatabase(listOf(SoraAccountLocal(WALLET_ID, "Retained")))
        clearTestBackups()

        assertTrue(
            WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(context, 0L).isSuccess
        )
        assertTrue(
            "CURRENT_SCHEMA_SNAPSHOT_SKIPPED_SPACE" in WalletUpgradeBackup.nonBlockingWarnings()
        )
        assertTrue(File(context.noBackupFilesDir, "wallet-snapshot-backoff").isFile)
        // The next start has space again but waits a day before trying (no hashing per start).
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertTrue("CURRENT_SCHEMA_SNAPSHOT_DEFERRED" in WalletUpgradeBackup.nonBlockingWarnings())
        assertTrue(
            context.noBackupFilesDir.listFiles().orEmpty()
                .none { it.name.startsWith(BACKUP_PREFIX) }
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun readOnlyIsOfferedExactlyWhenTheReadOnlyRouteWorks() {
        // A damaged database: read-only cannot open it, so it is not offered.
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        assertTrue(database.parentFile!!.isDirectory || database.parentFile!!.mkdirs())
        database.writeBytes(ByteArray(4_096) { index -> (index * 31 + 17).toByte() })
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        val damaged = requireNotNull(WalletUpgradeBackup.recoveryOptions())
        assertFalse(damaged.canReadOnly)
        assertTrue(damaged.canSetUpAgain && damaged.canRestoreFromPhrase && damaged.canExport)
        assertEquals(
            WalletUpgradeBackup.SafeReadOnlyRoute.VAULT_ONLY,
            WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow(),
        )

        // An interrupted verification on a healthy database: read-only works.
        clearTestStorage()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'VERIFYING', 1, 0,
                '$WALLET_ID', '${WalletMigrationIntegrity.snapshotHash(listOf(account), WALLET_ID)}',
                NULL, 1, NULL
            )
            """.trimIndent()
        )
        exhaustAutomaticRetries()
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertTrue(requireNotNull(WalletUpgradeBackup.recoveryOptions()).canReadOnly)
        assertEquals(
            WalletUpgradeBackup.SafeReadOnlyRoute.ROOM_READ_ONLY,
            WalletUpgradeBackup.authorizeSafeReadOnly(context).getOrThrow(),
        )
    }

    @Test
    fun verifiedReceiptFinishedBeforeItStartedDoesNotBlockStartup() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        val startedAt = System.currentTimeMillis() + 60 * 60 * 1_000L
        // The clock was ahead when the verification started and was corrected before it ended.
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'VERIFIED', 1, 1,
                '$WALLET_ID', '${WalletMigrationIntegrity.snapshotHash(listOf(account), WALLET_ID)}',
                NULL, $startedAt, ${startedAt - 10_000L}
            )
            """.trimIndent()
        )

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
    }

    @Test
    fun keysOnlyBackupNeedsOnlyAMarginForALargeDatabase() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        // A large cache database (history and pools), as on long-used phones.
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL("CREATE TABLE cache_blob(id INTEGER PRIMARY KEY, value BLOB)")
            repeat(40) { index ->
                sqlite.execSQL(
                    "INSERT INTO cache_blob(id, value) VALUES(?, ?)",
                    arrayOf(index, ByteArray(1_024 * 1_024) { it.toByte() }),
                )
            }
        }
        val databaseBytes = context.getDatabasePath(DATABASE_NAME).length()
        assertTrue(databaseBytes > 40L * 1_024L * 1_024L)

        assertTrue(
            WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(context, 0L).isFailure
        )
        val keysOnlyRequired = requireNotNull(WalletUpgradeBackup.blockingFailure()?.requiredBytes)
        // Not two copies of the database: the keys plus a bounded margin for the schema steps.
        assertTrue(keysOnlyRequired < databaseBytes)
        assertTrue(keysOnlyRequired <= 34L * 1_024L * 1_024L)

        assertTrue(
            WalletUpgradeBackup.prepareWithAvailableBackupBytesForTest(context, keysOnlyRequired)
                .isSuccess
        )
        assertTrue("KEYS_ONLY_BACKUP" in WalletUpgradeBackup.nonBlockingWarnings())
    }

    @Test
    fun unreadableDatabaseDirectoryIsNotMistakenForAMissingDatabase() {
        val wal = createLegacyWalDatabase()
        putWalletMarker()
        val database = context.getDatabasePath(DATABASE_NAME)
        val walFile = File("${database.path}-wal")
        try {
            assertTrue(walFile.isFile)
            val directory = requireNotNull(database.parentFile)
            val mode = Os.stat(directory.path).st_mode and 0x1FF
            // lstat on app.db now fails with "permission denied", not "no such file".
            Os.chmod(directory.path, 0)
            val result = try {
                WalletUpgradeBackup.prepare(context)
            } finally {
                Os.chmod(directory.path, mode)
            }
            assertTrue(result.isFailure)
            assertEquals("DATABASE_PATH_UNREADABLE", WalletUpgradeBackup.blockingFailure()?.code)
            assertEquals(
                WalletRecoveryKind.TRANSIENT,
                WalletUpgradeBackup.recoveryOptions()?.kind,
            )
            // The live write-ahead log was not set aside.
            assertTrue(walFile.isFile)
            assertTrue(
                context.noBackupFilesDir.listFiles().orEmpty()
                    .none { it.name.startsWith("wallet-quarantine-") }
            )
        } finally {
            wal.close()
        }
    }

    private fun clearTestBackups() {
        context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it.name.startsWith(BACKUP_PREFIX) || it.name == "wallet-snapshot-backoff" }
            .forEach(File::deleteRecursively)
    }

    @Test
    fun restartRecoveryValidatesLegacySelectionWithoutOpeningRoom() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        val integrityHash = WalletMigrationIntegrity.snapshotHash(
            listOf(account),
            WALLET_ID,
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'RECOVERY_REQUIRED', 1, 0,
                '$WALLET_ID', '$integrityHash', 'KEY_UNAVAILABLE', 1, 2
            )
            """.trimIndent()
        )

        exhaustAutomaticRetries()
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertTrue(WalletUpgradeBackup.hasValidatedLegacyRecovery())
        assertEquals(
            "verified",
            File(
                context.noBackupFilesDir,
                "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION/.complete",
            ).readText(),
        )
        assertTrue(WalletUpgradeBackup.authorizeLegacyReadOnly(context).isSuccess)

        assertEquals(null, WalletUpgradeBackup.blockingFailure())
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.LEGACY_READ_ONLY,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun legacyReadOnlyGuardsEveryPendingNetworkTransactionMutation() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        updateCurrentDatabase(
            pendingNetworkTransactionInsertSql(
                localId = "retained-pending",
                transactionHash = "a".repeat(64),
            )
        )
        val integrityHash = WalletMigrationIntegrity.snapshotHash(
            listOf(account),
            WALLET_ID,
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'RECOVERY_REQUIRED', 1, 0,
                '$WALLET_ID', '$integrityHash', 'KEY_UNAVAILABLE', 1, 2
            )
            """.trimIndent()
        )

        exhaustAutomaticRetries()
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertTrue(WalletUpgradeBackup.authorizeLegacyReadOnly(context).isSuccess)

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(CURRENT_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = error("Unexpected schema upgrade $oldVersion -> $newVersion")
                }
            )
            .build()
        val helper = WalletUpgradeBackup.gatedOpenHelperFactory().create(configuration)
        try {
            val guarded = helper.writableDatabase
            assertRecoveryWriteRejected {
                guarded.execSQL(
                    pendingNetworkTransactionInsertSql(
                        localId = "forbidden-insert",
                        transactionHash = "b".repeat(64),
                    )
                )
            }
            assertRecoveryWriteRejected {
                guarded.execSQL(
                    """
                    UPDATE pendingNetworkTransactions
                    SET walletId = '$WALLET_ID'
                    WHERE localId = 'retained-pending'
                    """.trimIndent()
                )
            }
            assertRecoveryWriteRejected {
                guarded.execSQL(
                    """
                    DELETE FROM pendingNetworkTransactions
                    WHERE localId = 'retained-pending'
                    """.trimIndent()
                )
            }

            // TEMP triggers disappear with their SQLite connection. Closing and reopening the
            // same gated helper must install a fresh set before exposing the new handle.
            helper.close()
            val reopened = helper.writableDatabase
            assertRecoveryWriteRejected {
                reopened.execSQL(
                    pendingNetworkTransactionInsertSql(
                        localId = "forbidden-after-reopen",
                        transactionHash = "c".repeat(64),
                    )
                )
            }
            assertRecoveryWriteRejected {
                reopened.execSQL(
                    """
                    UPDATE pendingNetworkTransactions
                    SET walletId = '$WALLET_ID'
                    WHERE localId = 'retained-pending'
                    """.trimIndent()
                )
            }
            assertRecoveryWriteRejected {
                reopened.execSQL(
                    """
                    DELETE FROM pendingNetworkTransactions
                    WHERE localId = 'retained-pending'
                    """.trimIndent()
                )
            }
            reopened.query(
                """
                SELECT localId FROM pendingNetworkTransactions
                ORDER BY localId
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("retained-pending", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun legacyReadOnlyGuardsEverySora2PendingSubmissionMutation() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        val retainedHash = "d".repeat(64)
        val retainedLocalId = "sora2:$retainedHash"
        updateCurrentDatabase(sora2PendingSubmissionInsertSql(retainedHash))
        val integrityHash = WalletMigrationIntegrity.snapshotHash(
            listOf(account),
            WALLET_ID,
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'RECOVERY_REQUIRED', 1, 0,
                '$WALLET_ID', '$integrityHash', 'KEY_UNAVAILABLE', 1, 2
            )
            """.trimIndent()
        )

        exhaustAutomaticRetries()
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertTrue(WalletUpgradeBackup.authorizeLegacyReadOnly(context).isSuccess)

        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(CURRENT_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = error("Unexpected schema upgrade $oldVersion -> $newVersion")
                }
            )
            .build()
        val helper = WalletUpgradeBackup.gatedOpenHelperFactory().create(configuration)
        try {
            val guarded = helper.writableDatabase
            assertRecoveryWriteRejected {
                guarded.execSQL(sora2PendingSubmissionInsertSql("e".repeat(64)))
            }
            assertRecoveryWriteRejected {
                guarded.execSQL(
                    """
                    UPDATE sora2PendingSubmissions
                    SET updatedAt = 2
                    WHERE localId = '$retainedLocalId'
                    """.trimIndent()
                )
            }
            assertRecoveryWriteRejected {
                guarded.execSQL(
                    """
                    DELETE FROM sora2PendingSubmissions
                    WHERE localId = '$retainedLocalId'
                    """.trimIndent()
                )
            }

            // The replacement SQLite connection must receive all SORA2 submission guards too.
            helper.close()
            val reopened = helper.writableDatabase
            assertRecoveryWriteRejected {
                reopened.execSQL(sora2PendingSubmissionInsertSql("f".repeat(64)))
            }
            assertRecoveryWriteRejected {
                reopened.execSQL(
                    """
                    UPDATE sora2PendingSubmissions
                    SET updatedAt = 3
                    WHERE localId = '$retainedLocalId'
                    """.trimIndent()
                )
            }
            assertRecoveryWriteRejected {
                reopened.execSQL(
                    """
                    DELETE FROM sora2PendingSubmissions
                    WHERE localId = '$retainedLocalId'
                    """.trimIndent()
                )
            }
            reopened.query(
                """
                SELECT localId FROM sora2PendingSubmissions
                ORDER BY localId
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(retainedLocalId, cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun normalToRecoveryTransitionDuringHandleAdmissionCannotEscapeUnguarded() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        var transitionCount = 0
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DATABASE_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(CURRENT_VERSION) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) = error("Unexpected schema upgrade $oldVersion -> $newVersion")
                }
            )
            .build()
        val helper = WalletUpgradeBackup.gatedOpenHelperFactoryWithGuardAdmissionForTest(
            beforeGuardAdmission = {
                assertEquals(0, transitionCount)
                assertEquals(
                    WalletRecoveryCapabilityGate.Mode.NORMAL,
                    WalletRecoveryCapabilityGate.mode(),
                )
                transitionCount += 1
                WalletUpgradeBackup.noteMigrationRecoveryRequired()
            }
        ).create(configuration)

        try {
            val guarded = helper.writableDatabase
            assertEquals(1, transitionCount)
            assertEquals(
                WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
                WalletRecoveryCapabilityGate.mode(),
            )
            assertRecoveryWriteRejected {
                guarded.execSQL(
                    """
                    UPDATE accounts
                    SET accountName = 'Forbidden'
                    WHERE substrateAddress = '$WALLET_ID'
                    """.trimIndent()
                )
            }
            guarded.query(
                """
                SELECT accountName FROM accounts
                WHERE substrateAddress = '$WALLET_ID'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Retained", cursor.getString(0))
                assertFalse(cursor.moveToNext())
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun explicitRetryCompareAndSetsOnlyTheMigrationJournal() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        val integrityHash = WalletMigrationIntegrity.snapshotHash(
            listOf(account),
            WALLET_ID,
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'RECOVERY_REQUIRED', 1, 0,
                '$WALLET_ID', '$integrityHash', 'KEY_UNAVAILABLE', 1, 2
            )
            """.trimIndent()
        )

        exhaustAutomaticRetries()
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertTrue(WalletUpgradeBackup.authorizeMigrationRetry(context).isSuccess)

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { sqlite ->
            sqlite.rawQuery(
                """
                SELECT state, verifiedAccountCount, failureCode, completedAt,
                    selectedWalletId, integrityHash, startedAt
                FROM walletMigrationJournal
                """.trimIndent(),
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("VERIFYING", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
                assertEquals(WALLET_ID, cursor.getString(4))
                assertEquals(integrityHash, cursor.getString(5))
                assertTrue(cursor.getLong(6) > 1L)
                assertFalse(cursor.moveToNext())
            }
        }
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun retryCannotMutateJournalBeforeRecoveryHasBeenInspected() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        val integrityHash = WalletMigrationIntegrity.snapshotHash(
            listOf(account),
            WALLET_ID,
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'RECOVERY_REQUIRED', 1, 0,
                '$WALLET_ID', '$integrityHash', 'KEY_UNAVAILABLE', 1, 2
            )
            """.trimIndent()
        )

        assertTrue(WalletUpgradeBackup.authorizeMigrationRetry(context).isFailure)

        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { sqlite ->
            sqlite.rawQuery(
                "SELECT state, failureCode, startedAt, completedAt " +
                    "FROM walletMigrationJournal",
                null,
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("RECOVERY_REQUIRED", cursor.getString(0))
                assertEquals("KEY_UNAVAILABLE", cursor.getString(1))
                assertEquals(1L, cursor.getLong(2))
                assertEquals(2L, cursor.getLong(3))
                assertFalse(cursor.moveToNext())
            }
        }
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.NORMAL,
            WalletRecoveryCapabilityGate.mode(),
        )
    }

    @Test
    fun retryAuthorizationReinspectsTheCurrentAccountRows() {
        val account = SoraAccountLocal(WALLET_ID, "Retained")
        createCurrentDatabase(listOf(account))
        val integrityHash = WalletMigrationIntegrity.snapshotHash(
            listOf(account),
            WALLET_ID,
        )
        updateCurrentDatabase(
            """
            INSERT INTO walletMigrationJournal(
                migrationId, state, legacyAccountCount, verifiedAccountCount,
                selectedWalletId, integrityHash, failureCode, startedAt, completedAt
            ) VALUES(
                'wallet-network-v1', 'RECOVERY_REQUIRED', 1, 0,
                '$WALLET_ID', '$integrityHash', 'KEY_UNAVAILABLE', 1, 2
            )
            """.trimIndent()
        )
        exhaustAutomaticRetries()
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        updateCurrentDatabase(
            "UPDATE accounts SET accountName = 'Changed after inspection'"
        )

        // A retry does not trust the earlier snapshot: the migration re-reads the current rows
        // and encrypted preferences and verifies every account again before signing is allowed.
        assertTrue(WalletUpgradeBackup.authorizeMigrationRetry(context).isSuccess)

        assertEquals(
            WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun currentSchemaPendingIdentityWithoutJournalIsVerifiedOnTheNextRun() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        updateCurrentDatabase(
            "UPDATE walletIdentities SET migrationState = 'PENDING_VERIFICATION'"
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
    }

    @Test
    fun confirmedDeletionRequiresExactPreviewedPreferenceFingerprint() {
        createCurrentDatabase(
            listOf(
                SoraAccountLocal(WALLET_ID, "Primary"),
                SoraAccountLocal(PARTIAL_WALLET_ID, "Remaining"),
            )
        )
        insertDeletionOperation(
            phase = WalletDeletionContract.PHASE_CONFIRMED,
            expectedWalletCount = 2,
            targetIds = setOf(WALLET_ID),
            selectedBefore = WALLET_ID,
            selectedAfter = PARTIAL_WALLET_ID,
            removeLegacyUnsuffixed = false,
        )

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    "prefs_mnemonic$PARTIAL_WALLET_ID",
                    "unexpected-change",
                )
                .commit()
        )
        val result = WalletUpgradeBackup.prepare(context)
        assertTrue(result.isFailure)
        assertEquals(
            "WALLET_DELETION_PREFERENCES_MISMATCH",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun databaseCommittedDeletionAllowsOnlyItsExactTargetOrphan() {
        val remaining = SoraAccountLocal(PARTIAL_WALLET_ID, "Remaining")
        createCurrentDatabase(listOf(remaining))
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_mnemonic$WALLET_ID", "encrypted")
                .putString("prefs_address_pure", WALLET_ID)
                .putString("cur_account_address", WALLET_ID)
                .commit()
        )
        insertDeletionOperation(
            phase = WalletDeletionContract.PHASE_DATABASE_COMMITTED,
            expectedWalletCount = 2,
            targetIds = setOf(WALLET_ID),
            selectedBefore = WALLET_ID,
            selectedAfter = PARTIAL_WALLET_ID,
            removeLegacyUnsuffixed = true,
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(null, WalletUpgradeBackup.blockingFailure())
    }

    @Test
    fun databaseCommittedDeletionLeavesAnUnrelatedOrphanUntouched() {
        val remaining = SoraAccountLocal(PARTIAL_WALLET_ID, "Remaining")
        createCurrentDatabase(listOf(remaining))
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_seed$UNRELATED_WALLET_ID", "encrypted")
                .putString("cur_account_address", PARTIAL_WALLET_ID)
                .commit()
        )
        insertDeletionOperation(
            phase = WalletDeletionContract.PHASE_DATABASE_COMMITTED,
            expectedWalletCount = 2,
            targetIds = setOf(WALLET_ID),
            selectedBefore = WALLET_ID,
            selectedAfter = PARTIAL_WALLET_ID,
            removeLegacyUnsuffixed = false,
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(
            "encrypted",
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString("prefs_seed$UNRELATED_WALLET_ID", null),
        )
        assertTrue(UNRELATED_WALLET_ID in WalletUpgradeBackup.quarantine().unlinkedWalletIds)
    }

    @Test
    fun unjournaledCurrentSchemaOrphanIsSetAside() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(PARTIAL_WALLET_ID, "Remaining"))
        )
        putWalletMarker(WALLET_ID)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(setOf(WALLET_ID), WalletUpgradeBackup.quarantine().unlinkedWalletIds)
    }

    @Test
    fun preferencesCommittedDeletionRequiresTargetKeysToBeGone() {
        val remaining = SoraAccountLocal(PARTIAL_WALLET_ID, "Remaining")
        createCurrentDatabase(listOf(remaining))
        insertDeletionOperation(
            phase = WalletDeletionContract.PHASE_PREFERENCES_COMMITTED,
            expectedWalletCount = 2,
            targetIds = setOf(WALLET_ID),
            selectedBefore = WALLET_ID,
            selectedAfter = PARTIAL_WALLET_ID,
            removeLegacyUnsuffixed = true,
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("cur_account_address", PARTIAL_WALLET_ID)
                .commit()
        )

        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_seed$WALLET_ID", "unexpected-retained-key")
                .commit()
        )
        val result = WalletUpgradeBackup.prepare(context)
        assertTrue(result.isFailure)
        assertEquals(
            "WALLET_DELETION_PREFERENCES_MISMATCH",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    private fun createLegacyDatabase(withAccount: Boolean) {
        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(database, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE accounts(
                    substrateAddress TEXT NOT NULL PRIMARY KEY,
                    accountName TEXT NOT NULL
                )
                """.trimIndent()
            )
            if (withAccount) {
                sqlite.execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(WALLET_ID, "Retained"),
                )
            }
            sqlite.execSQL("PRAGMA user_version = $LEGACY_VERSION")
        }
    }

    private fun createLegacyWalDatabase(): SQLiteDatabase {
        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(database, null).also { sqlite ->
            check(sqlite.enableWriteAheadLogging())
            sqlite.execSQL(
                """
                CREATE TABLE accounts(
                    substrateAddress TEXT NOT NULL PRIMARY KEY,
                    accountName TEXT NOT NULL
                )
                """.trimIndent()
            )
            sqlite.execSQL("PRAGMA user_version = $LEGACY_VERSION")
            sqlite.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(WALLET_ID, "Retained"),
            )
        }
    }

    private fun createCurrentDatabase(
        accounts: List<SoraAccountLocal>,
        secretSource: String = "MNEMONIC",
    ) {
        check(
            secretSource in setOf(
                "MNEMONIC",
                "MNEMONIC_UNSUPPORTED",
                "RAW_SEED",
                "LEGACY_SECRET",
                "WATCH_ONLY",
            )
        )
        val database = context.getDatabasePath(DATABASE_NAME)
        database.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(database, null).use { sqlite ->
            sqlite.execSQL(
                """
                CREATE TABLE accounts(
                    substrateAddress TEXT NOT NULL PRIMARY KEY,
                    accountName TEXT NOT NULL
                )
                """.trimIndent()
            )
            createExactCurrentModernizationSchema(sqlite)
            accounts.forEach { account ->
                sqlite.execSQL(
                    "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                    arrayOf(account.substrateAddress, account.accountName),
                )
                sqlite.execSQL(
                    """
                    INSERT INTO walletIdentities(
                        walletId, displayName, secretSource, migrationState,
                        derivationVersion
                    ) VALUES(?, ?, ?, 'VERIFIED', 1)
                    """.trimIndent(),
                    arrayOf(
                        account.substrateAddress,
                        account.accountName,
                        secretSource,
                    ),
                )
                sqlite.execSQL(
                    """
                    INSERT INTO networkAccounts(
                        walletId, networkId, publicKey, address, derivationPath,
                        derivationVersion, enabled
                    ) VALUES(?, 'sora2', '', ?, '', 1, 1)
                    """.trimIndent(),
                    arrayOf(account.substrateAddress, account.substrateAddress),
                )
            }
            sqlite.execSQL("PRAGMA user_version = $CURRENT_VERSION")
        }
        if (accounts.isNotEmpty()) {
            val editor = context.getSharedPreferences(
                PREFERENCES_NAME,
                Context.MODE_PRIVATE,
            ).edit()
                .putString("cur_account_address", accounts.first().substrateAddress)
            accounts.forEach { account ->
                if (secretSource != "WATCH_ONLY") {
                    editor
                        .putString(
                            "prefs_priv_key${account.substrateAddress}",
                            "encrypted-private-${account.substrateAddress}",
                        )
                        .putString(
                            "prefs_pub_key${account.substrateAddress}",
                            "encrypted-public-${account.substrateAddress}",
                        )
                        .putString(
                            "prefs_key_nonce${account.substrateAddress}",
                            "encrypted-nonce-${account.substrateAddress}",
                        )
                }
                when (secretSource) {
                    "MNEMONIC", "MNEMONIC_UNSUPPORTED" -> editor.putString(
                        "prefs_mnemonic${account.substrateAddress}",
                        "encrypted-${account.substrateAddress}",
                    )
                    "RAW_SEED" -> editor.putString(
                        "prefs_seed${account.substrateAddress}",
                        "encrypted-${account.substrateAddress}",
                    )
                    "WATCH_ONLY" -> editor.putBoolean(
                        "wallet.watchOnly.${account.substrateAddress}",
                        true,
                    )
                }
            }
            assertTrue(editor.commit())
        }
    }

    private fun assertLegacySecretConflictIsAdmitted(stringKey: String) {
        // Secret conflicts are now classified per account by the wallet
        // verification (that account becomes read-only); the startup gate admits them.
        createCurrentDatabase(
            accounts = listOf(SoraAccountLocal(WALLET_ID, "Legacy secret")),
            secretSource = "LEGACY_SECRET",
        )
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(stringKey, "conflicting-encrypted-source")
                .commit()
        )
        val preferencesBefore = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all,
        )
    }

    private fun assertCurrentSourceMutationIsAdmitted(
        secretSource: String,
        mutation: (android.content.SharedPreferences.Editor) -> Unit,
    ) {
        // Per-account problems no longer block startup; the wallet
        // verification makes only that account read-only. Nothing is rewritten here.
        createCurrentDatabase(
            accounts = listOf(SoraAccountLocal(WALLET_ID, "Strict source")),
            secretSource = secretSource,
        )
        val editor = context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).edit()
        mutation(editor)
        assertTrue(editor.commit())
        val preferencesBefore = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isSuccess)
        assertEquals(
            preferencesBefore,
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all,
        )
        assertEquals(1, legacyAccountCount())
    }

    private fun createExactCurrentModernizationSchema(sqlite: SQLiteDatabase) {
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletIdentities` (
                `walletId` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `secretSource` TEXT NOT NULL,
                `migrationState` TEXT NOT NULL,
                `derivationVersion` INTEGER NOT NULL,
                PRIMARY KEY(`walletId`),
                FOREIGN KEY(`walletId`) REFERENCES `accounts`(`substrateAddress`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_walletIdentities_walletId`
            ON `walletIdentities` (`walletId`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `networkAccounts` (
                `walletId` TEXT NOT NULL,
                `networkId` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `derivationPath` TEXT NOT NULL,
                `derivationVersion` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                PRIMARY KEY(`walletId`, `networkId`),
                FOREIGN KEY(`walletId`) REFERENCES `walletIdentities`(`walletId`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_networkAccounts_walletId`
            ON `networkAccounts` (`walletId`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_networkAccounts_networkId_address`
            ON `networkAccounts` (`networkId`, `address`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletMigrationJournal` (
                `migrationId` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `legacyAccountCount` INTEGER NOT NULL,
                `verifiedAccountCount` INTEGER NOT NULL,
                `selectedWalletId` TEXT NOT NULL,
                `integrityHash` TEXT NOT NULL,
                `failureCode` TEXT,
                `startedAt` INTEGER NOT NULL,
                `completedAt` INTEGER,
                PRIMARY KEY(`migrationId`)
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pendingNetworkTransactions` (
                `localId` TEXT NOT NULL,
                `walletId` TEXT NOT NULL,
                `networkId` TEXT NOT NULL,
                `chainId` TEXT,
                `transactionHash` TEXT,
                `assetId` TEXT NOT NULL,
                `amount` TEXT NOT NULL,
                `recipient` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `submissionIsAmbiguous` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`localId`),
                FOREIGN KEY(`walletId`) REFERENCES `walletIdentities`(`walletId`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_pendingNetworkTransactions_walletId`
            ON `pendingNetworkTransactions` (`walletId`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_pendingNetworkTransactions_networkId_chainId_transactionHash`
            ON `pendingNetworkTransactions` (`networkId`, `chainId`, `transactionHash`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `sora2PendingSubmissions` (
                `localId` TEXT NOT NULL,
                `recoverySchemaVersion` INTEGER NOT NULL,
                `walletId` TEXT NOT NULL,
                `networkId` TEXT NOT NULL,
                `transactionHash` TEXT NOT NULL,
                `accountId` TEXT NOT NULL,
                `publicKey` TEXT NOT NULL,
                `genesisHash` TEXT NOT NULL,
                `specVersion` INTEGER NOT NULL,
                `transactionVersion` INTEGER NOT NULL,
                `metadataSha256` TEXT NOT NULL,
                `typesSha256` TEXT NOT NULL,
                `eraBirthBlock` INTEGER NOT NULL,
                `eraDeathBlockExclusive` INTEGER NOT NULL,
                `eraPeriod` INTEGER NOT NULL,
                `eraPhase` INTEGER NOT NULL,
                `eraBirthBlockHash` TEXT NOT NULL,
                `operationKind` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `submissionIsAmbiguous` INTEGER NOT NULL,
                `terminalBlockNumber` INTEGER,
                `terminalBlockHash` TEXT,
                `terminalFinalizedHeight` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`localId`),
                FOREIGN KEY(`walletId`) REFERENCES `walletIdentities`(`walletId`)
                    ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_sora2PendingSubmissions_walletId`
            ON `sora2PendingSubmissions` (`walletId`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_sora2PendingSubmissions_networkId_transactionHash`
            ON `sora2PendingSubmissions` (`networkId`, `transactionHash`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_sora2PendingSubmissions_state`
            ON `sora2PendingSubmissions` (`state`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletDeletionOperations` (
                `operationId` TEXT NOT NULL,
                `activeSlot` INTEGER NOT NULL,
                `formatVersion` INTEGER NOT NULL,
                `scope` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `expectedWalletCount` INTEGER NOT NULL,
                `targetCount` INTEGER NOT NULL,
                `selectedBefore` TEXT NOT NULL,
                `selectedAfter` TEXT NOT NULL,
                `beforeSnapshotHash` TEXT NOT NULL,
                `afterSnapshotHash` TEXT NOT NULL,
                `beforePreferencesHash` TEXT NOT NULL,
                `afterPreferencesHash` TEXT NOT NULL,
                `requestDigest` TEXT NOT NULL,
                `removeLegacyUnsuffixed` INTEGER NOT NULL,
                `requestedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `failureCode` TEXT,
                PRIMARY KEY(`operationId`)
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_walletDeletionOperations_activeSlot`
            ON `walletDeletionOperations` (`activeSlot`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
                `index_walletDeletionOperations_operationId`
            ON `walletDeletionOperations` (`operationId`)
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `walletDeletionTargets` (
                `operationId` TEXT NOT NULL,
                `walletId` TEXT NOT NULL,
                PRIMARY KEY(`operationId`, `walletId`),
                FOREIGN KEY(`operationId`) REFERENCES `walletDeletionOperations`(`operationId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_walletDeletionTargets_operationId`
            ON `walletDeletionTargets` (`operationId`)
            """.trimIndent()
        )
    }

    private fun pendingNetworkTransactionInsertSql(
        localId: String,
        transactionHash: String,
    ): String {
        check(localId.matches(Regex("^[a-z-]+$")))
        check(transactionHash.matches(Regex("^[0-9a-f]{64}$")))
        return """
            INSERT INTO pendingNetworkTransactions(
                localId, walletId, networkId, chainId, transactionHash, assetId, amount,
                recipient, state, submissionIsAmbiguous, createdAt, updatedAt
            ) VALUES(
                '$localId', '$WALLET_ID', 'minamoto',
                '00000000-0000-0000-0000-000000000753', '$transactionHash',
                '$NEXUS_XOR_DEFINITION_ID', '1', '$WALLET_ID', 'SIGNED', 0, 1, 1
            )
        """.trimIndent()
    }

    private fun sora2PendingSubmissionInsertSql(transactionHash: String): String {
        check(transactionHash.matches(Regex("^[0-9a-f]{64}$")))
        val accountId = "1".repeat(64)
        val genesisHash = "2".repeat(64)
        val metadataHash = "3".repeat(64)
        val typesHash = "4".repeat(64)
        val eraBirthBlockHash = "5".repeat(64)
        return """
            INSERT INTO sora2PendingSubmissions(
                localId, recoverySchemaVersion, walletId, networkId, transactionHash,
                accountId, publicKey, genesisHash, specVersion, transactionVersion,
                metadataSha256, typesSha256, eraBirthBlock, eraDeathBlockExclusive,
                eraPeriod, eraPhase, eraBirthBlockHash, operationKind, state,
                submissionIsAmbiguous, terminalBlockNumber, terminalBlockHash,
                terminalFinalizedHeight, createdAt, updatedAt
            ) VALUES(
                'sora2:$transactionHash', 1, '$WALLET_ID', 'sora2', '$transactionHash',
                '$accountId', '$accountId', '$genesisHash', 1, 1,
                '$metadataHash', '$typesHash', 1, 65, 64, 1,
                '$eraBirthBlockHash', 'GENERIC_SORA2_MUTATION', 'SIGNED_BEFORE_TRANSPORT',
                0, NULL, NULL, NULL, 1, 1
            )
        """.trimIndent()
    }

    private fun insertDeletionOperation(
        phase: String,
        expectedWalletCount: Int,
        targetIds: Set<String>,
        selectedBefore: String,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
    ) {
        val accounts = currentAccounts()
        val identities = accounts.map {
            WalletIdentityLocal(
                walletId = it.substrateAddress,
                displayName = it.accountName,
                secretSource = "MNEMONIC",
                migrationState = "VERIFIED",
                derivationVersion = 1,
            )
        }
        val networkAccounts = accounts.map {
            NetworkAccountLocal(
                walletId = it.substrateAddress,
                networkId = "sora2",
                publicKey = "",
                address = it.substrateAddress,
                derivationPath = "",
                derivationVersion = 1,
                enabled = true,
            )
        }
        val scope = if (expectedWalletCount == targetIds.size) {
            WalletDeletionContract.SCOPE_ALL
        } else {
            WalletDeletionContract.SCOPE_SINGLE
        }
        val liveHash = WalletDeletionIntegrity.snapshotHash(
            accounts = accounts,
            identities = identities,
            networkAccounts = networkAccounts,
            selectedWalletId = if (
                phase == WalletDeletionContract.PHASE_CONFIRMED
            ) {
                selectedBefore
            } else {
                selectedAfter
            },
            scope = scope,
            targetWalletIds = targetIds,
        )
        val currentPreferencesHash = currentWalletPreferenceHash()
        val afterPreferencesHash = if (
            phase == WalletDeletionContract.PHASE_PREFERENCES_COMMITTED
        ) {
            currentPreferencesHash
        } else {
            walletPreferenceHashAfterDeletion(
                targetIds = targetIds,
                selectedAfter = selectedAfter,
                removeLegacyUnsuffixed = removeLegacyUnsuffixed,
                clearAll = scope == WalletDeletionContract.SCOPE_ALL,
            )
        }
        val now = System.currentTimeMillis()
        val base = WalletDeletionOperationLocal(
            operationId = UUID.randomUUID().toString(),
            activeSlot = WalletDeletionContract.ACTIVE_SLOT,
            formatVersion = WalletDeletionContract.FORMAT_VERSION,
            scope = scope,
            phase = phase,
            expectedWalletCount = expectedWalletCount,
            targetCount = targetIds.size,
            selectedBefore = selectedBefore,
            selectedAfter = selectedAfter,
            beforeSnapshotHash = if (
                phase == WalletDeletionContract.PHASE_CONFIRMED
            ) {
                liveHash
            } else {
                "0".repeat(64)
            },
            afterSnapshotHash = if (
                phase == WalletDeletionContract.PHASE_CONFIRMED
            ) {
                "1".repeat(64)
            } else {
                liveHash
            },
            beforePreferencesHash = if (
                phase == WalletDeletionContract.PHASE_PREFERENCES_COMMITTED
            ) {
                "4".repeat(64)
            } else {
                currentPreferencesHash
            },
            afterPreferencesHash = afterPreferencesHash,
            requestDigest = "",
            removeLegacyUnsuffixed = removeLegacyUnsuffixed,
            requestedAt = now,
            updatedAt = now,
            failureCode = null,
        )
        val operation = base.copy(
            requestDigest = WalletDeletionIntegrity.requestDigest(base, targetIds)
        )
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                """
                INSERT INTO walletDeletionOperations(
                    operationId, activeSlot, formatVersion, scope, phase,
                    expectedWalletCount, targetCount, selectedBefore, selectedAfter,
                    beforeSnapshotHash, afterSnapshotHash, beforePreferencesHash,
                    afterPreferencesHash, requestDigest, removeLegacyUnsuffixed,
                    requestedAt, updatedAt, failureCode
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                """.trimIndent(),
                arrayOf<Any?>(
                    operation.operationId,
                    operation.activeSlot,
                    operation.formatVersion,
                    operation.scope,
                    operation.phase,
                    operation.expectedWalletCount,
                    operation.targetCount,
                    operation.selectedBefore,
                    operation.selectedAfter,
                    operation.beforeSnapshotHash,
                    operation.afterSnapshotHash,
                    operation.beforePreferencesHash,
                    operation.afterPreferencesHash,
                    operation.requestDigest,
                    if (operation.removeLegacyUnsuffixed) 1 else 0,
                    operation.requestedAt,
                    operation.updatedAt,
                ),
            )
            targetIds.forEach { target ->
                sqlite.execSQL(
                    """
                    INSERT INTO walletDeletionTargets(operationId, walletId)
                    VALUES(?, ?)
                    """.trimIndent(),
                    arrayOf(operation.operationId, target),
                )
            }
        }
    }

    private fun currentAccounts(): List<SoraAccountLocal> {
        val database = context.getDatabasePath(DATABASE_NAME)
        return SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { sqlite ->
            sqlite.rawQuery(
                """
                SELECT substrateAddress, accountName
                FROM accounts
                ORDER BY substrateAddress
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(SoraAccountLocal(cursor.getString(0), cursor.getString(1)))
                    }
                }
            }
        }
    }

    private fun currentWalletPreferenceHash(): String {
        val (strings, booleans) = currentWalletPreferences()
        return WalletPreferenceIntegrity.hash(strings, booleans)
    }

    private fun walletPreferenceHashAfterDeletion(
        targetIds: Set<String>,
        selectedAfter: String,
        removeLegacyUnsuffixed: Boolean,
        clearAll: Boolean,
    ): String {
        if (clearAll) {
            return WalletPreferenceIntegrity.hash(emptyMap(), emptyMap())
        }
        val (currentStrings, currentBooleans) = currentWalletPreferences()
        val strings = currentStrings.toMutableMap()
        val booleans = currentBooleans.toMutableMap()
        targetIds.forEach { target ->
            WalletPreferenceKeys.scopedStringKeys(target).forEach(strings::remove)
            WalletPreferenceKeys.scopedBooleanKeys(target).forEach(booleans::remove)
        }
        if (removeLegacyUnsuffixed) {
            WalletPreferenceKeys.legacyUnsuffixedStringKeys.forEach(strings::remove)
        }
        strings[WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS] = selectedAfter
        return WalletPreferenceIntegrity.hash(strings, booleans)
    }

    private fun currentWalletPreferences(): Pair<Map<String, String>, Map<String, Boolean>> {
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .all
            .forEach { (key, value) ->
                if (WalletPreferenceKeys.isWalletStateKey(key)) {
                    when (value) {
                        is String -> strings[key] = value
                        is Boolean -> booleans[key] = value
                        else -> error("unexpected preference type")
                    }
                }
            }
        return strings to booleans
    }

    private fun putWalletMarker(walletId: String = WALLET_ID) {
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_mnemonic$walletId", "encrypted-value")
                .putString("cur_account_address", walletId)
                .commit()
        )
    }

    private fun putDataStoreStringMarker(
        key: String,
        marker: String = "encrypted-value",
    ) {
        val encodedKey = key.toByteArray(Charsets.UTF_8)
        writeDataStore(preferenceMapEntry(encodedKey, preferenceStringValue(marker)))
    }

    private fun preferenceStringValue(marker: String): ByteArray {
        val encodedValue = marker.toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().apply {
            write(42)
            writeVarint(encodedValue.size)
            write(encodedValue)
        }.toByteArray()
    }

    private fun preferenceMapEntry(key: ByteArray, value: ByteArray): ByteArray =
        ByteArrayOutputStream().apply {
            write(10)
            writeVarint(key.size)
            write(key)
            write(18)
            writeVarint(value.size)
            write(value)
        }.toByteArray()

    private fun writeDataStore(entry: ByteArray) {
        val preferences = ByteArrayOutputStream().apply {
            write(10)
            writeVarint(entry.size)
            write(entry)
        }.toByteArray()
        writeRawDataStore(preferences)
    }

    private fun writeRawDataStore(preferences: ByteArray) {
        val destination = File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        )
        destination.parentFile?.mkdirs()
        FileOutputStream(destination, false).use { output ->
            output.write(preferences)
            output.fd.sync()
        }
    }

    private fun writeSharedPreferencesBackup(contents: String) {
        writeSharedPreferencesBackupBytes(contents.toByteArray(Charsets.UTF_8))
    }

    private fun writeSharedPreferencesBackupBytes(contents: ByteArray) {
        val destination = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$PREFERENCES_NAME.xml.bak",
        )
        destination.parentFile?.mkdirs()
        FileOutputStream(destination, false).use { output ->
            output.write(contents)
            output.fd.sync()
        }
    }

    private fun insertLegacyAccount(address: String, name: String) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(address, name),
            )
        }
    }

    private fun updateCurrentDatabase(sql: String) {
        SQLiteDatabase.openDatabase(
            context.getDatabasePath(DATABASE_NAME).path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { sqlite ->
            sqlite.execSQL(sql)
        }
    }

    private fun assertRecoveryWriteRejected(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(
            "Expected WALLET_RECOVERY_READ_ONLY but was ${error?.message}",
            error?.message?.contains("WALLET_RECOVERY_READ_ONLY") == true,
        )
    }

    private fun ByteArrayOutputStream.writeVarint(value: Int) {
        var remaining = value
        do {
            var byte = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining != 0) byte = byte or 0x80
            write(byte)
        } while (remaining != 0)
    }

    private fun legacyAccountCount(): Int {
        val database = context.getDatabasePath(DATABASE_NAME)
        return SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { sqlite ->
            sqlite.rawQuery("SELECT COUNT(*) FROM accounts", null).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }
    }

    /** The manual recovery paths are reached after the three automatic retries are used up. */
    private fun exhaustAutomaticRetries() {
        File(context.noBackupFilesDir, "wallet-migration-auto-retry").writeText("3")
    }

    /** A file kept, byte for byte, inside a renamed-aside backup or staging directory. */
    private fun setAsideFile(name: String): File =
        context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it.name.startsWith("wallet-backup-quarantine-") }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.name == name }.toList() }
            .single()

    private fun clearTestStorage() {
        context.deleteDatabase(DATABASE_NAME)
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(KEYSTORE_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        File(
            context.applicationInfo.dataDir,
            "shared_prefs/$PREFERENCES_NAME.xml.bak",
        ).delete()
        File(
            context.applicationInfo.dataDir,
            "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml",
        ).delete()
        File(
            context.applicationInfo.dataDir,
            "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml.bak",
        ).delete()
        File(
            context.filesDir,
            "datastore/sora_prefs_datastore.preferences_pb",
        ).delete()
        context.noBackupFilesDir.listFiles()
            .orEmpty()
            .filter {
                it.name.startsWith(BACKUP_PREFIX) ||
                    it.name.startsWith(".wallet-backup-extra-link-") ||
                    it.name.startsWith("wallet-backup-quarantine-") ||
                    it.name.startsWith("wallet-quarantine-") ||
                    it.name.startsWith("wallet-corrupt-quarantine-") ||
                    it.name == "wallet-migration-auto-retry" ||
                    it.name == ".wallet-migration-auto-retry.tmp" ||
                    it.name == "wallet-set-up-again-requested" ||
                    it.name == ".wallet-set-up-again-requested.tmp" ||
                    it.name == "wallet-snapshot-backoff" ||
                    it.name == "wallet-backup-prune-state"
            }
            .forEach(File::deleteRecursively)
        listOf("-wal", "-shm", "-journal").forEach {
            File("${context.getDatabasePath(DATABASE_NAME).path}$it").delete()
        }
        File(context.cacheDir, "wallet-recovery-exports").deleteRecursively()
        context.cacheDir.listFiles()
            .orEmpty()
            .filter { it.name.startsWith("wallet-backup-validation-") }
            .forEach(File::deleteRecursively)
    }

    private companion object {
        const val DATABASE_NAME = "app.db"
        const val PREFERENCES_NAME = "sora_prefs"
        const val KEYSTORE_PREFERENCES_NAME = "key_alias"
        const val WRAPPED_AES_KEY = "secret_key"
        const val BACKUP_PREFIX = "wallet-upgrade-backup-v"
        const val LEGACY_VERSION = 73
        const val CURRENT_VERSION = 77
        const val NEXUS_XOR_DEFINITION_ID = "6TEAJqbb8oEPmLncoNiMRbLEK6tw"
        const val WALLET_ID = "cnRetainedWallet"
        const val PARTIAL_WALLET_ID = "cnUncommittedSecondWallet"
        const val UNRELATED_WALLET_ID = "cnUnrelatedOrphan"
    }
}
