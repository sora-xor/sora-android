package jp.co.soramitsu.core_db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletUpgradeBackupTest {

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
    fun missingDatabaseWithEncryptedWalletMarkerFailsClosed() {
        putWalletMarker()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
    }

    @Test
    fun missingDatabaseWithOnlyRetainedWrappedAesKeyFailsClosed() {
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

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertFalse(context.getDatabasePath(DATABASE_NAME).exists())
        assertArrayEquals(wrappedKeyBytes, wrappedKeyFile.readBytes())
    }

    @Test
    fun reinstallWithRestoredEncryptedWalletStateAndNoDatabaseFailsClosed() {
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

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun missingDatabaseWithRetainedUpgradeBackupNeverBecomesAnEmptyWalletInstall() {
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

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun emptyDatabaseWithEncryptedWalletMarkerFailsClosed() {
        createLegacyDatabase(withAccount = false)
        putWalletMarker()

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun accountWithSecretButMissingSelectionFailsClosed() {
        createLegacyDatabase(withAccount = true)
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_mnemonic$WALLET_ID", "encrypted-value")
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun currentLegacySecretWithIncompleteKeypairFailsClosed() {
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun currentLegacySecretWithMnemonicConflictFailsClosed() {
        assertLegacySecretConflictFailsClosed(
            stringKey = "prefs_mnemonic$WALLET_ID"
        )
    }

    @Test
    fun currentLegacySecretWithSeedConflictFailsClosed() {
        assertLegacySecretConflictFailsClosed(
            stringKey = "prefs_seed$WALLET_ID"
        )
    }

    @Test
    fun currentLegacySecretWithWatchOnlyConflictFailsClosed() {
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun currentMnemonicWithIncompleteKeypairFailsClosed() {
        assertCurrentSourceMutationFailsClosed("MNEMONIC") { editor ->
            editor.remove("prefs_key_nonce$WALLET_ID")
        }
    }

    @Test
    fun currentMnemonicWithWatchOnlyConflictFailsClosed() {
        assertCurrentSourceMutationFailsClosed("MNEMONIC") { editor ->
            editor.putBoolean("wallet.watchOnly.$WALLET_ID", true)
        }
    }

    @Test
    fun currentRawSeedWithIncompleteKeypairFailsClosed() {
        assertCurrentSourceMutationFailsClosed("RAW_SEED") { editor ->
            editor.remove("prefs_pub_key$WALLET_ID")
        }
    }

    @Test
    fun currentRawSeedWithMnemonicConflictFailsClosed() {
        assertCurrentSourceMutationFailsClosed("RAW_SEED") { editor ->
            editor.putString("prefs_mnemonic$WALLET_ID", "conflicting-encrypted-source")
        }
    }

    @Test
    fun currentRawSeedWithWatchOnlyConflictFailsClosed() {
        assertCurrentSourceMutationFailsClosed("RAW_SEED") { editor ->
            editor.putBoolean("wallet.watchOnly.$WALLET_ID", true)
        }
    }

    @Test
    fun currentWatchOnlyWithCompleteKeypairConflictFailsClosed() {
        assertCurrentSourceMutationFailsClosed("WATCH_ONLY") { editor ->
            editor
                .putString("prefs_priv_key$WALLET_ID", "conflicting-private")
                .putString("prefs_pub_key$WALLET_ID", "conflicting-public")
                .putString("prefs_key_nonce$WALLET_ID", "conflicting-nonce")
        }
    }

    @Test
    fun currentWatchOnlyWithMnemonicConflictFailsClosed() {
        assertCurrentSourceMutationFailsClosed("WATCH_ONLY") { editor ->
            editor.putString("prefs_mnemonic$WALLET_ID", "conflicting-encrypted-source")
        }
    }

    @Test
    fun currentWatchOnlyWithRawSeedConflictFailsClosed() {
        assertCurrentSourceMutationFailsClosed("WATCH_ONLY") { editor ->
            editor.putString("prefs_seed$WALLET_ID", "conflicting-encrypted-source")
        }
    }

    @Test
    fun secondWalletSecretWithoutCommittedAccountRowFailsClosed() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker(PARTIAL_WALLET_ID)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun selectedWalletWithoutCommittedAccountRowFailsClosed() {
        createLegacyDatabase(withAccount = true)
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("cur_account_address", PARTIAL_WALLET_ID)
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun dataStoreSecondWalletSecretWithoutCommittedAccountRowFailsClosed() {
        createLegacyDatabase(withAccount = true)
        putDataStoreStringMarker("prefs_seed$PARTIAL_WALLET_ID")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(1, legacyAccountCount())
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
    fun finishedRegistrationWithoutDatabaseFailsClosed() {
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("registration_state", "REGISTRATION_FINISHED")
                .commit()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun partialPublishedBackupWithoutCompletionMarkerBlocksRoomPreparation() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        val partialDestination = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(partialDestination.mkdir())
        File(partialDestination, "partial-publication").writeText("incomplete")

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "EXISTING_BACKUP_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(1, legacyAccountCount())
        assertFalse(File(partialDestination, ".complete").exists())
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
    fun currentSchemaShortcutRejectsInterruptedSiblingNamespace() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX${CURRENT_VERSION - 1}-to-$CURRENT_VERSION.staging",
        ).apply {
            assertTrue(mkdir())
            File(this, "partial").writeText("incomplete")
        }

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "STALE_STAGING",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun currentSchemaShortcutRejectsGenerationDirectoryMismatch() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        val manifestFile = File(backup, "manifest.json")
        manifestFile.writeText(
            JSONObject(manifestFile.readText())
                .put("backupGeneration", 2L)
                .toString()
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "EXISTING_BACKUP_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertEquals("STALE_STAGING", WalletUpgradeBackup.blockingFailure()?.code)

        val archive = WalletUpgradeBackup.createRecoveryArchive(context).getOrThrow()

        ZipFile(archive).use { zip ->
            assertTrue(
                zip.getEntry("live-encrypted-storage/databases/$DATABASE_NAME") != null
            )
            assertTrue(zip.getEntry("encrypted-backup/partial") == null)
        }
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun unexpectedMigrationFailureCannotDemoteExistingBackupBlocker() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION.staging",
        ).apply {
            assertTrue(mkdirs())
            File(this, "partial").writeText("incomplete")
        }
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        assertEquals("STALE_STAGING", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.BLOCKED,
            WalletRecoveryCapabilityGate.mode(),
        )

        WalletUpgradeBackup.noteUnexpectedMigrationFailure()

        assertEquals("STALE_STAGING", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(
            WalletRecoveryCapabilityGate.Mode.BLOCKED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertFalse(WalletUpgradeBackup.canAuthorizeMigrationRetry())
        assertTrue(WalletUpgradeBackup.authorizeMigrationRetry(context).isFailure)
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
    fun missingGenerationForcesRecoveryFallbackAndBlocksPreparation() {
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "EXISTING_BACKUP_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertEquals(2, legacyAccountCount())
    }

    @Test
    fun duplicateGenerationAcrossSourceVersionsFailsClosed() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)

        assertTrue(context.deleteDatabase(DATABASE_NAME))
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val generatedCurrentBackup = context.noBackupFilesDir.listFiles()
            .orEmpty()
            .single {
                it.isDirectory &&
                    it.name.startsWith(
                        "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION-g"
                    )
            }
        val manifestFile = File(generatedCurrentBackup, "manifest.json")
        manifestFile.writeText(
            JSONObject(manifestFile.readText())
                .put("backupGeneration", 1L)
                .toString()
        )
        val generationOneCurrentBackup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        assertTrue(generatedCurrentBackup.renameTo(generationOneCurrentBackup))

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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "EXISTING_BACKUP_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "EXISTING_BACKUP_INVALID",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun interruptedStagingDirectoryNeverPermitsRoomToOpen() {
        createLegacyDatabase(withAccount = true)
        putWalletMarker()
        File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$LEGACY_VERSION-to-$CURRENT_VERSION.staging",
        ).apply {
            assertTrue(mkdirs())
            File(this, "partial").writeText("incomplete")
        }

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals("STALE_STAGING", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun sharedPreferencesBackupIsAuthoritativeForOrphanDetection() {
        writeSharedPreferencesBackup(
            """<?xml version="1.0" encoding="utf-8" standalone="yes" ?>""" +
                """<map><string name="prefs_mnemonic$WALLET_ID">encrypted</string>""" +
                """<string name="cur_account_address">$WALLET_ID</string></map>"""
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun currentSchemaShortcutRejectsWrappedKeyMutationAtFinalAdmission() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        val wrappedKeyPreferences = context.getSharedPreferences(
            KEYSTORE_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )
        val originalWrappedKey = "retained-wrapped-wallet-key"
        val replacementWrappedKey = "changed-wrapped-wallet-key"
        assertTrue(
            wrappedKeyPreferences.edit()
                .putString(WRAPPED_AES_KEY, originalWrappedKey)
                .commit()
        )
        val installedWrappedKeyFile = File(
            context.applicationInfo.dataDir,
            "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml",
        )
        val installedWrappedKeyBytes = installedWrappedKeyFile.readBytes()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        val backup = File(
            context.noBackupFilesDir,
            "$BACKUP_PREFIX$CURRENT_VERSION-to-$CURRENT_VERSION",
        )
        val backedUpWrappedKeyFile = File(
            backup,
            "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml",
        )
        assertArrayEquals(installedWrappedKeyBytes, backedUpWrappedKeyFile.readBytes())

        val result = WalletUpgradeBackup.prepareWithFinalSourceMutationForTest(
            context = context,
        ) {
            assertTrue(
                wrappedKeyPreferences.edit()
                    .putString(WRAPPED_AES_KEY, replacementWrappedKey)
                    .commit()
            )
        }

        assertTrue(result.isFailure)
        assertEquals("SOURCE_CHANGED", WalletUpgradeBackup.blockingFailure()?.code)
        assertEquals(1, legacyAccountCount())
        assertTrue(context.getDatabasePath(DATABASE_NAME).isFile)
        assertArrayEquals(installedWrappedKeyBytes, backedUpWrappedKeyFile.readBytes())
        assertEquals(
            replacementWrappedKey,
            wrappedKeyPreferences.getString(WRAPPED_AES_KEY, null),
        )
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "WALLET_MIGRATION_RECOVERY_REQUIRED",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
        assertFalse(WalletUpgradeBackup.hasValidatedLegacyRecovery())
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
    fun retryAuthorizationFailsClosedWhenLegacySnapshotChanges() {
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
        assertTrue(WalletUpgradeBackup.prepare(context).isFailure)
        updateCurrentDatabase(
            "UPDATE accounts SET accountName = 'Changed after inspection'"
        )

        assertTrue(WalletUpgradeBackup.authorizeMigrationRetry(context).isFailure)

        assertEquals(
            WalletRecoveryCapabilityGate.Mode.BLOCKED,
            WalletRecoveryCapabilityGate.mode(),
        )
        assertEquals(1, legacyAccountCount())
    }

    @Test
    fun currentSchemaPendingIdentityWithoutJournalRequiresRecovery() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(WALLET_ID, "Retained"))
        )
        updateCurrentDatabase(
            "UPDATE walletIdentities SET migrationState = 'PENDING_VERIFICATION'"
        )

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "WALLET_MIGRATION_RECOVERY_REQUIRED",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
    fun databaseCommittedDeletionNeverAuthorizesUnrelatedOrphan() {
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

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    @Test
    fun unjournaledCurrentSchemaOrphanStillFailsClosed() {
        createCurrentDatabase(
            listOf(SoraAccountLocal(PARTIAL_WALLET_ID, "Remaining"))
        )
        putWalletMarker(WALLET_ID)

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
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
            "ORPHANED_WALLET_STORAGE",
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

    private fun assertLegacySecretConflictFailsClosed(stringKey: String) {
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    private fun assertCurrentSourceMutationFailsClosed(
        secretSource: String,
        mutation: (android.content.SharedPreferences.Editor) -> Unit,
    ) {
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

        val result = WalletUpgradeBackup.prepare(context)

        assertTrue(result.isFailure)
        assertEquals(
            "ORPHANED_WALLET_STORAGE",
            WalletUpgradeBackup.blockingFailure()?.code,
        )
    }

    /**
     * Reproduces the production v77 modernization tables exactly for raw-SQL backup tests.
     *
     * MigrationTestHelper owns the independently exported-schema checks. These raw fixtures are
     * still used to exercise pre-Room recovery paths, so they must retain every production column,
     * foreign key, and declared index instead of using a permissive subset that Room would reject.
     * Keep this in structural lockstep with the checked-in Room 77 schema.
     */
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
                    it.name.startsWith(".wallet-backup-extra-link-")
            }
            .forEach(File::deleteRecursively)
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
