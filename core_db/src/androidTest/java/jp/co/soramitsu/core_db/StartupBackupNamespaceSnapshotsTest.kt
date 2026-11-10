package jp.co.soramitsu.core_db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.lang.reflect.InvocationTargetException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `WalletUpgradeBackup.startupBackupNamespaceSnapshots` walks every entry of the upgrade backups
 * when an app process starts. Its result is part of the admission that lets Room construction skip
 * a repeat of the full wallet verification, so it decides what the app trusts without looking again.
 *
 * The function is private and the app turns its failures into "no shortcut", so no public call shows
 * which rule fired. The walk tests therefore call it by reflection on a sandbox directory, which
 * touches neither wallet storage nor the process-wide gate. The last tests go through the real
 * `prepare` and `requirePrepared` on real wallet storage.
 */
@RunWith(AndroidJUnit4::class)
class StartupBackupNamespaceSnapshotsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sandbox = File(context.filesDir, "startup-walk")
    private var nextRoot = 0
    private var attachAttemptedBefore = false

    private val walkMethod = WalletUpgradeBackup::class.java
        .getDeclaredMethod("startupBackupNamespaceSnapshots", File::class.java)
        .apply { isAccessible = true }
    private val attachAttempted = WalletUpgradeBackup::class.java
        .getDeclaredField("applicationAttachPreparationAttempted")
        .apply { isAccessible = true }
    private val preparedAdmission = WalletUpgradeBackup::class.java
        .getDeclaredField("preparedAdmission")
        .apply { isAccessible = true }

    @Before
    fun prepareSandbox() {
        // API 26 has no nanosecond timestamps, so the walk refuses every entry there by design.
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1)
        attachAttemptedBefore = attachAttempted.getBoolean(null)
        removeNoFollow(sandbox)
        assertTrue(sandbox.mkdirs())
        clearWalletStorage()
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
    }

    @After
    fun clearSandbox() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        removeNoFollow(sandbox)
        clearWalletStorage()
        // Empties the gate and any retained admission. Then the next class sees the process as this
        // one found it, including whether a first prepare is still to come.
        attachAttempted.setBoolean(null, true)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertNull(retainedAdmission())
        attachAttempted.setBoolean(null, attachAttemptedBefore)
    }

    @Test
    fun walksNestedDirectoriesAndRegularFilesWithTheirLstatMetadata() {
        val root = newRoot()
        val backup = root.directory("wallet-upgrade-backup-v2-to-77")
        backup.file(".complete", "verified")
        backup.file("z-last")
        backup.directory("empty")
        backup.directory("databases").file("app.db", "database bytes")
        backup.directory("databases/nested/deep").file("leaf")
        root.directory("wallet-upgrade-backup-v1-to-77.staging").file("partial")
        root.file("wallet-upgrade-backup-v3-stray-file")

        val snapshots = walk(root)

        assertEquals(
            listOf(
                "wallet-upgrade-backup-v1-to-77.staging",
                "wallet-upgrade-backup-v1-to-77.staging/partial",
                "wallet-upgrade-backup-v2-to-77",
                "wallet-upgrade-backup-v2-to-77/.complete",
                "wallet-upgrade-backup-v2-to-77/databases",
                "wallet-upgrade-backup-v2-to-77/databases/app.db",
                "wallet-upgrade-backup-v2-to-77/databases/nested",
                "wallet-upgrade-backup-v2-to-77/databases/nested/deep",
                "wallet-upgrade-backup-v2-to-77/databases/nested/deep/leaf",
                "wallet-upgrade-backup-v2-to-77/empty",
                "wallet-upgrade-backup-v2-to-77/z-last",
                "wallet-upgrade-backup-v3-stray-file",
            ),
            walkedPaths(root).sorted(),
        )
        snapshots.forEach { snapshot ->
            val path = snapshot.read("path") as String
            assertEquals(path, lstatOf(path), metadataOf(snapshot))
        }
    }

    @Test
    fun ignoresEverythingOutsideTheBackupPrefix() {
        val root = newRoot()
        // Each of these would be rejected if the walk looked at it.
        val elsewhere = sandbox.file("elsewhere").path
        Os.symlink(elsewhere, File(root, "wallet-upgrade-backup-").path)
        Os.symlink(elsewhere, File(root, ".wallet-upgrade-backup-v1").path)
        Os.symlink(elsewhere, File(root.directory("wallet-backup-quarantine-1-v1"), "link").path)
        Os.mkfifo(File(root, "wallet-migration-auto-retry").path, MODE_OWNER_READ_WRITE)
        root.file(".wallet-upgrade-publication.lock")

        assertEquals(emptyList<String>(), walkedPaths(root))

        root.directory("wallet-upgrade-backup-v1")
        assertEquals(listOf("wallet-upgrade-backup-v1"), walkedPaths(root))
    }

    @Test
    fun rejectsASymlinkToAFileAtEveryDepth() {
        val target = sandbox.file("target-file", "must not be followed")
        assertRejectedAtEveryDepth(OsConstants::S_ISLNK) { entry ->
            Os.symlink(target.path, entry.path)
        }
    }

    @Test
    fun rejectsASymlinkToADirectoryAtEveryDepthWithoutFollowingIt() {
        val target = sandbox.directory("target-directory")
        target.file("regular")
        assertRejectedAtEveryDepth(OsConstants::S_ISLNK) { entry ->
            Os.symlink(target.path, entry.path)
        }
    }

    @Test
    fun rejectsADanglingSymlinkAtEveryDepth() {
        val target = File(sandbox, "missing-target")
        assertRejectedAtEveryDepth(OsConstants::S_ISLNK) { entry ->
            Os.symlink(target.path, entry.path)
        }
    }

    @Test
    fun rejectsAFifoAtEveryDepth() {
        assertRejectedAtEveryDepth(OsConstants::S_ISFIFO) { entry ->
            Os.mkfifo(entry.path, MODE_OWNER_READ_WRITE)
        }
    }

    @Test
    fun rejectsASocketAtEveryDepth() {
        assertRejectedAtEveryDepth(OsConstants::S_ISSOCK) { entry ->
            LocalSocket(LocalSocket.SOCKET_STREAM).use { socket ->
                socket.bind(LocalSocketAddress(entry.path, LocalSocketAddress.Namespace.FILESYSTEM))
            }
        }
    }

    @Test
    fun rejectsADirectoryItCannotList() {
        val root = newRoot()
        val backup = root.directory("wallet-upgrade-backup-v1")
        val nested = backup.directory("nested")
        nested.file("regular")
        assertNull(walkFailureCode(root))

        Os.chmod(nested.path, MODE_NONE)
        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(root))
        Os.chmod(nested.path, MODE_OWNER_ALL)
        assertNull(walkFailureCode(root))

        Os.chmod(backup.path, MODE_NONE)
        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(root))
    }

    @Test
    fun rejectsARootItCannotList() {
        assertEquals(emptyList<String>(), walkedPaths(newRoot()))

        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(File(sandbox, "missing")))
        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(sandbox.file("not-a-directory")))
        val locked = newRoot()
        Os.chmod(locked.path, MODE_NONE)
        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(locked))
    }

    @Test
    fun reportsSourceChangedForAnEntryItCanListButCannotExamine() {
        val root = newRoot()
        val nested = root.directory("wallet-upgrade-backup-v1/nested")
        nested.file("regular")
        assertNull(walkFailureCode(root))

        // Read without search permission: the names can be listed, lstat on them fails.
        Os.chmod(nested.path, MODE_OWNER_READ)

        assertEquals("SOURCE_CHANGED", walkFailureCode(root))
    }

    @Test
    fun walksMaxPublishedBackupsAndRejectsOneMore() {
        val root = newRoot()
        repeat(MAX_PUBLISHED_BACKUPS) { index ->
            if (index % 2 == 0) {
                root.directory("wallet-upgrade-backup-v$index")
            } else {
                root.file("wallet-upgrade-backup-v$index")
            }
        }
        // Neither nested entries nor other names count against this limit.
        root.directory("wallet-upgrade-backup-v0/nested").file("regular")
        root.directory("wallet-backup-quarantine-1-v1")
        root.file(".wallet-upgrade-publication.lock")

        assertEquals(MAX_PUBLISHED_BACKUPS + 2, walk(root).size)

        root.file("wallet-upgrade-backup-vone-more")
        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(root))
    }

    @Test
    fun walksTheLargestLegalNamespaceAndRejectsOneMoreEntry() {
        val root = newRoot()
        repeat(MAX_PUBLISHED_BACKUPS) { index ->
            val backup = root.directory("wallet-upgrade-backup-v$index")
            val nested = backup.directory("databases/nested")
            // Two directories and the regular files that fill the rest of the backup's allowance.
            repeat((MAX_BACKUP_NAMESPACE_ENTRIES - 2) / 2) { file ->
                assertTrue(File(backup, "file-$file").createNewFile())
                assertTrue(File(nested, "file-$file").createNewFile())
            }
        }

        assertEquals(MAX_STARTUP_ADMISSION_BACKUP_ENTRIES, walk(root).size)

        assertTrue(File(root, "wallet-upgrade-backup-v0/databases/nested/one-more").createNewFile())
        assertEquals("BACKUP_NAMESPACE_INVALID", walkFailureCode(root))
    }

    @Test
    fun changesWithEveryChangeToANestedEntry() {
        val root = newRoot()
        val nested = root.directory("wallet-upgrade-backup-v1/databases")
        val file = nested.file("app.db", "database bytes")
        val changes = listOf<Pair<String, () -> Unit>>(
            "bytes appended" to { file.appendBytes(byteArrayOf(1)) },
            "replaced by a copy" to {
                val copy = sandbox.file("copy", file.readText())
                Os.rename(copy.path, file.path)
            },
            "mode changed" to { Os.chmod(file.path, MODE_OWNER_READ) },
            "modified time changed" to { assertTrue(file.setLastModified(1_000_000L)) },
            "file added" to { nested.file("added") },
            "file removed" to { assertTrue(File(nested, "added").delete()) },
            "directory added" to { nested.directory("added-directory") },
        )

        changes.forEach { (change, apply) ->
            val before = walk(root)
            assertEquals(change, before, walk(root))
            apply()
            assertNotEquals(change, before, walk(root))
        }
    }

    /**
     * The path every wallet with a backup takes at app start, on a backup the app published itself.
     * The counters prove that the shortcut really skipped the verification: without the token, the
     * same call runs it.
     */
    @Test
    fun firstPrepareAdmitsEveryEntryOfARealBackupAndRoomSkipsTheRepeatVerification() {
        val backup = publishRealBackup()

        prepareAsTheFirstOfAProcess()

        val admission = retainedAdmission()
        assertNotNull(admission)
        val backupPrefix = File(context.noBackupFilesDir, BACKUP_PREFIX).path

        @Suppress("UNCHECKED_CAST")
        val admitted = (admission!!.read("paths") as List<Any>)
            .map { it.read("path") as String }
            .filter { it.startsWith(backupPrefix) }
        val onDisk = backup.walkTopDown().map(File::getPath).toList()
        assertTrue(onDisk.toString(), onDisk.any { it.endsWith("/databases/$DATABASE_NAME") })
        assertEquals(onDisk.sorted(), admitted.sorted())

        WalletUpgradeBackup.WorkCountersForTest.reset()
        WalletUpgradeBackup.requirePrepared(context)
        assertEquals(0, WalletUpgradeBackup.WorkCountersForTest.fullBackupValidations.get())
        assertEquals(0L, WalletUpgradeBackup.WorkCountersForTest.hashedBytes.get())

        assertNull("the token is good for one database build", retainedAdmission())
        WalletUpgradeBackup.requirePrepared(context)
        assertTrue(WalletUpgradeBackup.WorkCountersForTest.fullBackupValidations.get() > 0)
    }

    /**
     * Without the walk these changes would go unseen until the next process, because the cheap
     * inventory reads only the completion marker and the manifest. The changed backup stays as
     * evidence, is not reused, and a verified copy is published beside it.
     */
    @Test
    fun changeInsideARealBackupAfterAdmissionForcesTheFullVerification() {
        val changes = listOf<Pair<String, (File) -> Unit>>(
            "nested file changed" to { backup ->
                File(backup, "databases/$DATABASE_NAME").appendBytes(byteArrayOf(1))
            },
            "nested file added" to { backup ->
                assertTrue(File(backup, "databases/added").createNewFile())
            },
            "nested symlink added" to { backup ->
                Os.symlink(sandbox.path, File(backup, "databases/link").path)
            },
        )

        changes.forEach { (change, apply) ->
            clearWalletStorage()
            val backup = publishRealBackup()
            prepareAsTheFirstOfAProcess()
            assertNotNull(change, retainedAdmission())

            apply(backup)
            WalletUpgradeBackup.WorkCountersForTest.reset()
            WalletUpgradeBackup.requirePrepared(context)

            assertTrue(change, WalletUpgradeBackup.WorkCountersForTest.fullBackupValidations.get() > 0)
            assertTrue(change, WalletUpgradeBackup.WorkCountersForTest.copiedFiles.get() > 0)
            assertEquals(change, 2, backupEntries().size)
            assertTrue(change, backupEntries().contains(backup))
        }
    }

    /** No database: the gate has nothing to verify and leaves the namespace alone. */
    @Test
    fun rejectedWalkForfeitsTheShortcutButDoesNotBlockStartup() {
        val backup = context.noBackupFilesDir.directory("${BACKUP_PREFIX}77-to-77")
        backup.directory("databases").file(DATABASE_NAME, "database bytes")
        prepareAsTheFirstOfAProcess()
        assertNotNull("a clean walk keeps the shortcut", retainedAdmission())

        val link = File(backup, "databases/link")
        Os.symlink(sandbox.path, link.path)
        prepareAsTheFirstOfAProcess()
        assertNull("a symlink forfeits the shortcut", retainedAdmission())
        assertNull(WalletUpgradeBackup.blockingFailure())
        WalletUpgradeBackup.requirePrepared(context)

        Os.remove(link.path)
        prepareAsTheFirstOfAProcess()
        assertNotNull("the shortcut returns with a clean walk", retainedAdmission())

        repeat(MAX_PUBLISHED_BACKUPS) { index ->
            context.noBackupFilesDir.file("${BACKUP_PREFIX}extra-$index")
        }
        prepareAsTheFirstOfAProcess()
        assertNull("too many backups forfeit the shortcut", retainedAdmission())
        assertNull(WalletUpgradeBackup.blockingFailure())
        WalletUpgradeBackup.requirePrepared(context)
    }

    /** Plants one entry directly in the root, then in a backup, then deep in a backup. */
    private fun assertRejectedAtEveryDepth(isExpectedType: (Int) -> Boolean, plant: (File) -> Unit) {
        val root = newRoot()
        root.directory("wallet-upgrade-backup-v0").file("regular")
        val deep = root.directory("wallet-upgrade-backup-v1/a/b")
        listOf(
            File(root, "wallet-upgrade-backup-vx"),
            File(root, "wallet-upgrade-backup-v1/x"),
            File(deep, "x"),
        ).forEach { entry ->
            assertNull(entry.path, walkFailureCode(root))
            plant(entry)
            assertTrue(entry.path, isExpectedType(Os.lstat(entry.path).st_mode))
            assertEquals(entry.path, "BACKUP_NAMESPACE_INVALID", walkFailureCode(root))
            Os.remove(entry.path)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun walk(root: File): List<Any> = try {
        walkMethod.invoke(WalletUpgradeBackup, root) as List<Any>
    } catch (error: InvocationTargetException) {
        throw error.targetException
    }

    /** The code the walk failed with, or null when it finished. */
    private fun walkFailureCode(root: File): String? = try {
        walk(root)
        null
    } catch (error: WalletUpgradeBackupException) {
        error.code
    }

    private fun walkedPaths(root: File): List<String> =
        walk(root).map { (it.read("path") as String).removePrefix("${root.path}/") }

    private fun Any.read(field: String): Any? =
        javaClass.getDeclaredField(field).apply { isAccessible = true }.get(this)

    private fun metadataOf(snapshot: Any): Map<String, Any?> {
        val metadata = requireNotNull(snapshot.read("metadata"))
        return lstatOf(snapshot.read("path") as String).keys.associateWith { metadata.read(it) }
    }

    private fun lstatOf(path: String): Map<String, Any?> = Os.lstat(path).run {
        mapOf(
            "device" to st_dev,
            "inode" to st_ino,
            "mode" to st_mode,
            "linkCount" to st_nlink,
            "owner" to st_uid,
            "group" to st_gid,
            "size" to st_size,
            "modifiedAtSeconds" to st_mtim.tv_sec,
            "modifiedAtNanoseconds" to st_mtim.tv_nsec,
            "changedAtSeconds" to st_ctim.tv_sec,
            "changedAtNanoseconds" to st_ctim.tv_nsec,
        )
    }

    /** The gate retains an admission only on the first prepare of a process. */
    private fun prepareAsTheFirstOfAProcess() {
        attachAttempted.setBoolean(null, false)
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        assertNull(WalletUpgradeBackup.blockingFailure())
    }

    private fun retainedAdmission(): Any? = preparedAdmission.get(null)

    private fun backupEntries(): List<File> = context.noBackupFilesDir.listFiles().orEmpty()
        .filter { it.name.startsWith(BACKUP_PREFIX) }

    /** A wallet with a pending schema migration, so the gate publishes a complete backup. */
    private fun publishRealBackup(): File {
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
            sqlite.execSQL(
                "INSERT INTO accounts(substrateAddress, accountName) VALUES(?, ?)",
                arrayOf(WALLET_ID, "Retained"),
            )
            sqlite.execSQL("PRAGMA user_version = $LEGACY_VERSION")
        }
        assertTrue(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("prefs_mnemonic$WALLET_ID", "encrypted-value")
                .putString("cur_account_address", WALLET_ID)
                .commit()
        )
        assertTrue(WalletUpgradeBackup.prepare(context).isSuccess)
        return backupEntries().single()
    }

    private fun clearWalletStorage() {
        check(context.packageName.endsWith(".test"))
        val dataDirectory = context.applicationInfo.dataDir
        context.deleteDatabase(DATABASE_NAME)
        listOf(PREFERENCES_NAME, KEYSTORE_PREFERENCES_NAME).forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            File(dataDirectory, "shared_prefs/$name.xml.bak").delete()
        }
        File(dataDirectory, "shared_prefs/$KEYSTORE_PREFERENCES_NAME.xml").delete()
        File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb").delete()
        context.noBackupFilesDir.listFiles().orEmpty()
            .filter { it.name.startsWith("wallet-") }
            .forEach(::removeNoFollow)
    }

    private fun newRoot(): File = sandbox.directory("r${nextRoot++}")

    private fun File.directory(path: String): File =
        File(this, path).also { assertTrue(it.path, it.isDirectory || it.mkdirs()) }

    private fun File.file(path: String, text: String = ""): File =
        File(this, path).also { it.writeText(text) }

    /** Deletes a tree without following a symlink, and gets back into a locked directory. */
    private fun removeNoFollow(file: File) {
        val mode = try {
            Os.lstat(file.path).st_mode
        } catch (_: ErrnoException) {
            return
        }
        if (OsConstants.S_ISDIR(mode)) {
            Os.chmod(file.path, MODE_OWNER_ALL)
            file.listFiles().orEmpty().forEach(::removeNoFollow)
        }
        Os.remove(file.path)
    }

    private companion object {
        const val BACKUP_PREFIX = "wallet-upgrade-backup-v"
        const val MAX_PUBLISHED_BACKUPS = 64
        const val MAX_BACKUP_NAMESPACE_ENTRIES = 128
        const val MAX_STARTUP_ADMISSION_BACKUP_ENTRIES =
            MAX_PUBLISHED_BACKUPS * (MAX_BACKUP_NAMESPACE_ENTRIES + 1)
        const val DATABASE_NAME = "app.db"
        const val PREFERENCES_NAME = "sora_prefs"
        const val KEYSTORE_PREFERENCES_NAME = "key_alias"
        const val LEGACY_VERSION = 73
        const val WALLET_ID = "cnRetainedWallet"
        const val MODE_NONE = 0
        const val MODE_OWNER_READ = 256 // 0400
        const val MODE_OWNER_READ_WRITE = 384 // 0600
        const val MODE_OWNER_ALL = 448 // 0700
    }
}
