package jp.co.soramitsu.feature_blockexplorer_api.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.OptionsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * History that already passed both PI qualification and canonical SORA2 RPC block-hash checks.
 *
 * This cache is deliberately separate from the raw PI response cache. A raw cached response still
 * needs live RPC qualification; this cache is the recovery path when both PI and RPC are offline.
 */
data class PiValidatedHistoryRecord(
    val items: List<IndexerHistoryElement>,
    val health: PiIndexerHealth,
    val endReached: Boolean? = null,
    val totalCount: Int? = null,
)

@Singleton
class PiValidatedHistoryCache private constructor(
    private val directory: File,
    private val json: Json,
    private val nowEpochMillis: () -> Long,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
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

    suspend fun readLast(
        address: String,
        count: Int,
    ): PiValidatedHistoryRecord? =
        load(CacheRequest.last(address, count))

    suspend fun storeLast(
        address: String,
        count: Int,
        validation: PiCanonicalHistoryValidationReceipt,
    ) {
        val request = CacheRequest.last(address, count)
        store(
            request = request,
            record = validation.toRecordFor(request),
        )
    }

    suspend fun readTransaction(
        address: String,
        transactionHash: String,
    ): PiValidatedHistoryRecord? =
        load(CacheRequest.transaction(address, transactionHash))

    suspend fun storeTransaction(
        address: String,
        transactionHash: String,
        validation: PiCanonicalHistoryValidationReceipt,
    ) {
        val request = CacheRequest.transaction(address, transactionHash)
        val validated = validation.toRecordFor(request)
        check(
            validated.items.all { it.id == request.transactionHash }
        ) { "PI_VALIDATED_HISTORY_RECEIPT_MISMATCH" }
        store(
            request = request,
            record = validated,
        )
    }

    suspend fun readPage(
        address: String,
        page: Long,
        pageCount: Int,
    ): PiValidatedHistoryRecord? =
        load(CacheRequest.page(address, page, pageCount))

    suspend fun storePage(
        address: String,
        page: Long,
        pageCount: Int,
        pageValue: IndexerHistoryPage,
        validation: PiCanonicalHistoryValidationReceipt,
    ) {
        val request = CacheRequest.page(address, page, pageCount)
        val validated = validation.toRecordFor(request)
        check(validated.items == pageValue.items) {
            "PI_VALIDATED_HISTORY_RECEIPT_MISMATCH"
        }
        store(
            request = request,
            record = PiValidatedHistoryRecord(
                items = validated.items,
                health = validated.health,
                endReached = pageValue.endReached,
                totalCount = pageValue.totalCount,
            ),
        )
    }

    private suspend fun load(
        request: CacheRequest,
    ): PiValidatedHistoryRecord? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = requestKey(request)
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

            val record = try {
                val encodedEnvelope = file.readBytes()
                check(encodedEnvelope.size.toLong() in 1L..MAXIMUM_FILE_BYTES)
                val envelope = json.decodeFromString(
                    CacheEnvelope.serializer(),
                    encodedEnvelope.decodeToString(),
                )
                check(
                    envelope.formatVersion == FORMAT_VERSION &&
                        envelope.requestKey == key &&
                        envelope.payloadJson.toByteArray(Charsets.UTF_8).size <=
                        MAXIMUM_PAYLOAD_BYTES
                )
                val payloadBytes = envelope.payloadJson.toByteArray(Charsets.UTF_8)
                check(
                    MessageDigest.isEqual(
                        sha256(payloadBytes),
                        decodeDigest(envelope.payloadSha256),
                    )
                )
                val payload = json.decodeFromString(
                    CachePayload.serializer(),
                    envelope.payloadJson,
                )
                check(
                    payload.formatVersion == FORMAT_VERSION &&
                        payload.requestKey == key &&
                        payload.request == request &&
                        timestampIsValid(payload.savedAtEpochMillis)
                )
                PiValidatedHistoryRecord(
                    items = payload.items,
                    health = payload.health,
                    endReached = payload.endReached,
                    totalCount = payload.totalCount,
                ).also { validateRecord(request, it, payload.savedAtEpochMillis) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }

            if (record == null) {
                deleteRegularFile(file)
            }
            record
        }
    }

    private fun PiCanonicalHistoryValidationReceipt.toRecordFor(
        request: CacheRequest,
    ): PiValidatedHistoryRecord {
        check(
            expectedAddress == request.address &&
                maximumRecords == request.maximumRecords
        ) { "PI_VALIDATED_HISTORY_RECEIPT_MISMATCH" }
        return PiValidatedHistoryRecord(
            items = elements,
            health = health,
        )
    }

    private suspend fun store(
        request: CacheRequest,
        record: PiValidatedHistoryRecord,
    ) = withContext(Dispatchers.IO) {
        val savedAt = nowEpochMillis()
        validateRecord(request, record, savedAt)
        val key = requestKey(request)
        val payload = CachePayload(
            requestKey = key,
            request = request,
            savedAtEpochMillis = savedAt,
            health = record.health,
            items = record.items,
            endReached = record.endReached,
            totalCount = record.totalCount,
        )
        val payloadJson = json.encodeToString(payload)
        val payloadBytes = payloadJson.toByteArray(Charsets.UTF_8)
        check(payloadBytes.size <= MAXIMUM_PAYLOAD_BYTES) {
            "PI_VALIDATED_HISTORY_CACHE_TOO_LARGE"
        }
        val encoded = json.encodeToString(
            CacheEnvelope(
                requestKey = key,
                payloadJson = payloadJson,
                payloadSha256 = sha256(payloadBytes).toHex(),
            )
        ).toByteArray(Charsets.UTF_8)
        check(encoded.size.toLong() in 1L..MAXIMUM_FILE_BYTES) {
            "PI_VALIDATED_HISTORY_CACHE_TOO_LARGE"
        }

        mutex.withLock {
            try {
                if (!directory.exists() && !directory.mkdirs()) return@withLock
                if (
                    !directory.isDirectory ||
                    Files.isSymbolicLink(directory.toPath())
                ) {
                    return@withLock
                }
                val destination = File(directory, "$key.json")
                if (
                    destination.exists() &&
                    (!destination.isFile || Files.isSymbolicLink(destination.toPath()))
                ) {
                    return@withLock
                }
                val temporary = File(directory, ".$key.${UUID.randomUUID()}.tmp")
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
                destination.setLastModified(savedAt)
                prune()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Persistence is best effort after the live read has already
                // passed canonical RPC validation.
            }
        }
    }

    private fun validateRecord(
        request: CacheRequest,
        record: PiValidatedHistoryRecord,
        savedAtEpochMillis: Long,
    ) {
        check(timestampIsValid(savedAtEpochMillis)) {
            "PI_VALIDATED_HISTORY_CACHE_TIMESTAMP_INVALID"
        }
        check(record.items.map(IndexerHistoryElement::id).distinct().size == record.items.size) {
            "PI_INDEXER_DUPLICATE_TRANSACTION"
        }
        PiHistoryCheckpointValidator.validatePersisted(
            elements = record.items,
            expectedAddress = request.address,
            health = record.health,
            maximumRecords = request.maximumRecords,
        )
        validateHealth(record.health, savedAtEpochMillis)
        when (request.operation) {
            OPERATION_PAGE -> {
                val endReached = checkNotNull(record.endReached) {
                    "PI_VALIDATED_HISTORY_PAGE_INVALID"
                }
                val totalCount = checkNotNull(record.totalCount) {
                    "PI_VALIDATED_HISTORY_PAGE_INVALID"
                }
                check(totalCount >= 0) { "PI_VALIDATED_HISTORY_PAGE_INVALID" }
                val offset = (checkNotNull(request.page) - 1L) *
                    checkNotNull(request.pageCount)
                val consumed = offset + record.items.size
                check(
                    offset <= totalCount.toLong() &&
                        consumed <= totalCount.toLong() &&
                        if (endReached) {
                            consumed == totalCount.toLong()
                        } else {
                            record.items.isNotEmpty() && consumed < totalCount.toLong()
                        }
                ) { "PI_VALIDATED_HISTORY_PAGE_INVALID" }
            }
            else -> check(record.endReached == null && record.totalCount == null) {
                "PI_VALIDATED_HISTORY_PAGE_INVALID"
            }
        }
    }

    private fun validateHealth(
        health: PiIndexerHealth,
        savedAtEpochMillis: Long,
    ) {
        check(
            health.ok &&
                health.repositoryReady &&
                health.service == PI_SERVICE &&
                health.serviceId == PI_SERVICE_ID &&
                health.schemaVersion == PI_SCHEMA_VERSION &&
                health.ecosystem == "sora2" &&
                health.chainId.equals(PI_CHAIN_ID, ignoreCase = true) &&
                health.network == "mainnet" &&
                health.publicBaseUrl == OptionsProvider.polkaswapIndexerEndpoint &&
                health.readOnly &&
                health.workerAvailable &&
                health.workerReady == true &&
                health.workerReadinessReason == null &&
                health.workerLifecycle == "running" &&
                health.workerStartupComplete == true
        ) { "PI_VALIDATED_HISTORY_IDENTITY_INVALID" }
        health.genesisHash?.let {
            check(it.equals(SORA_MAINNET_GENESIS_HASH, ignoreCase = true)) {
                "PI_VALIDATED_HISTORY_IDENTITY_INVALID"
            }
        }
        val workerIndexed = parseUnsignedLong(health.workerLatestIndexedBlock)
        val workerFinalized = parseUnsignedLong(health.workerLatestFinalizedBlock)
        val workerLag = parseUnsignedLong(health.workerLag)
        val indexed = health.latestIndexedBlock?.let(::parseUnsignedLong) ?: workerIndexed
        val workerTimestamp = parseUnsignedLong(health.workerLastSuccessfulIndexTimestamp)
        val indexedTimestamp = health.latestIndexedAt?.let(::parseUnsignedLong) ?: workerTimestamp
        val savedAtSeconds = savedAtEpochMillis / 1_000
        val earliest = if (savedAtSeconds >= MAXIMUM_HEALTH_AGE_SECONDS) {
            savedAtSeconds - MAXIMUM_HEALTH_AGE_SECONDS
        } else {
            0L
        }
        val latest = if (savedAtSeconds > Long.MAX_VALUE - MAXIMUM_FUTURE_SKEW_SECONDS) {
            Long.MAX_VALUE
        } else {
            savedAtSeconds + MAXIMUM_FUTURE_SKEW_SECONDS
        }
        check(
            indexed > 0 &&
                workerIndexed == indexed &&
                workerFinalized >= workerIndexed &&
                workerFinalized - workerIndexed == workerLag &&
                workerTimestamp in earliest..latest &&
                indexedTimestamp in earliest..latest
        ) { "PI_VALIDATED_HISTORY_CHECKPOINT_INVALID" }
        health.latestIndexedBlockHash?.let {
            check(SUBSTRATE_HASH.matches(it) && !ZERO_HASH.equals(it, ignoreCase = true)) {
                "PI_VALIDATED_HISTORY_CHECKPOINT_INVALID"
            }
        }
        check(
            (health.workerLastError == null) ==
                (health.workerLastErrorTimestamp == null)
        ) { "PI_VALIDATED_HISTORY_CHECKPOINT_INVALID" }
        health.workerLastErrorTimestamp?.let(::parseUnsignedLong)
        check(health.workerLastError == null || health.workerLastError.length <= 1_000) {
            "PI_VALIDATED_HISTORY_CHECKPOINT_INVALID"
        }
    }

    private fun timestampIsValid(savedAt: Long): Boolean {
        val now = nowEpochMillis()
        if (now < 0 || savedAt < 0) return false
        val earliest = if (now >= MAXIMUM_CACHE_AGE_MILLIS) {
            now - MAXIMUM_CACHE_AGE_MILLIS
        } else {
            0L
        }
        val latest = if (now > Long.MAX_VALUE - MAXIMUM_FUTURE_SKEW_MILLIS) {
            Long.MAX_VALUE
        } else {
            now + MAXIMUM_FUTURE_SKEW_MILLIS
        }
        return savedAt in earliest..latest
    }

    private fun parseUnsignedLong(value: String?): Long {
        check(
            value != null &&
                value.length <= MAXIMUM_EXACT_LONG_LENGTH &&
                UNSIGNED_INTEGER.matches(value)
        ) { "PI_VALIDATED_HISTORY_CHECKPOINT_INVALID" }
        return value.toLongOrNull()
            ?: throw IllegalStateException("PI_VALIDATED_HISTORY_CHECKPOINT_INVALID")
    }

    private fun requestKey(request: CacheRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            request.operation,
            request.address,
            request.transactionHash.orEmpty(),
            request.page?.toString().orEmpty(),
            request.pageCount?.toString().orEmpty(),
        ).forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().toHex()
    }

    private fun decodeDigest(value: String): ByteArray {
        check(
            value.length == SHA_256_HEX_LENGTH &&
                value.all { isLowerHex(it) }
        ) {
            "PI_VALIDATED_HISTORY_DIGEST_INVALID"
        }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    private fun isLowerHex(value: Char): Boolean =
        value in '0'..'9' || value in 'a'..'f'

    private fun prune() {
        val regularFiles = directory.listFiles()
            .orEmpty()
            .filter { it.isFile && !Files.isSymbolicLink(it.toPath()) }
        val files = regularFiles
            .filter {
                it.name.endsWith(".json") &&
                    it.name.removeSuffix(".json").let { key ->
                        key.length == SHA_256_HEX_LENGTH &&
                            key.all { value -> isLowerHex(value) }
                    }
            }
            .sortedByDescending(File::lastModified)
        regularFiles.filterNot(files::contains).forEach(::deleteRegularFile)
        var retainedBytes = 0L
        files.forEachIndexed { index, file ->
            val length = file.length()
            val retain =
                index < MAXIMUM_ENTRY_COUNT &&
                    length in 1L..MAXIMUM_FILE_BYTES &&
                    retainedBytes <= MAXIMUM_TOTAL_BYTES - length
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

    @Serializable
    private data class CacheRequest(
        val operation: String,
        val address: String,
        val transactionHash: String? = null,
        val page: Long? = null,
        val pageCount: Int? = null,
    ) {
        val maximumRecords: Int
            get() = when (operation) {
                OPERATION_LAST -> checkNotNull(pageCount)
                OPERATION_TRANSACTION -> 1
                OPERATION_PAGE -> checkNotNull(pageCount)
                else -> throw IllegalStateException("PI_VALIDATED_HISTORY_REQUEST_INVALID")
            }

        init {
            check(
                address.isNotEmpty() &&
                    address == address.trim() &&
                    address.toByteArray(Charsets.UTF_8).size <= MAXIMUM_ADDRESS_BYTES
            ) { "PI_VALIDATED_HISTORY_REQUEST_INVALID" }
            when (operation) {
                OPERATION_LAST -> check(
                    transactionHash == null &&
                        page == null &&
                        (pageCount ?: 0) in 1..MAXIMUM_PAGE_SIZE
                ) { "PI_VALIDATED_HISTORY_REQUEST_INVALID" }
                OPERATION_TRANSACTION -> check(
                    transactionHash != null &&
                        TRANSACTION_HASH.matches(transactionHash.orEmpty()) &&
                        page == null &&
                        pageCount == null
                ) { "PI_VALIDATED_HISTORY_REQUEST_INVALID" }
                OPERATION_PAGE -> check(
                    transactionHash == null &&
                        (page ?: 0L) in 1L..MAXIMUM_PAGE_COUNT &&
                        (pageCount ?: 0) in 1..MAXIMUM_PAGE_SIZE
                ) { "PI_VALIDATED_HISTORY_REQUEST_INVALID" }
                else -> error("PI_VALIDATED_HISTORY_REQUEST_INVALID")
            }
        }

        companion object {
            fun last(address: String, count: Int) =
                CacheRequest(
                    operation = OPERATION_LAST,
                    address = address,
                    pageCount = count,
                )

            fun transaction(address: String, transactionHash: String) =
                CacheRequest(
                    operation = OPERATION_TRANSACTION,
                    address = address,
                    transactionHash = canonicalTransactionHash(transactionHash),
                )

            fun page(address: String, page: Long, pageCount: Int) =
                CacheRequest(
                    operation = OPERATION_PAGE,
                    address = address,
                    page = page,
                    pageCount = pageCount,
                )

            private fun canonicalTransactionHash(value: String): String {
                check(value == value.trim()) { "PI_VALIDATED_HISTORY_REQUEST_INVALID" }
                val payload = if (
                    value.startsWith("0x") || value.startsWith("0X")
                ) {
                    value.substring(2)
                } else {
                    value
                }
                check(
                    payload.length == 64 &&
                        payload.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                ) { "PI_VALIDATED_HISTORY_REQUEST_INVALID" }
                return "0x${payload.lowercase()}"
            }
        }
    }

    @Serializable
    private data class CachePayload(
        val formatVersion: Int = FORMAT_VERSION,
        val requestKey: String,
        val request: CacheRequest,
        val savedAtEpochMillis: Long,
        val health: PiIndexerHealth,
        val items: List<IndexerHistoryElement>,
        val endReached: Boolean? = null,
        val totalCount: Int? = null,
    )

    @Serializable
    private data class CacheEnvelope(
        val formatVersion: Int = FORMAT_VERSION,
        val requestKey: String,
        val payloadJson: String,
        val payloadSha256: String,
    )

    private companion object {
        const val DIRECTORY_NAME = "pi-validated-history-v1"
        const val FORMAT_VERSION = 1
        const val OPERATION_LAST = "last"
        const val OPERATION_TRANSACTION = "transaction"
        const val OPERATION_PAGE = "page"
        const val MAXIMUM_PAGE_SIZE = 100
        const val MAXIMUM_PAGE_COUNT = 1_000L
        const val MAXIMUM_ADDRESS_BYTES = 512
        const val MAXIMUM_EXACT_LONG_LENGTH = 19
        const val MAXIMUM_CACHE_AGE_MILLIS = 24L * 60 * 60 * 1_000
        const val MAXIMUM_HEALTH_AGE_SECONDS = 5 * 60L
        const val MAXIMUM_FUTURE_SKEW_SECONDS = 30L
        const val MAXIMUM_FUTURE_SKEW_MILLIS = 30_000L
        const val MAXIMUM_ENTRY_COUNT = 64
        const val MAXIMUM_PAYLOAD_BYTES = 4 * 1_024 * 1_024
        const val MAXIMUM_FILE_BYTES = 8L * 1_024 * 1_024
        const val MAXIMUM_TOTAL_BYTES = 32L * 1_024 * 1_024
        const val SHA_256_HEX_LENGTH = 64
        const val PI_SCHEMA_VERSION = 1
        const val PI_SERVICE = "polkaswap-indexer"
        const val PI_SERVICE_ID = "pi.soramitsu.io"
        const val PI_CHAIN_ID = "sora:mainnet"
        const val SORA_MAINNET_GENESIS_HASH =
            "0x7e4e32d0feafd4f9c9414b0be86373f9a1efa904809b683453a9af6856d38ad5"
        const val ZERO_HASH =
            "0x0000000000000000000000000000000000000000000000000000000000000000"
        val UNSIGNED_INTEGER = Regex("^(?:0|[1-9][0-9]*)$")
        val SUBSTRATE_HASH = Regex("^0x[0-9a-fA-F]{64}$")
        val TRANSACTION_HASH = Regex("^0x[0-9a-f]{64}$")
    }
}
