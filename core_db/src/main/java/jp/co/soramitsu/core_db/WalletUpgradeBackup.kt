package jp.co.soramitsu.core_db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Xml
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletDeletionContract
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.CharacterCodingException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/**
 * Creates a verified, byte-for-byte backup before Room is allowed to migrate the production
 * wallet database. Encrypted DataStore and wrapped-key files stay encrypted in the backup.
 */
object WalletUpgradeBackup {
    private const val DATABASE_NAME = "app.db"
    private const val CURRENT_DATABASE_VERSION = 77
    // Keep the migration-release backup readable for at least the following dual-read release.
    // Advance only after the retention window is complete and separately qualified.
    private const val OLDEST_RECOVERABLE_BACKUP_TARGET_VERSION = 75
    private const val BACKUP_FORMAT_VERSION = 3
    private const val BACKUP_PREFIX = "wallet-upgrade-backup-v"
    private const val REQUIRED_DATABASE_BACKUP = "databases/app.db"
    private const val MAX_MANIFEST_BYTES = 64 * 1_024L
    private const val MAX_BACKUP_FILES = 32
    private const val MAX_BACKUP_NAMESPACE_ENTRIES = MAX_BACKUP_FILES * 4
    private const val MAX_PUBLISHED_BACKUPS = 64
    private const val MAX_PREFERENCES_INSPECTION_BYTES = 8L * 1_024L * 1_024L
    private const val MAX_PREFERENCE_KEY_LENGTH = 1_024
    private const val MAX_WALLET_ID_LENGTH = 256
    private const val MINIMUM_FREE_SPACE_HEADROOM = 16L * 1_024L * 1_024L
    private const val BACKUP_SPACE_MULTIPLIER = 2L
    private const val REGISTRATION_FINISHED = "REGISTRATION_FINISHED"
    private const val BACKUP_PUBLICATION_LOCK = ".wallet-upgrade-publication.lock"
    private const val RECOVERY_PUBLICATION_LOCK = ".wallet-recovery-publication.lock"
    private val SHA256 = Regex("^[0-9a-f]{64}$")
    private val RECOVERY_ARCHIVE_FILE_NAME = Regex(
        "^sora-wallet-recovery-[A-Za-z0-9-]{1,160}\\.zip$"
    )
    private val DELETION_OPERATION_ID = Regex(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
    )
    private val FAIL_CLOSED_DATABASE_ERROR_HANDLER = DatabaseErrorHandler {
        // Android's default handler deletes a database it considers corrupt. A production wallet
        // database is recovery material even when unreadable, so every raw inspection must retain
        // the exact bytes and route startup to recovery instead.
        throw WalletUpgradeBackupException("DATABASE_CORRUPT")
    }

    @Volatile
    private var blockingFailure: Failure? = null

    @Volatile
    private var migrationRecoverySnapshot: MigrationRecoverySnapshot? = null

    data class Failure(val code: String)

    fun blockingFailure(): Failure? = blockingFailure

    fun hasValidatedLegacyRecovery(): Boolean = migrationRecoverySnapshot != null

    fun canAuthorizeMigrationRetry(): Boolean {
        if (
            blockingFailure?.code?.let {
                it != "WALLET_MIGRATION_RECOVERY_REQUIRED"
            } == true
        ) {
            return false
        }
        val mode = WalletRecoveryCapabilityGate.mode()
        return when (mode) {
            WalletRecoveryCapabilityGate.Mode.BLOCKED ->
                migrationRecoverySnapshot != null
            WalletRecoveryCapabilityGate.Mode.RECOVERY_INSPECTED,
            WalletRecoveryCapabilityGate.Mode.LEGACY_READ_ONLY,
            WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED -> true
            WalletRecoveryCapabilityGate.Mode.NORMAL -> false
        }
    }

    fun isLegacyReadOnlyRecovery(): Boolean =
        WalletRecoveryCapabilityGate.isLegacyReadOnly()

    @Synchronized
    fun prepare(context: Context): Result<Unit> {
        return prepareWithCapacityOverride(
            context = context,
            availableBackupBytesOverride = null,
        )
    }

    /**
     * Deterministic low-storage seam for the retained-wallet instrumentation matrix.
     *
     * Production callers always use [prepare], which probes the target filesystem with
     * [StatFs]. Android tests use this internal entry point to prove that the complete
     * pre-migration path fails closed before creating a staging directory or changing the
     * installed database/settings when the capacity probe reports insufficient space.
     */
    @Synchronized
    internal fun prepareWithAvailableBackupBytesForTest(
        context: Context,
        availableBackupBytes: Long,
    ): Result<Unit> {
        require(availableBackupBytes >= 0L)
        return prepareWithCapacityOverride(
            context = context,
            availableBackupBytesOverride = availableBackupBytes,
            beforeFinalSourceValidationForTest = null,
        )
    }

    /** Deterministic seam proving that a last-moment source change blocks Room activation. */
    @Synchronized
    internal fun prepareWithFinalSourceMutationForTest(
        context: Context,
        beforeFinalSourceValidation: () -> Unit,
    ): Result<Unit> = prepareWithCapacityOverride(
        context = context,
        availableBackupBytesOverride = null,
        beforeFinalSourceValidationForTest = beforeFinalSourceValidation,
    )

    private fun prepareWithCapacityOverride(
        context: Context,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)? = null,
    ): Result<Unit> {
        val applicationContext = context.applicationContext
        val result = runCatching {
            prepareOrThrow(
                context = applicationContext,
                availableBackupBytesOverride = availableBackupBytesOverride,
                beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
            )
        }
        val failure = result.exceptionOrNull()?.let { Failure(it.safeCode()) }
        val recovery = if (failure?.code == "WALLET_MIGRATION_RECOVERY_REQUIRED") {
            runCatching { inspectMigrationRecovery(applicationContext) }.getOrNull()
        } else {
            null
        }
        migrationRecoverySnapshot = recovery
        blockingFailure = failure
        if (failure == null) {
            WalletRecoveryCapabilityGate.enterNormal()
        } else {
            WalletRecoveryCapabilityGate.enterBlocked()
        }
        return result
    }

    /**
     * Enables legacy SORA2 reads only after the current encrypted preferences and current-schema
     * database still match the exact recovery snapshot inspected before Room was opened.
     */
    @Synchronized
    fun authorizeLegacyReadOnly(context: Context): Result<Unit> = runCatching {
        val mode = WalletRecoveryCapabilityGate.mode()
        if (
            mode == WalletRecoveryCapabilityGate.Mode.NORMAL ||
            mode == WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED ||
            (
                mode == WalletRecoveryCapabilityGate.Mode.BLOCKED &&
                    migrationRecoverySnapshot == null
                )
        ) {
            throw WalletUpgradeBackupException(
                "LEGACY_RECOVERY_AUTHORIZATION_NOT_AVAILABLE"
            )
        }
        val current = inspectMigrationRecovery(context.applicationContext)
        migrationRecoverySnapshot?.let { expected ->
            if (expected.token != current.token) {
                throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
            }
        }
        migrationRecoverySnapshot = current
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()
        blockingFailure = null
    }

    /**
     * User-authorized, compare-and-set retry transition.
     *
     * Room is deliberately not used: restart recovery has not granted it access yet. Only the
     * single migration-journal row is updated, under an SQLite transaction, after the legacy
     * account/preference snapshot and every expected journal field have been revalidated.
     */
    @Synchronized
    fun authorizeMigrationRetry(context: Context): Result<Unit> = runCatching {
        if (!canAuthorizeMigrationRetry()) {
            throw WalletUpgradeBackupException(
                "MIGRATION_RETRY_AUTHORIZATION_NOT_AVAILABLE"
            )
        }
        val applicationContext = context.applicationContext
        val expected = inspectMigrationRecovery(applicationContext)
        migrationRecoverySnapshot?.let { previous ->
            if (previous.token != expected.token) {
                throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
            }
        }
        val databaseFile = applicationContext.getDatabasePath(DATABASE_NAME)
        val preferencesBefore = walletPreferenceInventory(applicationContext)
        val retryStartedAt = maxOf(
            System.currentTimeMillis(),
            Math.addExact(expected.journal.startedAt, 1L),
        )
        val sqlite = SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            FAIL_CLOSED_DATABASE_ERROR_HANDLER,
        )
        val authorized = sqlite.use { database ->
            database.beginTransaction()
            try {
                val before = validatedMigrationRecovery(
                    database = readWalletDatabaseInventory(
                        sqlite = database,
                        sourceVersion = CURRENT_DATABASE_VERSION,
                    ),
                    preferences = preferencesBefore,
                )
                if (before.token != expected.token) {
                    throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
                }
                val journal = before.journal
                val values = ContentValues().apply {
                    put("state", "VERIFYING")
                    put("verifiedAccountCount", 0)
                    putNull("failureCode")
                    put("startedAt", retryStartedAt)
                    putNull("completedAt")
                }
                val where = buildString {
                    append(
                        """
                        migrationId = ? and state = ? and legacyAccountCount = ?
                        and verifiedAccountCount = ? and selectedWalletId = ?
                        and integrityHash = ? and startedAt = ?
                        """.trimIndent().replace('\n', ' ')
                    )
                    append(
                        if (journal.failureCode == null) {
                            " and failureCode is null"
                        } else {
                            " and failureCode = ?"
                        }
                    )
                    append(
                        if (journal.completedAt == null) {
                            " and completedAt is null"
                        } else {
                            " and completedAt = ?"
                        }
                    )
                }
                val whereArgs = mutableListOf(
                    journal.migrationId,
                    journal.state,
                    journal.legacyAccountCount.toString(),
                    journal.verifiedAccountCount.toString(),
                    journal.selectedWalletId,
                    journal.integrityHash,
                    journal.startedAt.toString(),
                ).apply {
                    journal.failureCode?.let { add(it) }
                    journal.completedAt?.toString()?.let { add(it) }
                }.toTypedArray()
                if (
                    database.update(
                        "walletMigrationJournal",
                        values,
                        where,
                        whereArgs,
                    ) != 1
                ) {
                    throw WalletUpgradeBackupException("MIGRATION_RETRY_CAS_MISMATCH")
                }
                val preferencesAfter = walletPreferenceInventory(applicationContext)
                if (preferencesAfter != preferencesBefore) {
                    throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
                }
                val after = validatedMigrationRecovery(
                    database = readWalletDatabaseInventory(
                        sqlite = database,
                        sourceVersion = CURRENT_DATABASE_VERSION,
                    ),
                    preferences = preferencesAfter,
                )
                if (
                    after.journal.state != "VERIFYING" ||
                    after.journal.failureCode != null ||
                    after.journal.completedAt != null ||
                    after.journal.verifiedAccountCount != 0 ||
                    after.journal.startedAt != retryStartedAt
                ) {
                    throw WalletUpgradeBackupException(
                        "MIGRATION_RETRY_POSTCONDITION_MISMATCH"
                    )
                }
                database.setTransactionSuccessful()
                after
            } finally {
                database.endTransaction()
            }
        }
        migrationRecoverySnapshot = authorized
        WalletRecoveryCapabilityGate.authorizeMigrationRetry()
        blockingFailure = null
    }

    fun noteMigrationVerified() {
        migrationRecoverySnapshot = null
        blockingFailure = null
        WalletRecoveryCapabilityGate.enterNormal()
    }

    fun noteMigrationRecoveryRequired() {
        migrationRecoverySnapshot = null
        WalletRecoveryCapabilityGate.enterInspectedRecovery()
    }

    /**
     * Closes the live-process mutation boundary for an unclassified migration exception without
     * weakening an earlier preflight/backup blocker or clearing its recovery evidence.
     */
    @Synchronized
    fun noteUnexpectedMigrationFailure() {
        if (blockingFailure == null) {
            migrationRecoverySnapshot = null
            WalletRecoveryCapabilityGate.enterInspectedRecovery()
        } else {
            WalletRecoveryCapabilityGate.enterBlocked()
        }
    }

    fun requirePrepared(context: Context) {
        blockingFailure?.let { throw WalletUpgradeBackupException(it.code) }
        prepare(context).getOrElse { throw it }
    }

    /**
     * Room may be constructed by eager dependency injection so the recovery Activity can start,
     * but no DAO, worker, or singleton may actually open/migrate the database while backup
     * preparation is failed.
     */
    fun gatedOpenHelperFactory(
        delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
    ): SupportSQLiteOpenHelper.Factory = createGatedOpenHelperFactory(
        delegate = delegate,
        beforeGuardAdmissionForTest = null,
    )

    /** Deterministic seam for a gate transition after SQLite opens but before handle admission. */
    internal fun gatedOpenHelperFactoryWithGuardAdmissionForTest(
        beforeGuardAdmission: () -> Unit,
        delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
    ): SupportSQLiteOpenHelper.Factory = createGatedOpenHelperFactory(
        delegate = delegate,
        beforeGuardAdmissionForTest = beforeGuardAdmission,
    )

    private fun createGatedOpenHelperFactory(
        delegate: SupportSQLiteOpenHelper.Factory,
        beforeGuardAdmissionForTest: (() -> Unit)?,
    ): SupportSQLiteOpenHelper.Factory = SupportSQLiteOpenHelper.Factory { configuration ->
        val openHelper = delegate.create(configuration)
        object : SupportSQLiteOpenHelper by openHelper {
            private var walletWriteGuardsEnabled: Boolean? = null

            override fun close() {
                synchronized(this) {
                    try {
                        openHelper.close()
                    } finally {
                        // TEMP triggers belong to the closed SQLite connection. Never carry their
                        // cached state into a later connection opened through this same helper.
                        walletWriteGuardsEnabled = null
                    }
                }
            }

            override val readableDatabase
                get() = requireDatabaseAccess().let {
                    configured(openHelper.readableDatabase)
                }

            override val writableDatabase
                get() = requireDatabaseAccess().let {
                    configured(openHelper.writableDatabase)
                }

            private fun configured(
                database: SupportSQLiteDatabase,
            ): SupportSQLiteDatabase = synchronized(this) {
                beforeGuardAdmissionForTest?.invoke()
                val required =
                    WalletRecoveryCapabilityGate.requiresWalletWriteGuards()
                if (required) {
                    // These triggers are connection-local. Reapply idempotently on every
                    // recovery-mode handle so an internal close/reopen cannot inherit a stale
                    // Boolean and expose an unguarded wallet connection.
                    applyRecoveryWriteGuards(database, enabled = true)
                    walletWriteGuardsEnabled = true
                } else if (walletWriteGuardsEnabled != false) {
                    applyRecoveryWriteGuards(database, enabled = false)
                    walletWriteGuardsEnabled = false
                }
                database
            }
        }
    }

    /**
     * Packages only encrypted, on-device recovery material after explicit user action.
     *
     * A previously verified immutable upgrade backup is preferred. Some recovery conditions happen
     * before such a backup can be published (for example malformed preferences or an interrupted
     * staging directory), so the fail-safe fallback snapshots the still-encrypted live files
     * without opening Room. Every source file is size/hash checked before, during, and after export
     * so a concurrent write fails the export instead of producing an ambiguous archive.
     *
     * The archive never adds decrypted phrases, seeds, private keys, raw signed payloads, or
     * telemetry. The database necessarily retains public account metadata such as wallet addresses,
     * so the user must share it only with the official support channel they selected.
     */
    @Synchronized
    fun createRecoveryArchive(context: Context): Result<File> =
        createRecoveryArchiveInternal(
            context = context,
            archiveFileNameOverride = null,
        )

    /** Deterministic final-name seam for no-replace instrumentation. */
    @Synchronized
    internal fun createRecoveryArchiveWithFileNameForTest(
        context: Context,
        archiveFileName: String,
    ): Result<File> = createRecoveryArchiveInternal(
        context = context,
        archiveFileNameOverride = archiveFileName,
    )

    private fun createRecoveryArchiveInternal(
        context: Context,
        archiveFileNameOverride: String?,
    ): Result<File> = runCatching {
        val applicationContext = context.applicationContext
        val verifiedBackups = runCatching {
            verifiedBackupNamespace(
                context = applicationContext,
                stagingFailureCode = "BACKUP_NAMESPACE_INVALID",
                invalidFailureCode = "BACKUP_NAMESPACE_INVALID",
            )
        }.getOrNull()
        // A verified, append-only generation is the only ordering authority.
        // Any interrupted, malformed, or duplicate-generation namespace
        // deliberately selects the still-encrypted live-store fallback.
        val verifiedBackup = verifiedBackups
            ?.maxByOrNull(VerifiedBackupCandidate::generation)
            ?.directory
        val source = verifiedBackup?.let {
            recoveryArchiveSource(
                root = it,
                files = strictRegularFiles(it),
                archivePrefix = "encrypted-backup",
                sourceType = "verified-upgrade-backup",
            )
        } ?: run {
            val database = applicationContext.getDatabasePath(DATABASE_NAME)
            recoveryArchiveSource(
                root = File(applicationContext.applicationInfo.dataDir),
                files = knownWalletFiles(applicationContext, database)
                    .filter { it.existsNoFollow() },
                archivePrefix = "live-encrypted-storage",
                sourceType = "live-encrypted-storage",
            )
        }
        if (source.files.isEmpty()) {
            throw WalletUpgradeBackupException("RECOVERY_MATERIAL_MISSING")
        }

        val exportDirectory = File(applicationContext.cacheDir, "wallet-recovery-exports")
        if (
            exportDirectory.existsNoFollow() &&
            !exportDirectory.isDirectoryNoFollow()
        ) {
            throw WalletUpgradeBackupException("CREATE_EXPORT_DIRECTORY")
        }
        if (
            !exportDirectory.existsNoFollow()
        ) {
            createDirectoryExclusive(
                directory = exportDirectory,
                failureCode = "CREATE_EXPORT_DIRECTORY",
            )
        }
        if (!exportDirectory.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException("CREATE_EXPORT_DIRECTORY")
        }
        val archiveName = archiveFileNameOverride
            ?: "sora-wallet-recovery-${System.currentTimeMillis()}-${UUID.randomUUID()}.zip"
        if (!RECOVERY_ARCHIVE_FILE_NAME.matches(archiveName)) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_NAME_INVALID")
        }
        withPublicationLock(
            directory = exportDirectory,
            lockName = RECOVERY_PUBLICATION_LOCK,
            failureCode = "RECOVERY_EXPORT_LOCK_FAILED",
        ) {
            requireBackupCapacity(exportDirectory, source.files.map { it.file })
            val archive = File(exportDirectory, archiveName)
            val staging = File(exportDirectory, ".${archive.name}.staging")
            val readmeBytes =
                """
                SORA Wallet encrypted recovery archive.
                This file was exported after an upgrade verification failure.
                It contains app storage, including public wallet addresses and encrypted
                secret blobs. It does not add decrypted phrases, seeds, or private keys.
                Never add or send your recovery phrase.
                Share it only with the official SORA support channel you selected.
                """.trimIndent().toByteArray(Charsets.UTF_8)
            val manifestBytes = JSONObject()
                .put("formatVersion", 1)
                .put("sourceType", source.sourceType)
                .put(
                    "files",
                    JSONArray().apply {
                        source.files.forEach { file ->
                            put(
                                JSONObject()
                                    .put("name", file.archiveName)
                                    .put("size", file.size)
                                    .put("sha256", file.sha256)
                            )
                        }
                    }
                )
                .toString()
                .toByteArray(Charsets.UTF_8)
            staging.openNewRegularNoFollow(
                "RECOVERY_EXPORT_EXISTS"
            ).use { openedOutput ->
                val output = openedOutput.output
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry("README.txt"))
                    zip.write(readmeBytes)
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("recovery-manifest.json"))
                    zip.write(manifestBytes)
                    zip.closeEntry()
                    source.files.forEach { file ->
                        zip.putNextEntry(
                            ZipEntry("${source.archivePrefix}/${file.archiveName}")
                        )
                        file.copyVerifiedTo(zip)
                        zip.closeEntry()
                    }
                    source.files.forEach { it.verifyUnchanged() }
                    zip.finish()
                    zip.flush()
                    output.fd.sync()
                }
            }
            if (!staging.isRegularFileNoFollow() || staging.length() == 0L) {
                throw WalletUpgradeBackupException("RECOVERY_EXPORT_EMPTY")
            }
            fsyncDirectory(exportDirectory)
            publishRecoveryArchiveNoReplace(
                staging = staging,
                archive = archive,
                source = source,
                readmeBytes = readmeBytes,
                manifestBytes = manifestBytes,
            )
        }
    }

    private fun publishRecoveryArchiveNoReplace(
        staging: File,
        archive: File,
        source: RecoveryArchiveSource,
        readmeBytes: ByteArray,
        manifestBytes: ByteArray,
    ): File {
        val stagedFingerprint = staging.inspectRegularNoFollow(
            "RECOVERY_EXPORT_PUBLISH_FAILED"
        )
        val stagedIdentity = staging.regularInodeIdentityNoFollow(
            "RECOVERY_EXPORT_PUBLISH_FAILED"
        )
        requireRegularLinkCount(
            file = staging,
            expectedIdentity = stagedIdentity,
            expectedLinkCount = 1L,
            failureCode = "RECOVERY_EXPORT_PUBLISH_FAILED",
        )
        validateRecoveryArchive(
            archive = staging,
            source = source,
            readmeBytes = readmeBytes,
            manifestBytes = manifestBytes,
            expectedFingerprint = stagedFingerprint,
            expectedIdentity = stagedIdentity,
            expectedLinkCount = 1L,
        )
        try {
            copyRegularNoReplace(
                source = staging,
                destination = archive,
                expectedSourceIdentity = stagedIdentity,
                expectedFingerprint = stagedFingerprint,
                failureCode = "RECOVERY_EXPORT_PUBLISH_FAILED",
                destinationExistsFailureCode = "RECOVERY_EXPORT_EXISTS",
            )
        } catch (error: WalletUpgradeBackupException) {
            if (error.code == "RECOVERY_EXPORT_EXISTS") {
                removeOwnedRegularLink(
                    file = staging,
                    expectedIdentity = stagedIdentity,
                    expectedLinkCountBefore = 1L,
                    remainingLink = null,
                    expectedRemainingLinkCountAfter = null,
                    failureCode = "RECOVERY_EXPORT_STAGING_CLEANUP_FAILED",
                )
                fsyncDirectory(requireNotNull(staging.parentFile))
                throw WalletUpgradeBackupException("RECOVERY_EXPORT_EXISTS")
            }
            throw error
        }
        fsyncDirectory(requireNotNull(archive.parentFile))
        val archiveIdentity = archive.regularInodeIdentityNoFollow(
            "RECOVERY_EXPORT_PUBLISH_FAILED"
        )
        requireRegularLinkCount(
            file = staging,
            expectedIdentity = stagedIdentity,
            expectedLinkCount = 1L,
            failureCode = "RECOVERY_EXPORT_PUBLISH_FAILED",
        )
        validateRecoveryArchive(
            archive = archive,
            source = source,
            readmeBytes = readmeBytes,
            manifestBytes = manifestBytes,
            expectedFingerprint = stagedFingerprint,
            expectedIdentity = archiveIdentity,
            expectedLinkCount = 1L,
        )
        removeOwnedRegularLink(
            file = staging,
            expectedIdentity = stagedIdentity,
            expectedLinkCountBefore = 1L,
            remainingLink = null,
            expectedRemainingLinkCountAfter = null,
            failureCode = "RECOVERY_EXPORT_STAGING_CLEANUP_FAILED",
        )
        fsyncRegularFileNoFollow(
            file = archive,
            failureCode = "RECOVERY_EXPORT_PUBLISH_FAILED",
        )
        fsyncDirectory(requireNotNull(archive.parentFile))
        validateRecoveryArchive(
            archive = archive,
            source = source,
            readmeBytes = readmeBytes,
            manifestBytes = manifestBytes,
            expectedFingerprint = stagedFingerprint,
            expectedIdentity = archiveIdentity,
            expectedLinkCount = 1L,
        )
        return archive
    }

    private fun validateRecoveryArchive(
        archive: File,
        source: RecoveryArchiveSource,
        readmeBytes: ByteArray,
        manifestBytes: ByteArray,
        expectedFingerprint: FileFingerprint,
        expectedIdentity: InodeIdentity,
        expectedLinkCount: Long,
    ) {
        if (
            archive.regularLinkIdentityNoFollow(
                "RECOVERY_EXPORT_PUBLISH_FAILED"
            ) != RegularLinkIdentity(expectedIdentity, expectedLinkCount) ||
            archive.inspectRegularNoFollow(
                "RECOVERY_EXPORT_PUBLISH_FAILED"
            ) != expectedFingerprint
        ) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_PUBLISH_FAILED")
        }
        try {
            ZipFile(archive).use { zip ->
                val expectedNames = mutableSetOf(
                    "README.txt",
                    "recovery-manifest.json",
                ).apply {
                    source.files.forEach {
                        add("${source.archivePrefix}/${it.archiveName}")
                    }
                }
                val actualNames = mutableListOf<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        throw WalletUpgradeBackupException(
                            "RECOVERY_EXPORT_PUBLISH_FAILED"
                        )
                    }
                    actualNames += entry.name
                }
                if (
                    actualNames.size != expectedNames.size ||
                    actualNames.toSet() != expectedNames
                ) {
                    throw WalletUpgradeBackupException(
                        "RECOVERY_EXPORT_PUBLISH_FAILED"
                    )
                }
                zip.requireExactEntry(
                    name = "README.txt",
                    expectedSize = readmeBytes.size.toLong(),
                    expectedSha256 = readmeBytes.sha256(),
                )
                zip.requireExactEntry(
                    name = "recovery-manifest.json",
                    expectedSize = manifestBytes.size.toLong(),
                    expectedSha256 = manifestBytes.sha256(),
                )
                source.files.forEach { file ->
                    zip.requireExactEntry(
                        name = "${source.archivePrefix}/${file.archiveName}",
                        expectedSize = file.size,
                        expectedSha256 = file.sha256,
                    )
                }
            }
        } catch (error: WalletUpgradeBackupException) {
            throw error
        } catch (_: Exception) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_PUBLISH_FAILED")
        }
        if (
            archive.regularLinkIdentityNoFollow(
                "RECOVERY_EXPORT_PUBLISH_FAILED"
            ) != RegularLinkIdentity(expectedIdentity, expectedLinkCount) ||
            archive.inspectRegularNoFollow(
                "RECOVERY_EXPORT_PUBLISH_FAILED"
            ) != expectedFingerprint
        ) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_PUBLISH_FAILED")
        }
    }

    private fun ZipFile.requireExactEntry(
        name: String,
        expectedSize: Long,
        expectedSha256: String,
    ) {
        val entry = getEntry(name)
            ?: throw WalletUpgradeBackupException(
                "RECOVERY_EXPORT_PUBLISH_FAILED"
            )
        if (entry.isDirectory || entry.size != expectedSize) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_PUBLISH_FAILED")
        }
        val digest = MessageDigest.getInstance("SHA-256")
        var bytesRead = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        try {
            getInputStream(entry).use { input ->
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    bytesRead = Math.addExact(bytesRead, count.toLong())
                    if (bytesRead > expectedSize) {
                        throw WalletUpgradeBackupException(
                            "RECOVERY_EXPORT_PUBLISH_FAILED"
                        )
                    }
                    digest.update(buffer, 0, count)
                }
            }
        } finally {
            buffer.fill(0)
        }
        if (
            bytesRead != expectedSize ||
            digest.digest().toLowerHex() != expectedSha256
        ) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_PUBLISH_FAILED")
        }
    }

    private fun recoveryArchiveSource(
        root: File,
        files: List<File>,
        archivePrefix: String,
        sourceType: String,
    ): RecoveryArchiveSource {
        if (!root.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_PATH")
        }
        val rootPath = root.absoluteFile.toPath().normalize()
        val records = files.map { candidate ->
            if (!candidate.isRegularFileNoFollow()) {
                throw WalletUpgradeBackupException("RECOVERY_EXPORT_PATH")
            }
            val candidatePath = candidate.absoluteFile.toPath().normalize()
            if (!candidatePath.startsWith(rootPath)) {
                throw WalletUpgradeBackupException("RECOVERY_EXPORT_PATH")
            }
            val archiveName = rootPath.relativize(candidatePath).toString()
                .replace(File.separatorChar, '/')
            if (
                archiveName.isBlank() ||
                archiveName.startsWith("/") ||
                archiveName.split('/').any { it == ".." }
            ) {
                throw WalletUpgradeBackupException("RECOVERY_EXPORT_PATH")
            }
            val file = candidatePath.toFile()
            val fingerprint = file.inspectRegularNoFollow(
                "RECOVERY_SOURCE_CHANGED"
            )
            RecoveryArchiveFile(
                file = file,
                archiveName = archiveName,
                size = fingerprint.size,
                sha256 = fingerprint.sha256,
            )
        }.sortedBy(RecoveryArchiveFile::archiveName)
        if (
            records.size > MAX_BACKUP_FILES ||
            records.map(RecoveryArchiveFile::archiveName).toSet().size != records.size
        ) {
            throw WalletUpgradeBackupException("RECOVERY_EXPORT_INVENTORY")
        }
        return RecoveryArchiveSource(
            files = records,
            archivePrefix = archivePrefix,
            sourceType = sourceType,
        )
    }

    private fun prepareOrThrow(
        context: Context,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)?,
    ) {
        val database = context.getDatabasePath(DATABASE_NAME)
        val preferenceInventory = walletPreferenceInventory(context)
        if (!database.existsNoFollow()) {
            val retainedBackupEvidence = context.noBackupFilesDir.listFiles()
                ?.any { it.name.startsWith(BACKUP_PREFIX) }
                ?: throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
            val orphanedDatabaseSidecar = listOf(
                File("${database.path}-wal"),
                File("${database.path}-shm"),
                File("${database.path}-journal"),
            ).any { it.existsNoFollow() }
            if (
                retainedBackupEvidence ||
                orphanedDatabaseSidecar ||
                preferenceInventory.hasWalletMaterial ||
                preferenceInventory.hasWrappedWalletKeyEvidence
            ) {
                throw WalletUpgradeBackupException("ORPHANED_WALLET_STORAGE")
            }
            return
        }
        if (!database.isRegularFileNoFollow()) {
            throw WalletUpgradeBackupException("DATABASE_PATH_INVALID")
        }

        val databaseIdentity = database.regularFileIdentityNoFollow(
            "DATABASE_PATH_INVALID"
        )
        val sourceVersion = readVersion(database)
        if (sourceVersion <= 0) {
            throw WalletUpgradeBackupException("INVALID_DATABASE_VERSION")
        }
        if (sourceVersion > CURRENT_DATABASE_VERSION) {
            throw WalletUpgradeBackupException("DATABASE_DOWNGRADE_UNSUPPORTED")
        }
        val databaseInventory = readWalletDatabaseInventory(database, sourceVersion)
        if (
            database.regularFileIdentityNoFollow(
                "DATABASE_PATH_INVALID"
            ) != databaseIdentity
        ) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        val legacyWalletIds = databaseInventory.accounts
            .map(SoraAccountLocal::substrateAddress)
            .toSet()
        if (sourceVersion == CURRENT_DATABASE_VERSION) {
            val validation = runCatching {
                validateWalletStorageCoherence(databaseInventory, preferenceInventory)
            }
            if (validation.isSuccess) {
                // Some production cohorts can already have Room's current schema before this
                // release. They still need one verified immutable database/settings snapshot.
                // Match on the authoritative legacy wallet inventory instead of the SQLite
                // sidecar bytes so opening a WAL database cannot manufacture repeat backups.
                val needsCurrentSchemaSnapshot =
                    !hasVerifiedCurrentSchemaSnapshot(
                        context = context,
                        legacyWalletIds = legacyWalletIds,
                        selectedWalletId = preferenceInventory.selectedWalletId,
                    )
                if (needsCurrentSchemaSnapshot) {
                    publishVerifiedSnapshotBackup(
                        context = context,
                        database = database,
                        sourceVersion = sourceVersion,
                        databaseInventory = databaseInventory,
                        preferenceInventory = preferenceInventory,
                        legacyWalletIds = legacyWalletIds,
                        availableBackupBytesOverride = availableBackupBytesOverride,
                        beforeFinalSourceValidationForTest =
                            beforeFinalSourceValidationForTest,
                    )
                } else {
                    val currentSchemaSourceSnapshot = createSourceSnapshot(
                        context = context,
                        sources = knownWalletFiles(context, database)
                            .filter { it.existsNoFollow() },
                        legacyWalletIds = legacyWalletIds,
                        selectedWalletId = preferenceInventory.selectedWalletId,
                    )
                    beforeFinalSourceValidationForTest?.invoke()
                    requireInstalledSourceUnchanged(
                        context = context,
                        database = database,
                        sourceVersion = sourceVersion,
                        databaseInventory = databaseInventory,
                        preferenceInventory = preferenceInventory,
                        legacyWalletIds = legacyWalletIds,
                        sourceSnapshot = currentSchemaSourceSnapshot,
                    )
                }
                return
            }
            val failure = validation.exceptionOrNull()
                ?: throw WalletUpgradeBackupException("IO")
            if (
                (failure as? WalletUpgradeBackupException)?.code !=
                "WALLET_MIGRATION_RECOVERY_REQUIRED"
            ) {
                throw failure
            }

            // A previous process may already have completed Room's schema step while the
            // copy-on-write wallet namespace is still pending or interrupted. Back up that
            // current-schema database and encrypted settings before any application migration can
            // activate it. Only the exact pristine migration output may continue automatically.
            requireLegacyRecoveryPreferenceCoherence(
                database = databaseInventory,
                preferences = preferenceInventory,
            )
            publishVerifiedSnapshotBackup(
                context = context,
                database = database,
                sourceVersion = sourceVersion,
                databaseInventory = databaseInventory,
                preferenceInventory = preferenceInventory,
                legacyWalletIds = legacyWalletIds,
                availableBackupBytesOverride = availableBackupBytesOverride,
                beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
            )
            if (isPristineCurrentSchemaNamespace(databaseInventory)) return
            throw failure
        }

        validateWalletStorageCoherence(databaseInventory, preferenceInventory)
        publishVerifiedSnapshotBackup(
            context = context,
            database = database,
            sourceVersion = sourceVersion,
            databaseInventory = databaseInventory,
            preferenceInventory = preferenceInventory,
            legacyWalletIds = legacyWalletIds,
            availableBackupBytesOverride = availableBackupBytesOverride,
            beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
        )
    }

    private fun hasVerifiedCurrentSchemaSnapshot(
        context: Context,
        legacyWalletIds: Set<String>,
        selectedWalletId: String?,
    ): Boolean {
        val expectedWalletIdsHash = sha256Text(
            legacyWalletIds.sorted().joinToString(separator = "\n")
        )
        val expectedSelectionHash = selectedWalletId?.let(::sha256Text)
        val candidates = runCatching {
            verifiedBackupNamespace(
                context = context,
                stagingFailureCode = "BACKUP_NAMESPACE_INVALID",
                invalidFailureCode = "BACKUP_NAMESPACE_INVALID",
            )
        }.getOrNull() ?: return false
        return candidates.any { candidate ->
                runCatching {
                    val manifest = JSONObject(
                        File(candidate.directory, "manifest.json")
                            .readBoundedUtf8NoFollow(
                                minimumBytes = 1,
                                maximumBytes = MAX_MANIFEST_BYTES,
                                failureCode = "BACKUP_NAMESPACE_INVALID",
                            )
                    )
                    manifest.getInt("sourceDatabaseVersion") ==
                        CURRENT_DATABASE_VERSION &&
                        manifest.getInt("targetDatabaseVersion") ==
                        CURRENT_DATABASE_VERSION &&
                        manifest.getInt("legacyAccountCount") ==
                        legacyWalletIds.size &&
                        manifest.getString("legacyWalletIdsSha256") ==
                        expectedWalletIdsHash &&
                        (
                            if (expectedSelectionHash == null) {
                                manifest.isNull("selectedWalletIdSha256")
                            } else {
                                manifest.getString("selectedWalletIdSha256") ==
                                    expectedSelectionHash
                            }
                            )
                }.getOrDefault(false)
            }
    }

    private fun verifiedBackupNamespace(
        context: Context,
        stagingFailureCode: String,
        invalidFailureCode: String,
    ): List<VerifiedBackupCandidate> {
        val namespace = context.noBackupFilesDir.listFiles()
            ?.filter { it.name.startsWith(BACKUP_PREFIX) }
            ?: throw WalletUpgradeBackupException(invalidFailureCode)
        if (namespace.any { it.name.endsWith(".staging") }) {
            throw WalletUpgradeBackupException(stagingFailureCode)
        }
        if (namespace.size > MAX_PUBLISHED_BACKUPS) {
            throw WalletUpgradeBackupException(invalidFailureCode)
        }
        val candidates = namespace.map { candidate ->
            if (
                !candidate.isDirectoryNoFollow() ||
                !isCompleteAndValid(context, candidate)
            ) {
                throw WalletUpgradeBackupException(invalidFailureCode)
            }
            VerifiedBackupCandidate(
                directory = candidate,
                generation = backupGenerationFromValidatedName(candidate.name)
                    ?: throw WalletUpgradeBackupException(
                        invalidFailureCode
                    ),
            )
        }
        val generations = candidates
            .map(VerifiedBackupCandidate::generation)
            .sorted()
        if (
            generations.withIndex().any { (index, generation) ->
                generation != index.toLong() + 1L
            }
        ) {
            throw WalletUpgradeBackupException(invalidFailureCode)
        }
        return candidates
    }

    private fun publishVerifiedSnapshotBackup(
        context: Context,
        database: File,
        sourceVersion: Int,
        databaseInventory: WalletDatabaseInventory,
        preferenceInventory: WalletPreferenceInventory,
        legacyWalletIds: Set<String>,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)?,
    ) {
        withPublicationLock(
            directory = context.noBackupFilesDir,
            lockName = BACKUP_PUBLICATION_LOCK,
            failureCode = "BACKUP_PUBLICATION_LOCK_FAILED",
        ) {
            publishVerifiedSnapshotBackupUnderLock(
                context = context,
                database = database,
                sourceVersion = sourceVersion,
                databaseInventory = databaseInventory,
                preferenceInventory = preferenceInventory,
                legacyWalletIds = legacyWalletIds,
                availableBackupBytesOverride = availableBackupBytesOverride,
                beforeFinalSourceValidationForTest =
                    beforeFinalSourceValidationForTest,
            )
        }
    }

    private fun publishVerifiedSnapshotBackupUnderLock(
        context: Context,
        database: File,
        sourceVersion: Int,
        databaseInventory: WalletDatabaseInventory,
        preferenceInventory: WalletPreferenceInventory,
        legacyWalletIds: Set<String>,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)?,
    ) {
        val sources = knownWalletFiles(context, database)
            .filter { it.existsNoFollow() }
        val databasePath = database.absoluteFile.toPath().normalize()
        if (
            !database.isRegularFileNoFollow() ||
            sources.none {
                it.absoluteFile.toPath().normalize() == databasePath
            }
        ) {
            throw WalletUpgradeBackupException("DATABASE_MISSING")
        }
        val sourceSnapshot = createSourceSnapshot(
            context = context,
            sources = sources,
            legacyWalletIds = legacyWalletIds,
            selectedWalletId = preferenceInventory.selectedWalletId,
        )
        if (
            readWalletDatabaseInventory(database, sourceVersion) != databaseInventory ||
            walletPreferenceInventory(context) != preferenceInventory
        ) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        val backupParent = context.noBackupFilesDir
        val existingBackups = verifiedBackupNamespace(
            context = context,
            stagingFailureCode = "STALE_STAGING",
            invalidFailureCode = "EXISTING_BACKUP_INVALID",
        )
        if (
            existingBackups.any {
                matchesSourceSnapshot(
                    directory = it.directory,
                    snapshot = sourceSnapshot,
                    sourceVersion = sourceVersion,
                )
            }
        ) {
            beforeFinalSourceValidationForTest?.invoke()
            requireInstalledSourceUnchanged(
                context = context,
                database = database,
                sourceVersion = sourceVersion,
                databaseInventory = databaseInventory,
                preferenceInventory = preferenceInventory,
                legacyWalletIds = legacyWalletIds,
                sourceSnapshot = sourceSnapshot,
            )
            return
        }
        if (existingBackups.size >= MAX_PUBLISHED_BACKUPS) {
            throw WalletUpgradeBackupException("BACKUP_RETENTION_LIMIT")
        }
        val backupGeneration = try {
            Math.addExact(
                existingBackups.maxOfOrNull(
                    VerifiedBackupCandidate::generation
                ) ?: 0L,
                1L,
            )
        } catch (_: ArithmeticException) {
            throw WalletUpgradeBackupException("BACKUP_GENERATION_EXHAUSTED")
        }
        val destination = File(
            backupParent,
            backupDirectoryName(
                sourceVersion = sourceVersion,
                targetVersion = CURRENT_DATABASE_VERSION,
                generation = backupGeneration,
                fingerprint = sourceSnapshot.fingerprint,
            ),
        )
        if (destination.existsNoFollow()) {
            throw WalletUpgradeBackupException("EXISTING_BACKUP_INVALID")
        }
        requireBackupCapacity(
            directory = context.noBackupFilesDir,
            sources = sources,
            availableBytesOverride = availableBackupBytesOverride,
        )

        val staging = File(destination.parentFile, "${destination.name}.staging")
        if (staging.existsNoFollow()) {
            throw WalletUpgradeBackupException("STALE_STAGING")
        }
        createDirectoryExclusive(
            directory = staging,
            failureCode = "CREATE_STAGING",
        )

        try {
            val records = sourceSnapshot.files.map { sourceRecord ->
                val source = sourceRecord.source
                val destinationFile = File(staging, sourceRecord.name)
                val parent = requireNotNull(destinationFile.parentFile)
                ensureDirectoryTreeExclusive(
                    root = staging,
                    directory = parent,
                    failureCode = "CREATE_BACKUP_DIRECTORY",
                )
                copyDurably(source, destinationFile)
                if (
                    !source.isRegularFileNoFollow() ||
                    !destinationFile.isRegularFileNoFollow() ||
                    source.length() != sourceRecord.size ||
                    sourceRecord.size != destinationFile.length()
                ) {
                    throw WalletUpgradeBackupException("SIZE_MISMATCH")
                }
                val sourceHashAfterCopy = source.sha256(
                    expectedSize = sourceRecord.size
                )
                if (
                    sourceHashAfterCopy != sourceRecord.sha256 ||
                    sourceRecord.sha256 != destinationFile.sha256(
                        expectedSize = sourceRecord.size
                    )
                ) {
                    throw WalletUpgradeBackupException("HASH_MISMATCH")
                }
                BackupRecord(
                    name = sourceRecord.name,
                    size = sourceRecord.size,
                    sha256 = sourceRecord.sha256,
                )
            }
            sourceSnapshot.files.forEach { sourceRecord ->
                if (
                    !sourceRecord.source.isRegularFileNoFollow() ||
                    sourceRecord.source.length() != sourceRecord.size ||
                    sourceRecord.source.sha256(
                        expectedSize = sourceRecord.size
                    ) != sourceRecord.sha256
                ) {
                    throw WalletUpgradeBackupException("SOURCE_CHANGED")
                }
            }

            writeDurably(
                File(staging, "manifest.json"),
                JSONObject()
                    .put("formatVersion", BACKUP_FORMAT_VERSION)
                    .put("backupGeneration", backupGeneration)
                    .put("sourceDatabaseVersion", sourceVersion)
                    .put("targetDatabaseVersion", CURRENT_DATABASE_VERSION)
                    .put("sourceFingerprint", sourceSnapshot.fingerprint)
                    .put("legacyAccountCount", sourceSnapshot.legacyAccountCount)
                    .put(
                        "legacyWalletIdsSha256",
                        sourceSnapshot.legacyWalletIdsSha256,
                    )
                    .put(
                        "selectedWalletIdSha256",
                        sourceSnapshot.selectedWalletIdSha256 ?: JSONObject.NULL,
                    )
                    .put(
                        "files",
                        JSONArray().apply {
                            records.forEach {
                                put(
                                    JSONObject()
                                        .put("name", it.name)
                                        .put("size", it.size)
                                        .put("sha256", it.sha256)
                                )
                            }
                        }
                    )
                    .toString(),
            )
            writeDurably(File(staging, ".complete"), "verified")
            publishVerifiedBackupTreeNoReplace(
                context = context,
                staging = staging,
                destination = destination,
                sourceSnapshot = sourceSnapshot,
                sourceVersion = sourceVersion,
            )
            beforeFinalSourceValidationForTest?.invoke()
            requireInstalledSourceUnchanged(
                context = context,
                database = database,
                sourceVersion = sourceVersion,
                databaseInventory = databaseInventory,
                preferenceInventory = preferenceInventory,
                legacyWalletIds = legacyWalletIds,
                sourceSnapshot = sourceSnapshot,
            )
        } catch (error: Throwable) {
            // Preserve partial output as evidence/recovery material. Startup remains fail-closed
            // until it is inspected or exported by an explicit recovery flow.
            throw error
        }
    }

    private fun publishVerifiedBackupTreeNoReplace(
        context: Context,
        staging: File,
        destination: File,
        sourceSnapshot: BackupSourceSnapshot,
        sourceVersion: Int,
    ) {
        fsyncDirectoryTreeBottomUp(staging)
        val stagedTree = captureExactBackupTree(staging)
        requireBackupTreeLinkCounts(
            root = staging,
            expected = stagedTree,
            expectedLinkCount = 1L,
            failureCode = "PUBLISH_BACKUP_TREE_INVALID",
        )
        val stagedNames = stagedTree.files.map(BackupTreeFile::relativeName).toSet()
        if (
            REQUIRED_DATABASE_BACKUP !in stagedNames ||
            "manifest.json" !in stagedNames ||
            ".complete" !in stagedNames
        ) {
            throw WalletUpgradeBackupException("PUBLISH_BACKUP_TREE_INVALID")
        }
        createDirectoryExclusive(
            directory = destination,
            failureCode = "PUBLISH_BACKUP_DESTINATION_EXISTS",
        )
        fsyncDirectory(requireNotNull(destination.parentFile))

        stagedTree.directories
            .asSequence()
            .filter { it.relativeName.isNotEmpty() }
            .sortedWith(
                compareBy<BackupTreeDirectory> { it.relativeName.pathDepth() }
                    .thenBy(BackupTreeDirectory::relativeName)
            )
            .forEach { directory ->
                createDirectoryExclusive(
                    directory = File(destination, directory.relativeName),
                    failureCode = "PUBLISH_BACKUP_DIRECTORY",
                )
            }

        val completion = stagedTree.files.single {
            it.relativeName == ".complete"
        }
        stagedTree.files
            .filter { it.relativeName != ".complete" }
            .forEach { file ->
                copyRegularNoReplace(
                    source = File(staging, file.relativeName),
                    destination = File(destination, file.relativeName),
                    expectedSourceIdentity = file.identity,
                    expectedFingerprint = file.fingerprint,
                    failureCode = "PUBLISH_BACKUP_FILE",
                )
            }
        fsyncDirectoryTreeBottomUp(destination)
        requireExactCopiedBackupTree(
            staged = stagedTree.copy(
                files = stagedTree.files.filter {
                    it.relativeName != ".complete"
                }
            ),
            published = captureExactBackupTree(destination),
            failureCode = "PUBLISH_BACKUP_TREE_MISMATCH",
        )
        requireRegularLinkCount(
            file = File(staging, completion.relativeName),
            expectedIdentity = completion.identity,
            expectedLinkCount = 1L,
            failureCode = "PUBLISH_BACKUP_TREE_MISMATCH",
        )

        copyRegularNoReplace(
            source = File(staging, completion.relativeName),
            destination = File(destination, completion.relativeName),
            expectedSourceIdentity = completion.identity,
            expectedFingerprint = completion.fingerprint,
            failureCode = "PUBLISH_BACKUP_COMPLETION",
        )
        fsyncDirectoryTreeBottomUp(destination)
        fsyncDirectory(requireNotNull(destination.parentFile))
        requireExactCopiedBackupTree(
            staged = stagedTree,
            published = captureExactBackupTree(destination),
            failureCode = "PUBLISH_BACKUP_TREE_MISMATCH",
        )
        requireBackupTreeLinkCounts(
            root = staging,
            expected = stagedTree,
            expectedLinkCount = 1L,
            failureCode = "PUBLISH_BACKUP_TREE_MISMATCH",
        )
        val publishedTree = captureExactBackupTree(destination)
        requireBackupTreeLinkCounts(
            root = destination,
            expected = publishedTree,
            expectedLinkCount = 1L,
            failureCode = "PUBLISH_BACKUP_TREE_MISMATCH",
        )
        if (
            !isCompleteAndValid(
                context = context,
                directory = destination,
                expectedRegularLinkCount = 1L,
            ) ||
            !matchesSourceSnapshot(
                directory = destination,
                snapshot = sourceSnapshot,
                sourceVersion = sourceVersion,
            )
        ) {
            throw WalletUpgradeBackupException("POST_PUBLISH_VERIFICATION")
        }

        removeExactOwnedBackupTree(
            root = staging,
            expected = stagedTree,
        )
        fsyncBackupTreeFiles(destination)
        fsyncDirectoryTreeBottomUp(destination)
        fsyncDirectory(requireNotNull(destination.parentFile))
        val publishedTreeAfterCleanup = captureExactBackupTree(destination)
        requireExactCopiedBackupTree(
            staged = stagedTree,
            published = publishedTreeAfterCleanup,
            failureCode = "POST_CLEANUP_BACKUP_TREE_MISMATCH",
        )
        if (publishedTreeAfterCleanup != publishedTree) {
            throw WalletUpgradeBackupException(
                "POST_CLEANUP_BACKUP_TREE_MISMATCH"
            )
        }
        requireBackupTreeLinkCounts(
            root = destination,
            expected = publishedTree,
            expectedLinkCount = 1L,
            failureCode = "POST_CLEANUP_BACKUP_TREE_MISMATCH",
        )
        if (
            !isCompleteAndValid(
                context = context,
                directory = destination,
                expectedRegularLinkCount = 1L,
            ) ||
            !matchesSourceSnapshot(
                directory = destination,
                snapshot = sourceSnapshot,
                sourceVersion = sourceVersion,
            )
        ) {
            throw WalletUpgradeBackupException("POST_CLEANUP_BACKUP_VERIFICATION")
        }
    }

    private fun requireInstalledSourceUnchanged(
        context: Context,
        database: File,
        sourceVersion: Int,
        databaseInventory: WalletDatabaseInventory,
        preferenceInventory: WalletPreferenceInventory,
        legacyWalletIds: Set<String>,
        sourceSnapshot: BackupSourceSnapshot,
    ) {
        val inventoryBefore = readWalletDatabaseInventory(database, sourceVersion)
        val preferencesBefore = walletPreferenceInventory(context)
        val snapshotBefore = createSourceSnapshot(
            context = context,
            sources = knownWalletFiles(context, database)
                .filter { it.existsNoFollow() },
            legacyWalletIds = legacyWalletIds,
            selectedWalletId = preferenceInventory.selectedWalletId,
        )
        val inventoryAfter = readWalletDatabaseInventory(database, sourceVersion)
        val preferencesAfter = walletPreferenceInventory(context)
        val snapshotAfter = createSourceSnapshot(
            context = context,
            sources = knownWalletFiles(context, database)
                .filter { it.existsNoFollow() },
            legacyWalletIds = legacyWalletIds,
            selectedWalletId = preferenceInventory.selectedWalletId,
        )
        if (
            inventoryBefore != databaseInventory ||
            inventoryAfter != databaseInventory ||
            preferencesBefore != preferenceInventory ||
            preferencesAfter != preferenceInventory ||
            snapshotBefore != sourceSnapshot ||
            snapshotAfter != sourceSnapshot
        ) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
    }

    private fun captureExactBackupTree(root: File): BackupTreeSnapshot {
        val first = captureBackupTreeOnce(root)
        val confirmed = captureBackupTreeOnce(root)
        if (first != confirmed) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        return first
    }

    private fun captureBackupTreeOnce(root: File): BackupTreeSnapshot {
        val rootPath = root.absoluteFile.toPath().normalize()
        val regularFiles = strictRegularFiles(root)
        val directories = mutableMapOf(rootPath to root)
        val files = regularFiles.map { file ->
            var parent = requireNotNull(file.parentFile)
            while (true) {
                val parentPath = parent.absoluteFile.toPath().normalize()
                if (!parentPath.startsWith(rootPath)) {
                    throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
                }
                directories[parentPath] = parent
                if (parentPath == rootPath) break
                parent = requireNotNull(parent.parentFile)
            }
            BackupTreeFile(
                relativeName = file.relativeBackupName(root),
                identity = file.regularInodeIdentityNoFollow(
                    "BACKUP_NAMESPACE_INVALID"
                ),
                fingerprint = file.inspectRegularNoFollow(
                    "BACKUP_NAMESPACE_INVALID"
                ),
            )
        }.sortedBy(BackupTreeFile::relativeName)
        val directoryRecords = directories.values.map { directory ->
            val directoryPath = directory.absoluteFile.toPath().normalize()
            BackupTreeDirectory(
                relativeName = if (directoryPath == rootPath) {
                    ""
                } else {
                    directory.relativeBackupName(root)
                },
                identity = directory.directoryInodeIdentityNoFollow(
                    "BACKUP_NAMESPACE_INVALID"
                ),
            )
        }.sortedBy(BackupTreeDirectory::relativeName)
        return BackupTreeSnapshot(
            files = files,
            directories = directoryRecords,
        )
    }

    private fun File.relativeBackupName(root: File): String {
        val rootPath = root.absoluteFile.toPath().normalize()
        val filePath = absoluteFile.toPath().normalize()
        if (!filePath.startsWith(rootPath) || filePath == rootPath) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        val relative = rootPath.relativize(filePath).toString()
            .replace(File.separatorChar, '/')
        if (
            relative.isBlank() ||
            relative.startsWith('/') ||
            relative.split('/').any { it.isBlank() || it == ".." }
        ) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        return relative
    }

    private fun String.pathDepth(): Int = count { it == '/' }

    private fun createDirectoryExclusive(
        directory: File,
        failureCode: String,
    ) {
        try {
            Os.mkdir(directory.absolutePath, 0x1C0)
        } catch (_: ErrnoException) {
            throw WalletUpgradeBackupException(failureCode)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (!directory.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException(failureCode)
        }
    }

    private fun ensureDirectoryTreeExclusive(
        root: File,
        directory: File,
        failureCode: String,
    ) {
        if (!root.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException(failureCode)
        }
        val rootPath = root.absoluteFile.toPath().normalize()
        val directoryPath = directory.absoluteFile.toPath().normalize()
        if (!directoryPath.startsWith(rootPath)) {
            throw WalletUpgradeBackupException(failureCode)
        }
        val relative = rootPath.relativize(directoryPath).toString()
            .replace(File.separatorChar, '/')
        var current = root
        relative.split('/')
            .filter(String::isNotEmpty)
            .forEach { component ->
                if (component == "." || component == "..") {
                    throw WalletUpgradeBackupException(failureCode)
                }
                val child = File(current, component)
                if (child.existsNoFollow()) {
                    if (!child.isDirectoryNoFollow()) {
                        throw WalletUpgradeBackupException(failureCode)
                    }
                } else {
                    createDirectoryExclusive(child, failureCode)
                }
                current = child
            }
        if (current.absoluteFile.toPath().normalize() != directoryPath) {
            throw WalletUpgradeBackupException(failureCode)
        }
    }

    private fun copyRegularNoReplace(
        source: File,
        destination: File,
        expectedSourceIdentity: InodeIdentity,
        expectedFingerprint: FileFingerprint,
        failureCode: String,
        destinationExistsFailureCode: String? = null,
    ) {
        requireRegularLinkCount(
            file = source,
            expectedIdentity = expectedSourceIdentity,
            expectedLinkCount = 1L,
            failureCode = failureCode,
        )
        if (source.inspectRegularNoFollow(failureCode) != expectedFingerprint) {
            throw WalletUpgradeBackupException(failureCode)
        }
        var openedDestinationIdentity: InodeIdentity? = null
        source.openRegularNoFollow(failureCode).use { openedSource ->
            val openedSourceStat = Os.fstat(openedSource.descriptor)
            if (
                !OsConstants.S_ISREG(openedSourceStat.st_mode) ||
                InodeIdentity(
                    device = openedSourceStat.st_dev,
                    inode = openedSourceStat.st_ino,
                    size = openedSourceStat.st_size,
                ) != expectedSourceIdentity ||
                openedSource.size != expectedFingerprint.size
            ) {
                throw WalletUpgradeBackupException(failureCode)
            }
            destination.openNewRegularNoFollow(
                failureCode = failureCode,
                existsFailureCode = destinationExistsFailureCode,
            ).use { openedDestination ->
                val input = openedSource.input
                val output = openedDestination.output
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                try {
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        copied = Math.addExact(copied, count.toLong())
                    }
                    output.flush()
                    output.fd.sync()
                    val sourceStatAfterCopy = Os.fstat(input.fd)
                    val destinationStatAfterCopy = Os.fstat(output.fd)
                    openedDestinationIdentity = InodeIdentity(
                        device = destinationStatAfterCopy.st_dev,
                        inode = destinationStatAfterCopy.st_ino,
                        size = destinationStatAfterCopy.st_size,
                    )
                    if (
                        copied != expectedFingerprint.size ||
                        digest.digest().toLowerHex() != expectedFingerprint.sha256 ||
                        !OsConstants.S_ISREG(sourceStatAfterCopy.st_mode) ||
                        InodeIdentity(
                            device = sourceStatAfterCopy.st_dev,
                            inode = sourceStatAfterCopy.st_ino,
                            size = sourceStatAfterCopy.st_size,
                        ) != expectedSourceIdentity ||
                        !OsConstants.S_ISREG(destinationStatAfterCopy.st_mode) ||
                        destinationStatAfterCopy.st_nlink != 1L
                    ) {
                        throw WalletUpgradeBackupException(failureCode)
                    }
                } finally {
                    buffer.fill(0)
                }
            }
        }
        requireRegularLinkCount(
            file = source,
            expectedIdentity = expectedSourceIdentity,
            expectedLinkCount = 1L,
            failureCode = failureCode,
        )
        val destinationIdentity = destination.regularInodeIdentityNoFollow(failureCode)
        if (
            destinationIdentity != openedDestinationIdentity ||
            destinationIdentity == expectedSourceIdentity ||
            source.inspectRegularNoFollow(failureCode) != expectedFingerprint ||
            destination.inspectRegularNoFollow(failureCode) != expectedFingerprint
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
        requireRegularLinkCount(
            file = destination,
            expectedIdentity = destinationIdentity,
            expectedLinkCount = 1L,
            failureCode = failureCode,
        )
    }

    private fun requireRegularLinkCount(
        file: File,
        expectedIdentity: InodeIdentity,
        expectedLinkCount: Long,
        failureCode: String,
    ) {
        val link = file.regularLinkIdentityNoFollow(failureCode)
        if (
            link.inode != expectedIdentity ||
            link.linkCount != expectedLinkCount
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
    }

    private fun requireExactCopiedBackupTree(
        staged: BackupTreeSnapshot,
        published: BackupTreeSnapshot,
        failureCode: String,
    ) {
        if (
            staged.files.map { it.relativeName to it.fingerprint } !=
            published.files.map { it.relativeName to it.fingerprint } ||
            staged.directories.map(BackupTreeDirectory::relativeName) !=
            published.directories.map(BackupTreeDirectory::relativeName) ||
            staged.files.zip(published.files).any { (source, copy) ->
                source.identity == copy.identity
            }
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
    }

    private fun requireBackupTreeLinkCounts(
        root: File,
        expected: BackupTreeSnapshot,
        expectedLinkCount: Long,
        failureCode: String,
    ) {
        expected.files.forEach { file ->
            requireRegularLinkCount(
                file = File(root, file.relativeName),
                expectedIdentity = file.identity,
                expectedLinkCount = expectedLinkCount,
                failureCode = failureCode,
            )
        }
    }

    private fun fsyncDirectoryTreeBottomUp(root: File) {
        captureExactBackupTree(root).directories
            .sortedWith(
                compareByDescending<BackupTreeDirectory> {
                    it.relativeName.pathDepth()
                }.thenByDescending(BackupTreeDirectory::relativeName)
            )
            .forEach { directory ->
                fsyncDirectory(
                    if (directory.relativeName.isEmpty()) {
                        root
                    } else {
                        File(root, directory.relativeName)
                    }
                )
            }
    }

    private fun fsyncBackupTreeFiles(root: File) {
        captureExactBackupTree(root).files.forEach { file ->
            fsyncRegularFileNoFollow(
                file = File(root, file.relativeName),
                failureCode = "BACKUP_NAMESPACE_INVALID",
            )
        }
    }

    private fun fsyncRegularFileNoFollow(
        file: File,
        failureCode: String,
    ) {
        val descriptor = try {
            Os.open(
                file.absolutePath,
                OsConstants.O_RDWR or OsConstants.O_NOFOLLOW,
                0,
            )
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        var failure: Throwable? = null
        try {
            if (!OsConstants.S_ISREG(Os.fstat(descriptor).st_mode)) {
                throw WalletUpgradeBackupException(failureCode)
            }
            Os.fsync(descriptor)
        } catch (error: WalletUpgradeBackupException) {
            failure = error
            throw error
        } catch (_: Exception) {
            val wrapped = WalletUpgradeBackupException(failureCode)
            failure = wrapped
            throw wrapped
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            try {
                Os.close(descriptor)
            } catch (closeError: Throwable) {
                val firstFailure = failure
                if (firstFailure == null) {
                    throw WalletUpgradeBackupException(failureCode)
                } else {
                    firstFailure.addSuppressed(closeError)
                }
            }
        }
    }

    private fun removeExactOwnedBackupTree(
        root: File,
        expected: BackupTreeSnapshot,
    ) {
        if (captureExactBackupTree(root) != expected) {
            throw WalletUpgradeBackupException("BACKUP_STAGING_CHANGED")
        }
        requireBackupTreeLinkCounts(
            root = root,
            expected = expected,
            expectedLinkCount = 1L,
            failureCode = "BACKUP_STAGING_CHANGED",
        )
        expected.files
            .sortedWith(
                compareByDescending<BackupTreeFile> {
                    it.relativeName.pathDepth()
                }.thenByDescending(BackupTreeFile::relativeName)
            )
            .forEach { file ->
                removeOwnedRegularLink(
                    file = File(root, file.relativeName),
                    expectedIdentity = file.identity,
                    expectedLinkCountBefore = 1L,
                    remainingLink = null,
                    expectedRemainingLinkCountAfter = null,
                    failureCode = "BACKUP_STAGING_CLEANUP_FAILED",
                )
            }
        expected.directories
            .sortedWith(
                compareByDescending<BackupTreeDirectory> {
                    it.relativeName.pathDepth()
                }.thenByDescending(BackupTreeDirectory::relativeName)
            )
            .forEach { directory ->
                removeOwnedEmptyDirectory(
                    directory = if (directory.relativeName.isEmpty()) {
                        root
                    } else {
                        File(root, directory.relativeName)
                    },
                    expectedIdentity = directory.identity,
                    failureCode = "BACKUP_STAGING_CLEANUP_FAILED",
                )
            }
    }

    private fun removeExactValidationTree(
        directory: File,
        expectedRootIdentity: DirectoryInodeIdentity,
        expectedParentIdentity: DirectoryInodeIdentity,
    ) {
        val parent = directory.parentFile
            ?: throw WalletUpgradeBackupException("BACKUP_VALIDATION_CLEANUP_FAILED")
        if (
            parent.directoryInodeIdentityNoFollow(
                "BACKUP_VALIDATION_CLEANUP_FAILED"
            ) != expectedParentIdentity
        ) {
            throw WalletUpgradeBackupException("BACKUP_VALIDATION_CLEANUP_FAILED")
        }
        val expected = captureExactBackupTree(directory)
        val rootRecord = expected.directories.singleOrNull {
            it.relativeName.isEmpty()
        } ?: throw WalletUpgradeBackupException("BACKUP_VALIDATION_CLEANUP_FAILED")
        val allowedFiles = setOf(
            DATABASE_NAME,
            "$DATABASE_NAME-wal",
            "$DATABASE_NAME-shm",
            "$DATABASE_NAME-journal",
        )
        if (
            rootRecord.identity != expectedRootIdentity ||
            expected.directories.size != 1 ||
            expected.files.any { it.relativeName !in allowedFiles }
        ) {
            throw WalletUpgradeBackupException("BACKUP_VALIDATION_CLEANUP_FAILED")
        }
        requireBackupTreeLinkCounts(
            root = directory,
            expected = expected,
            expectedLinkCount = 1L,
            failureCode = "BACKUP_VALIDATION_CLEANUP_FAILED",
        )
        expected.files.forEach { file ->
            removeOwnedRegularLink(
                file = File(directory, file.relativeName),
                expectedIdentity = file.identity,
                expectedLinkCountBefore = 1L,
                remainingLink = null,
                expectedRemainingLinkCountAfter = null,
                failureCode = "BACKUP_VALIDATION_CLEANUP_FAILED",
            )
        }
        removeOwnedEmptyDirectory(
            directory = directory,
            expectedIdentity = expectedRootIdentity,
            failureCode = "BACKUP_VALIDATION_CLEANUP_FAILED",
        )
        if (
            parent.directoryInodeIdentityNoFollow(
                "BACKUP_VALIDATION_CLEANUP_FAILED"
            ) != expectedParentIdentity
        ) {
            throw WalletUpgradeBackupException("BACKUP_VALIDATION_CLEANUP_FAILED")
        }
        fsyncDirectory(parent)
        if (
            parent.directoryInodeIdentityNoFollow(
                "BACKUP_VALIDATION_CLEANUP_FAILED"
            ) != expectedParentIdentity
        ) {
            throw WalletUpgradeBackupException("BACKUP_VALIDATION_CLEANUP_FAILED")
        }
    }

    private fun removeOwnedRegularLink(
        file: File,
        expectedIdentity: InodeIdentity,
        expectedLinkCountBefore: Long,
        remainingLink: File?,
        expectedRemainingLinkCountAfter: Long?,
        failureCode: String,
    ) {
        if ((remainingLink == null) != (expectedRemainingLinkCountAfter == null)) {
            throw WalletUpgradeBackupException(failureCode)
        }
        val parent = file.parentFile
            ?: throw WalletUpgradeBackupException(failureCode)
        // Android's API-26 public Os surface does not expose descriptor-relative unlinkat/rmdirat.
        // Publication locks where held and exclusive per-operation namespaces keep cooperating
        // same-UID app writers disjoint. Bracket every removal with exact parent and leaf dev/inode
        // checks and preserve all evidence on a mismatch. This detects but does not claim atomic
        // safety against a hostile same-UID process replacing an intermediate component in the
        // final syscall window.
        val parentIdentity = parent.directoryInodeIdentityNoFollow(failureCode)
        val remainingParent = remainingLink?.parentFile
        val remainingParentIdentity = remainingParent?.directoryInodeIdentityNoFollow(
            failureCode
        )
        fun requireOwnedPathImmediatelyBeforeUnlink() {
            if (
                parent.directoryInodeIdentityNoFollow(failureCode) != parentIdentity ||
                file.regularLinkIdentityNoFollow(failureCode) !=
                RegularLinkIdentity(expectedIdentity, expectedLinkCountBefore)
            ) {
                throw WalletUpgradeBackupException(failureCode)
            }
            remainingLink?.let { retained ->
                if (
                    remainingParent?.directoryInodeIdentityNoFollow(failureCode) !=
                    remainingParentIdentity ||
                    retained.regularLinkIdentityNoFollow(failureCode) !=
                    RegularLinkIdentity(expectedIdentity, expectedLinkCountBefore)
                ) {
                    throw WalletUpgradeBackupException(failureCode)
                }
            }
        }
        requireOwnedPathImmediatelyBeforeUnlink()
        requireOwnedPathImmediatelyBeforeUnlink()
        try {
            Os.remove(file.absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (
            parent.directoryInodeIdentityNoFollow(failureCode) != parentIdentity ||
            file.existsNoFollow()
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
        remainingLink?.let { retained ->
            if (
                remainingParent?.directoryInodeIdentityNoFollow(failureCode) !=
                remainingParentIdentity ||
                retained.regularLinkIdentityNoFollow(failureCode) !=
                RegularLinkIdentity(
                    expectedIdentity,
                    requireNotNull(expectedRemainingLinkCountAfter),
                )
            ) {
                throw WalletUpgradeBackupException(failureCode)
            }
        }
    }

    private fun removeOwnedEmptyDirectory(
        directory: File,
        expectedIdentity: DirectoryInodeIdentity,
        failureCode: String,
    ) {
        val parent = directory.parentFile
            ?: throw WalletUpgradeBackupException(failureCode)
        val parentIdentity = parent.directoryInodeIdentityNoFollow(failureCode)
        fun requireOwnedEmptyDirectoryImmediatelyBeforeRemoval() {
            if (
                parent.directoryInodeIdentityNoFollow(failureCode) != parentIdentity ||
                directory.directoryInodeIdentityNoFollow(failureCode) != expectedIdentity ||
                directory.listFiles()?.isNotEmpty() != false ||
                directory.directoryInodeIdentityNoFollow(failureCode) != expectedIdentity
            ) {
                throw WalletUpgradeBackupException(failureCode)
            }
        }
        requireOwnedEmptyDirectoryImmediatelyBeforeRemoval()
        requireOwnedEmptyDirectoryImmediatelyBeforeRemoval()
        try {
            Os.remove(directory.absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (
            parent.directoryInodeIdentityNoFollow(failureCode) != parentIdentity ||
            directory.existsNoFollow()
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
    }

    private fun requireDatabaseAccess() {
        blockingFailure?.let { throw WalletUpgradeBackupException(it.code) }
        try {
            WalletRecoveryCapabilityGate.requireDatabaseAccessAllowed()
        } catch (_: IllegalStateException) {
            throw WalletUpgradeBackupException("WALLET_DATABASE_RECOVERY_BLOCKED")
        }
    }

    /**
     * TEMP triggers are connection-local and protect the authoritative wallet tables even if a
     * future feature calls a DAO without first consulting the process gate. Non-wallet cache and
     * history writes remain available so legacy read-only mode can render the existing app.
     */
    private fun applyRecoveryWriteGuards(
        database: SupportSQLiteDatabase,
        enabled: Boolean,
    ) {
        val tables = listOf(
            "accounts",
            "walletIdentities",
            "networkAccounts",
            "walletMigrationJournal",
            "pendingNetworkTransactions",
            "sora2PendingSubmissions",
            "walletDeletionOperations",
            "walletDeletionTargets",
        )
        val operations = listOf(
            "insert" to "INSERT",
            "update" to "UPDATE",
            "delete" to "DELETE",
        )
        tables.forEach { table ->
            operations.forEach { (operationId, operationSql) ->
                val trigger = "wallet_recovery_guard_${table}_$operationId"
                if (enabled) {
                    database.execSQL(
                        """
                        CREATE TEMP TRIGGER IF NOT EXISTS `$trigger`
                        BEFORE $operationSql ON main.`$table`
                        BEGIN
                            SELECT RAISE(ABORT, 'WALLET_RECOVERY_READ_ONLY');
                        END
                        """.trimIndent()
                    )
                } else {
                    database.execSQL("DROP TRIGGER IF EXISTS temp.`$trigger`")
                }
            }
        }
    }

    private fun knownWalletFiles(context: Context, database: File): List<File> {
        val dataDir = File(context.applicationInfo.dataDir)
        return listOf(
            database,
            File("${database.path}-wal"),
            File("${database.path}-shm"),
            File("${database.path}-journal"),
            File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb"),
            File(dataDir, "shared_prefs/sora_prefs.xml"),
            File(dataDir, "shared_prefs/sora_prefs.xml.bak"),
            File(dataDir, "shared_prefs/key_alias.xml"),
            File(dataDir, "shared_prefs/key_alias.xml.bak"),
        )
    }

    private fun strictRegularFiles(root: File): List<File> {
        if (!root.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        val rootPath = root.absoluteFile.toPath().normalize()
        val pending = ArrayDeque<File>()
        val files = mutableListOf<File>()
        val directories = mutableListOf<File>()
        var entryCount = 0
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            val identityBefore = directory.directoryIdentityNoFollow(
                "BACKUP_NAMESPACE_INVALID"
            )
            val children = directory.listFiles()
                ?.sortedBy { it.name }
                ?: throw WalletUpgradeBackupException(
                    "BACKUP_NAMESPACE_INVALID"
                )
            children.forEach { child ->
                entryCount = Math.addExact(entryCount, 1)
                if (entryCount > MAX_BACKUP_NAMESPACE_ENTRIES) {
                    throw WalletUpgradeBackupException(
                        "BACKUP_NAMESPACE_INVALID"
                    )
                }
                val childIsDirectory = child.isDirectoryNoFollow()
                val childIsRegularFile = child.isRegularFileNoFollow()
                if (!childIsDirectory && !childIsRegularFile) {
                    throw WalletUpgradeBackupException(
                        "BACKUP_NAMESPACE_INVALID"
                    )
                }
                val childPath = child.absoluteFile.toPath().normalize()
                if (!childPath.startsWith(rootPath)) {
                    throw WalletUpgradeBackupException(
                        "BACKUP_NAMESPACE_INVALID"
                    )
                }
                when {
                    childIsDirectory -> {
                        directories += child
                        pending.addLast(child)
                    }
                    childIsRegularFile ->
                        files += child
                    else ->
                        throw WalletUpgradeBackupException(
                            "BACKUP_NAMESPACE_INVALID"
                        )
                }
            }
            val confirmedChildNames = directory.listFiles()
                ?.map(File::getName)
                ?.sorted()
                ?: throw WalletUpgradeBackupException(
                    "BACKUP_NAMESPACE_INVALID"
                )
            if (
                confirmedChildNames != children.map(File::getName) ||
                directory.directoryIdentityNoFollow(
                    "BACKUP_NAMESPACE_INVALID"
                ) != identityBefore
            ) {
                throw WalletUpgradeBackupException(
                    "BACKUP_NAMESPACE_INVALID"
                )
            }
        }
        // Backup creation never materializes an empty directory. Reject one so
        // unexpected namespace evidence cannot disappear from the exact file
        // inventory comparison performed during validation.
        if (
            directories.any { directory ->
                val directoryPath =
                    directory.absoluteFile.toPath().normalize()
                files.none {
                    it.absoluteFile.toPath().normalize()
                        .startsWith(directoryPath)
                }
            }
        ) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        return files
    }

    private fun File.existsNoFollow(): Boolean =
        Files.exists(toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun File.isDirectoryNoFollow(): Boolean =
        Files.isDirectory(toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun File.isRegularFileNoFollow(): Boolean =
        Files.isRegularFile(toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun File.directoryIdentityNoFollow(
        failureCode: String,
    ): DirectoryIdentity {
        val stat = try {
            Os.lstat(absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (!OsConstants.S_ISDIR(stat.st_mode)) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return DirectoryIdentity(
            device = stat.st_dev,
            inode = stat.st_ino,
            linkCount = stat.st_nlink,
            size = stat.st_size,
            modifiedAtSeconds = stat.st_mtime,
            changedAtSeconds = stat.st_ctime,
        )
    }

    private fun File.directoryInodeIdentityNoFollow(
        failureCode: String,
    ): DirectoryInodeIdentity {
        val stat = try {
            Os.lstat(absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (!OsConstants.S_ISDIR(stat.st_mode)) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return DirectoryInodeIdentity(
            device = stat.st_dev,
            inode = stat.st_ino,
        )
    }

    private fun File.regularFileIdentityNoFollow(
        failureCode: String,
    ): FileIdentity {
        val stat = try {
            Os.lstat(absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (
            !OsConstants.S_ISREG(stat.st_mode) ||
            stat.st_size < 0L
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return FileIdentity(
            device = stat.st_dev,
            inode = stat.st_ino,
            size = stat.st_size,
            modifiedAtSeconds = stat.st_mtime,
            changedAtSeconds = stat.st_ctime,
        )
    }

    private fun File.regularInodeIdentityNoFollow(
        failureCode: String,
    ): InodeIdentity {
        val stat = try {
            Os.lstat(absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_size < 0L) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return InodeIdentity(
            device = stat.st_dev,
            inode = stat.st_ino,
            size = stat.st_size,
        )
    }

    /**
     * Final backup and recovery files must remain single-linked. Track dev/inode/size separately
     * from mutable metadata so every validation and owned cleanup can reject an unexpected alias;
     * ctime is not carried across phases because unlinking an owned staging file changes it.
     */
    private fun File.regularLinkIdentityNoFollow(
        failureCode: String,
    ): RegularLinkIdentity {
        val stat = try {
            Os.lstat(absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        if (
            !OsConstants.S_ISREG(stat.st_mode) ||
            stat.st_size < 0L ||
            stat.st_nlink <= 0L
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return RegularLinkIdentity(
            inode = InodeIdentity(
                device = stat.st_dev,
                inode = stat.st_ino,
                size = stat.st_size,
            ),
            linkCount = stat.st_nlink,
        )
    }

    private fun File.openPublicationLock(
        failureCode: String,
    ): OpenedPublicationLock {
        // The rendezvous inode is intentionally persistent, so O_EXCL is not appropriate here.
        // O_NOFOLLOW prevents a symlink lock, and withPublicationLock takes an exclusive advisory
        // lock before requiring the still-named path to be the exact opened dev/inode/size.
        val descriptor = try {
            Os.open(
                absolutePath,
                OsConstants.O_RDWR or
                    OsConstants.O_CREAT or
                    OsConstants.O_NOFOLLOW,
                0x180,
            )
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return try {
            val stat = Os.fstat(descriptor)
            if (!OsConstants.S_ISREG(stat.st_mode)) {
                throw WalletUpgradeBackupException(failureCode)
            }
            OpenedPublicationLock(
                descriptor = descriptor,
                output = FileOutputStream(descriptor),
            )
        } catch (error: Throwable) {
            runCatching { Os.close(descriptor) }
            when (error) {
                is WalletUpgradeBackupException -> throw error
                is Exception -> throw WalletUpgradeBackupException(failureCode)
                else -> throw error
            }
        }
    }

    private fun <T> withPublicationLock(
        directory: File,
        lockName: String,
        failureCode: String,
        block: () -> T,
    ): T {
        if (!directory.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException(failureCode)
        }
        val lockFile = File(directory, lockName)
        return lockFile.openPublicationLock(failureCode).use { opened ->
            val lock = try {
                opened.channel.lock()
            } catch (_: Exception) {
                throw WalletUpgradeBackupException(failureCode)
            }
            var failure: Throwable? = null
            try {
                val stat = Os.fstat(opened.descriptor)
                val pathIdentity = lockFile.regularInodeIdentityNoFollow(
                    failureCode
                )
                if (
                    pathIdentity.device != stat.st_dev ||
                    pathIdentity.inode != stat.st_ino ||
                    pathIdentity.size != stat.st_size
                ) {
                    throw WalletUpgradeBackupException(failureCode)
                }
                block()
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                try {
                    lock.release()
                } catch (releaseError: Throwable) {
                    val firstFailure = failure
                    if (firstFailure == null) {
                        throw releaseError
                    } else {
                        firstFailure.addSuppressed(releaseError)
                    }
                }
            }
        }
    }

    private fun File.openRegularNoFollow(
        failureCode: String,
    ): OpenedRegularFile {
        val descriptor = try {
            Os.open(
                absolutePath,
                OsConstants.O_RDONLY or
                    OsConstants.O_NOFOLLOW,
                0,
            )
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return try {
            val stat = Os.fstat(descriptor)
            if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_size < 0L) {
                throw WalletUpgradeBackupException(failureCode)
            }
            OpenedRegularFile(
                descriptor = descriptor,
                input = FileInputStream(descriptor),
                size = stat.st_size,
            )
        } catch (error: Throwable) {
            runCatching { Os.close(descriptor) }
            when (error) {
                is WalletUpgradeBackupException -> throw error
                is Exception ->
                    throw WalletUpgradeBackupException(failureCode)
                else -> throw error
            }
        }
    }

    private fun File.openNewRegularNoFollow(
        failureCode: String,
        existsFailureCode: String? = null,
    ): OpenedRegularOutput {
        val descriptor = try {
            Os.open(
                absolutePath,
                OsConstants.O_WRONLY or
                    OsConstants.O_CREAT or
                    OsConstants.O_EXCL or
                    OsConstants.O_NOFOLLOW,
                0x180,
            )
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.EEXIST && existsFailureCode != null) {
                throw WalletUpgradeBackupException(existsFailureCode)
            }
            throw WalletUpgradeBackupException(failureCode)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return try {
            val stat = Os.fstat(descriptor)
            if (!OsConstants.S_ISREG(stat.st_mode)) {
                throw WalletUpgradeBackupException(failureCode)
            }
            OpenedRegularOutput(
                descriptor = descriptor,
                output = FileOutputStream(descriptor),
            )
        } catch (error: Throwable) {
            runCatching { Os.close(descriptor) }
            when (error) {
                is WalletUpgradeBackupException -> throw error
                is Exception ->
                    throw WalletUpgradeBackupException(failureCode)
                else -> throw error
            }
        }
    }

    private fun File.readBoundedBytesNoFollow(
        minimumBytes: Long,
        maximumBytes: Long,
        failureCode: String,
    ): ByteArray {
        if (
            minimumBytes < 0 ||
            maximumBytes < minimumBytes ||
            maximumBytes > Int.MAX_VALUE
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return openRegularNoFollow(failureCode).use { opened ->
            val expectedSize = opened.size
            if (expectedSize !in minimumBytes..maximumBytes) {
                throw WalletUpgradeBackupException(failureCode)
            }
            val bytes = ByteArray(expectedSize.toInt())
            try {
                val input = opened.input
                var offset = 0
                while (offset < bytes.size) {
                    val count = input.read(bytes, offset, bytes.size - offset)
                    if (count <= 0) {
                        throw WalletUpgradeBackupException(failureCode)
                    }
                    offset += count
                }
                if (input.read() != -1) {
                    throw WalletUpgradeBackupException(failureCode)
                }
                if (Os.fstat(input.fd).st_size != expectedSize) {
                    throw WalletUpgradeBackupException(failureCode)
                }
                bytes
            } catch (error: WalletUpgradeBackupException) {
                bytes.fill(0)
                throw error
            } catch (_: Exception) {
                bytes.fill(0)
                throw WalletUpgradeBackupException(failureCode)
            }
        }
    }

    private fun File.readBoundedUtf8NoFollow(
        minimumBytes: Long,
        maximumBytes: Long,
        failureCode: String,
    ): String {
        val bytes = readBoundedBytesNoFollow(
            minimumBytes = minimumBytes,
            maximumBytes = maximumBytes,
            failureCode = failureCode,
        )
        return try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw WalletUpgradeBackupException(failureCode)
        } finally {
            bytes.fill(0)
        }
    }

    private fun createSourceSnapshot(
        context: Context,
        sources: List<File>,
        legacyWalletIds: Set<String>,
        selectedWalletId: String?,
    ): BackupSourceSnapshot {
        if (sources.any { !it.isRegularFileNoFollow() }) {
            throw WalletUpgradeBackupException("BACKUP_SOURCE_INVALID")
        }
        val records = sources.map { source ->
            val fingerprint = source.inspectRegularNoFollow(
                "BACKUP_SOURCE_INVALID"
            )
            BackupSourceRecord(
                source = source,
                name = source.backupName(context),
                size = fingerprint.size,
                sha256 = fingerprint.sha256,
            )
        }.sortedBy(BackupSourceRecord::name)
        if (records.map(BackupSourceRecord::name).toSet().size != records.size) {
            throw WalletUpgradeBackupException("BACKUP_SOURCE_DUPLICATE")
        }
        val fingerprint = sha256Text(
            records.joinToString(separator = "\n") {
                "${it.name}\u0000${it.size}\u0000${it.sha256}"
            }
        )
        return BackupSourceSnapshot(
            files = records,
            fingerprint = fingerprint,
            legacyAccountCount = legacyWalletIds.size,
            legacyWalletIdsSha256 = sha256Text(
                legacyWalletIds.sorted().joinToString(separator = "\n")
            ),
            selectedWalletIdSha256 = selectedWalletId?.let(::sha256Text),
        )
    }

    private fun matchesSourceSnapshot(
        directory: File,
        snapshot: BackupSourceSnapshot,
        sourceVersion: Int,
    ): Boolean = runCatching {
        val manifest = JSONObject(
            File(directory, "manifest.json").readBoundedUtf8NoFollow(
                minimumBytes = 1,
                maximumBytes = MAX_MANIFEST_BYTES,
                failureCode = "BACKUP_NAMESPACE_INVALID",
            )
        )
        manifest.getInt("sourceDatabaseVersion") == sourceVersion &&
            manifest.getInt("targetDatabaseVersion") ==
            CURRENT_DATABASE_VERSION &&
            manifest.getString("sourceFingerprint") == snapshot.fingerprint &&
            manifest.getInt("legacyAccountCount") == snapshot.legacyAccountCount &&
            manifest.getString("legacyWalletIdsSha256") ==
            snapshot.legacyWalletIdsSha256 &&
            (
                if (snapshot.selectedWalletIdSha256 == null) {
                    manifest.isNull("selectedWalletIdSha256")
                } else {
                    manifest.getString("selectedWalletIdSha256") ==
                        snapshot.selectedWalletIdSha256
                }
                )
    }.getOrDefault(false)

    private fun backupGenerationFromValidatedName(name: String): Long? {
        val markerIndex = name.lastIndexOf("-g")
        if (markerIndex < 0) return 1L
        val start = markerIndex + 2
        val end = name.indexOf('-', start)
        if (end <= start) return null
        return name.substring(start, end)
            .takeIf {
                it.length == 20 &&
                    it.all { character -> character in '0'..'9' }
            }
            ?.toLongOrNull()
            ?.takeIf { it > 1L }
    }

    private fun backupDirectoryName(
        sourceVersion: Int,
        targetVersion: Int,
        generation: Long,
        fingerprint: String,
    ): String {
        val base = "$BACKUP_PREFIX$sourceVersion-to-$targetVersion"
        return if (generation == 1L) {
            base
        } else {
            "$base-g${generation.toString().padStart(20, '0')}-$fingerprint"
        }
    }

    private fun readVersion(database: File): Int {
        val sqlite = SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            FAIL_CLOSED_DATABASE_ERROR_HANDLER,
        )
        return sqlite.use {
            it.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private fun readWalletDatabaseInventory(
        database: File,
        sourceVersion: Int,
    ): WalletDatabaseInventory =
        SQLiteDatabase.openDatabase(
            database.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            FAIL_CLOSED_DATABASE_ERROR_HANDLER,
        ).use { sqlite ->
            readWalletDatabaseInventory(sqlite, sourceVersion)
        }

    private fun readWalletDatabaseInventory(
        sqlite: SQLiteDatabase,
        sourceVersion: Int,
    ): WalletDatabaseInventory {
        return sqlite.let { database ->
            if (!database.tableExists("accounts")) {
                throw WalletUpgradeBackupException("LEGACY_ACCOUNTS_TABLE_MISSING")
            }
            val accounts = database.rawQuery(
                """
                SELECT substrateAddress, accountName
                FROM accounts
                ORDER BY substrateAddress
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    val ids = mutableSetOf<String>()
                    while (cursor.moveToNext()) {
                        val walletId = cursor.getString(0)
                        if (
                            walletId.isNullOrBlank() ||
                            walletId.length > MAX_WALLET_ID_LENGTH
                        ) {
                            throw WalletUpgradeBackupException(
                                "LEGACY_ACCOUNT_ID_INVALID"
                            )
                        }
                        if (!ids.add(walletId)) {
                            throw WalletUpgradeBackupException(
                                "LEGACY_ACCOUNT_ID_DUPLICATE"
                            )
                        }
                        add(SoraAccountLocal(walletId, cursor.getString(1)))
                    }
                }
            }

            if (sourceVersion < CURRENT_DATABASE_VERSION) {
                return@let WalletDatabaseInventory(accounts = accounts)
            }
            val requiredTables = setOf(
                "walletIdentities",
                "networkAccounts",
                "walletMigrationJournal",
                "pendingNetworkTransactions",
                "sora2PendingSubmissions",
                "walletDeletionOperations",
                "walletDeletionTargets",
            )
            if (requiredTables.any { table -> !database.tableExists(table) }) {
                throw WalletUpgradeBackupException("WALLET_DELETION_JOURNAL_INVALID")
            }
            val identities = database.rawQuery(
                """
                SELECT walletId, displayName, secretSource, migrationState, derivationVersion
                FROM walletIdentities
                ORDER BY walletId
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            WalletIdentityLocal(
                                walletId = cursor.getString(0),
                                displayName = cursor.getString(1),
                                secretSource = cursor.getString(2),
                                migrationState = cursor.getString(3),
                                derivationVersion = cursor.getInt(4),
                            )
                        )
                    }
                }
            }
            val networkAccounts = database.rawQuery(
                """
                SELECT walletId, networkId, publicKey, address, derivationPath,
                    derivationVersion, enabled
                FROM networkAccounts
                ORDER BY walletId, networkId
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val enabled = cursor.getInt(6)
                        if (enabled !in 0..1) {
                            throw WalletUpgradeBackupException(
                                "WALLET_DELETION_JOURNAL_INVALID"
                            )
                        }
                        add(
                            NetworkAccountLocal(
                                walletId = cursor.getString(0),
                                networkId = cursor.getString(1),
                                publicKey = cursor.getString(2),
                                address = cursor.getString(3),
                                derivationPath = cursor.getString(4),
                                derivationVersion = cursor.getInt(5),
                                enabled = enabled == 1,
                            )
                        )
                    }
                }
            }
            val pendingWalletIds = database.rawQuery(
                """
                SELECT walletId, networkId, chainId
                FROM pendingNetworkTransactions
                ORDER BY walletId, localId
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            val operations = database.rawQuery(
                """
                SELECT operationId, activeSlot, formatVersion, scope, phase,
                    expectedWalletCount, targetCount, selectedBefore, selectedAfter,
                    beforeSnapshotHash, afterSnapshotHash, beforePreferencesHash,
                    afterPreferencesHash, requestDigest, removeLegacyUnsuffixed,
                    requestedAt, updatedAt, failureCode
                FROM walletDeletionOperations
                ORDER BY operationId
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val removeLegacy = cursor.getInt(14)
                        if (removeLegacy !in 0..1) {
                            throw WalletUpgradeBackupException(
                                "WALLET_DELETION_JOURNAL_INVALID"
                            )
                        }
                        add(
                            WalletDeletionOperationLocal(
                                operationId = cursor.getString(0),
                                activeSlot = cursor.getInt(1),
                                formatVersion = cursor.getInt(2),
                                scope = cursor.getString(3),
                                phase = cursor.getString(4),
                                expectedWalletCount = cursor.getInt(5),
                                targetCount = cursor.getInt(6),
                                selectedBefore = cursor.getString(7),
                                selectedAfter = cursor.getString(8),
                                beforeSnapshotHash = cursor.getString(9),
                                afterSnapshotHash = cursor.getString(10),
                                beforePreferencesHash = cursor.getString(11),
                                afterPreferencesHash = cursor.getString(12),
                                requestDigest = cursor.getString(13),
                                removeLegacyUnsuffixed = removeLegacy == 1,
                                requestedAt = cursor.getLong(15),
                                updatedAt = cursor.getLong(16),
                                failureCode = if (cursor.isNull(17)) {
                                    null
                                } else {
                                    cursor.getString(17)
                                },
                            )
                        )
                    }
                }
            }
            val deletionTargets = database.rawQuery(
                """
                SELECT operationId, walletId
                FROM walletDeletionTargets
                ORDER BY operationId, walletId
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0) to cursor.getString(1))
                    }
                }
            }
            val migrationJournals = database.rawQuery(
                """
                SELECT migrationId, state, legacyAccountCount, verifiedAccountCount,
                    selectedWalletId, integrityHash, failureCode, startedAt, completedAt
                FROM walletMigrationJournal
                ORDER BY migrationId
                """.trimIndent(),
                null,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            WalletMigrationJournalLocal(
                                migrationId = cursor.getString(0),
                                state = cursor.getString(1),
                                legacyAccountCount = cursor.getInt(2),
                                verifiedAccountCount = cursor.getInt(3),
                                selectedWalletId = cursor.getString(4),
                                integrityHash = cursor.getString(5),
                                failureCode = if (cursor.isNull(6)) {
                                    null
                                } else {
                                    cursor.getString(6)
                                },
                                startedAt = cursor.getLong(7),
                                completedAt = if (cursor.isNull(8)) {
                                    null
                                } else {
                                    cursor.getLong(8)
                                },
                            )
                        )
                    }
                }
            }
            WalletDatabaseInventory(
                accounts = accounts,
                identities = identities,
                networkAccounts = networkAccounts,
                pendingWalletIds = pendingWalletIds,
                deletionOperations = operations,
                deletionTargets = deletionTargets,
                migrationJournals = migrationJournals,
            )
        }
    }

    private fun SQLiteDatabase.tableExists(table: String): Boolean =
        rawQuery(
            """
            SELECT 1 FROM sqlite_master
            WHERE type = 'table' AND name = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }

    private fun validateWalletStorageCoherence(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ) {
        val legacyWalletIds = database.accounts
            .map(SoraAccountLocal::substrateAddress)
            .toSet()
        val operation = when (database.deletionOperations.size) {
            0 -> null
            1 -> database.deletionOperations.single()
            else -> throw WalletUpgradeBackupException(
                "WALLET_DELETION_JOURNAL_INVALID"
            )
        }
        validateMigrationJournal(database, preferences)
        if (operation == null) {
            if (database.deletionTargets.isNotEmpty()) {
                throw WalletUpgradeBackupException(
                    "WALLET_DELETION_JOURNAL_INVALID"
                )
            }
            requireStrictWalletCoherence(database, preferences)
            return
        }

        val targets = database.deletionTargets.map { (operationId, walletId) ->
            if (operationId != operation.operationId) {
                throw WalletUpgradeBackupException(
                    "WALLET_DELETION_JOURNAL_INVALID"
                )
            }
            walletId
        }.toSet()
        if (
            !DELETION_OPERATION_ID.matches(operation.operationId) ||
            operation.activeSlot != WalletDeletionContract.ACTIVE_SLOT ||
            operation.formatVersion != WalletDeletionContract.FORMAT_VERSION ||
            operation.scope !in setOf(
                WalletDeletionContract.SCOPE_SINGLE,
                WalletDeletionContract.SCOPE_ALL,
            ) ||
            operation.phase !in setOf(
                WalletDeletionContract.PHASE_CONFIRMED,
                WalletDeletionContract.PHASE_DATABASE_COMMITTED,
                WalletDeletionContract.PHASE_PREFERENCES_COMMITTED,
            ) ||
            operation.expectedWalletCount !in
                1..WalletDeletionContract.MAX_TARGET_WALLETS ||
            operation.targetCount !in 1..operation.expectedWalletCount ||
            targets.size != operation.targetCount ||
            database.deletionTargets.size != targets.size ||
            targets.any { it.isBlank() || it.length > MAX_WALLET_ID_LENGTH } ||
            operation.selectedBefore.isBlank() ||
            operation.selectedBefore.length > MAX_WALLET_ID_LENGTH ||
            operation.selectedAfter.length > MAX_WALLET_ID_LENGTH ||
            !WalletDeletionIntegrity.isSha256(operation.beforeSnapshotHash) ||
            !WalletDeletionIntegrity.isSha256(operation.afterSnapshotHash) ||
            !WalletPreferenceIntegrity.isSha256(operation.beforePreferencesHash) ||
            !WalletPreferenceIntegrity.isSha256(operation.afterPreferencesHash) ||
            !WalletDeletionIntegrity.isSha256(operation.requestDigest) ||
            operation.requestedAt <= 0L ||
            operation.updatedAt < operation.requestedAt ||
            (operation.failureCode?.length ?: 0) > 128 ||
            operation.requestDigest !=
            WalletDeletionIntegrity.requestDigest(operation, targets)
        ) {
            throw WalletUpgradeBackupException("WALLET_DELETION_JOURNAL_INVALID")
        }
        if (operation.failureCode != null) {
            throw WalletUpgradeBackupException("WALLET_DELETION_RECOVERY_REQUIRED")
        }

        when (operation.scope) {
            WalletDeletionContract.SCOPE_SINGLE -> {
                if (
                    targets.size != 1 ||
                    operation.selectedAfter.isBlank() ||
                    operation.selectedAfter in targets
                ) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_JOURNAL_INVALID"
                    )
                }
            }
            WalletDeletionContract.SCOPE_ALL -> {
                if (
                    operation.targetCount != operation.expectedWalletCount ||
                    operation.selectedAfter.isNotEmpty()
                ) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_JOURNAL_INVALID"
                    )
                }
            }
        }

        val snapshotHash = when (operation.phase) {
            WalletDeletionContract.PHASE_CONFIRMED -> {
                if (
                    database.accounts.size != operation.expectedWalletCount ||
                    !legacyWalletIds.containsAll(targets) ||
                    (
                        operation.scope == WalletDeletionContract.SCOPE_ALL &&
                            legacyWalletIds != targets
                        ) ||
                    (
                        operation.scope == WalletDeletionContract.SCOPE_SINGLE &&
                            operation.selectedAfter !in legacyWalletIds
                        ) ||
                    operation.selectedBefore !in legacyWalletIds
                ) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_BEFORE_SNAPSHOT_MISMATCH"
                    )
                }
                deletionSnapshotHash(
                    accounts = database.accounts,
                    identities = database.identities,
                    networkAccounts = database.networkAccounts,
                    selectedWalletId = operation.selectedBefore,
                    scope = operation.scope,
                    targetWalletIds = targets,
                )
            }
            else -> {
                if (
                    database.accounts.size !=
                    operation.expectedWalletCount - operation.targetCount ||
                    legacyWalletIds.any { it in targets } ||
                    database.identities.any { it.walletId in targets } ||
                    database.networkAccounts.any { it.walletId in targets } ||
                    database.pendingWalletIds.any { it in targets } ||
                    (
                        operation.scope == WalletDeletionContract.SCOPE_ALL &&
                            legacyWalletIds.isNotEmpty()
                        ) ||
                    (
                        operation.scope == WalletDeletionContract.SCOPE_SINGLE &&
                            operation.selectedAfter !in legacyWalletIds
                        )
                ) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_AFTER_SNAPSHOT_MISMATCH"
                    )
                }
                deletionSnapshotHash(
                    accounts = database.accounts,
                    identities = database.identities,
                    networkAccounts = database.networkAccounts,
                    selectedWalletId = operation.selectedAfter,
                    scope = operation.scope,
                    targetWalletIds = targets,
                )
            }
        }
        val expectedSnapshotHash =
            if (operation.phase == WalletDeletionContract.PHASE_CONFIRMED) {
                operation.beforeSnapshotHash
            } else {
                operation.afterSnapshotHash
            }
        if (snapshotHash != expectedSnapshotHash) {
            throw WalletUpgradeBackupException(
                "WALLET_DELETION_SNAPSHOT_HASH_MISMATCH"
            )
        }

        when (operation.phase) {
            WalletDeletionContract.PHASE_CONFIRMED -> {
                requireStrictWalletCoherence(database, preferences)
                if (preferences.fingerprint != operation.beforePreferencesHash) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_PREFERENCES_MISMATCH"
                    )
                }
            }
            WalletDeletionContract.PHASE_DATABASE_COMMITTED -> {
                val orphanedScopedIds =
                    preferences.scopedWalletIds - legacyWalletIds
                if (
                    orphanedScopedIds.any { it !in targets } ||
                    (
                        preferences.selectedWalletId != null &&
                            preferences.selectedWalletId !in legacyWalletIds &&
                            preferences.selectedWalletId !in targets
                        ) ||
                    (
                        preferences.legacyPureAddress != null &&
                            preferences.legacyPureAddress !in legacyWalletIds &&
                            preferences.legacyPureAddress !in targets
                        ) ||
                    (
                        legacyWalletIds.isEmpty() &&
                            operation.scope != WalletDeletionContract.SCOPE_ALL &&
                            preferences.hasWalletMaterial
                        )
                ) {
                    throw WalletUpgradeBackupException(
                        "ORPHANED_WALLET_STORAGE"
                    )
                }
                if (
                    preferences.fingerprint !in setOf(
                        operation.beforePreferencesHash,
                        operation.afterPreferencesHash,
                    )
                ) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_PREFERENCES_MISMATCH"
                    )
                }
            }
            WalletDeletionContract.PHASE_PREFERENCES_COMMITTED -> {
                requireStrictWalletCoherence(database, preferences)
                val targetKeys = targets.flatMap { walletId ->
                    WalletPreferenceKeys.scopedStringKeys(walletId) +
                        WalletPreferenceKeys.scopedBooleanKeys(walletId)
                }.toSet()
                if (
                    preferences.allKeys.any { it in targetKeys } ||
                    (
                        operation.removeLegacyUnsuffixed &&
                            preferences.allKeys.any {
                                it in WalletPreferenceKeys.legacyUnsuffixedStringKeys
                            }
                        ) ||
                    preferences.fingerprint != operation.afterPreferencesHash ||
                    preferences.selectedWalletId.orEmpty() !=
                    operation.selectedAfter ||
                    (
                        operation.scope == WalletDeletionContract.SCOPE_ALL &&
                            preferences.allKeys.isNotEmpty()
                        )
                ) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_PREFERENCES_MISMATCH"
                    )
                }
            }
        }
    }

    private fun validateMigrationJournal(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ) {
        val journal = when (database.migrationJournals.size) {
            0 -> null
            1 -> database.migrationJournals.single()
            else -> throw WalletUpgradeBackupException(
                "WALLET_MIGRATION_JOURNAL_INVALID"
            )
        }
        if (journal == null) {
            if (database.identities.any { it.migrationState != "VERIFIED" }) {
                throw WalletUpgradeBackupException(
                    "WALLET_MIGRATION_RECOVERY_REQUIRED"
                )
            }
            return
        }
        if (
            journal.migrationId != WalletMigrationIds.NETWORK_ACCOUNTS_V1 ||
            journal.state !in setOf("VERIFYING", "VERIFIED", "RECOVERY_REQUIRED") ||
            journal.legacyAccountCount < 0 ||
            journal.verifiedAccountCount !in 0..journal.legacyAccountCount ||
            journal.selectedWalletId.length > MAX_WALLET_ID_LENGTH ||
            !SHA256.matches(journal.integrityHash) ||
            journal.startedAt <= 0L ||
            (journal.failureCode?.length ?: 0) > 128 ||
            (
                journal.completedAt != null &&
                    journal.completedAt < journal.startedAt
                )
        ) {
            throw WalletUpgradeBackupException("WALLET_MIGRATION_JOURNAL_INVALID")
        }
        if (journal.state != "VERIFIED") {
            throw WalletUpgradeBackupException("WALLET_MIGRATION_RECOVERY_REQUIRED")
        }
        if (
            journal.failureCode != null ||
            journal.completedAt == null ||
            journal.legacyAccountCount <= 0 ||
            journal.verifiedAccountCount != journal.legacyAccountCount ||
            journal.selectedWalletId.isBlank()
        ) {
            throw WalletUpgradeBackupException("WALLET_MIGRATION_JOURNAL_INVALID")
        }
        // A VERIFIED journal is the last fully verified wallet checkpoint, not a requirement that
        // the live wallet inventory remain immutable. Explicit post-migration account creation,
        // renaming, selection, and removal legitimately change the current count/hash/selection.
        // requireStrictWalletCoherence() validates the live dual-read model below; MigrationManager
        // then re-verifies every secret/model and advances the exact prior checkpoint by CAS.
    }

    private fun inspectMigrationRecovery(context: Context): MigrationRecoverySnapshot {
        val database = context.getDatabasePath(DATABASE_NAME)
        if (!database.isFile || readVersion(database) != CURRENT_DATABASE_VERSION) {
            throw WalletUpgradeBackupException("MIGRATION_RECOVERY_DATABASE_INVALID")
        }
        val preferencesBefore = walletPreferenceInventory(context)
        val inventory = readWalletDatabaseInventory(
            database = database,
            sourceVersion = CURRENT_DATABASE_VERSION,
        )
        val preferencesAfter = walletPreferenceInventory(context)
        if (preferencesAfter != preferencesBefore) {
            throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
        }
        return validatedMigrationRecovery(inventory, preferencesAfter)
    }

    private fun requireLegacyRecoveryPreferenceCoherence(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ) {
        val legacyWalletIds = database.accounts
            .map(SoraAccountLocal::substrateAddress)
            .toSet()
        val coveredWalletIds =
            preferences.mnemonicWalletIds +
                preferences.rawSeedWalletIds +
                preferences.keyPairWalletIds +
                preferences.watchOnlyWalletIds
        if (
            legacyWalletIds.isEmpty() ||
            preferences.selectedWalletId == null ||
            preferences.selectedWalletId !in legacyWalletIds ||
            !coveredWalletIds.containsAll(legacyWalletIds) ||
            preferences.scopedWalletIds.any { it !in legacyWalletIds } ||
            (
                preferences.legacyPureAddress != null &&
                    preferences.legacyPureAddress !in legacyWalletIds
                )
        ) {
            throw WalletUpgradeBackupException(
                "MIGRATION_RECOVERY_SNAPSHOT_INVALID"
            )
        }
    }

    private fun isPristineCurrentSchemaNamespace(
        database: WalletDatabaseInventory,
    ): Boolean {
        val accounts = database.accounts.associateBy(SoraAccountLocal::substrateAddress)
        if (
            database.migrationJournals.isNotEmpty() ||
            database.pendingWalletIds.isNotEmpty() ||
            database.deletionOperations.isNotEmpty() ||
            database.deletionTargets.isNotEmpty() ||
            database.identities.size != accounts.size ||
            database.networkAccounts.size != accounts.size
        ) {
            return false
        }
        val identities = database.identities.associateBy(WalletIdentityLocal::walletId)
        if (identities.keys != accounts.keys) return false
        if (
            identities.any { (walletId, identity) ->
                identity.displayName != accounts.getValue(walletId).accountName ||
                    identity.secretSource != "UNKNOWN" ||
                    identity.migrationState != "PENDING_VERIFICATION" ||
                    identity.derivationVersion != 1
            }
        ) {
            return false
        }
        val networks = database.networkAccounts.associateBy(NetworkAccountLocal::walletId)
        return networks.keys == accounts.keys &&
            networks.values.all { account ->
                account.networkId == "sora2" &&
                    account.publicKey.isEmpty() &&
                    account.address == account.walletId &&
                    account.derivationPath.isEmpty() &&
                    account.derivationVersion == 1 &&
                    !account.enabled
            }
    }

    /**
     * Recovery deliberately validates only the immutable legacy authority plus the journal.
     * Partially written walletIdentities/networkAccounts are allowed because they are the
     * copy-on-write destination that failed verification; they are never activated here.
     */
    private fun validatedMigrationRecovery(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ): MigrationRecoverySnapshot {
        val journal = database.migrationJournals.singleOrNull()
            ?: throw WalletUpgradeBackupException("WALLET_MIGRATION_JOURNAL_INVALID")
        val legacyWalletIds = database.accounts
            .map(SoraAccountLocal::substrateAddress)
            .toSet()
        val coveredWalletIds =
            preferences.mnemonicWalletIds +
                preferences.rawSeedWalletIds +
                preferences.keyPairWalletIds +
                preferences.watchOnlyWalletIds
        val stateFieldsValid = when (journal.state) {
            "VERIFYING" ->
                journal.failureCode == null &&
                    journal.completedAt == null
            "RECOVERY_REQUIRED" ->
                !journal.failureCode.isNullOrBlank() &&
                    journal.completedAt != null
            else -> false
        }
        if (
            journal.migrationId != WalletMigrationIds.NETWORK_ACCOUNTS_V1 ||
            !stateFieldsValid ||
            journal.legacyAccountCount <= 0 ||
            journal.legacyAccountCount != database.accounts.size ||
            journal.verifiedAccountCount !in 0..journal.legacyAccountCount ||
            journal.selectedWalletId.isBlank() ||
            journal.selectedWalletId.length > MAX_WALLET_ID_LENGTH ||
            journal.selectedWalletId != preferences.selectedWalletId ||
            journal.selectedWalletId !in legacyWalletIds ||
            journal.integrityHash !=
            WalletMigrationIntegrity.snapshotHash(
                database.accounts,
                journal.selectedWalletId,
            ) ||
            journal.startedAt <= 0L ||
            (
                journal.completedAt != null &&
                    journal.completedAt < journal.startedAt
                ) ||
            (journal.failureCode?.length ?: 0) > 128 ||
            database.deletionOperations.isNotEmpty() ||
            database.deletionTargets.isNotEmpty() ||
            database.identities.any { it.walletId !in legacyWalletIds } ||
            database.networkAccounts.any { it.walletId !in legacyWalletIds } ||
            database.pendingWalletIds.any { it !in legacyWalletIds } ||
            !coveredWalletIds.containsAll(legacyWalletIds) ||
            preferences.scopedWalletIds.any { it !in legacyWalletIds } ||
            (
                preferences.legacyPureAddress != null &&
                    preferences.legacyPureAddress !in legacyWalletIds
                )
        ) {
            throw WalletUpgradeBackupException(
                "MIGRATION_RECOVERY_SNAPSHOT_INVALID"
            )
        }
        val token = sha256Text(
            buildString {
                append(journal.migrationId)
                append('\u0000')
                append(journal.state)
                append('\u0000')
                append(journal.legacyAccountCount)
                append('\u0000')
                append(journal.verifiedAccountCount)
                append('\u0000')
                append(journal.selectedWalletId)
                append('\u0000')
                append(journal.integrityHash)
                append('\u0000')
                append(journal.failureCode.orEmpty())
                append('\u0000')
                append(journal.startedAt)
                append('\u0000')
                append(journal.completedAt ?: -1L)
                append('\n')
                append(preferences.fingerprint)
                append('\n')
                database.identities.sortedBy(WalletIdentityLocal::walletId).forEach {
                    append(it.walletId)
                    append('\u0000')
                    append(it.displayName)
                    append('\u0000')
                    append(it.secretSource)
                    append('\u0000')
                    append(it.migrationState)
                    append('\u0000')
                    append(it.derivationVersion)
                    append('\n')
                }
                database.networkAccounts
                    .sortedWith(
                        compareBy(
                            NetworkAccountLocal::walletId,
                            NetworkAccountLocal::networkId,
                        )
                    )
                    .forEach {
                        append(it.walletId)
                        append('\u0000')
                        append(it.networkId)
                        append('\u0000')
                        append(it.publicKey)
                        append('\u0000')
                        append(it.address)
                        append('\u0000')
                        append(it.derivationPath)
                        append('\u0000')
                        append(it.derivationVersion)
                        append('\u0000')
                        append(it.enabled)
                        append('\n')
                    }
            }
        )
        return MigrationRecoverySnapshot(
            token = token,
            journal = journal,
        )
    }

    private fun deletionSnapshotHash(
        accounts: List<SoraAccountLocal>,
        identities: List<WalletIdentityLocal>,
        networkAccounts: List<NetworkAccountLocal>,
        selectedWalletId: String,
        scope: String,
        targetWalletIds: Set<String>,
    ): String = try {
        WalletDeletionIntegrity.snapshotHash(
            accounts = accounts,
            identities = identities,
            networkAccounts = networkAccounts,
            selectedWalletId = selectedWalletId,
            scope = scope,
            targetWalletIds = targetWalletIds,
        )
    } catch (_: IllegalStateException) {
        throw WalletUpgradeBackupException(
            "WALLET_DELETION_SNAPSHOT_INVALID"
        )
    }

    private fun requireStrictWalletCoherence(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ) {
        val legacyWalletIds = database.accounts
            .map(SoraAccountLocal::substrateAddress)
            .toSet()
        val coveredWalletIds =
            preferences.mnemonicWalletIds +
                preferences.rawSeedWalletIds +
                preferences.keyPairWalletIds +
                preferences.watchOnlyWalletIds
        val currentIdentityMismatch =
            if (database.identities.isEmpty()) {
                !coveredWalletIds.containsAll(legacyWalletIds)
            } else {
                database.identities.size != legacyWalletIds.size ||
                    database.identities
                        .map(WalletIdentityLocal::walletId)
                        .toSet() != legacyWalletIds ||
                    database.identities.any { identity ->
                        identity.migrationState != "VERIFIED" ||
                            when (identity.secretSource) {
                                "MNEMONIC", "MNEMONIC_UNSUPPORTED" ->
                                    identity.walletId !in preferences.mnemonicWalletIds ||
                                        identity.walletId !in preferences.keyPairWalletIds ||
                                        identity.walletId in preferences.watchOnlyWalletIds
                                "RAW_SEED" ->
                                    identity.walletId !in preferences.rawSeedWalletIds ||
                                        identity.walletId !in preferences.keyPairWalletIds ||
                                        identity.walletId in preferences.mnemonicWalletIds ||
                                        identity.walletId in preferences.watchOnlyWalletIds
                                "LEGACY_SECRET" ->
                                    identity.walletId !in preferences.keyPairWalletIds ||
                                        identity.walletId in preferences.mnemonicWalletIds ||
                                        identity.walletId in preferences.rawSeedWalletIds ||
                                        identity.walletId in preferences.watchOnlyWalletIds
                                "WATCH_ONLY" ->
                                    identity.walletId !in preferences.watchOnlyWalletIds ||
                                        identity.walletId in preferences.keyPairWalletIds ||
                                        identity.walletId in preferences.mnemonicWalletIds ||
                                        identity.walletId in preferences.rawSeedWalletIds
                                else -> true
                            }
                    }
            }
        if (
            preferences.incompleteKeyPairWalletIds.isNotEmpty() ||
            (legacyWalletIds.isEmpty() && preferences.hasWalletMaterial) ||
            (
                legacyWalletIds.isNotEmpty() &&
                    (
                        preferences.selectedWalletId == null ||
                            currentIdentityMismatch
                        )
                ) ||
            preferences.scopedWalletIds.any { it !in legacyWalletIds } ||
            (
                preferences.selectedWalletId != null &&
                    preferences.selectedWalletId !in legacyWalletIds
                ) ||
            (
                preferences.legacyPureAddress != null &&
                    preferences.legacyPureAddress !in legacyWalletIds
                )
        ) {
            // Missing rows or secrets never imply user authorization. Only a
            // fully validated active deletion journal can grant the narrow
            // crash-recovery exception above.
            throw WalletUpgradeBackupException("ORPHANED_WALLET_STORAGE")
        }
    }

    private fun walletPreferenceInventory(context: Context): WalletPreferenceInventory {
        val dataDir = File(context.applicationInfo.dataDir)
        val inventory = walletPreferenceInventory(
            dataStore = File(
                context.filesDir,
                "datastore/sora_prefs_datastore.preferences_pb",
            ),
            sharedPreferences = File(dataDir, "shared_prefs/sora_prefs.xml"),
            sharedPreferencesBackup = File(
                dataDir,
                "shared_prefs/sora_prefs.xml.bak",
            ),
        )
        val wrappedKeyPreferences = File(dataDir, "shared_prefs/key_alias.xml")
        val wrappedKeyPreferencesBackup = File(
            dataDir,
            "shared_prefs/key_alias.xml.bak",
        )
        val wrappedKeyEntries = when {
            wrappedKeyPreferencesBackup.existsNoFollow() -> {
                if (!wrappedKeyPreferencesBackup.isRegularFileNoFollow()) {
                    throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
                }
                wrappedKeyPreferencesBackup.readSharedPreferenceEntries()
            }
            wrappedKeyPreferences.existsNoFollow() -> {
                if (!wrappedKeyPreferences.isRegularFileNoFollow()) {
                    throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
                }
                wrappedKeyPreferences.readSharedPreferenceEntries()
            }
            else -> emptyList()
        }
        val hasWrappedWalletKeyEvidence = wrappedKeyEntries.any {
            it.key == "secret_key"
        }
        return inventory.copy(
            hasWrappedWalletKeyEvidence = hasWrappedWalletKeyEvidence,
        )
    }

    private fun walletPreferenceInventory(
        dataStore: File,
        sharedPreferences: File,
        sharedPreferencesBackup: File,
    ): WalletPreferenceInventory {
        // Preferences DataStore is authoritative once published. Inspect the
        // legacy XML only before that file exists so stale post-migration XML
        // keys cannot manufacture an orphaned-wallet false positive. Before
        // DataStore exists, Android's SharedPreferences recovery semantics
        // restore `.bak` over the main XML, so the backup is authoritative.
        val entries = if (dataStore.existsNoFollow()) {
            if (!dataStore.isRegularFileNoFollow()) {
                throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
            }
            dataStore.readPreferenceDataStoreEntries()
        } else if (sharedPreferencesBackup.existsNoFollow()) {
            if (!sharedPreferencesBackup.isRegularFileNoFollow()) {
                throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
            }
            sharedPreferencesBackup.readSharedPreferenceEntries()
        } else {
            if (
                sharedPreferences.existsNoFollow() &&
                !sharedPreferences.isRegularFileNoFollow()
            ) {
                throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
            }
            sharedPreferences.readSharedPreferenceEntries()
        }
        val walletEntries = entries.filter { it.isWalletEvidence() }
        val scopedWalletIds = walletEntries.mapNotNull { entry ->
            WalletPreferenceKeys.scopedEvidencePrefixes
                .firstOrNull(entry.key::startsWith)
                ?.let { prefix ->
                    entry.key.removePrefix(prefix).takeIf(String::isNotEmpty)
                }
        }.toSet()
        val legacyOwner = entries.firstOrNull {
            it.key == WalletPreferenceKeys.LEGACY_ADDRESS &&
                !it.stringValue.isNullOrBlank()
        }?.stringValue
        val mnemonicWalletIds = entries.scopedNonBlankStringIds(
            WalletPreferenceKeys.MNEMONIC
        ).toMutableSet().apply {
            if (
                legacyOwner != null &&
                entries.any {
                    it.key == WalletPreferenceKeys.MNEMONIC &&
                        !it.stringValue.isNullOrBlank()
                }
            ) {
                add(legacyOwner)
            }
        }
        val rawSeedWalletIds = entries.scopedNonBlankStringIds(
            WalletPreferenceKeys.SEED
        ).toMutableSet().apply {
            if (
                legacyOwner != null &&
                entries.any {
                    it.key == WalletPreferenceKeys.SEED &&
                        !it.stringValue.isNullOrBlank()
                }
            ) {
                add(legacyOwner)
            }
        }
        fun credentialFieldWalletIds(prefix: String): Set<String> =
            entries.scopedNonBlankStringIds(prefix).toMutableSet().apply {
                if (
                    legacyOwner != null &&
                    entries.any {
                        it.key == prefix && !it.stringValue.isNullOrBlank()
                    }
                ) {
                    add(legacyOwner)
                }
            }
        val privateKeyWalletIds =
            credentialFieldWalletIds(WalletPreferenceKeys.PRIVATE_KEY)
        val publicKeyWalletIds =
            credentialFieldWalletIds(WalletPreferenceKeys.PUBLIC_KEY)
        val keyNonceWalletIds =
            credentialFieldWalletIds(WalletPreferenceKeys.KEY_NONCE)
        val keyPairWalletIds = privateKeyWalletIds
            .intersect(publicKeyWalletIds)
            .intersect(keyNonceWalletIds)
        val incompleteKeyPairWalletIds =
            (privateKeyWalletIds + publicKeyWalletIds + keyNonceWalletIds) -
                keyPairWalletIds
        val watchOnlyWalletIds = entries.mapNotNull { entry ->
            if (
                entry.key.startsWith(WalletPreferenceKeys.WATCH_ONLY) &&
                entry.booleanValue == true
            ) {
                entry.key.removePrefix(WalletPreferenceKeys.WATCH_ONLY)
                    .takeIf(String::isNotEmpty)
            } else {
                null
            }
        }.toSet()
        return WalletPreferenceInventory(
            hasWalletMaterial = walletEntries.isNotEmpty(),
            hasWrappedWalletKeyEvidence = false,
            scopedWalletIds = scopedWalletIds,
            selectedWalletId = entries.firstOrNull {
                it.key == WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS &&
                    !it.stringValue.isNullOrBlank()
            }?.stringValue,
            legacyPureAddress = legacyOwner,
            mnemonicWalletIds = mnemonicWalletIds,
            rawSeedWalletIds = rawSeedWalletIds,
            keyPairWalletIds = keyPairWalletIds,
            incompleteKeyPairWalletIds = incompleteKeyPairWalletIds,
            watchOnlyWalletIds = watchOnlyWalletIds,
            allKeys = entries.map(PreferenceEntry::key).toSet(),
            fingerprint = walletPreferenceFingerprint(entries),
        )
    }

    private fun List<PreferenceEntry>.scopedNonBlankStringIds(
        prefix: String,
    ): Set<String> = mapNotNull { entry ->
        if (
            entry.key.length > prefix.length &&
            entry.key.startsWith(prefix) &&
            !entry.stringValue.isNullOrBlank()
        ) {
            entry.key.removePrefix(prefix)
        } else {
            null
        }
    }.toSet()

    private fun walletPreferenceFingerprint(
        entries: List<PreferenceEntry>,
    ): String {
        val strings = mutableMapOf<String, String>()
        val booleans = mutableMapOf<String, Boolean>()
        entries
            .filter { WalletPreferenceKeys.isWalletStateKey(it.key) }
            .forEach { entry ->
                when {
                    entry.stringValue != null && entry.booleanValue == null ->
                        strings[entry.key] = entry.stringValue
                    entry.booleanValue != null && entry.stringValue == null ->
                        booleans[entry.key] = entry.booleanValue
                    else -> throw WalletUpgradeBackupException(
                        "PREFERENCES_INVENTORY_INVALID"
                    )
                }
            }
        return try {
            WalletPreferenceIntegrity.hash(strings, booleans)
        } catch (_: IllegalStateException) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }
    }

    private fun File.readPreferenceDataStoreEntries(): List<PreferenceEntry> {
        if (!existsNoFollow()) return emptyList()
        // An empty Preferences protobuf is the canonical result of DataStore.clear().
        val bytes = readBoundedBytesNoFollow(
            minimumBytes = 0,
            maximumBytes = MAX_PREFERENCES_INSPECTION_BYTES,
            failureCode = "PREFERENCES_INVENTORY_INVALID",
        )
        return try {
            val entries = mutableListOf<PreferenceEntry>()
            val keys = mutableSetOf<String>()
            var position = 0
            while (position < bytes.size) {
                val tag = bytes.readVarint(position, bytes.size)
                position = tag.next
                val fieldNumber = (tag.value ushr 3).toInt()
                val wireType = (tag.value and 0x07).toInt()
                if (fieldNumber == 0) {
                    throw WalletUpgradeBackupException(
                        "PREFERENCES_INVENTORY_INVALID"
                    )
                }
                if (fieldNumber == 1 && wireType == 2) {
                    val length = bytes.readLength(position, bytes.size)
                    position = length.next
                    val entryEnd = position.checkedEnd(length.value, bytes.size)
                    val entry = bytes.readPreferenceEntry(position, entryEnd)
                    if (!keys.add(entry.key)) {
                        throw WalletUpgradeBackupException(
                            "PREFERENCES_INVENTORY_INVALID"
                        )
                    }
                    entries += entry
                    position = entryEnd
                } else throw WalletUpgradeBackupException(
                    "PREFERENCES_INVENTORY_INVALID"
                )
            }
            entries
        } finally {
            bytes.fill(0)
        }
    }

    private fun ByteArray.readPreferenceEntry(start: Int, end: Int): PreferenceEntry {
        var position = start
        var key: String? = null
        var value = PreferenceValue()
        var hasValue = false
        while (position < end) {
            val tag = readVarint(position, end)
            position = tag.next
            val fieldNumber = (tag.value ushr 3).toInt()
            val wireType = (tag.value and 0x07).toInt()
            if (fieldNumber == 0) {
                throw WalletUpgradeBackupException(
                    "PREFERENCES_INVENTORY_INVALID"
                )
            }
            when {
                fieldNumber == 1 && wireType == 2 -> {
                    if (key != null) {
                        throw WalletUpgradeBackupException(
                            "PREFERENCES_INVENTORY_INVALID"
                        )
                    }
                    val length = readLength(position, end)
                    position = length.next
                    val valueEnd = position.checkedEnd(length.value, end)
                    val decoded = decodePreferenceUtf8(position, valueEnd)
                    if (decoded.isBlank() || decoded.length > MAX_PREFERENCE_KEY_LENGTH) {
                        throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
                    }
                    key = decoded
                    position = valueEnd
                }
                fieldNumber == 2 && wireType == 2 -> {
                    if (hasValue) {
                        throw WalletUpgradeBackupException(
                            "PREFERENCES_INVENTORY_INVALID"
                        )
                    }
                    val length = readLength(position, end)
                    position = length.next
                    val valueEnd = position.checkedEnd(length.value, end)
                    value = readPreferenceValue(position, valueEnd)
                    hasValue = true
                    position = valueEnd
                }
                else -> throw WalletUpgradeBackupException(
                    "PREFERENCES_INVENTORY_INVALID"
                )
            }
        }
        if (!hasValue) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }
        return PreferenceEntry(
            key = key ?: throw WalletUpgradeBackupException(
                "PREFERENCES_INVENTORY_INVALID"
            ),
            booleanValue = value.booleanValue,
            stringValue = value.stringValue,
        )
    }

    private fun ByteArray.readPreferenceValue(start: Int, end: Int): PreferenceValue {
        var position = start
        var booleanValue: Boolean? = null
        var stringValue: String? = null
        var valueField: Int? = null
        while (position < end) {
            val tag = readVarint(position, end)
            position = tag.next
            val fieldNumber = (tag.value ushr 3).toInt()
            val wireType = (tag.value and 0x07).toInt()
            if (fieldNumber == 0) {
                throw WalletUpgradeBackupException(
                    "PREFERENCES_INVENTORY_INVALID"
                )
            }
            val expectedWireType = when (fieldNumber) {
                1, 3, 4 -> 0
                2 -> 5
                5, 6, 8 -> 2
                7 -> 1
                else -> throw WalletUpgradeBackupException(
                    "PREFERENCES_INVENTORY_INVALID"
                )
            }
            if (wireType != expectedWireType || valueField != null) {
                throw WalletUpgradeBackupException(
                    "PREFERENCES_INVENTORY_INVALID"
                )
            }
            valueField = fieldNumber
            when {
                fieldNumber == 1 && wireType == 0 -> {
                    val value = readVarint(position, end)
                    if (value.value !in 0L..1L) {
                        throw WalletUpgradeBackupException(
                            "PREFERENCES_INVENTORY_INVALID"
                        )
                    }
                    booleanValue = value.value == 1L
                    position = value.next
                }
                fieldNumber == 5 && wireType == 2 -> {
                    val length = readLength(position, end)
                    position = length.next
                    val valueEnd = position.checkedEnd(length.value, end)
                    stringValue = decodePreferenceUtf8(position, valueEnd)
                    position = valueEnd
                }
                else -> position = skipWireValue(position, end, wireType)
            }
        }
        if (valueField == null) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }
        return PreferenceValue(
            booleanValue = booleanValue,
            stringValue = stringValue,
        )
    }

    private fun File.readSharedPreferenceEntries(): List<PreferenceEntry> {
        if (!existsNoFollow()) return emptyList()
        val bytes = readBoundedBytesNoFollow(
            minimumBytes = 1,
            maximumBytes = MAX_PREFERENCES_INSPECTION_BYTES,
            failureCode = "PREFERENCES_INVENTORY_INVALID",
        )
        return try {
            ByteArrayInputStream(bytes).use { input ->
                val parser = Xml.newPullParser()
                parser.setInput(input, Charsets.UTF_8.name())
                val keys = mutableSetOf<String>()
                val entries = mutableListOf<PreferenceEntry>()
                var rootSeen = false
                var rootClosed = false
                var setDepth: Int? = null
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    when (parser.eventType) {
                        XmlPullParser.START_TAG -> when (parser.depth) {
                            1 -> {
                                if (rootSeen || parser.name != "map") {
                                    throw WalletUpgradeBackupException(
                                        "PREFERENCES_INVENTORY_INVALID"
                                    )
                                }
                                rootSeen = true
                            }
                            2 -> {
                                if (!rootSeen || rootClosed) {
                                    throw WalletUpgradeBackupException(
                                        "PREFERENCES_INVENTORY_INVALID"
                                    )
                                }
                                val type = parser.name
                                if (type !in setOf(
                                        "string",
                                        "boolean",
                                        "int",
                                        "long",
                                        "float",
                                        "set",
                                    )
                                ) {
                                    throw WalletUpgradeBackupException(
                                        "PREFERENCES_INVENTORY_INVALID"
                                    )
                                }
                                val key = parser.getAttributeValue(null, "name")
                                if (
                                    key.isNullOrBlank() ||
                                    key.length > MAX_PREFERENCE_KEY_LENGTH ||
                                    !keys.add(key)
                                ) {
                                    throw WalletUpgradeBackupException(
                                        "PREFERENCES_INVENTORY_INVALID"
                                    )
                                }
                                val entry = when (type) {
                                    "string" -> PreferenceEntry(
                                        key = key,
                                        booleanValue = null,
                                        stringValue = parser.nextText(),
                                    )
                                    "boolean" -> PreferenceEntry(
                                        key = key,
                                        booleanValue = when (
                                            parser.getAttributeValue(null, "value")
                                        ) {
                                            "true" -> true
                                            "false" -> false
                                            else -> throw WalletUpgradeBackupException(
                                                "PREFERENCES_INVENTORY_INVALID"
                                            )
                                        },
                                        stringValue = null,
                                    )
                                    "int" -> {
                                        parser.requiredNumericAttribute().toInt()
                                        PreferenceEntry(key, null, null)
                                    }
                                    "long" -> {
                                        parser.requiredNumericAttribute().toLong()
                                        PreferenceEntry(key, null, null)
                                    }
                                    "float" -> {
                                        val value = parser.requiredNumericAttribute().toFloat()
                                        if (!value.isFinite()) {
                                            throw WalletUpgradeBackupException(
                                                "PREFERENCES_INVENTORY_INVALID"
                                            )
                                        }
                                        PreferenceEntry(key, null, null)
                                    }
                                    "set" -> {
                                        setDepth = parser.depth
                                        PreferenceEntry(key, null, null)
                                    }
                                    else -> error("unreachable")
                                }
                                entries += entry
                            }
                            3 -> {
                                if (setDepth != 2 || parser.name != "string") {
                                    throw WalletUpgradeBackupException(
                                        "PREFERENCES_INVENTORY_INVALID"
                                    )
                                }
                                parser.nextText()
                            }
                            else -> throw WalletUpgradeBackupException(
                                "PREFERENCES_INVENTORY_INVALID"
                            )
                        }
                        XmlPullParser.END_TAG -> when (parser.depth) {
                            2 -> if (parser.name == "set") setDepth = null
                            1 -> {
                                if (parser.name != "map") {
                                    throw WalletUpgradeBackupException(
                                        "PREFERENCES_INVENTORY_INVALID"
                                    )
                                }
                                rootClosed = true
                            }
                        }
                        XmlPullParser.TEXT,
                        XmlPullParser.CDSECT -> if (!parser.text.isNullOrBlank()) {
                            throw WalletUpgradeBackupException(
                                "PREFERENCES_INVENTORY_INVALID"
                            )
                        }
                        XmlPullParser.DOCDECL,
                        XmlPullParser.ENTITY_REF -> throw WalletUpgradeBackupException(
                            "PREFERENCES_INVENTORY_INVALID"
                        )
                    }
                    parser.next()
                }
                if (!rootSeen || !rootClosed || setDepth != null) {
                    throw WalletUpgradeBackupException(
                        "PREFERENCES_INVENTORY_INVALID"
                    )
                }
                entries
            }
        } catch (error: WalletUpgradeBackupException) {
            throw error
        } catch (_: Exception) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        } finally {
            bytes.fill(0)
        }
    }

    private fun XmlPullParser.requiredNumericAttribute(): String =
        getAttributeValue(null, "value")
            ?.takeIf(String::isNotBlank)
            ?: throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")

    private fun ByteArray.readLength(start: Int, end: Int): WireVarint {
        val value = readVarint(start, end)
        if (value.value > Int.MAX_VALUE) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }
        return value
    }

    private fun ByteArray.decodePreferenceUtf8(start: Int, end: Int): String {
        val copy = copyOfRange(start, end)
        return try {
            copy.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        } finally {
            copy.fill(0)
        }
    }

    private fun ByteArray.readVarint(start: Int, end: Int): WireVarint {
        var position = start
        var shift = 0
        var result = 0L
        while (position < end && shift <= 63) {
            val byte = this[position].toInt() and 0xff
            if (shift == 63 && (byte and 0x7e) != 0) {
                throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
            }
            result = result or ((byte and 0x7f).toLong() shl shift)
            position += 1
            if (byte and 0x80 == 0) return WireVarint(result, position)
            shift += 7
        }
        throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
    }

    private fun ByteArray.skipWireValue(start: Int, end: Int, wireType: Int): Int =
        when (wireType) {
            0 -> readVarint(start, end).next
            1 -> start.checkedEnd(8, end)
            2 -> {
                val length = readLength(start, end)
                length.next.checkedEnd(length.value, end)
            }
            5 -> start.checkedEnd(4, end)
            else -> throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }

    private fun Int.checkedEnd(length: Long, limit: Int): Int {
        if (length < 0 || length > Int.MAX_VALUE) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }
        val result = toLong() + length
        if (result < this || result > limit) {
            throw WalletUpgradeBackupException("PREFERENCES_INVENTORY_INVALID")
        }
        return result.toInt()
    }

    private fun isCompleteAndValid(
        context: Context,
        directory: File,
        expectedRegularLinkCount: Long = 1L,
    ): Boolean {
        if (expectedRegularLinkCount <= 0L) return false
        if (!directory.isDirectoryNoFollow()) return false
        val strictFiles = runCatching {
            strictRegularFiles(directory)
        }.getOrNull() ?: return false
        val completion = File(directory, ".complete")
        val completionValue = runCatching {
            completion.readBoundedUtf8NoFollow(
                minimumBytes = 1,
                maximumBytes = 32,
                failureCode = "BACKUP_NAMESPACE_INVALID",
            )
        }.getOrNull()
        if (completionValue != "verified") {
            return false
        }
        val manifestFile = File(directory, "manifest.json")
        if (
            !manifestFile.isRegularFileNoFollow() ||
            manifestFile.length() !in 1..MAX_MANIFEST_BYTES
        ) return false
        return runCatching {
            val manifestText = manifestFile.readBoundedUtf8NoFollow(
                minimumBytes = 1,
                maximumBytes = MAX_MANIFEST_BYTES,
                failureCode = "BACKUP_NAMESPACE_INVALID",
            )
            val manifest = JSONObject(manifestText)
            val backupGeneration = manifest.getLong("backupGeneration")
            val sourceDatabaseVersion = manifest.getInt("sourceDatabaseVersion")
            val targetDatabaseVersion = manifest.getInt("targetDatabaseVersion")
            if (
                manifest.getInt("formatVersion") != BACKUP_FORMAT_VERSION ||
                backupGeneration <= 0L ||
                targetDatabaseVersion !in
                    OLDEST_RECOVERABLE_BACKUP_TARGET_VERSION..CURRENT_DATABASE_VERSION ||
                sourceDatabaseVersion !in 1..targetDatabaseVersion
            ) {
                return@runCatching false
            }
            val sourceFingerprint = manifest.getString("sourceFingerprint")
            val legacyWalletIdsSha256 =
                manifest.getString("legacyWalletIdsSha256")
            val selectedWalletIdSha256 =
                if (manifest.isNull("selectedWalletIdSha256")) {
                    null
                } else {
                    manifest.getString("selectedWalletIdSha256")
                }
            if (
                !SHA256.matches(sourceFingerprint) ||
                manifest.getInt("legacyAccountCount") < 0 ||
                !SHA256.matches(legacyWalletIdsSha256) ||
                (
                    selectedWalletIdSha256 != null &&
                        !SHA256.matches(selectedWalletIdSha256)
                    )
            ) {
                return@runCatching false
            }
            val files = manifest.getJSONArray("files")
            if (files.length() !in 1..MAX_BACKUP_FILES) return@runCatching false
            val names = mutableSetOf<String>()
            val validatedRecords = mutableListOf<BackupRecord>()
            val directoryPath =
                directory.absoluteFile.toPath().normalize()
            val initialFileIdentities = strictFiles.associate { file ->
                val filePath = file.absoluteFile.toPath().normalize()
                directoryPath.relativize(filePath).toString() to
                    file.regularFileIdentityNoFollow(
                        "BACKUP_NAMESPACE_INVALID"
                    )
            }
            val initialLinkIdentities = strictFiles.associate { file ->
                val filePath = file.absoluteFile.toPath().normalize()
                directoryPath.relativize(filePath).toString() to
                    file.regularLinkIdentityNoFollow(
                        "BACKUP_NAMESPACE_INVALID"
                    )
            }
            val initialFingerprints = strictFiles.associate { file ->
                val filePath = file.absoluteFile.toPath().normalize()
                directoryPath.relativize(filePath).toString() to
                    file.inspectRegularNoFollow("BACKUP_NAMESPACE_INVALID")
            }
            if (
                initialLinkIdentities.values.any {
                    it.linkCount != expectedRegularLinkCount
                }
            ) {
                return@runCatching false
            }
            for (index in 0 until files.length()) {
                val record = files.getJSONObject(index)
                val name = record.getString("name")
                if (
                    name.isBlank() ||
                    name.startsWith(File.separator) ||
                    name.split('/', '\\').any { it == ".." } ||
                    !names.add(name)
                ) {
                    return@runCatching false
                }
                val unresolvedFile = File(directory, name)
                if (!unresolvedFile.isRegularFileNoFollow()) {
                    return@runCatching false
                }
                val filePath =
                    unresolvedFile.absoluteFile.toPath().normalize()
                if (!filePath.startsWith(directoryPath)) {
                    return@runCatching false
                }
                val hash = record.getString("sha256")
                if (
                    !unresolvedFile.isRegularFileNoFollow() ||
                    unresolvedFile.length() != record.getLong("size") ||
                    !SHA256.matches(hash) ||
                    unresolvedFile.sha256(
                        failureCode = "BACKUP_NAMESPACE_INVALID",
                        expectedSize = record.getLong("size"),
                    ) != hash
                ) {
                    return@runCatching false
                }
                validatedRecords += BackupRecord(
                    name = name,
                    size = record.getLong("size"),
                    sha256 = hash,
                )
            }
            val confirmedStrictFiles = strictRegularFiles(directory)
            val confirmedFileIdentities = confirmedStrictFiles.associate { file ->
                val filePath = file.absoluteFile.toPath().normalize()
                directoryPath.relativize(filePath).toString() to
                    file.regularFileIdentityNoFollow(
                        "BACKUP_NAMESPACE_INVALID"
                    )
            }
            val confirmedLinkIdentities = confirmedStrictFiles.associate { file ->
                val filePath = file.absoluteFile.toPath().normalize()
                directoryPath.relativize(filePath).toString() to
                    file.regularLinkIdentityNoFollow(
                        "BACKUP_NAMESPACE_INVALID"
                    )
            }
            if (
                confirmedFileIdentities != initialFileIdentities ||
                confirmedLinkIdentities != initialLinkIdentities ||
                confirmedLinkIdentities.values.any {
                    it.linkCount != expectedRegularLinkCount
                }
            ) {
                return@runCatching false
            }
            val actualNames = mutableSetOf<String>()
            for (file in confirmedStrictFiles) {
                val filePath = file.absoluteFile.toPath().normalize()
                if (!filePath.startsWith(directoryPath)) {
                    return@runCatching false
                }
                actualNames += directoryPath.relativize(filePath).toString()
                    .replace(File.separatorChar, '/')
            }
            REQUIRED_DATABASE_BACKUP in names &&
                actualNames == names + setOf("manifest.json", ".complete") &&
                directory.name == backupDirectoryName(
                    sourceVersion = sourceDatabaseVersion,
                    targetVersion = targetDatabaseVersion,
                    generation = backupGeneration,
                    fingerprint = sourceFingerprint,
                ) &&
                manifestFile.readBoundedUtf8NoFollow(
                    minimumBytes = 1,
                    maximumBytes = MAX_MANIFEST_BYTES,
                    failureCode = "BACKUP_NAMESPACE_INVALID",
                ) == manifestText &&
                completion.readBoundedUtf8NoFollow(
                    minimumBytes = 1,
                    maximumBytes = 32,
                    failureCode = "BACKUP_NAMESPACE_INVALID",
                ) == "verified" &&
                validatedRecords.all { record ->
                    File(directory, record.name).sha256(
                        failureCode = "BACKUP_NAMESPACE_INVALID",
                        expectedSize = record.size,
                    ) == record.sha256
                } &&
                confirmedStrictFiles.all { file ->
                    file.regularLinkIdentityNoFollow(
                        "BACKUP_NAMESPACE_INVALID"
                    ).let { link ->
                        link == confirmedLinkIdentities[
                            directoryPath.relativize(
                                file.absoluteFile.toPath().normalize()
                            ).toString()
                        ] && link.linkCount == expectedRegularLinkCount
                    }
                } &&
                sourceFingerprint == sha256Text(
                    validatedRecords.sortedBy(BackupRecord::name)
                        .joinToString(separator = "\n") {
                            "${it.name}\u0000${it.size}\u0000${it.sha256}"
                        }
                ) &&
                validateCopiedWalletInventory(
                    context = context,
                    directory = directory,
                    manifest = manifest,
                    names = names,
                ) &&
                hasExactRegularLinkInventory(
                    directory = directory,
                    expected = confirmedLinkIdentities,
                    expectedFingerprints = initialFingerprints,
                    expectedLinkCount = expectedRegularLinkCount,
                )
        }.getOrDefault(false)
    }

    private fun hasExactRegularLinkInventory(
        directory: File,
        expected: Map<String, RegularLinkIdentity>,
        expectedFingerprints: Map<String, FileFingerprint>,
        expectedLinkCount: Long,
    ): Boolean = runCatching {
        val directoryPath = directory.absoluteFile.toPath().normalize()
        val filesBefore = strictRegularFiles(directory)
        val linksBefore = filesBefore.associate { file ->
            val filePath = file.absoluteFile.toPath().normalize()
            directoryPath.relativize(filePath).toString() to
                file.regularLinkIdentityNoFollow("BACKUP_NAMESPACE_INVALID")
        }
        val fingerprints = filesBefore.associate { file ->
            val filePath = file.absoluteFile.toPath().normalize()
            directoryPath.relativize(filePath).toString() to
                file.inspectRegularNoFollow("BACKUP_NAMESPACE_INVALID")
        }
        val linksAfter = strictRegularFiles(directory).associate { file ->
            val filePath = file.absoluteFile.toPath().normalize()
            directoryPath.relativize(filePath).toString() to
                file.regularLinkIdentityNoFollow("BACKUP_NAMESPACE_INVALID")
        }
        linksBefore == expected &&
            linksAfter == expected &&
            fingerprints == expectedFingerprints &&
            linksAfter.values.all { it.linkCount == expectedLinkCount }
    }.getOrDefault(false)

    private fun validateCopiedWalletInventory(
        context: Context,
        directory: File,
        manifest: JSONObject,
        names: Set<String>,
    ): Boolean {
        val sourceVersion = manifest.getInt("sourceDatabaseVersion")
        val validationDirectory = File(
            context.cacheDir,
            "wallet-backup-validation-${java.util.UUID.randomUUID()}",
        )
        val validationParent = validationDirectory.parentFile
            ?: throw WalletUpgradeBackupException(
                "CREATE_BACKUP_VALIDATION_DIRECTORY"
            )
        val validationParentIdentity =
            validationParent.directoryInodeIdentityNoFollow(
                "CREATE_BACKUP_VALIDATION_DIRECTORY"
            )
        createDirectoryExclusive(
            directory = validationDirectory,
            failureCode = "CREATE_BACKUP_VALIDATION_DIRECTORY",
        )
        val validationDirectoryIdentity =
            validationDirectory.directoryInodeIdentityNoFollow(
                "CREATE_BACKUP_VALIDATION_DIRECTORY"
            )
        if (
            validationParent.directoryInodeIdentityNoFollow(
                "CREATE_BACKUP_VALIDATION_DIRECTORY"
            ) != validationParentIdentity
        ) {
            throw WalletUpgradeBackupException(
                "CREATE_BACKUP_VALIDATION_DIRECTORY"
            )
        }
        return try {
            val databaseSource = File(directory, REQUIRED_DATABASE_BACKUP)
            val databaseCopy = File(validationDirectory, DATABASE_NAME)
            copyDurably(databaseSource, databaseCopy)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                val backupName = "$REQUIRED_DATABASE_BACKUP$suffix"
                if (backupName in names) {
                    copyDurably(
                        File(directory, backupName),
                        File(validationDirectory, "$DATABASE_NAME$suffix"),
                    )
                }
            }
            if (
                readVersion(databaseCopy) != sourceVersion ||
                !databaseCopy.passesSqliteIntegrityCheck()
            ) {
                return false
            }
            val inventory = readWalletDatabaseInventory(databaseCopy, sourceVersion)
            val preferences = walletPreferenceInventory(
                dataStore = File(
                    directory,
                    "files/datastore/sora_prefs_datastore.preferences_pb",
                ),
                sharedPreferences = File(
                    directory,
                    "shared_prefs/sora_prefs.xml",
                ),
                sharedPreferencesBackup = File(
                    directory,
                    "shared_prefs/sora_prefs.xml.bak",
                ),
            )
            val storageCoherent =
                runCatching {
                    validateWalletStorageCoherence(inventory, preferences)
                }.isSuccess ||
                    (
                        sourceVersion == CURRENT_DATABASE_VERSION &&
                            runCatching {
                                requireLegacyRecoveryPreferenceCoherence(
                                    inventory,
                                    preferences,
                                )
                            }.isSuccess
                        )
            if (!storageCoherent) return false
            val walletIds = inventory.accounts
                .map(SoraAccountLocal::substrateAddress)
                .toSet()
            val selectedHash = preferences.selectedWalletId?.let(::sha256Text)
            inventory.accounts.size == manifest.getInt("legacyAccountCount") &&
                sha256Text(walletIds.sorted().joinToString(separator = "\n")) ==
                manifest.getString("legacyWalletIdsSha256") &&
                if (manifest.isNull("selectedWalletIdSha256")) {
                    selectedHash == null
                } else {
                    selectedHash == manifest.getString("selectedWalletIdSha256")
                }
        } finally {
            removeExactValidationTree(
                directory = validationDirectory,
                expectedRootIdentity = validationDirectoryIdentity,
                expectedParentIdentity = validationParentIdentity,
            )
        }
    }

    private fun File.passesSqliteIntegrityCheck(): Boolean =
        SQLiteDatabase.openDatabase(
            path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            FAIL_CLOSED_DATABASE_ERROR_HANDLER,
        ).use { sqlite ->
            sqlite.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getString(0).equals("ok", ignoreCase = true) &&
                    !cursor.moveToNext()
            }
        }

    private fun File.backupName(context: Context): String {
        val dataPath = File(context.applicationInfo.dataDir)
            .absoluteFile
            .toPath()
            .normalize()
        val filePath = absoluteFile.toPath().normalize()
        return if (filePath.startsWith(dataPath)) {
            dataPath.relativize(filePath).toString()
        } else {
            name
        }
    }

    private fun File.inspectRegularNoFollow(
        failureCode: String,
    ): FileFingerprint =
        openRegularNoFollow(failureCode).use { opened ->
            val digest = MessageDigest.getInstance("SHA-256")
            var bytesRead = 0L
            val input = opened.input
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    bytesRead = Math.addExact(bytesRead, count.toLong())
                }
                if (
                    bytesRead != opened.size ||
                    Os.fstat(input.fd).st_size != opened.size
                ) {
                    throw WalletUpgradeBackupException(failureCode)
                }
            } finally {
                buffer.fill(0)
            }
            FileFingerprint(
                size = opened.size,
                sha256 = digest.digest().toLowerHex(),
            )
        }

    private fun File.sha256(
        failureCode: String = "BACKUP_SOURCE_INVALID",
        expectedSize: Long? = null,
    ): String {
        val fingerprint = inspectRegularNoFollow(failureCode)
        if (
            expectedSize != null &&
            fingerprint.size != expectedSize
        ) {
            throw WalletUpgradeBackupException(failureCode)
        }
        return fingerprint.sha256
    }

    private fun sha256Text(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .toLowerHex()

    private fun RecoveryArchiveFile.copyVerifiedTo(output: ZipOutputStream) {
        file.openRegularNoFollow("RECOVERY_SOURCE_CHANGED").use { opened ->
            if (opened.size != size) {
                throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            val input = opened.input
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                    copied = Math.addExact(copied, count.toLong())
                }
                if (Os.fstat(input.fd).st_size != size) {
                    throw WalletUpgradeBackupException(
                        "RECOVERY_SOURCE_CHANGED"
                    )
                }
            } finally {
                buffer.fill(0)
            }
            if (
                copied != size ||
                digest.digest().toLowerHex() != sha256
            ) {
                throw WalletUpgradeBackupException(
                    "RECOVERY_SOURCE_CHANGED"
                )
            }
        }
    }

    private fun RecoveryArchiveFile.verifyUnchanged() {
        if (
            !file.isRegularFileNoFollow() ||
            file.length() != size ||
            file.sha256(
                failureCode = "RECOVERY_SOURCE_CHANGED",
                expectedSize = size,
            ) != sha256
        ) {
            throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
        }
    }

    private fun ByteArray.toLowerHex(): String = joinToString("") {
        (it.toInt() and 0xff).toString(16).padStart(2, '0')
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this).toLowerHex()

    private fun copyDurably(source: File, destination: File) {
        source.openRegularNoFollow(
            "BACKUP_SOURCE_INVALID"
        ).use { openedSource ->
            destination.openNewRegularNoFollow(
                "CREATE_BACKUP_FILE"
            ).use { openedDestination ->
                val input = openedSource.input
                val output = openedDestination.output
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                try {
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                    output.fd.sync()
                    if (
                        Os.fstat(input.fd).st_size != openedSource.size ||
                        !OsConstants.S_ISREG(Os.fstat(output.fd).st_mode)
                    ) {
                        throw WalletUpgradeBackupException(
                            "BACKUP_SOURCE_INVALID"
                        )
                    }
                } finally {
                    buffer.fill(0)
                }
            }
        }
        if (
            !source.isRegularFileNoFollow() ||
            !destination.isRegularFileNoFollow()
        ) {
            throw WalletUpgradeBackupException("BACKUP_SOURCE_INVALID")
        }
    }

    private fun requireBackupCapacity(
        directory: File,
        sources: List<File>,
        availableBytesOverride: Long? = null,
    ) {
        val required = runCatching {
            sources.fold(MINIMUM_FREE_SPACE_HEADROOM) { total, source ->
                Math.addExact(
                    total,
                    Math.multiplyExact(source.length(), BACKUP_SPACE_MULTIPLIER),
                )
            }
        }.getOrElse { Long.MAX_VALUE }
        val available = availableBytesOverride ?: runCatching {
            StatFs(directory.absolutePath).availableBytes
        }.getOrElse {
            throw WalletUpgradeBackupException("STORAGE_CAPACITY_UNAVAILABLE")
        }
        if (available < required) {
            throw WalletUpgradeBackupException("INSUFFICIENT_BACKUP_STORAGE")
        }
    }

    private fun writeDurably(destination: File, value: String) {
        destination.openNewRegularNoFollow(
            "CREATE_BACKUP_FILE"
        ).use { openedOutput ->
            val output = openedOutput.output
            output.write(value.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
    }

    private fun fsyncDirectory(directory: File) {
        val descriptor = Os.open(
            directory.absolutePath,
            OsConstants.O_RDONLY or
                OsConstants.O_NOFOLLOW,
            0,
        )
        try {
            if (!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) {
                throw WalletUpgradeBackupException(
                    "BACKUP_NAMESPACE_INVALID"
                )
            }
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun Throwable.safeCode(): String = when (this) {
        is WalletUpgradeBackupException -> code
        is SecurityException -> "PERMISSION"
        else -> "IO"
    }

    private data class BackupRecord(
        val name: String,
        val size: Long,
        val sha256: String,
    )

    private class OpenedRegularFile(
        val descriptor: FileDescriptor,
        val input: FileInputStream,
        val size: Long,
    ) : Closeable {
        override fun close() {
            WalletUpgradeBackup.closeStreamAndDescriptor(
                closeStream = input::close,
                descriptor = descriptor,
            )
        }
    }

    private class OpenedRegularOutput(
        val descriptor: FileDescriptor,
        val output: FileOutputStream,
    ) : Closeable {
        override fun close() {
            WalletUpgradeBackup.closeStreamAndDescriptor(
                closeStream = output::close,
                descriptor = descriptor,
            )
        }
    }

    private class OpenedPublicationLock(
        val descriptor: FileDescriptor,
        private val output: FileOutputStream,
    ) : Closeable {
        val channel: FileChannel
            get() = output.channel

        override fun close() {
            WalletUpgradeBackup.closeStreamAndDescriptor(
                closeStream = output::close,
                descriptor = descriptor,
            )
        }
    }

    private fun closeStreamAndDescriptor(
        closeStream: () -> Unit,
        descriptor: FileDescriptor,
    ) {
        var failure: Throwable? = null
        try {
            closeStream()
        } catch (error: Throwable) {
            failure = error
        }
        // FileInputStream/FileOutputStream own the FileDescriptor passed to their
        // constructors and normally invalidate it from close(). Closing the same descriptor
        // unconditionally through Os.close() then reports EBADF after an otherwise successful
        // backup copy/hash. Retain the explicit close only as a fallback for a stream-close
        // failure that left the descriptor open.
        if (descriptor.valid()) {
            try {
                Os.close(descriptor)
            } catch (error: Throwable) {
                val firstFailure = failure
                if (firstFailure == null) {
                    failure = error
                } else {
                    firstFailure.addSuppressed(error)
                }
            }
        }
        failure?.let { throw it }
    }

    private data class FileFingerprint(
        val size: Long,
        val sha256: String,
    )

    private data class DirectoryIdentity(
        val device: Long,
        val inode: Long,
        val linkCount: Long,
        val size: Long,
        val modifiedAtSeconds: Long,
        val changedAtSeconds: Long,
    )

    private data class FileIdentity(
        val device: Long,
        val inode: Long,
        val size: Long,
        val modifiedAtSeconds: Long,
        val changedAtSeconds: Long,
    )

    private data class InodeIdentity(
        val device: Long,
        val inode: Long,
        val size: Long,
    )

    private data class RegularLinkIdentity(
        val inode: InodeIdentity,
        val linkCount: Long,
    )

    private data class DirectoryInodeIdentity(
        val device: Long,
        val inode: Long,
    )

    private data class BackupTreeFile(
        val relativeName: String,
        val identity: InodeIdentity,
        val fingerprint: FileFingerprint,
    )

    private data class BackupTreeDirectory(
        val relativeName: String,
        val identity: DirectoryInodeIdentity,
    )

    private data class BackupTreeSnapshot(
        val files: List<BackupTreeFile>,
        val directories: List<BackupTreeDirectory>,
    )

    private data class VerifiedBackupCandidate(
        val directory: File,
        val generation: Long,
    )

    private data class RecoveryArchiveSource(
        val files: List<RecoveryArchiveFile>,
        val archivePrefix: String,
        val sourceType: String,
    )

    private data class RecoveryArchiveFile(
        val file: File,
        val archiveName: String,
        val size: Long,
        val sha256: String,
    )

    private data class MigrationRecoverySnapshot(
        val token: String,
        val journal: WalletMigrationJournalLocal,
    )

    private data class WalletPreferenceInventory(
        val hasWalletMaterial: Boolean,
        val hasWrappedWalletKeyEvidence: Boolean,
        val scopedWalletIds: Set<String>,
        val selectedWalletId: String?,
        val legacyPureAddress: String?,
        val mnemonicWalletIds: Set<String>,
        val rawSeedWalletIds: Set<String>,
        val keyPairWalletIds: Set<String>,
        val incompleteKeyPairWalletIds: Set<String>,
        val watchOnlyWalletIds: Set<String>,
        val allKeys: Set<String>,
        val fingerprint: String,
    )

    private data class BackupSourceSnapshot(
        val files: List<BackupSourceRecord>,
        val fingerprint: String,
        val legacyAccountCount: Int,
        val legacyWalletIdsSha256: String,
        val selectedWalletIdSha256: String?,
    )

    private data class BackupSourceRecord(
        val source: File,
        val name: String,
        val size: Long,
        val sha256: String,
    )

    private data class WalletDatabaseInventory(
        val accounts: List<SoraAccountLocal>,
        val identities: List<WalletIdentityLocal> = emptyList(),
        val networkAccounts: List<NetworkAccountLocal> = emptyList(),
        val pendingWalletIds: List<String> = emptyList(),
        val deletionOperations: List<WalletDeletionOperationLocal> = emptyList(),
        val deletionTargets: List<Pair<String, String>> = emptyList(),
        val migrationJournals: List<WalletMigrationJournalLocal> = emptyList(),
    )

    private data class PreferenceEntry(
        val key: String,
        val booleanValue: Boolean?,
        val stringValue: String?,
    )

    private fun PreferenceEntry.isWalletEvidence(): Boolean = when {
        key.startsWith(WalletPreferenceKeys.WATCH_ONLY) -> booleanValue == true
        WalletPreferenceKeys.scopedEvidencePrefixes.any(key::startsWith) -> true
        key == WalletPreferenceKeys.REGISTRATION_STATE ->
            stringValue == REGISTRATION_FINISHED
        key in WalletPreferenceKeys.generalEvidenceKeys ->
            !stringValue.isNullOrBlank()
        else -> false
    }

    private data class PreferenceValue(
        val booleanValue: Boolean? = null,
        val stringValue: String? = null,
    )

    private data class WireVarint(
        val value: Long,
        val next: Int,
    )
}

class WalletUpgradeBackupException(val code: String) : IllegalStateException(code)
