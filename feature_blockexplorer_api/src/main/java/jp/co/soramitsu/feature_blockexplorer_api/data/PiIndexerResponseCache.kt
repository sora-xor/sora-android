package jp.co.soramitsu.feature_blockexplorer_api.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import jp.co.soramitsu.common.network.requireStrictJsonDocumentWithoutDuplicateKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The identity-bearing health response which qualified an indexed response.
 *
 * This travels with every cached body so an offline read cannot accidentally
 * detach data from the chain/checkpoint that was live immediately before it.
 */
@Serializable
internal data class PiIndexerCacheQualification(
    val health: PiIndexerHealth,
    val qualifiedAtEpochMillis: Long,
)

@Serializable
internal data class PiIndexerCacheEntry(
    val formatVersion: Int = 1,
    val requestKey: String,
    val savedAtEpochMillis: Long,
    val qualification: PiIndexerCacheQualification,
    val responseJson: String,
)

internal interface PiIndexerResponseCache {
    suspend fun load(
        key: String,
        maximumAgeMillis: Long,
    ): PiIndexerCacheEntry?

    suspend fun store(
        key: String,
        entry: PiIndexerCacheEntry,
    )

    suspend fun remove(key: String)
}

internal object DisabledPiIndexerResponseCache : PiIndexerResponseCache {
    override suspend fun load(key: String, maximumAgeMillis: Long): PiIndexerCacheEntry? = null
    override suspend fun store(key: String, entry: PiIndexerCacheEntry) = Unit
    override suspend fun remove(key: String) = Unit
}

/**
 * Small app-private, no-backup cache for PI read responses.
 *
 * Files are request-body SHA-256 keys, writes are copy-on-write, and both each
 * entry and the complete directory have hard bounds. Corrupt, oversized,
 * symlinked, future-dated, and expired entries are treated as absent.
 */
internal class FilePiIndexerResponseCache private constructor(
    private val directory: File,
    private val json: Json,
    private val nowEpochMillis: () -> Long,
) : PiIndexerResponseCache {

    internal constructor(
        context: Context,
        json: Json,
    ) : this(
        directory = File(context.noBackupFilesDir, DIRECTORY_NAME),
        json = json,
        nowEpochMillis = System::currentTimeMillis,
    )

    internal constructor(
        directory: File,
        json: Json,
        nowEpochMillis: () -> Long,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit,
    ) : this(directory, json, nowEpochMillis)

    private val mutex = Mutex()

    override suspend fun load(
        key: String,
        maximumAgeMillis: Long,
    ): PiIndexerCacheEntry? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!validKey(key) || maximumAgeMillis !in 0L..MAXIMUM_CACHE_AGE_MILLIS) {
                return@withLock null
            }
            if (
                !directory.isDirectory ||
                Files.isSymbolicLink(directory.toPath())
            ) {
                return@withLock null
            }
            prune()
            val file = File(directory, "$key.json")
            if (
                !file.isFile ||
                Files.isSymbolicLink(file.toPath()) ||
                file.length() !in 1L..MAXIMUM_FILE_BYTES
            ) {
                deleteRegularFile(file)
                return@withLock null
            }

            val entry = try {
                val bytes = file.readBytes()
                if (bytes.size.toLong() !in 1L..MAXIMUM_FILE_BYTES) {
                    null
                } else {
                    val rawEntry = bytes.decodeToString(throwOnInvalidSequence = true)
                    requireStrictJsonDocumentWithoutDuplicateKeys(rawEntry)
                    json.decodeFromString(PiIndexerCacheEntry.serializer(), rawEntry)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
            val responseIsAdmitted = entry?.responseJson?.let { responseJson ->
                if (
                    responseJson.toByteArray(Charsets.UTF_8).size > MAXIMUM_RESPONSE_BYTES
                ) {
                    false
                } else {
                    try {
                        requireStrictJsonDocumentWithoutDuplicateKeys(responseJson)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            } ?: false

            val now = nowEpochMillis()
            if (
                entry == null ||
                entry.formatVersion != FORMAT_VERSION ||
                entry.requestKey != key ||
                !responseIsAdmitted ||
                !timestampsAreValid(
                    now = now,
                    savedAt = entry.savedAtEpochMillis,
                    qualifiedAt = entry.qualification.qualifiedAtEpochMillis,
                    maximumAgeMillis = maximumAgeMillis,
                )
            ) {
                deleteRegularFile(file)
                null
            } else {
                entry
            }
        }
    }

    override suspend fun store(
        key: String,
        entry: PiIndexerCacheEntry,
    ) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = nowEpochMillis()
            val responseBytes = entry.responseJson.toByteArray(Charsets.UTF_8).size
            if (responseBytes > MAXIMUM_RESPONSE_BYTES) return@withLock
            try {
                requireStrictJsonDocumentWithoutDuplicateKeys(entry.responseJson)
            } catch (_: Exception) {
                return@withLock
            }
            if (
                !validKey(key) ||
                entry.formatVersion != FORMAT_VERSION ||
                entry.requestKey != key ||
                !timestampsAreValid(
                    now = now,
                    savedAt = entry.savedAtEpochMillis,
                    qualifiedAt = entry.qualification.qualifiedAtEpochMillis,
                    maximumAgeMillis = MAXIMUM_CACHE_AGE_MILLIS,
                )
            ) {
                return@withLock
            }
            val encoded = try {
                json.encodeToString(entry).toByteArray(Charsets.UTF_8)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return@withLock
            }
            if (encoded.size.toLong() !in 1L..MAXIMUM_FILE_BYTES) return@withLock

            try {
                if (!directory.exists() && !directory.mkdirs()) return@withLock
                if (
                    !directory.isDirectory ||
                    Files.isSymbolicLink(directory.toPath())
                ) {
                    return@withLock
                }
                val destination = File(directory, "$key.json")
                val temporary = File(
                    directory,
                    ".$key.${UUID.randomUUID()}.tmp",
                )
                if (temporary.exists()) {
                    if (Files.isSymbolicLink(temporary.toPath())) return@withLock
                    deleteRegularFile(temporary)
                }
                FileOutputStream(temporary).use { output ->
                    output.write(encoded)
                    output.fd.sync()
                }
                try {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: Exception) {
                    Files.move(
                        temporary.toPath(),
                        destination.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                } finally {
                    deleteRegularFile(temporary)
                }
                destination.setLastModified(entry.savedAtEpochMillis)
                prune()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A cache failure must never fail an otherwise qualified live read.
            }
        }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (validKey(key)) deleteRegularFile(File(directory, "$key.json"))
        }
    }

    private fun prune() {
        val regularFiles = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
        val files = regularFiles
            .filter { file ->
                file.name.endsWith(".json") &&
                    validKey(file.name.removeSuffix(".json"))
            }
            .sortedByDescending(File::lastModified)
        regularFiles
            .filterNot(files::contains)
            .forEach(::deleteRegularFile)
        var retainedBytes = 0L
        files.forEachIndexed { index, file ->
            val length = file.length()
            val retain =
                index < MAXIMUM_ENTRY_COUNT &&
                    length in 1L..MAXIMUM_FILE_BYTES &&
                    retainedBytes + length <= MAXIMUM_TOTAL_BYTES
            if (retain) {
                retainedBytes += length
            } else {
                deleteRegularFile(file)
            }
        }
    }

    private fun deleteRegularFile(file: File) {
        if (file.isFile && !Files.isSymbolicLink(file.toPath())) {
            runCatching { file.delete() }
        }
    }

    private fun validKey(key: String): Boolean =
        key.length == SHA_256_HEX_LENGTH && key.all { it in '0'..'9' || it in 'a'..'f' }

    private fun timestampsAreValid(
        now: Long,
        savedAt: Long,
        qualifiedAt: Long,
        maximumAgeMillis: Long,
    ): Boolean {
        if (
            now < 0 ||
            savedAt < 0 ||
            qualifiedAt < 0 ||
            maximumAgeMillis !in 0L..MAXIMUM_CACHE_AGE_MILLIS
        ) {
            return false
        }
        val latestAllowed = if (now > Long.MAX_VALUE - MAXIMUM_FUTURE_SKEW_MILLIS) {
            Long.MAX_VALUE
        } else {
            now + MAXIMUM_FUTURE_SKEW_MILLIS
        }
        val earliestAllowed = if (now >= maximumAgeMillis) {
            now - maximumAgeMillis
        } else {
            0L
        }
        val earliestQualification = if (savedAt >= MAXIMUM_HEALTH_TO_RESPONSE_MILLIS) {
            savedAt - MAXIMUM_HEALTH_TO_RESPONSE_MILLIS
        } else {
            0L
        }
        return savedAt in earliestAllowed..latestAllowed &&
            qualifiedAt in earliestQualification..savedAt
    }

    private companion object {
        const val DIRECTORY_NAME = "pi-indexer-cache-v1"
        const val FORMAT_VERSION = 1
        const val SHA_256_HEX_LENGTH = 64
        const val MAXIMUM_ENTRY_COUNT = 64
        const val MAXIMUM_RESPONSE_BYTES = 4 * 1_024 * 1_024
        const val MAXIMUM_FILE_BYTES = 8L * 1_024 * 1_024
        const val MAXIMUM_TOTAL_BYTES = 32L * 1_024 * 1_024
        const val MAXIMUM_CACHE_AGE_MILLIS = 24L * 60 * 60 * 1_000
        const val MAXIMUM_HEALTH_TO_RESPONSE_MILLIS = 30_000L
        const val MAXIMUM_FUTURE_SKEW_MILLIS = 30_000L
    }
}
