package jp.co.soramitsu.core_db

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteReadOnlyDatabaseException
import android.os.Build
import android.os.StatFs
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Xml
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import jp.co.soramitsu.common.util.EncryptionUtil
import jp.co.soramitsu.common.util.KeyMaterialUnavailableException
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.data.WalletPreferenceIntegrity
import jp.co.soramitsu.common.data.WalletPreferenceKeys
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.logger.FirebaseWrapper.WalletRecoveryEvent
import jp.co.soramitsu.core_db.model.NetworkAccountLocal
import jp.co.soramitsu.core_db.model.SoraAccountLocal
import jp.co.soramitsu.core_db.model.WalletDeletionContract
import jp.co.soramitsu.core_db.model.WalletDeletionOperationLocal
import jp.co.soramitsu.core_db.model.WalletIdentityLocal
import jp.co.soramitsu.core_db.model.WalletMigrationIds
import jp.co.soramitsu.core_db.model.WalletMigrationJournalLocal
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.DataInputStream
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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
    private const val MAX_STARTUP_ADMISSION_BACKUP_ENTRIES =
        MAX_PUBLISHED_BACKUPS * (MAX_BACKUP_NAMESPACE_ENTRIES + 1)
    private const val MAX_PREFERENCES_INSPECTION_BYTES = 8L * 1_024L * 1_024L
    private const val FILE_IO_BUFFER_BYTES = 256 * 1_024
    private const val MAX_PREFERENCE_KEY_LENGTH = 1_024
    private const val MAX_WALLET_ID_LENGTH = 256
    private const val MINIMUM_FREE_SPACE_HEADROOM = 16L * 1_024L * 1_024L
    private const val BACKUP_SPACE_MULTIPLIER = 2L
    // A keys-only backup copies the encrypted preferences and wrapped-key files only. Room still
    // needs room for its own migration, so the database size is counted twice on top.
    private const val KEYS_ONLY_MINIMUM_HEADROOM = 1L * 1_024L * 1_024L
    private const val MAX_KEPT_VERIFIED_BACKUPS = 3
    private const val BACKUP_KIND_FULL = "full"
    private const val BACKUP_KIND_KEYS_ONLY = "keys-only"
    // Renamed-aside names never start with BACKUP_PREFIX, so they leave the scanned namespace.
    private const val BACKUP_QUARANTINE_PREFIX = "wallet-backup-quarantine-"
    private const val STORAGE_QUARANTINE_PREFIX = "wallet-quarantine-"
    private const val CORRUPT_DATABASE_COPY_PREFIX = "wallet-corrupt-quarantine-"
    private const val AUTO_RETRY_STATE_FILE = "wallet-migration-auto-retry"
    private const val STARTUP_FAILURE_STATE_FILE = "wallet-startup-failure-count"
    private var consecutiveStartupFailureCode: String? = null
    private var consecutiveStartupFailures = 0
    private const val MAX_AUTOMATIC_MIGRATION_RETRIES = 3

    /** Written by [requestSetUpAgain]; the next app process sets the old wallet files aside. */
    private const val SET_UP_AGAIN_MARKER = "wallet-set-up-again-requested"
    private const val SET_UP_AGAIN_SUFFIX = "-set-up-again"

    /**
     * A current-schema snapshot protects no pending migration (each later schema step makes its
     * own pre-migration backup). It is made only when cheap: small sources, enough space, and not
     * retried for a day after a failure. It never blocks startup.
     */
    private const val MAX_CURRENT_SCHEMA_SNAPSHOT_SOURCE_BYTES = 16L * 1_024L * 1_024L
    private const val SNAPSHOT_BACKOFF_FILE = "wallet-snapshot-backoff"
    private const val SNAPSHOT_BACKOFF_MILLIS = 24L * 60L * 60L * 1_000L

    /**
     * Free space kept for the Room schema steps when only the keys-only backup fits. The database
     * holds no secrets. Every schema step runs inside one SQLite transaction, so if space still
     * runs out the database stays exactly as it was and the person is shown the storage options.
     * The steps from 73 add small tables; older steps drop or rebuild cache tables. Space for one
     * rewrite of a database of up to this size is kept; a larger cache database needs no more for
     * correctness.
     */
    private const val KEYS_ONLY_MIGRATION_MARGIN_CAP = 32L * 1_024L * 1_024L

    /** Remembers the last pruning decision so unchanged backups are not deep-validated again. */
    private const val PRUNE_STATE_FILE = "wallet-backup-prune-state"
    private const val PRUNED_BACKUP_PREFIX = "wallet-backup-quarantine-pruned-"

    /** Android's SQLiteConnection message for a write statement on a read-only connection. */
    private const val READ_ONLY_CONNECTION_REFUSAL = "the connection is read-only"

    /** Never an account id: marks copied key material that no account can be shown to own. */
    private const val UNOWNED_RELEASE_ERA_KEYS = "\u0000unowned-release-era-keys"

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

    // Application.attachBaseContext completes this gate before Hilt can construct AppDatabase.
    // Retain one exact, context-bound admission so requirePrepared() can avoid repeating the full
    // verification moments later without overlooking a file change in between. Explicit prepare()
    // calls still always recheck, and the admission is consumed by the first database build.
    @Volatile
    private var preparedAdmission: StartupAdmission? = null

    // SoraApp's attachBaseContext call is the first production prepare() in every app process.
    // Later explicit retries must perform the full gate and must not mint another startup token.
    private var applicationAttachPreparationAttempted = false

    // An automatic verification retry was tried in this process and could not be authorized
    // (for example its counter could not be saved on a full disk). The recovery options are then
    // shown at once instead of waiting for a retry that cannot happen.
    @Volatile
    private var automaticRetryFailedInProcess = false

    /** Material that is set aside (never deleted) because no account row owns it. */
    @Volatile
    private var quarantine: WalletQuarantine = WalletQuarantine.NONE

    /** A failure that may pass on retry; it is reported in memory only, never journaled. */
    @Volatile
    private var transientVerificationFailure: String? = null

    /**
     * The wallet verification failed in this process after the startup checks had passed
     * ([noteMigrationRecoveryRequired]). Kept in memory only, so the recovery options (and
     * [requestSetUpAgain]) describe the same state the recovery screen shows. Unlike
     * [blockingFailure] it does not close database access: the process is already read-only
     * through [WalletRecoveryCapabilityGate].
     */
    @Volatile
    private var inSessionRecoveryCode: String? = null

    /** Non-blocking problems met during the last preparation (for example a skipped snapshot). */
    @Volatile
    private var nonBlockingWarnings: List<String> = emptyList()

    @Volatile
    private var recoveryFacts: RecoveryFacts = RecoveryFacts()

    @Volatile
    private var databaseCorruptionCopied = false

    /** Set by [simulateProcessStartForTest]: the next [prepare] behaves like a new app process. */
    @Volatile
    private var processStartForTest = false

    /** The application context of the last preparation (for support copies made later). */
    @Volatile
    private var preparedContext: Context? = null

    /**
     * Counts the expensive steps of a preparation: full backup validations, bytes hashed and files
     * copied. Device tests use it to prove that a start with no pending migration stays on the
     * cheap path (directory listings, small manifest reads, two small SQLite reads). Counting
     * only; nothing reads it to make a decision.
     */
    @VisibleForTesting
    internal object WorkCountersForTest {
        val fullBackupValidations = AtomicInteger()
        val hashedBytes = AtomicLong()
        val copiedFiles = AtomicInteger()

        fun reset() {
            fullBackupValidations.set(0)
            hashedBytes.set(0L)
            copiedFiles.set(0)
        }
    }

    data class Failure(
        val code: String,
        val requiredBytes: Long? = null,
        val availableBytes: Long? = null,
    )

    fun blockingFailure(): Failure? = blockingFailure

    fun hasValidatedLegacyRecovery(): Boolean = migrationRecoverySnapshot != null

    /** Key sets and settings that were set aside during the last preparation. */
    fun quarantine(): WalletQuarantine = quarantine

    fun nonBlockingWarnings(): List<String> = nonBlockingWarnings

    /**
     * What the last preparation learned about the stored wallet data (key names, account names,
     * the release-era owner), for the recovery screens. Nothing in it is secret.
     */
    fun recoveryFacts(): RecoveryFacts = recoveryFacts

    /**
     * The forward paths available for the current blocking or transient state, or null when the
     * wallet is usable. Every state offers at least Try again and Contact support; the other
     * paths are offered only where they work (see [WalletRecoveryOptions]).
     */
    fun recoveryOptions(): WalletRecoveryOptions? {
        val failure = blockingFailure
        val transient = transientVerificationFailure
        val code = failure?.code ?: transient ?: inSessionRecoveryCode ?: return null
        return WalletRecoveryOptions.forState(
            code = code,
            transientFailure = failure == null && transient != null,
            facts = recoveryFacts.copy(
                hasMigrationRecoverySnapshot = migrationRecoverySnapshot != null,
                keyMaterialUnavailable = isWalletKeyUnavailable(),
            ),
            requiredBytes = failure?.requiredBytes,
            availableBytes = failure?.availableBytes,
            consecutiveFailedStarts = if (code == consecutiveStartupFailureCode) consecutiveStartupFailures else 0,
        )
    }

    /** The wallet key cannot be used right now (missing Keystore key or busy Keystore). */
    private fun isWalletKeyUnavailable(): Boolean =
        WalletRecoveryOptions.isKeyMaterialUnavailable(blockingFailure?.code) ||
            WalletRecoveryOptions.isKeyMaterialUnavailable(transientVerificationFailure)

    fun canAuthorizeMigrationRetry(): Boolean {
        if (transientVerificationFailure != null && migrationRecoverySnapshot == null) {
            return false
        }
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
        val retainStartupAdmission = !applicationAttachPreparationAttempted
        applicationAttachPreparationAttempted = true
        val firstInProcess = retainStartupAdmission || processStartForTest
        processStartForTest = false
        if (firstInProcess) {
            automaticRetryFailedInProcess = false
            // Only the first preparation of a process may set wallet files aside: nothing has
            // opened the DataStore or Room yet, so no open handle can write the old data back.
            val applicationContext = context.applicationContext ?: context
            val setUpAgain = runCatching { performPendingSetUpAgain(applicationContext) }
            setUpAgain.exceptionOrNull()?.let { error ->
                preparedAdmission = null
                migrationRecoverySnapshot = null
                blockingFailure = Failure("SET_UP_AGAIN_INCOMPLETE")
                recoveryFacts = RecoveryFacts(hasWalletFiles = true)
                WalletRecoveryCapabilityGate.enterBlocked()
                return Result.failure(error)
            }
        }
        val result = prepareWithCapacityOverride(
            context = context,
            availableBackupBytesOverride = null,
            retainStartupAdmission = retainStartupAdmission,
        )
        if (firstInProcess) rememberStartupFailure(context.applicationContext ?: context, blockingFailure?.code)
        else if (result.isSuccess) rememberStartupFailure(context.applicationContext ?: context, null)
        return result
    }

    /** Counts separate process starts, never taps on Try again or repeated DI preparation. */
    private fun rememberStartupFailure(context: Context, code: String?) {
        val state = File(context.noBackupFilesDir, STARTUP_FAILURE_STATE_FILE)
        if (code == null) {
            consecutiveStartupFailureCode = null
            consecutiveStartupFailures = 0
            runCatching { if (state.existsNoFollow()) { Os.remove(state.path); fsyncDirectory(context.noBackupFilesDir) } }
            return
        }
        val previous = runCatching {
            state.readBoundedUtf8NoFollow(1, 160, "STARTUP_FAILURE_STATE_INVALID").split('\n')
        }.getOrNull()
        val previousCount = previous?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3) ?: 0
        consecutiveStartupFailureCode = code
        consecutiveStartupFailures = if (previous?.firstOrNull() == code) (previousCount + 1).coerceAtMost(3) else 1
        // Counting is diagnostic. An unavailable counter never changes the original cause or
        // authorizes a reset; keep the existing retry/export routes if it cannot be persisted.
        val persisted = runCatching {
            val staging = File(context.noBackupFilesDir, ".$STARTUP_FAILURE_STATE_FILE.tmp")
            if (staging.existsNoFollow()) Os.remove(staging.path)
            writeDurably(staging, "$code\n$consecutiveStartupFailures")
            Os.rename(staging.path, state.path)
            fsyncDirectory(context.noBackupFilesDir)
        }.isSuccess
        if (!persisted) consecutiveStartupFailures = 0
    }

    /** Makes the next [prepare] behave like the first preparation of a new app process. */
    @VisibleForTesting
    @Synchronized
    fun simulateProcessStartForTest() {
        processStartForTest = true
    }

    /**
     * User-requested "Try again". Re-runs every startup check and, for an interrupted or failed
     * wallet verification, authorizes one more verification run regardless of the automatic retry
     * budget. Never deletes or rewrites wallet material.
     *
     * Refused (SET_UP_AGAIN_INCOMPLETE, nothing checked or changed) while a confirmed "Set up
     * this device again" waits for the next app start: only a new process may set the old files
     * aside, so the app must not verify and open them in this one. The caller restarts instead.
     */
    @Synchronized
    fun retry(context: Context): Result<Unit> {
        if (isSetUpAgainRequested(context)) {
            return Result.failure(WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE"))
        }
        transientVerificationFailure = null
        inSessionRecoveryCode = null
        if (migrationRecoverySnapshot != null && canAuthorizeMigrationRetry()) {
            val authorized = authorizeMigrationRetry(context)
            if (authorized.isSuccess) return authorized
        }
        val result = prepareWithCapacityOverride(
            context = context,
            availableBackupBytesOverride = null,
            retainStartupAdmission = false,
        )
        val failure = blockingFailure
        if (
            result.isFailure &&
            failure?.code == "WALLET_MIGRATION_RECOVERY_REQUIRED" &&
            migrationRecoverySnapshot != null
        ) {
            val authorized = authorizeMigrationRetry(context)
            if (authorized.isSuccess) return authorized
            blockingFailure = failure
            WalletRecoveryCapabilityGate.enterBlocked()
        }
        return result
    }

    /**
     * Called once when the app's screens start, before the wallet verification runs (Splash).
     * An interrupted or failed verification is retried automatically, at most
     * [MAX_AUTOMATIC_MIGRATION_RETRIES] times in a row, before the recovery options are shown.
     * Only a start that runs the verification uses this budget: background starts (workers,
     * receivers, boot) never call this. The retry re-runs only the copy-on-write verification;
     * account rows and encrypted preferences are never changed by it.
     */
    @Synchronized
    fun authorizeAutomaticMigrationRetry(context: Context): Boolean {
        val failure = blockingFailure ?: return false
        if (
            failure.code != "WALLET_MIGRATION_RECOVERY_REQUIRED" ||
            migrationRecoverySnapshot == null ||
            automaticRetryFailedInProcess
        ) {
            return false
        }
        val applicationContext = context.applicationContext ?: context
        if (automaticMigrationRetriesUsed(applicationContext) >= MAX_AUTOMATIC_MIGRATION_RETRIES) {
            return false
        }
        // From here on this process has tried. If the counter cannot be saved or the retry
        // cannot be authorized, the recovery options are shown now rather than after a retry
        // that cannot happen.
        if (!claimMigrationRetry(applicationContext)) {
            automaticRetryFailedInProcess = true
            return false
        }
        if (authorizeMigrationRetry(applicationContext).isSuccess) return true
        automaticRetryFailedInProcess = true
        blockingFailure = failure
        WalletRecoveryCapabilityGate.enterBlocked()
        return false
    }

    /** True when [authorizeAutomaticMigrationRetry] would retry. Reads one small file. */
    @Synchronized
    fun isAutomaticMigrationRetryAvailable(context: Context): Boolean =
        !automaticRetryFailedInProcess &&
            blockingFailure?.code == "WALLET_MIGRATION_RECOVERY_REQUIRED" &&
            migrationRecoverySnapshot != null &&
            automaticMigrationRetriesUsed(context.applicationContext ?: context) <
            MAX_AUTOMATIC_MIGRATION_RETRIES

    /**
     * The startup failure that the first screen must show at once, or null when the start can
     * continue: nothing blocks it, or an interrupted verification is about to be (or was just)
     * retried automatically. Consistent with a concurrent [authorizeAutomaticMigrationRetry].
     */
    @Synchronized
    fun startupFailureToShow(context: Context): Failure? {
        val failure = blockingFailure ?: return null
        return if (isAutomaticMigrationRetryAvailable(context)) null else failure
    }

    /**
     * "Set up this device again (keeps old data)", offered when
     * [WalletRecoveryOptions.canSetUpAgain]. Records the request and its
     * [WalletRecoveryOptions.setUpAgainScope] durably; the next app process, before anything opens
     * the wallet files, renames them into `no_backup/wallet-quarantine-<time>-set-up-again/`
     * (renamed, never copied and deleted, never removed). The Android Keystore key is not touched.
     *
     * - [SetUpAgainScope.DATABASE_ONLY]: only the database moves. Every stored key set stays in
     *   place and is offered again (import adopts a matching key set; "Restore wallets found on
     *   this phone" adopts the others).
     * - [SetUpAgainScope.ALL_WALLET_FILES]: the preference files and the wrapped key move too,
     *   because this phone cannot use them (missing Keystore key, unreadable preference files).
     *
     * The app then starts like a new install, and the person restores with a recovery phrase or a
     * Google Drive backup. The caller restarts the app process after this succeeds. Refused
     * (SET_UP_AGAIN_NOT_AVAILABLE) when no recovery state offers it.
     */
    @Synchronized
    fun requestSetUpAgain(context: Context): Result<Unit> = runCatching {
        val scope = recoveryOptions()?.setUpAgainScope ?: SetUpAgainScope.NONE
        if (scope == SetUpAgainScope.NONE) {
            throw WalletUpgradeBackupException("SET_UP_AGAIN_NOT_AVAILABLE")
        }
        val applicationContext = context.applicationContext ?: context
        val noBackup = applicationContext.noBackupFilesDir
        val marker = File(noBackup, SET_UP_AGAIN_MARKER)
        if (
            marker.existsNoFollowOrThrow("SET_UP_AGAIN_REQUEST_FAILED") &&
            pendingSetUpAgainScope(marker) == SetUpAgainScope.ALL_WALLET_FILES
        ) {
            // A wider request is already waiting; never narrow it silently.
            return@runCatching
        }
        // Written beside the marker, then renamed over it: a crash leaves the old request or the
        // new one, never a half-written scope.
        val staging = File(noBackup, ".$SET_UP_AGAIN_MARKER.tmp")
        if (staging.existsNoFollowOrThrow("SET_UP_AGAIN_REQUEST_FAILED")) {
            Os.remove(staging.absolutePath)
        }
        val operation = JSONObject()
            .put("format", 2).put("id", UUID.randomUUID().toString())
            .put("scope", scope.name).put("cause", blockingFailure?.code ?: transientVerificationFailure ?: "UNKNOWN")
            .put("created", System.currentTimeMillis()).put("phase", "REQUESTED")
        writeDurably(staging, operation.toString())
        try {
            Os.rename(staging.absolutePath, marker.absolutePath)
        } catch (_: ErrnoException) {
            throw WalletUpgradeBackupException("SET_UP_AGAIN_REQUEST_FAILED")
        }
        fsyncDirectory(noBackup)
    }

    /** The set-up-again request waiting for the next app start, or null when there is none. */
    fun pendingSetUpAgainScope(context: Context): SetUpAgainScope? =
        runCatching {
            val marker = File(
                (context.applicationContext ?: context).noBackupFilesDir,
                SET_UP_AGAIN_MARKER,
            )
            if (marker.existsNoFollowOrThrow("SET_UP_AGAIN_REQUEST_FAILED")) {
                pendingSetUpAgainScope(marker)
            } else {
                null
            }
        }.getOrNull()

    /**
     * Reads a request's scope. Anything that is not exactly [SetUpAgainScope.ALL_WALLET_FILES]
     * (an unreadable marker, or one written by an earlier 3.9.0 test build) moves only the
     * database: the scope that leaves every key in place.
     */
    private fun pendingSetUpAgainScope(marker: File): SetUpAgainScope {
        val value = marker.readBoundedUtf8NoFollow(1, MAX_MANIFEST_BYTES, "SET_UP_AGAIN_INCOMPLETE").trim()
        val scope = if (value.startsWith("{")) JSONObject(value).getString("scope") else value
        return SetUpAgainScope.valueOf(scope).also {
            if (it == SetUpAgainScope.NONE) throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
        }
    }

    /** Cancellation is safe before the operation starts; once files move the same journal must
     * finish (the resulting generation remains selectable). */
    @Synchronized
    fun cancelSetUpAgain(context: Context): Result<Unit> = runCatching {
        val marker = File(context.noBackupFilesDir, SET_UP_AGAIN_MARKER)
        if (!marker.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")) return@runCatching
        val value = marker.readBoundedUtf8NoFollow(1, MAX_MANIFEST_BYTES, "SET_UP_AGAIN_INCOMPLETE")
        if (value.startsWith("{") && JSONObject(value).optString("phase") != "REQUESTED") {
            throw WalletUpgradeBackupException("SET_UP_AGAIN_ALREADY_STARTED")
        }
        Os.remove(marker.path)
        fsyncDirectory(context.noBackupFilesDir)
    }

    data class PreservedGeneration(val id: String, val createdAtMillis: Long, val scope: SetUpAgainScope)

    data class PreservedGenerationInventory(val available: List<PreservedGeneration>, val hasUnavailable: Boolean)

    /** A damaged sibling must never hide a different, byte-verified generation. */
    fun preservedGenerations(context: Context): List<PreservedGeneration> = preservedGenerationInventory(context).available

    fun preservedGenerationInventory(context: Context): PreservedGenerationInventory {
        val children = context.noBackupFilesDir.listFiles() ?: throw WalletUpgradeBackupException("GENERATION_INVENTORY_UNAVAILABLE")
        var hasUnavailable = false
        val available = children.filter { it.name.startsWith(STORAGE_QUARANTINE_PREFIX) && it.name.contains(SET_UP_AGAIN_SUFFIX) }
            .mapNotNull { directory ->
                runCatching {
                    requireGenerationId(directory.name)
                    val manifest = File(directory, "generation.json")
                    if (!manifest.existsNoFollowOrThrow("GENERATION_INVALID")) return@runCatching null
                    val value = JSONObject(manifest.readBoundedUtf8NoFollow(1, MAX_MANIFEST_BYTES, "GENERATION_INVALID"))
                    if (value.optString("state") != "PRESERVED") return@runCatching null
                    val scope = generationScope(value)
                    verifyGeneration(directory, value.getJSONArray("files"))
                    PreservedGeneration(directory.name, value.getLong("created"), scope)
                }.getOrElse { hasUnavailable = true; null }
            }.sortedByDescending { it.createdAtMillis }
        return PreservedGenerationInventory(available, hasUnavailable)
    }

    /** Index older set-asides without changing a byte in them. Older builds could split one
     * operation across folders; complementary fragments are copied into one durable generation.
     * Conflicting fragments remain separately selectable and are never overwritten or guessed. */
    @Synchronized
    fun reconcileLegacyPreservedGenerations(context: Context): Result<Unit> = runCatching {
        val noBackup = context.noBackupFilesDir
        withPublicationLock(noBackup, BACKUP_PUBLICATION_LOCK, "GENERATION_INVALID") {
            val directories = (noBackup.listFiles() ?: throw WalletUpgradeBackupException("GENERATION_INVENTORY_UNAVAILABLE"))
                .filter { it.name.startsWith(STORAGE_QUARANTINE_PREFIX) && it.name.contains(SET_UP_AGAIN_SUFFIX) }
                .sortedBy { it.name }
            val legacy = directories.filter {
                it.name.matches(Regex("wallet-quarantine-[0-9]+-set-up-again(?:-[0-9]+)?")) &&
                    !File(it, "generation.json").existsNoFollowOrThrow("GENERATION_INVALID")
            }
            if (legacy.isEmpty()) return@withPublicationLock
            val fragments = legacy.associateWith { directory ->
                requireGenerationId(directory.name)
                val records = JSONArray()
                strictRegularFiles(directory).forEach { file ->
                    val name = directory.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                    val fingerprint = file.inspectRegularNoFollow("GENERATION_INVALID")
                    records.put(JSONObject().put("name", name).put("size", fingerprint.size).put("sha256", fingerprint.sha256))
                }
                generationRecords(records)
                records
            }
            val sources = mutableMapOf<String, Pair<File, FileFingerprint>>()
            var conflicting = false
            fragments.forEach { (directory, files) ->
                generationRecords(files).forEach { (name, fingerprint) ->
                    val previous = sources[name]
                    if (previous != null && previous.second != fingerprint) conflicting = true
                    else sources[name] = File(directory, name) to fingerprint
                }
            }
            if (legacy.size > 1 && !conflicting) {
                val id = UUID.nameUUIDFromBytes(legacy.joinToString("\n") { it.name }.toByteArray()).toString()
                val joined = File(noBackup, "$STORAGE_QUARANTINE_PREFIX$id$SET_UP_AGAIN_SUFFIX")
                if (!joined.existsNoFollowOrThrow("GENERATION_INVALID")) createDirectoryExclusive(joined, "GENERATION_INVALID")
                val records = JSONArray()
                sources.toSortedMap().forEach { (name, source) ->
                    val (original, fingerprint) = source
                    val target = File(joined, name)
                    ensureDirectoryTreeExclusive(joined, requireNotNull(target.parentFile), "GENERATION_INVALID")
                    if (!target.existsNoFollowOrThrow("GENERATION_INVALID")) {
                        copyRegularNoReplace(original, target, original.regularInodeIdentityNoFollow("GENERATION_INVALID"), fingerprint, "GENERATION_INVALID")
                    }
                    if (target.inspectRegularNoFollow("GENERATION_INVALID") != fingerprint) throw WalletUpgradeBackupException("GENERATION_INVALID")
                    var parent: File? = target.parentFile
                    while (parent != null && parent.toPath().startsWith(joined.toPath())) {
                        fsyncDirectory(parent)
                        if (parent == joined) break
                        parent = parent.parentFile
                    }
                    records.put(JSONObject().put("name", name).put("size", fingerprint.size).put("sha256", fingerprint.sha256))
                }
                writeGeneration(joined, JSONObject().put("scope", SetUpAgainScope.ALL_WALLET_FILES.name)
                    .put("created", legacy.maxOf { it.lastModified() }).put("state", "PRESERVED").put("files", records))
                fsyncDirectory(noBackup)
            }
            fragments.forEach { (directory, records) ->
                writeGeneration(directory, JSONObject().put("scope", SetUpAgainScope.ALL_WALLET_FILES.name)
                    .put("created", directory.lastModified()).put("state", if (legacy.size > 1 && !conflicting) "JOINED_SOURCE" else "PRESERVED")
                    .put("files", records))
            }
        }
    }

    @Synchronized
    fun requestUsePreservedGeneration(context: Context, id: String): Result<Unit> = runCatching {
        if (isSetUpAgainRequested(context)) throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
        val selected = preservedGenerations(context).singleOrNull { it.id == id }
            ?: throw WalletUpgradeBackupException("GENERATION_INVALID")
        val request = JSONObject().put("format", 2).put("id", UUID.randomUUID().toString())
            .put("scope", selected.scope.name).put("cause", "SWITCH_GENERATION")
            .put("created", System.currentTimeMillis()).put("phase", "REQUESTED").put("target", id)
        writeOperation(context, request)
    }

    private fun writeOperation(context: Context, operation: JSONObject) {
        val marker = File(context.noBackupFilesDir, SET_UP_AGAIN_MARKER)
        val staging = File(context.noBackupFilesDir, ".$SET_UP_AGAIN_MARKER.tmp")
        if (staging.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")) Os.remove(staging.path)
        writeDurably(staging, operation.toString())
        Os.rename(staging.path, marker.path)
        fsyncDirectory(context.noBackupFilesDir)
    }

    /** True while a set-up-again request waits for the next app start. */
    fun isSetUpAgainRequested(context: Context): Boolean =
        runCatching {
            File(
                (context.applicationContext ?: context).noBackupFilesDir,
                SET_UP_AGAIN_MARKER,
            ).existsNoFollowOrThrow("SET_UP_AGAIN_REQUEST_FAILED")
        }.getOrDefault(true)

    /**
     * Deterministic low-storage seam for the retained-wallet instrumentation matrix.
     *
     * Production callers always use [prepare], which probes the target filesystem with
     * [StatFs]. Android tests use this internal entry point to prove that the complete
     * pre-migration path fails closed before creating a staging directory or changing the
     * installed database/settings when the capacity probe reports insufficient space.
     */
    @VisibleForTesting
    @Synchronized
    fun prepareWithAvailableBackupBytesForTest(
        context: Context,
        availableBackupBytes: Long,
    ): Result<Unit> {
        require(availableBackupBytes >= 0L)
        return prepareWithCapacityOverride(
            context = context,
            availableBackupBytesOverride = availableBackupBytes,
            beforeFinalSourceValidationForTest = null,
            retainStartupAdmission = false,
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
        retainStartupAdmission = false,
    )

    private fun prepareWithCapacityOverride(
        context: Context,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)? = null,
        retainStartupAdmission: Boolean,
    ): Result<Unit> {
        preparedAdmission = null
        transientVerificationFailure = null
        inSessionRecoveryCode = null
        nonBlockingWarnings = emptyList()
        // Application.attachBaseContext is intentionally the earliest caller so no injected
        // database consumer can race this gate. Android may not expose applicationContext until
        // that lifecycle method returns, even though the supplied Application is already backed
        // by a valid base context.
        val applicationContext = context.applicationContext ?: context
        preparedContext = applicationContext
        val result = runCatching {
            prepareOrThrow(
                context = applicationContext,
                availableBackupBytesOverride = availableBackupBytesOverride,
                beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
            )
        }
        val failure = result.exceptionOrNull()?.let { error ->
            Failure(
                code = error.safeCode(),
                requiredBytes = (error as? WalletUpgradeBackupException)?.requiredBytes,
                availableBytes = (error as? WalletUpgradeBackupException)?.availableBytes,
            )
        }
        val recovery = if (failure?.code == "WALLET_MIGRATION_RECOVERY_REQUIRED") {
            runCatching { inspectMigrationRecovery(applicationContext) }.getOrNull()
        } else {
            null
        }
        migrationRecoverySnapshot = recovery
        blockingFailure = failure
        if (failure != null) {
            FirebaseWrapper.recordWalletRecovery(when (failure.code) {
                "STORAGE_FULL", "INSUFFICIENT_BACKUP_STORAGE" -> WalletRecoveryEvent.GATE_STORAGE_FULL
                "DATABASE_LOCKED" -> WalletRecoveryEvent.GATE_DATABASE_LOCKED
                "DATABASE_IO" -> WalletRecoveryEvent.GATE_DATABASE_IO
                "DATABASE_OPEN_UNAVAILABLE", "DATABASE_OPEN_FAILED", "DATABASE_READ_ONLY", "DATABASE_CORRUPT" ->
                    WalletRecoveryEvent.GATE_DATABASE_OPEN_FAILED
                "DATABASE_PATH_UNREADABLE" -> WalletRecoveryEvent.GATE_DATABASE_PATH_UNREADABLE
                "PREFERENCES_INVENTORY_INVALID" -> WalletRecoveryEvent.GATE_PREFERENCES_INVALID
                "KEYSTORE_UNAVAILABLE", "KEYSTORE_KEY_UNAVAILABLE" -> WalletRecoveryEvent.GATE_KEYSTORE_UNAVAILABLE
                "SOURCE_CHANGED", "RECOVERY_SOURCE_CHANGED" -> WalletRecoveryEvent.GATE_SOURCE_CHANGED
                else -> WalletRecoveryEvent.GATE_OTHER
            })
            // Offer "Continue read-only" only when it will work (the same checks as
            // authorizeSafeReadOnly). Runs only in a failure state.
            recoveryFacts = recoveryFacts.copy(
                accountsReadableReadOnly = recovery != null ||
                    accountsReadableReadOnly(applicationContext),
            )
        }
        if (failure == null) {
            rememberStartupFailure(applicationContext, null)
            automaticRetryFailedInProcess = false
            WalletRecoveryCapabilityGate.enterNormal()
            // A failed metadata capture only forfeits the shortcut; the already-completed
            // preparation remains valid and requirePrepared() will conservatively run it again.
            if (retainStartupAdmission) {
                preparedAdmission = runCatching {
                    captureStartupAdmission(applicationContext)
                }.getOrNull()
            }
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
        preparedAdmission = null
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
        val current = inspectMigrationRecovery(context.applicationContext ?: context)
        migrationRecoverySnapshot?.let { expected ->
            if (expected.token != current.token) {
                throw WalletUpgradeBackupException("RECOVERY_SOURCE_CHANGED")
            }
        }
        migrationRecoverySnapshot = current
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()
        FirebaseWrapper.recordWalletRecovery(WalletRecoveryEvent.READ_ONLY_ENTERED)
        keepCodeForRecoveryScreen()
        blockingFailure = null
    }

    /**
     * Read-only access lifts the blocking failure (Room may open, guarded) but the state still
     * needs recovery: its code stays in memory so the recovery screen, if it shows again in this
     * process, offers the same actions and [requestSetUpAgain] accepts them.
     */
    private fun keepCodeForRecoveryScreen() {
        blockingFailure?.code?.let { inSessionRecoveryCode = it }
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
        preparedAdmission = null
        if (!canAuthorizeMigrationRetry()) {
            throw WalletUpgradeBackupException(
                "MIGRATION_RETRY_AUTHORIZATION_NOT_AVAILABLE"
            )
        }
        val applicationContext = context.applicationContext ?: context
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
        inSessionRecoveryCode = null
    }

    @Synchronized
    fun noteMigrationVerified() {
        preparedAdmission = null
        migrationRecoverySnapshot = null
        blockingFailure = null
        transientVerificationFailure = null
        inSessionRecoveryCode = null
        automaticRetryFailedInProcess = false
        WalletRecoveryCapabilityGate.enterNormal()
    }

    /**
     * Routes to read-only access without changing any wallet data.
     *
     * Room is admitted read-only (wallet tables guarded) only when the installed database is
     * already at the current schema and passes `PRAGMA quick_check`; opening Room on an older
     * schema would run a migration. Otherwise the caller must use a Room-free view.
     */
    @Synchronized
    fun authorizeSafeReadOnly(context: Context): Result<SafeReadOnlyRoute> = runCatching {
        preparedAdmission = null
        val mode = WalletRecoveryCapabilityGate.mode()
        if (
            mode == WalletRecoveryCapabilityGate.Mode.NORMAL &&
            blockingFailure == null &&
            transientVerificationFailure == null
        ) {
            return@runCatching SafeReadOnlyRoute.NORMAL_ACCESS
        }
        if (isWalletKeyUnavailable()) {
            // The main screen decrypts the app PIN as soon as it opens; with no usable wallet key
            // that fails. Only the Room-free view works until a retry succeeds.
            return@runCatching SafeReadOnlyRoute.VAULT_ONLY
        }
        if (migrationRecoverySnapshot != null) {
            authorizeLegacyReadOnly(context).getOrThrow()
            return@runCatching SafeReadOnlyRoute.ROOM_READ_ONLY
        }
        val applicationContext = context.applicationContext ?: context
        if (!accountsReadableReadOnly(applicationContext)) {
            recoveryFacts = recoveryFacts.copy(accountsReadableReadOnly = false)
            return@runCatching SafeReadOnlyRoute.VAULT_ONLY
        }
        if (mode == WalletRecoveryCapabilityGate.Mode.NORMAL ||
            mode == WalletRecoveryCapabilityGate.Mode.MIGRATION_RETRY_AUTHORIZED
        ) {
            WalletRecoveryCapabilityGate.enterBlocked()
        }
        WalletRecoveryCapabilityGate.enterLegacyReadOnly()
        FirebaseWrapper.recordWalletRecovery(WalletRecoveryEvent.READ_ONLY_ENTERED)
        keepCodeForRecoveryScreen()
        blockingFailure = null
        SafeReadOnlyRoute.ROOM_READ_ONLY
    }

    /**
     * The read-only check shared by [authorizeSafeReadOnly] and the recovery options: the
     * database is a regular file at the current schema, passes `PRAGMA quick_check`, its wallet
     * tables can be read, and it holds at least one account. Opens SQLite read-only with the
     * fail-closed handler (never deletes a damaged file).
     */
    private fun accountsReadableReadOnly(context: Context): Boolean {
        val database = context.getDatabasePath(DATABASE_NAME)
        return database.isRegularFileNoFollow() && runCatching {
            readVersion(database) == CURRENT_DATABASE_VERSION &&
                database.passesSqliteQuickCheck() &&
                readWalletDatabaseInventory(database, CURRENT_DATABASE_VERSION)
                    .accounts.isNotEmpty()
        }.getOrDefault(false)
    }

    enum class SafeReadOnlyRoute {
        /** No recovery is active; continue normally. */
        NORMAL_ACCESS,

        /** The existing database is open read-only; signing and wallet changes are disabled. */
        ROOM_READ_ONLY,

        /** The database cannot be opened safely; show addresses and phrases without Room. */
        VAULT_ONLY,
    }

    /**
     * Called when SQLite reports corruption on an open Room connection. The database file is kept
     * exactly as it is; a copy is placed in no-backup storage for support.
     */
    @Synchronized
    fun noteDatabaseCorruption(context: Context?) {
        preparedAdmission = null
        if (context != null && !databaseCorruptionCopied) {
            databaseCorruptionCopied = runCatching { copyCorruptDatabaseForSupport(context) }
                .isSuccess
        }
        blockingFailure = Failure("DATABASE_CORRUPT")
        migrationRecoverySnapshot = null
        recoveryFacts = recoveryFacts.copy(
            hasWalletFiles = true,
            accountsReadableReadOnly = false,
        )
        WalletRecoveryCapabilityGate.enterBlocked()
    }

    /**
     * Wallet verification met a transient failure (busy Keystore, full disk, I/O). Nothing was
     * journaled. This process stays read-only until a retry succeeds; the next start retries.
     */
    @Synchronized
    fun noteTransientVerificationFailure(code: String, accountsReadable: Boolean = true) {
        preparedAdmission = null
        transientVerificationFailure = code
        // Wallet verification runs only on an open current-schema database; read-only needs at
        // least one account to show.
        recoveryFacts = recoveryFacts.copy(
            databaseAtCurrentVersion = true,
            accountsReadableReadOnly = accountsReadable,
        )
        if (blockingFailure == null) {
            migrationRecoverySnapshot = null
            WalletRecoveryCapabilityGate.enterInspectedRecovery()
        } else {
            WalletRecoveryCapabilityGate.enterBlocked()
        }
    }

    /** A deterministic failure opening Room: only the database is eligible for set-up-again. */
    @Synchronized
    fun noteDatabaseOpenFailure() {
        preparedAdmission = null
        if (blockingFailure == null) blockingFailure = Failure("DATABASE_OPEN_FAILED")
        recoveryFacts = recoveryFacts.copy(accountsReadableReadOnly = false)
        WalletRecoveryCapabilityGate.enterBlocked()
    }

    /** True while the last wallet verification in this process failed transiently. */
    fun transientVerificationFailure(): String? = transientVerificationFailure

    /**
     * Storing new keys failed because this phone cannot unlock the key material already stored
     * here (for example after a phone-to-phone transfer). Records the state for the recovery
     * options ("Set up this device again") without changing wallet access.
     */
    @Synchronized
    fun noteKeyMaterialUnavailable() {
        transientVerificationFailure = WalletRecoveryOptions.KEYSTORE_KEY_UNAVAILABLE
        recoveryFacts = recoveryFacts.copy(hasWalletFiles = true)
    }

    /**
     * Release-era (unsuffixed) keys were found but could not be verified, so no account was made
     * for them. They stay untouched; the recovery screens list them so the phrase can be shown or
     * imported.
     */
    @Synchronized
    fun noteUnrestorableLegacyWallet(owner: String?) {
        quarantine = quarantine.copy(
            unscopedMaterialWithoutRecordedOwner = true,
            unrestorableLegacyOwner = owner?.takeIf { it.isNotBlank() },
        )
    }

    /**
     * Copies the encrypted wallet preference files and the wrapped-key file into
     * `no_backup/wallet-quarantine-<time>-<reason>/` before an explicitly confirmed removal of an
     * account whose keys cannot be read here, so support can still look at the old bytes. Copies
     * only; the originals are not touched.
     */
    @Synchronized
    fun preserveWalletPreferencesCopy(reason: String): Result<String> = runCatching {
        require(reason.matches(Regex("^[a-z0-9-]{1,40}$"))) { "COPY_REASON_INVALID" }
        val context = preparedContext ?: throw WalletUpgradeBackupException("COPY_CONTEXT_MISSING")
        val dataDir = File(context.applicationInfo.dataDir)
        val sources = listOf(
            File(context.filesDir, "datastore/sora_prefs_datastore.preferences_pb"),
            File(dataDir, "shared_prefs/sora_prefs.xml"),
            File(dataDir, "shared_prefs/sora_prefs.xml.bak"),
            File(dataDir, "shared_prefs/key_alias.xml"),
            File(dataDir, "shared_prefs/key_alias.xml.bak"),
        ).filter { it.isRegularFileNoFollow() }
        val destination = uniqueSiblingName(
            parent = context.noBackupFilesDir,
            baseName = "$STORAGE_QUARANTINE_PREFIX${System.currentTimeMillis()}-$reason",
        )
        createDirectoryExclusive(destination, "PRESERVE_COPY_FAILED")
        sources.forEach { source ->
            val target = File(destination, source.name)
            copyDurably(source, target)
            if (source.sha256() != target.sha256()) {
                throw WalletUpgradeBackupException("PRESERVE_COPY_FAILED")
            }
        }
        fsyncDirectory(destination)
        fsyncDirectory(context.noBackupFilesDir)
        destination.absolutePath
    }

    /**
     * The wallet verification in this process needs recovery. [code] (the recorded failure) is
     * kept in memory so [recoveryOptions] and [requestSetUpAgain] describe this state, exactly as
     * they would after the next start reads it from the journal. A null [code] (an interrupted
     * run) keeps whatever was known.
     */
    @Synchronized
    fun noteMigrationRecoveryRequired(code: String? = null) {
        preparedAdmission = null
        migrationRecoverySnapshot = null
        if (code != null) {
            inSessionRecoveryCode = code
            // The verification ran on the open current-schema database and read at least one
            // account. authorizeSafeReadOnly checks it again (quick_check, wallet tables) before
            // admitting read-only, and reports VAULT_ONLY otherwise.
            recoveryFacts = recoveryFacts.copy(
                databaseAtCurrentVersion = true,
                accountsReadableReadOnly = true,
            )
        }
        WalletRecoveryCapabilityGate.enterInspectedRecovery()
    }

    /**
     * Closes the live-process mutation boundary for an unclassified migration exception without
     * weakening an earlier preflight/backup blocker or clearing its recovery evidence.
     */
    @Synchronized
    fun noteUnexpectedMigrationFailure() {
        preparedAdmission = null
        if (blockingFailure == null) {
            migrationRecoverySnapshot = null
            WalletRecoveryCapabilityGate.enterInspectedRecovery()
        } else {
            WalletRecoveryCapabilityGate.enterBlocked()
        }
    }

    @Synchronized
    fun requirePrepared(context: Context) {
        val admission = preparedAdmission
        preparedAdmission = null
        blockingFailure?.let { throw WalletUpgradeBackupException(it.code) }
        if (admission != null) {
            val currentAdmission = runCatching {
                captureStartupAdmission(context.applicationContext)
            }.getOrNull()
            blockingFailure?.let { throw WalletUpgradeBackupException(it.code) }
            if (
                currentAdmission == admission &&
                WalletRecoveryCapabilityGate.mode() ==
                WalletRecoveryCapabilityGate.Mode.NORMAL
            ) {
                return
            }
        }
        val preparation = prepare(context)
        // This call is itself the database-admission boundary. Do not leave a reusable token.
        preparedAdmission = null
        preparation.getOrElse { throw it }
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
        // Room's callback inherits SupportSQLiteOpenHelper.Callback.onCorruption, whose default
        // deletes the database file. Wrap it so corruption keeps every byte and routes startup to
        // recovery, and never allow the framework helper to delete the file on a failed open.
        val openHelper = delegate.create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(
                    NonDestructiveCorruptionCallback(
                        delegate = configuration.callback,
                        context = configuration.context.applicationContext
                            ?: configuration.context,
                    )
                )
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build()
        )
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
                    // Remember the guards as removed only once they were dropped on the writer
                    // connection (not refused on a pooled read-only one).
                    if (applyRecoveryWriteGuards(database, enabled = false)) {
                        walletWriteGuardsEnabled = false
                    }
                }
                database
            }
        }
    }

    private class NonDestructiveCorruptionCallback(
        private val delegate: SupportSQLiteOpenHelper.Callback,
        private val context: Context,
    ) : SupportSQLiteOpenHelper.Callback(delegate.version) {
        override fun onConfigure(db: SupportSQLiteDatabase) = delegate.onConfigure(db)

        override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            delegate.onUpgrade(db, oldVersion, newVersion)

        override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
            delegate.onDowngrade(db, oldVersion, newVersion)

        override fun onOpen(db: SupportSQLiteDatabase) = delegate.onOpen(db)

        override fun onCorruption(db: SupportSQLiteDatabase) {
            // Deliberately never calls super.onCorruption(): that deletes app.db.
            noteDatabaseCorruption(context)
            runCatching { db.close() }
            throw WalletUpgradeBackupException("DATABASE_CORRUPT")
        }
    }

    /** Copies (never moves) the corrupt database and its sidecars for support. */
    private fun copyCorruptDatabaseForSupport(context: Context) {
        val database = context.getDatabasePath(DATABASE_NAME)
        val sources = listOf(
            database,
            File("${database.path}-wal"),
            File("${database.path}-shm"),
            File("${database.path}-journal"),
        ).filter { it.isRegularFileNoFollow() }
        if (sources.isEmpty()) return
        val destination = uniqueSiblingName(
            parent = context.noBackupFilesDir,
            baseName = "$CORRUPT_DATABASE_COPY_PREFIX${System.currentTimeMillis()}",
        )
        createDirectoryExclusive(destination, "CORRUPT_DATABASE_COPY_FAILED")
        sources.forEach { source -> copyDurably(source, File(destination, source.name)) }
        fsyncDirectory(destination)
        fsyncDirectory(context.noBackupFilesDir)
    }

    private fun uniqueSiblingName(parent: File, baseName: String): File {
        var candidate = File(parent, baseName)
        var suffix = 1
        while (candidate.existsNoFollow()) {
            candidate = File(parent, "$baseName-$suffix")
            suffix += 1
        }
        return candidate
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
        val buffer = ByteArray(FILE_IO_BUFFER_BYTES)
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
        quarantine = WalletQuarantine.NONE
        recoveryFacts = RecoveryFacts(
            hasWalletFiles = runCatching {
                knownWalletFiles(context, database).any { it.existsNoFollow() }
            }.getOrDefault(true),
        )
        val preferenceInventory = walletPreferenceInventory(context)
        recoveryFacts = recoveryFacts.copy(
            hasRecoveryPhraseOrSeed = preferenceInventory.hasRecoveryPhraseOrSeed,
            phraseWalletIds = (
                preferenceInventory.mnemonicWalletIds + preferenceInventory.rawSeedWalletIds
                ).sorted(),
            legacyOwner = preferenceInventory.legacyPureAddress,
            hasUnscopedPhraseOrSeed = preferenceInventory.hasUnscopedPhraseOrSeed,
            scopedPhraseWalletIds = preferenceInventory.scopedPhraseOrSeedWalletIds,
        )
        // Only "no such file" means missing. Any other lstat error is reported as a transient
        // failure: treating it as missing would set aside a live write-ahead log.
        if (!database.existsNoFollowOrThrow("DATABASE_PATH_UNREADABLE")) {
            // A missing database with retained keys is a reinstall-style state, not an error.
            // Keys stay untouched; Room creates an empty current-schema database and the person
            // can restore or adopt the retained wallets. Database sidecars without their
            // database are set aside (renamed, never deleted) so a new database cannot replay a
            // stale write-ahead log.
            val setAside = setAsideOrphanedDatabaseSidecars(context, database)
            val retainedBackup = context.noBackupFilesDir.listFiles()
                ?.any { it.name.startsWith(BACKUP_PREFIX) }
                ?: false
            quarantine = computeQuarantine(
                database = WalletDatabaseInventory(accounts = emptyList()),
                preferences = preferenceInventory,
            ).copy(
                retainedBackupWithoutDatabase = retainedBackup,
                setAsidePaths = setAside,
                retainedWrappedKeyWithoutDatabase =
                    preferenceInventory.hasWrappedWalletKeyEvidence,
            )
            return
        }
        if (!database.isRegularFileNoFollow()) {
            throw WalletUpgradeBackupException("DATABASE_PATH_INVALID")
        }

        // A rollback journal can require a write even to read this database. Preserve and
        // validate the complete pre-recovery generation before SQLite touches the live files.
        val journalRecovered = if (hasRollbackJournalHeader(database)) {
            recoverRollbackJournal(context, database, preferenceInventory, availableBackupBytesOverride)
            true
        } else {
            false
        }
        val sourceVersion = try {
            readVersion(database)
        } catch (error: SQLiteReadOnlyDatabaseException) {
            if (journalRecovered) throw error
            recoverRollbackJournal(context, database, preferenceInventory, availableBackupBytesOverride)
            readVersion(database)
        }
        val databaseIdentity = database.regularFileIdentityNoFollow(
            "DATABASE_PATH_INVALID"
        )
        if (sourceVersion <= 0) {
            throw WalletUpgradeBackupException("INVALID_DATABASE_VERSION")
        }
        if (sourceVersion > CURRENT_DATABASE_VERSION) {
            throw WalletUpgradeBackupException("DATABASE_DOWNGRADE_UNSUPPORTED")
        }
        recoveryFacts = recoveryFacts.copy(
            databaseAtCurrentVersion = sourceVersion == CURRENT_DATABASE_VERSION,
        )
        val databaseInventory = readWalletDatabaseInventory(database, sourceVersion)
        if (
            database.regularFileIdentityNoFollow(
                "DATABASE_PATH_INVALID"
            ) != databaseIdentity
        ) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        recoveryFacts = recoveryFacts.copy(
            accountNames = databaseInventory.accounts
                .filter { it.substrateAddress.isNotBlank() }
                .associate { it.substrateAddress to it.accountName },
        )
        val legacyWalletIds = databaseInventory.accounts
            .map(SoraAccountLocal::substrateAddress)
            .toSet()
        quarantine = computeQuarantine(databaseInventory, preferenceInventory)
        runCatching {
            recoverInterruptedBackup(
                context, database, sourceVersion, databaseInventory,
                preferenceInventory, legacyWalletIds,
            )
        }.onFailure {
            // An interrupted copy that cannot be proven redundant is evidence, not a blocker:
            // rename it aside (same filesystem, never deleted) and continue.
            setAsideInterruptedStaging(context)
        }
        if (sourceVersion == CURRENT_DATABASE_VERSION) {
            // No Room schema migration is pending. The checks below are cheap; the snapshot and
            // pruning work is best effort and never blocks startup.
            val validation = runCatching {
                validateWalletStorageCoherence(databaseInventory, preferenceInventory)
            }
            val failure = validation.exceptionOrNull()
            if (
                failure != null &&
                (failure as? WalletUpgradeBackupException)?.code !=
                "WALLET_MIGRATION_RECOVERY_REQUIRED"
            ) {
                throw failure
            }
            maintainBackupsBestEffort(
                context = context,
                database = database,
                sourceVersion = sourceVersion,
                databaseInventory = databaseInventory,
                preferenceInventory = preferenceInventory,
                legacyWalletIds = legacyWalletIds,
                availableBackupBytesOverride = availableBackupBytesOverride,
                beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
                // Accounts whose live keys were verified; only copies of these are redundant.
                liveVerifiedWalletIds = if (failure == null) {
                    databaseInventory.identities
                        .filter { it.migrationState == "VERIFIED" && it.walletId in legacyWalletIds }
                        .map(WalletIdentityLocal::walletId)
                        .toSet()
                } else {
                    emptySet()
                },
            )
            if (failure != null) throw failure
            resetAutomaticMigrationRetries(context)
            return
        }

        // Room will migrate this database. Keep the authors' rule: never without a verified
        // pre-migration backup. When storage is too low for a full copy, a keys-only backup of
        // the encrypted preferences and wrapped key (the database holds no secrets) is made.
        validateWalletStorageCoherence(databaseInventory, preferenceInventory)
        withPublicationLock(
            directory = context.noBackupFilesDir,
            lockName = BACKUP_PUBLICATION_LOCK,
            failureCode = "BACKUP_PUBLICATION_LOCK_FAILED",
        ) {
            // Nothing is pruned before a migration: no live key has been verified on this schema.
            val inventory = cheapBackupInventoryUnderLock(context)
            publishVerifiedSnapshotBackupUnderLock(
                context = context,
                database = database,
                sourceVersion = sourceVersion,
                databaseInventory = databaseInventory,
                preferenceInventory = preferenceInventory,
                legacyWalletIds = legacyWalletIds,
                availableBackupBytesOverride = availableBackupBytesOverride,
                beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
                inventory = inventory,
            )
        }
    }

    /**
     * Current schema: no Room migration is pending, so every step here is optional and a failure
     * is recorded as a warning, never a blocker.
     * - Old snapshots beyond the newest [MAX_KEPT_VERIFIED_BACKUPS] verified ones are pruned when
     *   they are redundant (see [pruneBackupsUnderLock]).
     * - One current-schema snapshot is made only when it is cheap (see
     *   [MAX_CURRENT_SCHEMA_SNAPSHOT_SOURCE_BYTES]); this runs on the main thread at app start.
     */
    private fun maintainBackupsBestEffort(
        context: Context,
        database: File,
        sourceVersion: Int,
        databaseInventory: WalletDatabaseInventory,
        preferenceInventory: WalletPreferenceInventory,
        legacyWalletIds: Set<String>,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)?,
        liveVerifiedWalletIds: Set<String>,
    ) {
        val hasWalletState = legacyWalletIds.isNotEmpty() || preferenceInventory.hasWalletMaterial
        runCatching {
            withPublicationLock(
                directory = context.noBackupFilesDir,
                lockName = BACKUP_PUBLICATION_LOCK,
                failureCode = "BACKUP_PUBLICATION_LOCK_FAILED",
            ) {
                val inventory = pruneBackupsUnderLock(
                    context = context,
                    inventory = cheapBackupInventoryUnderLock(context),
                    liveVerifiedWalletIds = liveVerifiedWalletIds,
                )
                if (
                    hasWalletState &&
                    inventory.none { it.targetVersion == CURRENT_DATABASE_VERSION } &&
                    currentSchemaSnapshotIsCheap(context, database, availableBackupBytesOverride)
                ) {
                    val published = runCatching {
                        publishVerifiedSnapshotBackupUnderLock(
                            context = context,
                            database = database,
                            sourceVersion = sourceVersion,
                            databaseInventory = databaseInventory,
                            preferenceInventory = preferenceInventory,
                            legacyWalletIds = legacyWalletIds,
                            availableBackupBytesOverride = availableBackupBytesOverride,
                            beforeFinalSourceValidationForTest = beforeFinalSourceValidationForTest,
                            inventory = inventory,
                        )
                    }
                    if (published.isFailure) {
                        recordSnapshotBackoff(context)
                    } else {
                        clearSnapshotBackoff(context)
                    }
                    published.getOrThrow()
                }
            }
        }.onFailure { error ->
            nonBlockingWarnings = nonBlockingWarnings + error.safeCode()
        }
    }

    /**
     * A current-schema snapshot is attempted only when its sources are small, the free space is
     * clearly enough for a full copy, and no attempt failed in the last day. Reads file sizes and
     * one small file; hashes nothing.
     */
    private fun currentSchemaSnapshotIsCheap(
        context: Context,
        database: File,
        availableBackupBytesOverride: Long?,
    ): Boolean {
        val sources = knownWalletFiles(context, database).filter { it.existsNoFollow() }
        val sourceBytes = sources.fold(0L) { total, file -> saturatingAdd(total, file.length()) }
        if (sourceBytes > MAX_CURRENT_SCHEMA_SNAPSHOT_SOURCE_BYTES) {
            nonBlockingWarnings = nonBlockingWarnings + "CURRENT_SCHEMA_SNAPSHOT_SKIPPED_SIZE"
            return false
        }
        if (isSnapshotBackoffActive(context)) {
            nonBlockingWarnings = nonBlockingWarnings + "CURRENT_SCHEMA_SNAPSHOT_DEFERRED"
            return false
        }
        val available = availableBackupBytesOverride ?: runCatching {
            StatFs(context.noBackupFilesDir.absolutePath).availableBytes
        }.getOrDefault(0L)
        if (
            available < requiredBackupBytes(
                sources = sources,
                headroom = MINIMUM_FREE_SPACE_HEADROOM,
                extra = 0L,
            )
        ) {
            recordSnapshotBackoff(context)
            nonBlockingWarnings = nonBlockingWarnings + "CURRENT_SCHEMA_SNAPSHOT_SKIPPED_SPACE"
            return false
        }
        return true
    }

    private fun snapshotBackoffFile(context: Context): File =
        File(context.noBackupFilesDir, SNAPSHOT_BACKOFF_FILE)

    private fun isSnapshotBackoffActive(context: Context): Boolean = runCatching {
        val file = snapshotBackoffFile(context)
        if (!file.existsNoFollow()) return@runCatching false
        val failedAt = file.readBoundedUtf8NoFollow(1, 32, "SNAPSHOT_BACKOFF_INVALID")
            .trim().toLong()
        val now = System.currentTimeMillis()
        // A time in the future (the clock moved back) does not extend the wait.
        failedAt <= now && now - failedAt < SNAPSHOT_BACKOFF_MILLIS
    }.getOrDefault(false)

    private fun recordSnapshotBackoff(context: Context) {
        runCatching {
            val file = snapshotBackoffFile(context)
            val staging = File(context.noBackupFilesDir, ".$SNAPSHOT_BACKOFF_FILE.tmp")
            if (staging.existsNoFollow()) Os.remove(staging.absolutePath)
            writeDurably(staging, System.currentTimeMillis().toString())
            Os.rename(staging.absolutePath, file.absolutePath)
        }
    }

    private fun clearSnapshotBackoff(context: Context) {
        val file = snapshotBackoffFile(context)
        if (file.existsNoFollow()) runCatching { Os.remove(file.absolutePath) }
    }

    /** Renames `app.db-wal`, `-shm` and `-journal` aside when `app.db` itself is missing. */
    private fun setAsideOrphanedDatabaseSidecars(
        context: Context,
        database: File,
    ): List<String> {
        val sidecars = listOf(
            File("${database.path}-wal"),
            File("${database.path}-shm"),
            File("${database.path}-journal"),
        ).filter { it.existsNoFollowOrThrow("DATABASE_SIDECAR_QUARANTINE_FAILED") }
        if (sidecars.isEmpty()) return emptyList()
        val destination = uniqueSiblingName(
            parent = context.noBackupFilesDir,
            baseName = "$STORAGE_QUARANTINE_PREFIX${System.currentTimeMillis()}",
        )
        createDirectoryExclusive(destination, "DATABASE_SIDECAR_QUARANTINE_FAILED")
        return sidecars.map { sidecar ->
            val target = File(destination, sidecar.name)
            try {
                Os.rename(sidecar.absolutePath, target.absolutePath)
            } catch (_: Exception) {
                throw WalletUpgradeBackupException("DATABASE_SIDECAR_QUARANTINE_FAILED")
            }
            target.absolutePath
        }.also {
            fsyncDirectory(destination)
            runCatching { fsyncDirectory(requireNotNull(database.parentFile)) }
            fsyncDirectory(context.noBackupFilesDir)
        }
    }

    /** Renames every interrupted staging directory aside. Its bytes are kept for support. */
    private fun setAsideInterruptedStaging(context: Context) {
        runCatching {
            withPublicationLock(
                directory = context.noBackupFilesDir,
                lockName = BACKUP_PUBLICATION_LOCK,
                failureCode = "BACKUP_PUBLICATION_LOCK_FAILED",
            ) {
                context.noBackupFilesDir.listFiles().orEmpty()
                    .filter { it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(".staging") }
                    .forEach { setAsideBackupEntryUnderLock(context, it) }
            }
        }.onFailure { error ->
            nonBlockingWarnings = nonBlockingWarnings + error.safeCode()
        }
    }

    private fun setAsideBackupEntryUnderLock(context: Context, entry: File): String {
        val target = uniqueSiblingName(
            parent = context.noBackupFilesDir,
            baseName = "$BACKUP_QUARANTINE_PREFIX${System.currentTimeMillis()}-${entry.name}",
        )
        try {
            Os.rename(entry.absolutePath, target.absolutePath)
        } catch (_: Exception) {
            throw WalletUpgradeBackupException("BACKUP_QUARANTINE_FAILED")
        }
        fsyncDirectory(context.noBackupFilesDir)
        quarantine = quarantine.copy(setAsidePaths = quarantine.setAsidePaths + target.absolutePath)
        return target.absolutePath
    }

    /**
     * Cheap startup inventory: directory type, completion marker, manifest shape and name only.
     * No hashing, copying or SQLite. An entry that fails is renamed aside (never deleted), so an
     * invalid or foreign backup can no longer block startup.
     */
    private fun cheapBackupInventoryUnderLock(context: Context): List<BackupInventoryEntry> {
        val entries = context.noBackupFilesDir.listFiles()
            ?.filter { it.name.startsWith(BACKUP_PREFIX) && !it.name.endsWith(".staging") }
            ?.sortedBy(File::getName)
            ?: throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        val valid = mutableListOf<BackupInventoryEntry>()
        entries.forEach { entry ->
            val inspected = runCatching { inspectBackupCheaply(entry) }.getOrNull()
            if (inspected == null) {
                runCatching { setAsideBackupEntryUnderLock(context, entry) }
                    .onFailure { error ->
                        nonBlockingWarnings = nonBlockingWarnings + error.safeCode()
                    }
            } else {
                valid += inspected
            }
        }
        return valid
    }

    private fun inspectBackupCheaply(directory: File): BackupInventoryEntry {
        if (!directory.isDirectoryNoFollow()) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        val completion = File(directory, ".complete").readBoundedUtf8NoFollow(
            minimumBytes = 1,
            maximumBytes = 32,
            failureCode = "BACKUP_NAMESPACE_INVALID",
        )
        if (completion != "verified") {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        val manifest = JSONObject(
            File(directory, "manifest.json").readBoundedUtf8NoFollow(
                minimumBytes = 1,
                maximumBytes = MAX_MANIFEST_BYTES,
                failureCode = "BACKUP_NAMESPACE_INVALID",
            )
        )
        val generation = manifest.getLong("backupGeneration")
        val sourceVersion = manifest.getInt("sourceDatabaseVersion")
        val targetVersion = manifest.getInt("targetDatabaseVersion")
        val fingerprint = manifest.getString("sourceFingerprint")
        val kind = manifest.optString("kind", BACKUP_KIND_FULL)
        if (
            manifest.getInt("formatVersion") != BACKUP_FORMAT_VERSION ||
            generation <= 0L ||
            targetVersion !in
            OLDEST_RECOVERABLE_BACKUP_TARGET_VERSION..CURRENT_DATABASE_VERSION ||
            sourceVersion !in 1..targetVersion ||
            !SHA256.matches(fingerprint) ||
            kind !in setOf(BACKUP_KIND_FULL, BACKUP_KIND_KEYS_ONLY) ||
            directory.name != backupDirectoryName(
                sourceVersion = sourceVersion,
                targetVersion = targetVersion,
                generation = generation,
                fingerprint = fingerprint,
            )
        ) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        return BackupInventoryEntry(
            directory = directory,
            generation = generation,
            sourceVersion = sourceVersion,
            targetVersion = targetVersion,
            kind = kind,
        )
    }

    /**
     * Keeps the newest [MAX_KEPT_VERIFIED_BACKUPS] backups that pass the full validation (hashes,
     * copied database integrity, manifest inventory), and every backup taken before a schema
     * migration. An older current-schema snapshot is pruned only when it is redundant: every
     * wallet in it (account rows, key sets and a release-era owner) is a live account whose keys
     * were verified on this device. Pruning first renames the tree out of the backup namespace,
     * then removes exactly the files that were validated. Anything that cannot be proven redundant
     * stays where it is. Runs only when there are more than [MAX_KEPT_VERIFIED_BACKUPS] backups and
     * the backups or verified accounts changed since the last decision. Best effort.
     */
    private fun pruneBackupsUnderLock(
        context: Context,
        inventory: List<BackupInventoryEntry>,
        liveVerifiedWalletIds: Set<String>,
    ): List<BackupInventoryEntry> {
        if (inventory.size <= MAX_KEPT_VERIFIED_BACKUPS || liveVerifiedWalletIds.isEmpty()) {
            return inventory
        }
        val decisionKey = sha256Text(
            inventory.map { it.directory.name }.sorted().joinToString("\n") +
                "\n--\n" + liveVerifiedWalletIds.sorted().joinToString("\n")
        )
        val stateFile = File(context.noBackupFilesDir, PRUNE_STATE_FILE)
        val previousDecision = runCatching {
            if (stateFile.existsNoFollow()) {
                stateFile.readBoundedUtf8NoFollow(1, 128, "PRUNE_STATE_INVALID").trim()
            } else {
                null
            }
        }.getOrNull()
        if (previousDecision == decisionKey) return inventory

        val newestFirst = inventory.sortedWith(
            compareByDescending<BackupInventoryEntry> { it.generation }
                .thenByDescending { it.directory.name }
        )
        // Only a current-schema snapshot older than the newest [MAX_KEPT_VERIFIED_BACKUPS] can
        // ever be removed. When there is none (for example one pre-migration backup and the
        // snapshots of earlier 3.9.0 builds were already pruned), nothing is validated: this runs
        // on the main thread at app start.
        if (newestFirst.drop(MAX_KEPT_VERIFIED_BACKUPS).none(::isPrunableKind)) {
            return inventory
        }
        val kept = mutableListOf<BackupInventoryEntry>()
        var verified = 0
        val candidates = mutableListOf<Pair<BackupInventoryEntry, Set<String>>>()
        newestFirst.forEach { entry ->
            val removableKind = isPrunableKind(entry)
            if (verified < MAX_KEPT_VERIFIED_BACKUPS || !removableKind) {
                kept += entry
                if (
                    verified < MAX_KEPT_VERIFIED_BACKUPS &&
                    isCompleteAndValid(context, entry.directory)
                ) {
                    verified += 1
                }
                return@forEach
            }
            val walletIds = mutableSetOf<String>()
            if (isCompleteAndValid(context, entry.directory, walletIdsOut = walletIds)) {
                candidates += entry to walletIds
            } else {
                kept += entry
            }
        }
        candidates.forEach { (entry, walletIds) ->
            val redundant = walletIds.isNotEmpty() && liveVerifiedWalletIds.containsAll(walletIds)
            val removed = redundant && runCatching {
                val tree = captureExactBackupTree(entry.directory)
                val renamed = uniqueSiblingName(
                    parent = context.noBackupFilesDir,
                    baseName = "$PRUNED_BACKUP_PREFIX${System.currentTimeMillis()}-${entry.directory.name}",
                )
                Os.rename(entry.directory.absolutePath, renamed.absolutePath)
                fsyncDirectory(context.noBackupFilesDir)
                removeExactOwnedBackupTree(renamed, tree)
                fsyncDirectory(context.noBackupFilesDir)
            }.isSuccess
            if (!removed && entry.directory.existsNoFollow()) kept += entry
        }
        runCatching {
            val staging = File(context.noBackupFilesDir, ".$PRUNE_STATE_FILE.tmp")
            if (staging.existsNoFollow()) Os.remove(staging.absolutePath)
            val remaining = kept.map { it.directory.name }.sorted().joinToString("\n")
            writeDurably(
                staging,
                sha256Text(
                    remaining + "\n--\n" + liveVerifiedWalletIds.sorted().joinToString("\n")
                ),
            )
            Os.rename(staging.absolutePath, stateFile.absolutePath)
        }
        return kept
    }

    /** A current-schema snapshot; a backup taken before a schema migration is never pruned. */
    private fun isPrunableKind(entry: BackupInventoryEntry): Boolean =
        entry.sourceVersion == CURRENT_DATABASE_VERSION &&
            entry.targetVersion == CURRENT_DATABASE_VERSION

    private fun automaticRetryStateFile(context: Context): File =
        File(context.noBackupFilesDir, AUTO_RETRY_STATE_FILE)

    /**
     * Claims one automatic verification retry. A user-requested retry always proceeds. Automatic
     * retries stop after [MAX_AUTOMATIC_MIGRATION_RETRIES] consecutive attempts, after which the
     * recovery options are shown; the count resets once the wallet verifies.
     */
    private fun automaticMigrationRetriesUsed(context: Context): Int {
        val file = automaticRetryStateFile(context)
        return if (file.existsNoFollow()) {
            runCatching {
                file.readBoundedUtf8NoFollow(1, 16, "AUTO_RETRY_STATE_INVALID").trim().toInt()
            }.getOrDefault(MAX_AUTOMATIC_MIGRATION_RETRIES)
        } else {
            0
        }
    }

    private fun claimMigrationRetry(context: Context): Boolean {
        val used = automaticMigrationRetriesUsed(context)
        if (used >= MAX_AUTOMATIC_MIGRATION_RETRIES) return false
        return runCatching {
            val staging = File(context.noBackupFilesDir, ".$AUTO_RETRY_STATE_FILE.tmp")
            if (staging.existsNoFollow()) Os.remove(staging.absolutePath)
            writeDurably(staging, (used + 1).toString())
            Os.rename(staging.absolutePath, automaticRetryStateFile(context).absolutePath)
            fsyncDirectory(context.noBackupFilesDir)
        }.isSuccess
    }

    private fun resetAutomaticMigrationRetries(context: Context) {
        val file = automaticRetryStateFile(context)
        if (file.existsNoFollow()) {
            runCatching {
                Os.remove(file.absolutePath)
                fsyncDirectory(context.noBackupFilesDir)
            }
        }
    }

    /** Number of automatic verification retries used since the wallet last verified. */
    @VisibleForTesting
    fun automaticMigrationRetriesUsedForTest(context: Context): Int {
        val file = automaticRetryStateFile(context.applicationContext ?: context)
        if (!file.existsNoFollow()) return 0
        return file.readBoundedUtf8NoFollow(1, 16, "AUTO_RETRY_STATE_INVALID").trim().toInt()
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
        // Generations must be unique. Gaps are expected once invalid backups are set aside or old
        // current-schema snapshots are pruned.
        val generations = candidates.map(VerifiedBackupCandidate::generation)
        if (generations.toSet().size != generations.size) {
            throw WalletUpgradeBackupException(invalidFailureCode)
        }
        return candidates
    }

    /**
     * A process can stop while copying the preflight backup, before Room ever opens. Retry may
     * discard only copies whose every byte still occurs at the same offset in the unchanged
     * installed source. Unknown files, changed copies and older snapshots remain recovery evidence.
     * The publication lock also covers validation and cleanup; installed wallet files are read only.
     */
    private fun recoverInterruptedBackup(
        context: Context,
        database: File,
        sourceVersion: Int,
        databaseInventory: WalletDatabaseInventory,
        preferenceInventory: WalletPreferenceInventory,
        legacyWalletIds: Set<String>,
    ) {
        if (context.noBackupFilesDir.listFiles().orEmpty().none {
                it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(".staging")
            }
        ) return
        withPublicationLock(
            context.noBackupFilesDir, BACKUP_PUBLICATION_LOCK,
            "BACKUP_PUBLICATION_LOCK_FAILED",
        ) {
            val namespace = context.noBackupFilesDir.listFiles()
                ?.filter { it.name.startsWith(BACKUP_PREFIX) }
                ?: throw WalletUpgradeBackupException("STALE_STAGING")
            val staging = namespace.singleOrNull { it.name.endsWith(".staging") }
                ?: throw WalletUpgradeBackupException("STALE_STAGING")
            val source = createSourceSnapshot(
                context, knownWalletFiles(context, database).filter { it.existsNoFollow() },
                legacyWalletIds, preferenceInventory.selectedWalletId,
            )
            val destination = File(staging.parentFile, staging.name.removeSuffix(".staging"))
            val generation = backupGenerationFromValidatedName(destination.name)
                ?: throw WalletUpgradeBackupException("STALE_STAGING")
            if (generation < 1L ||
                destination.name != backupDirectoryName(
                    sourceVersion, CURRENT_DATABASE_VERSION, generation, source.fingerprint,
                )
            ) throw WalletUpgradeBackupException("STALE_STAGING")
            val previous = namespace.filter { it != staging && it != destination }
            val previousGenerations = previous.map { backupGenerationFromValidatedName(it.name) }
            if (previousGenerations.any { it == null || it >= generation } ||
                previousGenerations.toSet().size != previousGenerations.size
            ) throw WalletUpgradeBackupException("STALE_STAGING")

            val manifest = backupManifest(source, sourceVersion, generation).toByteArray(Charsets.UTF_8)
            val stagedTree = requireRedundantBackupPrefix(staging, source, manifest)
            val publishedTree = if (destination.existsNoFollow()) {
                requireRedundantBackupPrefix(destination, source, manifest)
            } else null
            val publishedComplete = publishedTree != null &&
                isCompleteAndValid(context, destination) &&
                matchesSourceSnapshot(destination, source, sourceVersion)
            requireInstalledSourceUnchanged(
                context, database, sourceVersion, databaseInventory,
                preferenceInventory, legacyWalletIds, source,
            )
            // Validate all candidates before removing either tree. A partially published tree is
            // redundant too; retain a completed publication and only finish its staging cleanup.
            if (publishedTree != null && !publishedComplete) {
                removeExactOwnedBackupTree(destination, publishedTree, allowEmptyDirectories = true)
                fsyncDirectory(context.noBackupFilesDir)
            }
            removeExactOwnedBackupTree(staging, stagedTree, allowEmptyDirectories = true)
            fsyncDirectory(context.noBackupFilesDir)
        }
    }

    private fun requireRedundantBackupPrefix(
        directory: File,
        source: BackupSourceSnapshot,
        manifest: ByteArray,
    ): BackupTreeSnapshot {
        val failure = "STALE_STAGING"
        val tree = captureExactBackupTree(directory, allowEmptyDirectories = true)
        requireBackupTreeLinkCounts(directory, tree, 1L, failure)
        val sourceByName = source.files.associateBy(BackupSourceRecord::name)
        val metadata = mapOf("manifest.json" to manifest, ".complete" to "verified".toByteArray())
        val allowedDirectories = (sourceByName.keys + metadata.keys).flatMap { name ->
            val parts = name.split('/')
            (1 until parts.size).map { parts.take(it).joinToString("/") }
        }.toSet() + ""
        if (tree.directories.any { it.relativeName !in allowedDirectories }) {
            throw WalletUpgradeBackupException(failure)
        }
        tree.files.forEach { file ->
            val original = sourceByName[file.relativeName]
            val expected = metadata[file.relativeName]
            val partial = File(directory, file.relativeName)
            if (original != null) {
                if (file.fingerprint.size > original.size) throw WalletUpgradeBackupException(failure)
                partial.openRegularNoFollow(failure).use { copied ->
                    original.source.openRegularNoFollow(failure).use { installed ->
                        val copiedInput = DataInputStream(copied.input)
                        val originalInput = DataInputStream(installed.input)
                        val copiedBytes = ByteArray(FILE_IO_BUFFER_BYTES)
                        val originalBytes = ByteArray(FILE_IO_BUFFER_BYTES)
                        var remaining = file.fingerprint.size
                        while (remaining > 0) {
                            val count = minOf(remaining, FILE_IO_BUFFER_BYTES.toLong()).toInt()
                            copiedInput.readFully(copiedBytes, 0, count)
                            originalInput.readFully(originalBytes, 0, count)
                            if ((0 until count).any { copiedBytes[it] != originalBytes[it] }) {
                                throw WalletUpgradeBackupException(failure)
                            }
                            remaining -= count
                        }
                    }
                }
            } else if (expected == null || file.fingerprint.size > expected.size ||
                !partial.readBoundedBytesNoFollow(0, expected.size.toLong(), failure)
                    .contentEquals(expected.copyOf(file.fingerprint.size.toInt()))
            ) {
                throw WalletUpgradeBackupException(failure)
            }
        }
        if (captureExactBackupTree(directory, allowEmptyDirectories = true) != tree) {
            throw WalletUpgradeBackupException(failure)
        }
        return tree
    }

    private fun backupManifest(
        source: BackupSourceSnapshot,
        sourceVersion: Int,
        generation: Long,
        keysOnlyAccounts: List<SoraAccountLocal>? = null,
    ): String = JSONObject()
        .put("formatVersion", BACKUP_FORMAT_VERSION)
        .put("backupGeneration", generation)
        .put("sourceDatabaseVersion", sourceVersion)
        .put("targetDatabaseVersion", CURRENT_DATABASE_VERSION)
        .put("sourceFingerprint", source.fingerprint)
        .put("legacyAccountCount", source.legacyAccountCount)
        .put("legacyWalletIdsSha256", source.legacyWalletIdsSha256)
        .put("selectedWalletIdSha256", source.selectedWalletIdSha256 ?: JSONObject.NULL)
        .put("files", JSONArray().apply {
            source.files.forEach {
                put(JSONObject().put("name", it.name).put("size", it.size).put("sha256", it.sha256))
            }
        })
        .apply {
            // A keys-only backup carries the account list (addresses and names only; the
            // database never holds secrets) so the account rows can be rebuilt if needed.
            if (keysOnlyAccounts != null) {
                put("kind", BACKUP_KIND_KEYS_ONLY)
                put("accounts", JSONArray().apply {
                    keysOnlyAccounts.sortedBy(SoraAccountLocal::substrateAddress).forEach {
                        put(
                            JSONObject()
                                .put("address", it.substrateAddress)
                                .put("name", it.accountName)
                        )
                    }
                })
            }
        }
        .toString()

    private fun publishVerifiedSnapshotBackupUnderLock(
        context: Context,
        database: File,
        sourceVersion: Int,
        databaseInventory: WalletDatabaseInventory,
        preferenceInventory: WalletPreferenceInventory,
        legacyWalletIds: Set<String>,
        availableBackupBytesOverride: Long?,
        beforeFinalSourceValidationForTest: (() -> Unit)?,
        inventory: List<BackupInventoryEntry>,
        // The inventory was obtained by replaying a disposable copy. This is the complete raw
        // generation from before replay, including every sidecar and encrypted wallet file.
        preRecoverySnapshot: BackupSourceSnapshot? = null,
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
        val keySources = sources.filterNot { it.isDatabaseFile(database) }
        val databaseBytes = sources.filter { it.isDatabaseFile(database) }
            .fold(0L) { total, file -> saturatingAdd(total, file.length()) }
        // Hash only what is needed: a source is hashed for reuse only when a backup of the same
        // schema and kind already exists, and otherwise only after the free space was checked.
        val snapshots = mutableMapOf<Boolean, BackupSourceSnapshot>()
        if (preRecoverySnapshot != null) snapshots[false] = preRecoverySnapshot
        fun snapshot(keysOnly: Boolean): BackupSourceSnapshot = snapshots.getOrPut(keysOnly) {
            createSourceSnapshot(
                context = context,
                sources = if (keysOnly) keySources else sources,
                legacyWalletIds = legacyWalletIds,
                selectedWalletId = preferenceInventory.selectedWalletId,
            )
        }
        fun requireSourceUnchangedSinceInventory() {
            if (
                (preRecoverySnapshot == null &&
                    readWalletDatabaseInventory(database, sourceVersion) != databaseInventory) ||
                (preRecoverySnapshot != null && createSourceSnapshot(
                    context, knownWalletFiles(context, database).filter { it.existsNoFollow() },
                    legacyWalletIds, preferenceInventory.selectedWalletId,
                ) != preRecoverySnapshot) ||
                walletPreferenceInventory(context) != preferenceInventory
            ) {
                throw WalletUpgradeBackupException("SOURCE_CHANGED")
            }
        }
        // Reuse an existing backup of exactly this source only after it is deeply verified.
        val sameSchema = inventory.filter { it.sourceVersion == sourceVersion }
        for (keysOnlyCandidate in if (preRecoverySnapshot == null) listOf(false, true) else listOf(false)) {
            val kind = if (keysOnlyCandidate) BACKUP_KIND_KEYS_ONLY else BACKUP_KIND_FULL
            val candidates = sameSchema.filter { it.kind == kind }
            if (candidates.isEmpty() || (keysOnlyCandidate && keySources.isEmpty())) continue
            val candidateSnapshot = snapshot(keysOnlyCandidate)
            requireSourceUnchangedSinceInventory()
            val reusable = candidates.firstOrNull { entry ->
                matchesSourceSnapshot(entry.directory, candidateSnapshot, sourceVersion) &&
                    isCompleteAndValid(context, entry.directory)
            }
            if (reusable != null) {
                beforeFinalSourceValidationForTest?.invoke()
                requireInstalledSourceUnchanged(
                    context = context,
                    database = database,
                    sourceVersion = sourceVersion,
                    databaseInventory = databaseInventory,
                    preferenceInventory = preferenceInventory,
                    legacyWalletIds = legacyWalletIds,
                    sourceSnapshot = candidateSnapshot,
                    keysOnly = keysOnlyCandidate,
                    databaseReplayedInCopy = preRecoverySnapshot != null,
                )
                return
            }
        }
        val backupGeneration = try {
            Math.addExact(inventory.maxOfOrNull(BackupInventoryEntry::generation) ?: 0L, 1L)
        } catch (_: ArithmeticException) {
            throw WalletUpgradeBackupException("BACKUP_GENERATION_EXHAUSTED")
        }
        val available = availableBackupBytesOverride ?: runCatching {
            StatFs(context.noBackupFilesDir.absolutePath).availableBytes
        }.getOrElse {
            throw WalletUpgradeBackupException("STORAGE_CAPACITY_UNAVAILABLE")
        }
        val fullRequired = requiredBackupBytes(
            sources = sources,
            headroom = MINIMUM_FREE_SPACE_HEADROOM,
            extra = 0L,
        )
        val keysOnly = available < fullRequired
        if (keysOnly && preRecoverySnapshot != null) {
            // A keys-only backup cannot undo SQLite recovery. Leave the original database and
            // journal alone when there is insufficient room for their complete preserved copy.
            throw WalletUpgradeBackupException(
                "INSUFFICIENT_BACKUP_STORAGE", fullRequired, available,
            )
        }
        if (keysOnly) {
            // The keys-only backup copies only the encrypted preferences and the wrapped key
            // (kilobytes). The margin keeps room for the Room schema steps without requiring a
            // second copy of the whole database (see KEYS_ONLY_MIGRATION_MARGIN_CAP).
            val keysRequired = requiredBackupBytes(
                sources = keySources,
                headroom = KEYS_ONLY_MINIMUM_HEADROOM,
                extra = minOf(databaseBytes, KEYS_ONLY_MIGRATION_MARGIN_CAP),
            )
            if (keySources.isEmpty() || available < keysRequired) {
                throw WalletUpgradeBackupException(
                    code = "INSUFFICIENT_BACKUP_STORAGE",
                    requiredBytes = if (keySources.isEmpty()) fullRequired else keysRequired,
                    availableBytes = available,
                )
            }
        }
        val keysSnapshot = if (keysOnly) snapshot(true) else null
        val fullSnapshot = if (keysOnly) null else snapshot(false)
        requireSourceUnchangedSinceInventory()
        val sourceSnapshot = requireNotNull(if (keysOnly) keysSnapshot else fullSnapshot)
        val destination = File(
            context.noBackupFilesDir,
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

        val staging = File(destination.parentFile, "${destination.name}.staging")
        if (staging.existsNoFollow()) {
            throw WalletUpgradeBackupException("STALE_STAGING")
        }
        createDirectoryExclusive(
            directory = staging,
            failureCode = "CREATE_STAGING",
        )

        sourceSnapshot.files.forEach { sourceRecord ->
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

        val manifestText = backupManifest(
            source = sourceSnapshot,
            sourceVersion = sourceVersion,
            generation = backupGeneration,
            keysOnlyAccounts = if (keysOnly) databaseInventory.accounts else null,
        )
        if (manifestText.toByteArray(Charsets.UTF_8).size > MAX_MANIFEST_BYTES) {
            throw WalletUpgradeBackupException("BACKUP_MANIFEST_TOO_LARGE")
        }
        writeDurably(File(staging, "manifest.json"), manifestText)
        writeDurably(File(staging, ".complete"), "verified")
        publishVerifiedBackupTreeNoReplace(
            context = context,
            staging = staging,
            destination = destination,
            sourceSnapshot = sourceSnapshot,
            sourceVersion = sourceVersion,
            requireDatabase = !keysOnly,
        )
        if (keysOnly) {
            nonBlockingWarnings = nonBlockingWarnings + "KEYS_ONLY_BACKUP"
        }
        beforeFinalSourceValidationForTest?.invoke()
        requireInstalledSourceUnchanged(
            context = context,
            database = database,
            sourceVersion = sourceVersion,
            databaseInventory = databaseInventory,
            preferenceInventory = preferenceInventory,
            legacyWalletIds = legacyWalletIds,
            sourceSnapshot = sourceSnapshot,
            keysOnly = keysOnly,
            databaseReplayedInCopy = preRecoverySnapshot != null,
        )
    }

    private fun File.isDatabaseFile(database: File): Boolean {
        val path = absoluteFile.toPath().normalize()
        return listOf(
            database,
            File("${database.path}-wal"),
            File("${database.path}-shm"),
            File("${database.path}-journal"),
        ).any { it.absoluteFile.toPath().normalize() == path }
    }

    private fun requiredBackupBytes(sources: List<File>, headroom: Long, extra: Long): Long =
        sources.fold(saturatingAdd(headroom, extra)) { total, source ->
            saturatingAdd(total, saturatingMultiply(source.length(), BACKUP_SPACE_MULTIPLIER))
        }

    private fun saturatingAdd(left: Long, right: Long): Long =
        try {
            Math.addExact(left, right)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    private fun saturatingMultiply(left: Long, right: Long): Long =
        try {
            Math.multiplyExact(left, right)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }

    private fun publishVerifiedBackupTreeNoReplace(
        context: Context,
        staging: File,
        destination: File,
        sourceSnapshot: BackupSourceSnapshot,
        sourceVersion: Int,
        requireDatabase: Boolean,
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
            (REQUIRED_DATABASE_BACKUP in stagedNames) != requireDatabase ||
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
        keysOnly: Boolean = false,
        databaseReplayedInCopy: Boolean = false,
    ) {
        fun currentSources(): List<File> = knownWalletFiles(context, database)
            .filter { it.existsNoFollow() }
            .filter { !keysOnly || !it.isDatabaseFile(database) }
        val inventoryBefore = if (databaseReplayedInCopy) databaseInventory
        else readWalletDatabaseInventory(database, sourceVersion)
        val preferencesBefore = walletPreferenceInventory(context)
        val snapshotBefore = createSourceSnapshot(
            context = context,
            sources = currentSources(),
            legacyWalletIds = legacyWalletIds,
            selectedWalletId = preferenceInventory.selectedWalletId,
        )
        val inventoryAfter = if (databaseReplayedInCopy) databaseInventory
        else readWalletDatabaseInventory(database, sourceVersion)
        val preferencesAfter = walletPreferenceInventory(context)
        val snapshotAfter = createSourceSnapshot(
            context = context,
            sources = currentSources(),
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

    private fun captureExactBackupTree(
        root: File,
        allowEmptyDirectories: Boolean = false,
    ): BackupTreeSnapshot {
        val first = captureBackupTreeOnce(root, allowEmptyDirectories)
        val confirmed = captureBackupTreeOnce(root, allowEmptyDirectories)
        if (first != confirmed) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        return first
    }

    private fun captureBackupTreeOnce(
        root: File,
        allowEmptyDirectories: Boolean,
    ): BackupTreeSnapshot {
        val rootPath = root.absoluteFile.toPath().normalize()
        val emptyDirectoryInventory = if (allowEmptyDirectories) mutableListOf<File>() else null
        val regularFiles = strictRegularFiles(root, emptyDirectoryInventory)
        val directories = mutableMapOf(rootPath to root)
        emptyDirectoryInventory.orEmpty().forEach {
            directories[it.absoluteFile.toPath().normalize()] = it
        }
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
        WorkCountersForTest.copiedFiles.incrementAndGet()
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
                val buffer = ByteArray(FILE_IO_BUFFER_BYTES)
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
        allowEmptyDirectories: Boolean = false,
    ) {
        if (captureExactBackupTree(root, allowEmptyDirectories) != expected) {
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
    /**
     * Creates (or drops) the TEMP triggers that make wallet tables read-only in recovery modes.
     *
     * Returns false when the calling thread currently holds one of SQLite's pooled read-only
     * connections (Room reads through them; Android 14 and older refuse any schema statement
     * there). Such a connection cannot write, so it needs no guard: writes always use the writer
     * connection, which gets the guards the next time it is configured, before any write.
     */
    private fun applyRecoveryWriteGuards(
        database: SupportSQLiteDatabase,
        enabled: Boolean,
    ): Boolean {
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
                try {
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
                } catch (error: android.database.sqlite.SQLiteException) {
                    if (error.message?.contains(READ_ONLY_CONNECTION_REFUSAL) == true) {
                        return false
                    }
                    throw error
                }
            }
        }
        return true
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

    /**
     * Captures only filesystem metadata, not wallet contents. The complete semantic and
     * cryptographic validation has already succeeded immediately before this snapshot is made.
     * Comparing the stable snapshot at Room construction catches any intervening replacement,
     * write, link, or namespace change while avoiding another database copy/integrity check.
     */
    private fun captureStartupAdmission(context: Context): StartupAdmission {
        val first = captureStartupAdmissionOnce(context)
        val confirmed = captureStartupAdmissionOnce(context)
        if (first != confirmed) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        return first
    }

    private fun captureStartupAdmissionOnce(context: Context): StartupAdmission {
        val database = context.getDatabasePath(DATABASE_NAME)
        val backupRoot = context.noBackupFilesDir.absoluteFile
        val watchedPaths = mutableListOf<StartupPathSnapshot>()
        watchedPaths += knownWalletFiles(context, database)
            .map { it.startupPathSnapshot(allowMissing = true) }
        watchedPaths += startupBackupNamespaceSnapshots(backupRoot)
        val sortedPaths = watchedPaths.sortedBy(StartupPathSnapshot::path)
        if (sortedPaths.map(StartupPathSnapshot::path).toSet().size != sortedPaths.size) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        return StartupAdmission(
            packageName = context.packageName,
            dataDirectory = File(context.applicationInfo.dataDir)
                .absoluteFile.toPath().normalize().toString(),
            databasePath = database.absoluteFile.toPath().normalize().toString(),
            filesDirectory = context.filesDir.absoluteFile.toPath().normalize().toString(),
            noBackupDirectory = backupRoot.toPath().normalize().toString(),
            noBackupDirectoryIdentity = backupRoot.startupDirectoryIdentity(),
            paths = sortedPaths,
        )
    }

    private fun File.startupDirectoryIdentity(): StartupDirectoryIdentity {
        val stat = try {
            Os.lstat(absolutePath)
        } catch (_: ErrnoException) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        if (!OsConstants.S_ISDIR(stat.st_mode)) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        return StartupDirectoryIdentity(
            device = stat.st_dev,
            inode = stat.st_ino,
            mode = stat.st_mode,
            owner = stat.st_uid,
            group = stat.st_gid,
        )
    }

    private fun startupBackupNamespaceSnapshots(
        backupRoot: File,
    ): List<StartupPathSnapshot> {
        val candidates = backupRoot.listFiles()
            ?.filter { it.name.startsWith(BACKUP_PREFIX) }
            ?.sortedBy(File::getName)
            ?: throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        if (candidates.size > MAX_PUBLISHED_BACKUPS) {
            throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
        }
        val pending = ArrayDeque<File>()
        candidates.forEach(pending::addLast)
        val snapshots = mutableListOf<StartupPathSnapshot>()
        while (pending.isNotEmpty()) {
            val entry = pending.removeFirst()
            val snapshot = entry.startupPathSnapshot(allowMissing = false)
            val metadata = snapshot.metadata
                ?: throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
            snapshots += snapshot
            if (snapshots.size > MAX_STARTUP_ADMISSION_BACKUP_ENTRIES) {
                throw WalletUpgradeBackupException("BACKUP_NAMESPACE_INVALID")
            }
            when {
                OsConstants.S_ISDIR(metadata.mode) -> {
                    entry.listFiles()
                        ?.sortedBy(File::getName)
                        ?.forEach(pending::addLast)
                        ?: throw WalletUpgradeBackupException(
                            "BACKUP_NAMESPACE_INVALID"
                        )
                }
                OsConstants.S_ISREG(metadata.mode) -> Unit
                else -> throw WalletUpgradeBackupException(
                    "BACKUP_NAMESPACE_INVALID"
                )
            }
        }
        return snapshots
    }

    private fun File.startupPathSnapshot(
        allowMissing: Boolean,
    ): StartupPathSnapshot {
        // API 26 exposes only second-resolution StructStat timestamps. Without nanoseconds the
        // metadata shortcut could miss a same-second wallet write, so requirePrepared() must
        // repeat the complete source verification instead of retaining that shortcut.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        val path = absoluteFile.toPath().normalize().toString()
        val stat = try {
            Os.lstat(path)
        } catch (error: ErrnoException) {
            if (allowMissing && error.errno == OsConstants.ENOENT) {
                return StartupPathSnapshot(path = path, metadata = null)
            }
            throw WalletUpgradeBackupException("SOURCE_CHANGED")
        }
        return StartupPathSnapshot(
            path = path,
            metadata = StartupPathMetadata(
                device = stat.st_dev,
                inode = stat.st_ino,
                mode = stat.st_mode,
                linkCount = stat.st_nlink,
                owner = stat.st_uid,
                group = stat.st_gid,
                size = stat.st_size,
                modifiedAtSeconds = stat.st_mtim.tv_sec,
                modifiedAtNanoseconds = stat.st_mtim.tv_nsec,
                changedAtSeconds = stat.st_ctim.tv_sec,
                changedAtNanoseconds = stat.st_ctim.tv_nsec,
            ),
        )
    }

    private fun strictRegularFiles(
        root: File,
        incompleteDirectoryInventory: MutableList<File>? = null,
    ): List<File> {
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
            incompleteDirectoryInventory == null && directories.any { directory ->
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
        incompleteDirectoryInventory?.addAll(directories)
        return files
    }

    private fun File.existsNoFollow(): Boolean =
        Files.exists(toPath(), LinkOption.NOFOLLOW_LINKS)

    /**
     * Existence by `lstat`, where only "no such file" means missing. Any other error (for
     * example an I/O error) throws [failureCode] instead of being mistaken for a missing file.
     */
    private fun File.existsNoFollowOrThrow(failureCode: String): Boolean =
        try {
            Os.lstat(absolutePath)
            true
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) {
                false
            } else {
                throw WalletUpgradeBackupException(failureCode)
            }
        }

    /**
     * Carries out a [requestSetUpAgain] request at the start of a new process, before anything
     * opens the wallet files. Each file in the request's scope is renamed (same filesystem,
     * atomic) into `no_backup/wallet-quarantine-<time>-set-up-again/`, keeping its relative path;
     * nothing is copied, rewritten or deleted, and the Android Keystore key is not touched. For
     * [SetUpAgainScope.DATABASE_ONLY] the encrypted preferences and the wrapped key stay exactly
     * where they are. If a rename fails the request stays recorded and the next start finishes
     * it; files already moved stay moved.
     */
    @VisibleForTesting
    var afterSetAsideRenameForTest: (() -> Unit)? = null

    private fun performPendingSetUpAgain(context: Context) {
        val noBackup = context.noBackupFilesDir
        val marker = File(noBackup, SET_UP_AGAIN_MARKER)
        if (!marker.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")) return
        withPublicationLock(noBackup, BACKUP_PUBLICATION_LOCK, "SET_UP_AGAIN_INCOMPLETE") {
            val raw = marker.readBoundedUtf8NoFollow(1, MAX_MANIFEST_BYTES, "SET_UP_AGAIN_INCOMPLETE")
            val operation = if (raw.startsWith("{")) JSONObject(raw) else JSONObject()
                .put("format", 2).put("id", UUID.randomUUID().toString())
                .put("scope", pendingSetUpAgainScope(marker).name).put("cause", "LEGACY_REQUEST")
                .put("created", System.currentTimeMillis()).put("phase", "REQUESTED")
            val scope = SetUpAgainScope.valueOf(operation.getString("scope"))
            val id = operation.getString("id")
            if (!id.matches(Regex("[a-f0-9-]{36}"))) throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
            val destination = File(noBackup, "$STORAGE_QUARANTINE_PREFIX$id$SET_UP_AGAIN_SUFFIX")
            if (operation.getString("phase") == "REQUESTED") {
                // Recheck the original failure in the new process, before any file is moved.
                // This probe must neither initialize preferences nor generate an alias.
                if (scope == SetUpAgainScope.ALL_WALLET_FILES && !operation.has("target")) {
                    val cause = operation.getString("cause")
                    val originalStillFails = when (cause) {
                        WalletRecoveryOptions.PREFERENCES_INVENTORY_INVALID -> try {
                            walletPreferenceInventory(context); false
                        } catch (error: WalletUpgradeBackupException) {
                            if (error.code != cause) throw error
                            true
                        }
                        WalletRecoveryOptions.KEYSTORE_KEY_UNAVAILABLE -> try {
                            EncryptionUtil(context, readOnlyInspection = true).requireKeyMaterialReadable(); false
                        } catch (error: KeyMaterialUnavailableException) {
                            if (error.transient) throw error
                            true
                        }
                        // An old request has no original cause to recheck: retain everything and
                        // return to the panel rather than guessing which files are unusable.
                        else -> throw WalletUpgradeBackupException("SET_UP_AGAIN_RECONFIRM")
                    }
                    if (!originalStillFails) {
                        Os.remove(marker.path); fsyncDirectory(noBackup)
                        return@withPublicationLock
                    }
                }
                operation.optString("target").takeIf { it.isNotEmpty() }?.let { target ->
                    val generation = generationManifest(context, target)
                    if (generation.optString("state") != "PRESERVED" || generationScope(generation) != scope) {
                        throw WalletUpgradeBackupException("GENERATION_INVALID")
                    }
                    verifyGeneration(File(noBackup, target), generation.getJSONArray("files"))
                    operation.put("restoreFiles", generation.getJSONArray("files"))
                }
                val files = setAsideInventory(context, scope)
                operation.put("files", files).put("phase", "PRESERVING")
                // The complete immutable path/hash inventory is durable before the first rename.
                writeOperation(context, operation)
            }
            if (!destination.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")) {
                createDirectoryExclusive(destination, "SET_UP_AGAIN_INCOMPLETE")
                fsyncDirectory(noBackup)
            }
            val files = operation.getJSONArray("files")
            if (operation.getString("phase") == "PRESERVING") {
                moveGenerationFiles(File(context.applicationInfo.dataDir), destination, files)
                writeGeneration(destination, JSONObject().put("scope", scope.name).put("created", operation.getLong("created"))
                    .put("state", "PRESERVED").put("files", files))
                operation.put("phase", "RESTORING")
                writeOperation(context, operation)
            }
            val target = operation.optString("target")
            if (target.isNotEmpty()) {
                val targetDirectory = File(noBackup, requireGenerationId(target))
                moveGenerationFiles(targetDirectory, File(context.applicationInfo.dataDir), operation.getJSONArray("restoreFiles"))
                val manifest = generationManifest(context, target)
                manifest.put("state", "ACTIVE")
                writeGeneration(targetDirectory, manifest)
            }
            resetAutomaticMigrationRetries(context)
            Os.remove(marker.path)
            fsyncDirectory(noBackup)
        }
    }

    private fun setAsideInventory(context: Context, scope: SetUpAgainScope): JSONArray {
        val dataDir = File(context.applicationInfo.dataDir)
        val names = mutableListOf("databases/app.db", "databases/app.db-wal", "databases/app.db-shm", "databases/app.db-journal")
        if (scope == SetUpAgainScope.ALL_WALLET_FILES) {
            val datastore = File(context.filesDir, "datastore")
            if (datastore.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")) {
                datastore.directoryIdentityNoFollow("SET_UP_AGAIN_INCOMPLETE")
                val children = datastore.listFiles() ?: throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
                names += children.filter { it.name.startsWith("sora_prefs_datastore.preferences_pb") }.map { "files/datastore/${it.name}" }
            }
            names += listOf("sora_prefs.xml", "sora_prefs.xml.bak", "key_alias.xml", "key_alias.xml.bak").map { "shared_prefs/$it" }
        }
        return JSONArray().also { array ->
            names.sorted().forEach { name ->
                val file = File(dataDir, name)
                if (file.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")) {
                    val fingerprint = file.inspectRegularNoFollow("SET_UP_AGAIN_INCOMPLETE")
                    array.put(JSONObject().put("name", name).put("size", fingerprint.size).put("sha256", fingerprint.sha256))
                }
            }
        }
    }

    private fun requireGenerationId(id: String): String = id.also {
        if (!it.matches(Regex("wallet-quarantine-[A-Za-z0-9-]{1,100}-set-up-again(?:-[0-9]+)?"))) throw WalletUpgradeBackupException("GENERATION_INVALID")
    }

    private fun generationManifest(context: Context, id: String): JSONObject = JSONObject(
        File(File(context.noBackupFilesDir, requireGenerationId(id)), "generation.json")
            .readBoundedUtf8NoFollow(1, MAX_MANIFEST_BYTES, "GENERATION_INVALID"),
    )

    private fun generationScope(manifest: JSONObject): SetUpAgainScope {
        val scope = SetUpAgainScope.valueOf(manifest.getString("scope"))
        if (scope == SetUpAgainScope.NONE) throw WalletUpgradeBackupException("GENERATION_INVALID")
        if (scope == SetUpAgainScope.DATABASE_ONLY && generationRecords(manifest.getJSONArray("files")).any { !it.first.startsWith("databases/") }) {
            throw WalletUpgradeBackupException("GENERATION_INVALID")
        }
        return scope
    }

    private fun writeGeneration(directory: File, manifest: JSONObject) {
        val staging = File(directory, ".generation.json.tmp")
        if (staging.existsNoFollowOrThrow("GENERATION_INVALID")) Os.remove(staging.path)
        writeDurably(staging, manifest.toString())
        Os.rename(staging.path, File(directory, "generation.json").path)
        fsyncDirectory(directory)
    }

    private fun generationRecords(files: JSONArray): List<Pair<String, FileFingerprint>> {
        if (files.length() > MAX_BACKUP_FILES) throw WalletUpgradeBackupException("GENERATION_INVALID")
        return (0 until files.length()).map { index ->
            val record = files.getJSONObject(index)
            val name = record.getString("name")
            val valid = name.matches(Regex("databases/app\\.db(?:-wal|-shm|-journal)?")) ||
                name.matches(Regex("shared_prefs/(?:sora_prefs|key_alias)\\.xml(?:\\.bak)?")) ||
                name.matches(Regex("files/datastore/sora_prefs_datastore\\.preferences_pb[A-Za-z0-9._-]*"))
            if (!valid) throw WalletUpgradeBackupException("GENERATION_INVALID")
            name to FileFingerprint(record.getLong("size"), record.getString("sha256"))
        }.also { if (it.map { record -> record.first }.distinct().size != it.size) throw WalletUpgradeBackupException("GENERATION_INVALID") }
    }

    private fun verifyGeneration(directory: File, files: JSONArray) {
        directory.directoryIdentityNoFollow("GENERATION_INVALID")
        generationRecords(files).forEach { (name, fingerprint) ->
            requireGenerationParents(directory, name)
            if (File(directory, name).inspectRegularNoFollow("GENERATION_INVALID") != fingerprint) throw WalletUpgradeBackupException("GENERATION_INVALID")
        }
    }

    private fun requireGenerationParents(root: File, name: String, allowMissing: Boolean = false) {
        root.directoryIdentityNoFollow("GENERATION_INVALID")
        var directory = root
        name.substringBeforeLast('/').split('/').forEach { component ->
            directory = File(directory, component)
            if (allowMissing && !directory.existsNoFollowOrThrow("GENERATION_INVALID")) return
            directory.directoryIdentityNoFollow("GENERATION_INVALID")
        }
    }

    /** Progress is inferred from the immutable hashes: exactly one side must own every byte.
     * Any interrupted rename resumes the same destination; conflicts never overwrite a file. */
    private fun moveGenerationFiles(source: File, destination: File, files: JSONArray) {
        generationRecords(files).forEach { (name, fingerprint) ->
            val original = File(source, name)
            val target = File(destination, name)
            requireGenerationParents(source, name, allowMissing = true)
            requireGenerationParents(destination, name, allowMissing = true)
            val sourceExists = original.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")
            val targetExists = target.existsNoFollowOrThrow("SET_UP_AGAIN_INCOMPLETE")
            if (sourceExists == targetExists) throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
            if (sourceExists) {
                if (original.inspectRegularNoFollow("SET_UP_AGAIN_INCOMPLETE") != fingerprint) throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
                ensureDirectoryTreeExclusive(destination, requireNotNull(target.parentFile), "SET_UP_AGAIN_INCOMPLETE")
                var createdParent: File? = target.parentFile
                while (createdParent != null && createdParent.toPath().startsWith(destination.toPath())) {
                    fsyncDirectory(createdParent)
                    if (createdParent == destination) break
                    createdParent = createdParent.parentFile
                }
                Os.rename(original.path, target.path)
                fsyncDirectory(requireNotNull(target.parentFile))
                fsyncDirectory(requireNotNull(original.parentFile))
                afterSetAsideRenameForTest?.invoke()
            } else if (target.inspectRegularNoFollow("SET_UP_AGAIN_INCOMPLETE") != fingerprint) {
                throw WalletUpgradeBackupException("SET_UP_AGAIN_INCOMPLETE")
            }
        }
    }

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

    /** A journal header is only a reason to ask SQLite; SQLite decides whether it is hot. */
    private fun hasRollbackJournalHeader(database: File): Boolean {
        val journal = File("${database.path}-journal")
        if (!journal.existsNoFollowOrThrow("DATABASE_PATH_UNREADABLE")) return false
        return journal.openRegularNoFollow("DATABASE_PATH_UNREADABLE").use { opened ->
            val header = ByteArray(8)
            try {
                opened.input.read(header) > 0 && header.any { it != 0.toByte() }
            } finally {
                header.fill(0)
            }
        }
    }

    /** No create flag, schema changes, Room, or destructive corruption handler. */
    private fun replayRollbackJournal(database: File) {
        SQLiteDatabase.openDatabase(
            database.path, null, SQLiteDatabase.OPEN_READWRITE,
            FAIL_CLOSED_DATABASE_ERROR_HANDLER,
        ).use { sqlite ->
            // Opening a handle or reading user_version alone need not touch a b-tree page.
            sqlite.rawQuery("SELECT count(*) FROM sqlite_master", null).use { cursor ->
                if (!cursor.moveToFirst()) throw WalletUpgradeBackupException("DATABASE_RECOVERY_FAILED")
                cursor.getLong(0)
            }
        }
    }

    /**
     * First replay a disposable copy, then publish a verified backup of the still-unchanged raw
     * files. Only after publication and a final byte comparison may SQLite recover the live DB.
     * A crash at any step leaves either the original hot journal or a recoverable full backup.
     */
    private fun recoverRollbackJournal(
        context: Context,
        database: File,
        preferences: WalletPreferenceInventory,
        availableBytes: Long?,
    ) = withPublicationLock(context.noBackupFilesDir, BACKUP_PUBLICATION_LOCK,
        "BACKUP_PUBLICATION_LOCK_FAILED") {
        val sources = knownWalletFiles(context, database).filter { it.existsNoFollow() }
        requireBackupCapacity(context.noBackupFilesDir, sources, availableBytes)
        val rawSnapshot = createSourceSnapshot(context, sources, emptySet(), preferences.selectedWalletId)
        val directory = File(context.cacheDir, "wallet-journal-replay-${UUID.randomUUID()}")
        val parent = requireNotNull(directory.parentFile)
        val parentIdentity = parent.directoryInodeIdentityNoFollow("DATABASE_RECOVERY_COPY_FAILED")
        createDirectoryExclusive(directory, "DATABASE_RECOVERY_COPY_FAILED")
        val identity = directory.directoryInodeIdentityNoFollow("DATABASE_RECOVERY_COPY_FAILED")
        try {
            rawSnapshot.files.filter { it.source.isDatabaseFile(database) }.forEach { record ->
                val target = File(directory, record.source.name)
                copyDurably(record.source, target)
                if (target.inspectRegularNoFollow("SOURCE_CHANGED") != FileFingerprint(record.size, record.sha256)) {
                    throw WalletUpgradeBackupException("SOURCE_CHANGED")
                }
            }
            val copy = File(directory, DATABASE_NAME)
            replayRollbackJournal(copy)
            val version = readVersion(copy)
            if (version <= 0 || version > CURRENT_DATABASE_VERSION) {
                throw WalletUpgradeBackupException("INVALID_DATABASE_VERSION")
            }
            if (!copy.passesSqliteIntegrityCheck()) throw WalletUpgradeBackupException("DATABASE_CORRUPT")
            val inventory = readWalletDatabaseInventory(copy, version)
            val walletIds = inventory.accounts.map(SoraAccountLocal::substrateAddress).toSet()
            val beforeReplay = createSourceSnapshot(context,
                knownWalletFiles(context, database).filter { it.existsNoFollow() },
                emptySet(), preferences.selectedWalletId)
            if (beforeReplay != rawSnapshot || walletPreferenceInventory(context) != preferences) {
                throw WalletUpgradeBackupException("SOURCE_CHANGED")
            }
            val snapshot = rawSnapshot.copy(
                legacyAccountCount = walletIds.size,
                legacyWalletIdsSha256 = sha256Text(walletIds.sorted().joinToString("\n")),
            )
            publishVerifiedSnapshotBackupUnderLock(
                context, database, version, inventory, preferences, walletIds,
                availableBytes, null, cheapBackupInventoryUnderLock(context), snapshot,
            )
            replayRollbackJournal(database)
            if (readVersion(database) != version || readWalletDatabaseInventory(database, version) != inventory) {
                throw WalletUpgradeBackupException("DATABASE_RECOVERY_FAILED")
            }
            nonBlockingWarnings = nonBlockingWarnings + "DATABASE_JOURNAL_RECOVERED"
            FirebaseWrapper.recordWalletRecovery(WalletRecoveryEvent.JOURNAL_RECOVERED)
        } finally {
            removeExactValidationTree(directory, identity, parentIdentity)
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
        var blankRows = 0
        return sqlite.let { database ->
            if (!database.tableExists("accounts")) {
                // Multi-account storage did not exist in these release schemas. Keys remain
                // in the independently inventoried encrypted preferences, never in this cache.
                if (sourceVersion in 15 until 58) {
                    return@let WalletDatabaseInventory(accounts = emptyList())
                }
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
                var ignoredRows = 0
                buildList {
                    val ids = mutableSetOf<String>()
                    while (cursor.moveToNext()) {
                        val walletId = cursor.getString(0)
                        if (
                            walletId.isNullOrBlank() ||
                            walletId.length > MAX_WALLET_ID_LENGTH
                        ) {
                            // A blank row (written by a 2.x upgrade that could not derive an
                            // address) cannot hold a wallet. It is ignored, never deleted.
                            ignoredRows += 1
                            continue
                        }
                        if (!ids.add(walletId)) {
                            throw WalletUpgradeBackupException(
                                "LEGACY_ACCOUNT_ID_DUPLICATE"
                            )
                        }
                        add(SoraAccountLocal(walletId, cursor.getString(1)))
                    }
                } to ignoredRows
            }.let { (rows, ignored) ->
                blankRows = ignored
                rows
            }

            if (sourceVersion < CURRENT_DATABASE_VERSION) {
                return@let WalletDatabaseInventory(
                    accounts = accounts,
                    blankAccountRows = blankRows,
                )
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
                blankAccountRows = blankRows,
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
        // A user may skip the releases that first populated the accounts table. Admit only
        // the unambiguous old single-account shape (or its interrupted metadata insert).
        // This allows backup/schema preparation, not signing: repository initialization must
        // decrypt and prove the retained keypair before publishing the account and selection.
        if (isPendingLegacySingleAccountUpgrade(database, preferences)) return
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
            // Key sets without an account row, a stale selection or a stale release-era owner
            // are set aside by computeQuarantine(); per-account problems are classified by the
            // wallet verification. None of them blocks startup.
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
                if (preferences.fingerprint != operation.beforePreferencesHash) {
                    throw WalletUpgradeBackupException(
                        "WALLET_DELETION_PREFERENCES_MISMATCH"
                    )
                }
            }
            WalletDeletionContract.PHASE_DATABASE_COMMITTED -> {
                // Unrelated key sets without an account row are unchanged bytes; they are set
                // aside, never a reason to block. The exact preference fingerprint still binds the
                // journal to the previewed state.
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
                    operation.selectedAfter
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
        // Without account rows there is nothing to verify; the wallet verification clears a stale
        // journal. Without a journal, identities that are not verified yet (for example the
        // untouched output of the 73 -> 74 schema step) are verified by the next run.
        if (journal == null || database.accounts.isEmpty()) {
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
                // A VERIFIED receipt finished before it started only because the clock moved
                // back. The wallet verification records it as needing a retry (which the next
                // start does automatically) instead of blocking every start here.
                journal.state != "VERIFIED" &&
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
            // The count holds only the accounts that can sign: read-only accounts are classified
            // by the verification, not verified. The range was checked above.
            journal.selectedWalletId.isBlank()
        ) {
            throw WalletUpgradeBackupException("WALLET_MIGRATION_JOURNAL_INVALID")
        }
        // A VERIFIED journal is the last fully verified wallet checkpoint, not a requirement that
        // the live wallet inventory remain immutable. Explicit post-migration account creation,
        // renaming, selection, and removal legitimately change the current count/hash/selection.
        // MigrationManager re-verifies every secret/model on each start, classifies problems per
        // account and advances the exact prior checkpoint by CAS.
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
        val stateFieldsValid = when (journal.state) {
            "VERIFYING" ->
                journal.failureCode == null &&
                    journal.completedAt == null
            "RECOVERY_REQUIRED" ->
                !journal.failureCode.isNullOrBlank() &&
                    journal.completedAt != null
            else -> false
        }
        // The recorded counts, selection and hash may be stale (an earlier build recorded some
        // failures without a selection). A retry re-verifies everything from the account rows and
        // encrypted preferences, so only the journal's own shape is required here. Key sets without
        // an account row and rows without secrets are classified per account by the retry.
        if (
            journal.migrationId != WalletMigrationIds.NETWORK_ACCOUNTS_V1 ||
            !stateFieldsValid ||
            legacyWalletIds.isEmpty() ||
            journal.legacyAccountCount < 0 ||
            journal.selectedWalletId.length > MAX_WALLET_ID_LENGTH ||
            !SHA256.matches(journal.integrityHash) && journal.integrityHash.isNotEmpty() ||
            journal.startedAt <= 0L ||
            (
                journal.completedAt != null &&
                    journal.completedAt < journal.startedAt
                ) ||
            (journal.failureCode?.length ?: 0) > 128 ||
            database.deletionOperations.isNotEmpty() ||
            database.deletionTargets.isNotEmpty()
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

    private fun isPendingLegacySingleAccountUpgrade(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ): Boolean =
        preferences.hasUnscopedRegisteredKeyPair &&
            preferences.selectedWalletId == null &&
            preferences.scopedWalletIds.isEmpty() &&
            database.accounts.size <= 1 &&
            (preferences.legacyPureAddress == null || database.accounts.all {
                it.substrateAddress == preferences.legacyPureAddress
            }) &&
            database.identities.isEmpty() &&
            database.networkAccounts.isEmpty() &&
            database.migrationJournals.isEmpty() &&
            database.pendingWalletIds.isEmpty() &&
            database.deletionOperations.isEmpty() &&
            database.deletionTargets.isEmpty()

    /**
     * Describes key material that no account row owns. Nothing here is written, renamed or
     * deleted: the material stays byte-identical, is ignored for the active wallet set, and can be
     * adopted later when its owner proves it (for example by importing the matching phrase).
     */
    private fun computeQuarantine(
        database: WalletDatabaseInventory,
        preferences: WalletPreferenceInventory,
    ): WalletQuarantine {
        val rows = database.accounts.map(SoraAccountLocal::substrateAddress).toSet()
        val pendingLegacyUpgrade = isPendingLegacySingleAccountUpgrade(database, preferences)
        val legacyOwner = preferences.legacyPureAddress
        return WalletQuarantine(
            unlinkedWalletIds = preferences.scopedWalletIds - rows,
            incompleteKeySetWalletIds = preferences.incompleteKeyPairWalletIds,
            unlinkedLegacyOwner = legacyOwner?.takeIf {
                it !in rows && !pendingLegacyUpgrade
            },
            unscopedMaterialWithoutRecordedOwner = preferences.hasUnscopedCredential &&
                !pendingLegacyUpgrade &&
                (legacyOwner == null || legacyOwner !in rows),
            staleSelection = preferences.selectedWalletId?.takeIf { it !in rows },
            blankAccountRows = database.blankAccountRows,
        )
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
            hasRecoveryPhraseOrSeed = entries.any { entry ->
                !entry.stringValue.isNullOrBlank() &&
                    (
                        entry.key.startsWith(WalletPreferenceKeys.MNEMONIC) ||
                            entry.key.startsWith(WalletPreferenceKeys.SEED)
                        )
            },
            hasUnscopedCredential = entries.any { entry ->
                entry.key in WalletPreferenceKeys.encryptedCredentialPrefixes &&
                    !entry.stringValue.isNullOrBlank()
            },
            hasUnscopedPhraseOrSeed = entries.any { entry ->
                (entry.key == WalletPreferenceKeys.MNEMONIC || entry.key == WalletPreferenceKeys.SEED) &&
                    !entry.stringValue.isNullOrBlank()
            },
            hasWalletMaterial = walletEntries.isNotEmpty(),
            hasWrappedWalletKeyEvidence = false,
            hasUnscopedRegisteredKeyPair =
                entries.any {
                    it.key == WalletPreferenceKeys.REGISTRATION_STATE &&
                        it.stringValue == REGISTRATION_FINISHED
                } && listOf(
                    WalletPreferenceKeys.PRIVATE_KEY,
                    WalletPreferenceKeys.PUBLIC_KEY,
                    WalletPreferenceKeys.KEY_NONCE,
                ).all { key ->
                    entries.any { it.key == key && !it.stringValue.isNullOrBlank() }
                },
            scopedWalletIds = scopedWalletIds,
            selectedWalletId = entries.firstOrNull {
                it.key == WalletPreferenceKeys.CURRENT_ACCOUNT_ADDRESS &&
                    !it.stringValue.isNullOrBlank()
            }?.stringValue,
            legacyPureAddress = legacyOwner,
            mnemonicWalletIds = mnemonicWalletIds,
            rawSeedWalletIds = rawSeedWalletIds,
            scopedPhraseOrSeedWalletIds = entries.scopedPhraseOrSeedIds(),
            keyPairWalletIds = keyPairWalletIds,
            incompleteKeyPairWalletIds = incompleteKeyPairWalletIds,
            watchOnlyWalletIds = watchOnlyWalletIds,
            allKeys = entries.map(PreferenceEntry::key).toSet(),
            fingerprint = walletPreferenceFingerprint(entries),
        )
    }

    /** Addresses with a non-blank phrase or seed stored under the address itself. */
    private fun List<PreferenceEntry>.scopedPhraseOrSeedIds(): Set<String> =
        scopedNonBlankStringIds(WalletPreferenceKeys.MNEMONIC) +
            scopedNonBlankStringIds(WalletPreferenceKeys.SEED)

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

    /**
     * Full validation of one backup tree (hashes, manifest, copied database integrity and wallet
     * inventory). When [walletIdsOut] is given and the backup is valid, it receives every wallet
     * the copy holds: account rows, address-scoped key sets and a release-era owner. Release-era
     * keys without a recorded owner add a marker that matches no account, so such a copy is never
     * treated as redundant.
     */
    private fun isCompleteAndValid(
        context: Context,
        directory: File,
        expectedRegularLinkCount: Long = 1L,
        walletIdsOut: MutableSet<String>? = null,
    ): Boolean {
        WorkCountersForTest.fullBackupValidations.incrementAndGet()
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
            val kind = manifest.optString("kind", BACKUP_KIND_FULL)
            if (kind != BACKUP_KIND_FULL && kind != BACKUP_KIND_KEYS_ONLY) {
                return@runCatching false
            }
            val keysOnly = kind == BACKUP_KIND_KEYS_ONLY
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
            (REQUIRED_DATABASE_BACKUP in names) != keysOnly &&
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
                (
                    if (keysOnly) {
                        validateKeysOnlyInventory(directory, manifest, walletIdsOut)
                    } else {
                        validateCopiedWalletInventory(
                            context = context,
                            directory = directory,
                            manifest = manifest,
                            names = names,
                            walletIdsOut = walletIdsOut,
                        )
                    }
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

    private fun MutableSet<String>.addCopiedWallets(
        rows: Collection<String>,
        preferences: WalletPreferenceInventory,
    ) {
        addAll(rows)
        addAll(preferences.scopedWalletIds)
        preferences.legacyPureAddress?.let(::add)
        if (preferences.hasUnscopedCredential && preferences.legacyPureAddress == null) {
            add(UNOWNED_RELEASE_ERA_KEYS)
        }
    }

    private fun validateKeysOnlyInventory(
        directory: File,
        manifest: JSONObject,
        walletIdsOut: MutableSet<String>? = null,
    ): Boolean {
        val accounts = manifest.getJSONArray("accounts")
        val walletIds = (0 until accounts.length()).map { index ->
            accounts.getJSONObject(index).getString("address")
        }
        if (
            walletIds.toSet().size != walletIds.size ||
            walletIds.any { it.isBlank() || it.length > MAX_WALLET_ID_LENGTH }
        ) {
            return false
        }
        val preferences = walletPreferenceInventory(
            dataStore = File(directory, "files/datastore/sora_prefs_datastore.preferences_pb"),
            sharedPreferences = File(directory, "shared_prefs/sora_prefs.xml"),
            sharedPreferencesBackup = File(directory, "shared_prefs/sora_prefs.xml.bak"),
        )
        val selectedHash = preferences.selectedWalletId?.let(::sha256Text)
        val valid = walletIds.size == manifest.getInt("legacyAccountCount") &&
            sha256Text(walletIds.sorted().joinToString(separator = "\n")) ==
            manifest.getString("legacyWalletIdsSha256") &&
            if (manifest.isNull("selectedWalletIdSha256")) {
                selectedHash == null
            } else {
                selectedHash == manifest.getString("selectedWalletIdSha256")
            }
        if (valid) walletIdsOut?.addCopiedWallets(walletIds, preferences)
        return valid
    }

    private fun validateCopiedWalletInventory(
        context: Context,
        directory: File,
        manifest: JSONObject,
        names: Set<String>,
        walletIdsOut: MutableSet<String>? = null,
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
            if (hasRollbackJournalHeader(databaseCopy)) replayRollbackJournal(databaseCopy)
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
            // A copy of a wallet whose verification was interrupted is still a valid backup.
            val coherence = runCatching {
                validateWalletStorageCoherence(inventory, preferences)
            }
            val storageCoherent = coherence.isSuccess ||
                (
                    sourceVersion == CURRENT_DATABASE_VERSION &&
                        (coherence.exceptionOrNull() as? WalletUpgradeBackupException)?.code ==
                        "WALLET_MIGRATION_RECOVERY_REQUIRED"
                    )
            if (!storageCoherent) return false
            val walletIds = inventory.accounts
                .map(SoraAccountLocal::substrateAddress)
                .toSet()
            val selectedHash = preferences.selectedWalletId?.let(::sha256Text)
            val valid = inventory.accounts.size == manifest.getInt("legacyAccountCount") &&
                sha256Text(walletIds.sorted().joinToString(separator = "\n")) ==
                manifest.getString("legacyWalletIdsSha256") &&
                if (manifest.isNull("selectedWalletIdSha256")) {
                    selectedHash == null
                } else {
                    selectedHash == manifest.getString("selectedWalletIdSha256")
                }
            if (valid) {
                walletIdsOut?.addCopiedWallets(walletIds, preferences)
                // Blank rows cannot be compared with live accounts; keep such a copy.
                if (inventory.blankAccountRows > 0) walletIdsOut?.add(UNOWNED_RELEASE_ERA_KEYS)
            }
            valid
        } finally {
            removeExactValidationTree(
                directory = validationDirectory,
                expectedRootIdentity = validationDirectoryIdentity,
                expectedParentIdentity = validationParentIdentity,
            )
        }
    }

    private fun File.passesSqliteQuickCheck(): Boolean =
        SQLiteDatabase.openDatabase(
            path,
            null,
            SQLiteDatabase.OPEN_READONLY,
            FAIL_CLOSED_DATABASE_ERROR_HANDLER,
        ).use { sqlite ->
            sqlite.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() &&
                    cursor.getString(0).equals("ok", ignoreCase = true) &&
                    !cursor.moveToNext()
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
            val buffer = ByteArray(FILE_IO_BUFFER_BYTES)
            try {
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    bytesRead = Math.addExact(bytesRead, count.toLong())
                }
                WorkCountersForTest.hashedBytes.addAndGet(bytesRead)
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
            val buffer = ByteArray(FILE_IO_BUFFER_BYTES)
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
        WorkCountersForTest.copiedFiles.incrementAndGet()
        source.openRegularNoFollow(
            "BACKUP_SOURCE_INVALID"
        ).use { openedSource ->
            destination.openNewRegularNoFollow(
                "CREATE_BACKUP_FILE"
            ).use { openedDestination ->
                val input = openedSource.input
                val output = openedDestination.output
                val buffer = ByteArray(FILE_IO_BUFFER_BYTES)
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
        is android.database.sqlite.SQLiteFullException -> WalletRecoveryOptions.STORAGE_FULL
        is android.database.sqlite.SQLiteDatabaseLockedException -> WalletRecoveryOptions.DATABASE_LOCKED
        is android.database.sqlite.SQLiteCantOpenDatabaseException -> "DATABASE_OPEN_UNAVAILABLE"
        is android.database.sqlite.SQLiteDiskIOException -> "DATABASE_IO"
        is SQLiteReadOnlyDatabaseException -> "DATABASE_READ_ONLY"
        is android.database.sqlite.SQLiteException -> "DATABASE_OPEN_FAILED"
        is ErrnoException -> if (errno == OsConstants.ENOSPC) WalletRecoveryOptions.STORAGE_FULL else "IO"
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

    private data class StartupAdmission(
        val packageName: String,
        val dataDirectory: String,
        val databasePath: String,
        val filesDirectory: String,
        val noBackupDirectory: String,
        val noBackupDirectoryIdentity: StartupDirectoryIdentity,
        val paths: List<StartupPathSnapshot>,
    )

    private data class StartupDirectoryIdentity(
        val device: Long,
        val inode: Long,
        val mode: Int,
        val owner: Int,
        val group: Int,
    )

    private data class StartupPathSnapshot(
        val path: String,
        val metadata: StartupPathMetadata?,
    )

    private data class StartupPathMetadata(
        val device: Long,
        val inode: Long,
        val mode: Int,
        val linkCount: Long,
        val owner: Int,
        val group: Int,
        val size: Long,
        val modifiedAtSeconds: Long,
        val modifiedAtNanoseconds: Long,
        val changedAtSeconds: Long,
        val changedAtNanoseconds: Long,
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
        val hasRecoveryPhraseOrSeed: Boolean,
        /** A release-era phrase or seed is stored without an address suffix. */
        val hasUnscopedPhraseOrSeed: Boolean = false,
        val hasUnscopedCredential: Boolean,
        val hasWalletMaterial: Boolean,
        val hasWrappedWalletKeyEvidence: Boolean,
        val hasUnscopedRegisteredKeyPair: Boolean,
        val scopedWalletIds: Set<String>,
        val selectedWalletId: String?,
        val legacyPureAddress: String?,
        val mnemonicWalletIds: Set<String>,
        val rawSeedWalletIds: Set<String>,
        /** Addresses with a phrase or seed stored under the address itself (never the 2.x owner's unsuffixed one). */
        val scopedPhraseOrSeedWalletIds: Set<String> = emptySet(),
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
        val blankAccountRows: Int = 0,
    )

    private data class BackupInventoryEntry(
        val directory: File,
        val generation: Long,
        val sourceVersion: Int,
        val targetVersion: Int,
        val kind: String,
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

class WalletUpgradeBackupException(
    val code: String,
    /** For INSUFFICIENT_BACKUP_STORAGE: free bytes needed to continue the upgrade. */
    val requiredBytes: Long? = null,
    /** For INSUFFICIENT_BACKUP_STORAGE: free bytes found. */
    val availableBytes: Long? = null,
) : IllegalStateException(code)
