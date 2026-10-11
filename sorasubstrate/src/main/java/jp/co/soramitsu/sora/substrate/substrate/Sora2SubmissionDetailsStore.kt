package jp.co.soramitsu.sora.substrate.substrate

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.io.FileOutputStream
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import jp.co.soramitsu.common.logger.FirebaseWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Presentation details of locally signed SORA2 transactions: what each one does, and the reason
 * of a refusal or an on-chain failure. Keyed by wallet and transaction hash.
 *
 * This is deliberately not part of the status journal. The journal alone decides whether a
 * transaction is pending, confirmed or failed, and it is never changed from here. Losing this
 * store only makes an Activity entry less descriptive, so no method ever throws (other than for
 * cancellation): a full disk or an unreadable file must not change the result of a transaction.
 */
interface Sora2SubmissionDetailsStore {
    suspend fun get(walletId: String, transactionHash: String): Sora2SubmissionDetails?

    suspend fun list(walletId: String): List<Sora2SubmissionDetails>

    suspend fun recordActivity(
        walletId: String,
        transactionHash: String,
        activity: Sora2SubmissionActivity,
    )

    suspend fun recordNodeRefusal(
        walletId: String,
        transactionHash: String,
        refusal: Sora2NodeRefusal,
    )

    suspend fun recordChainFailure(walletId: String, transactionHash: String, detail: String)

    suspend fun remove(walletId: String, transactionHash: String)

    /**
     * Drops the details of [walletId] that were recorded before [createdBefore] and whose
     * transaction is no longer in the status journal ([transactionHashes]). Recent entries are
     * kept because their journal row may have been written after the caller read the journal.
     */
    suspend fun retainOnly(
        walletId: String,
        transactionHashes: Set<String>,
        createdBefore: Long,
    )
}

/**
 * One small JSON file in the app's private files directory. It is rewritten as a whole through a
 * temporary file and an atomic rename, holds at most [MAXIMUM_ENTRIES] transactions and contains
 * nothing secret (addresses, token ids, amounts, hashes, short status texts).
 */
class FileSora2SubmissionDetailsStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) : Sora2SubmissionDetailsStore {

    private val mutex = Mutex()
    private var cache: LinkedHashMap<String, Sora2SubmissionDetails>? = null

    override suspend fun get(
        walletId: String,
        transactionHash: String,
    ): Sora2SubmissionDetails? = read(null) { it[key(walletId, transactionHash)] }

    override suspend fun list(walletId: String): List<Sora2SubmissionDetails> =
        read(emptyList()) { entries -> entries.values.filter { it.walletId == walletId } }

    override suspend fun recordActivity(
        walletId: String,
        transactionHash: String,
        activity: Sora2SubmissionActivity,
    ) = update(walletId, transactionHash) { it.copy(activity = activity.stored()) }

    override suspend fun recordNodeRefusal(
        walletId: String,
        transactionHash: String,
        refusal: Sora2NodeRefusal,
    ) = update(walletId, transactionHash) {
        it.copy(nodeRefusal = refusal.copy(message = refusal.message?.sanitizedStatusText()))
    }

    override suspend fun recordChainFailure(
        walletId: String,
        transactionHash: String,
        detail: String,
    ) = update(walletId, transactionHash) { it.copy(chainFailure = detail.sanitizedStatusText()) }

    override suspend fun remove(walletId: String, transactionHash: String) = mutate { entries ->
        entries.remove(key(walletId, transactionHash)) != null
    }

    override suspend fun retainOnly(
        walletId: String,
        transactionHashes: Set<String>,
        createdBefore: Long,
    ) = mutate { entries ->
        entries.values.removeAll {
            it.walletId == walletId &&
                it.createdAt < createdBefore &&
                it.transactionHash !in transactionHashes
        }
    }

    private suspend fun update(
        walletId: String,
        transactionHash: String,
        change: (Sora2SubmissionDetails) -> Sora2SubmissionDetails,
    ) = mutate { entries ->
        if (!walletId.isStorableWalletId() || !TRANSACTION_HASH.matches(transactionHash)) {
            return@mutate false
        }
        val key = key(walletId, transactionHash)
        val current = entries[key] ?: Sora2SubmissionDetails(
            walletId = walletId,
            transactionHash = transactionHash,
            createdAt = maxOf(now(), 1L),
        )
        val changed = change(current)
        if (changed == current && key in entries) return@mutate false
        entries[key] = changed
        true
    }

    private suspend fun <T> read(
        fallback: T,
        block: (Map<String, Sora2SubmissionDetails>) -> T,
    ): T = guarded(fallback) {
        withContext(ioDispatcher) { mutex.withLock { block(loadLocked()) } }
    }

    private suspend fun mutate(
        block: (LinkedHashMap<String, Sora2SubmissionDetails>) -> Boolean,
    ) = guarded(Unit) {
        withContext(ioDispatcher) {
            mutex.withLock {
                val entries = loadLocked()
                if (block(entries)) {
                    pruneLocked(entries)
                    // The in-memory copy stays authoritative for this process even when the disk
                    // is full: the entry is still shown correctly until the app is restarted.
                    persistLocked(entries)
                }
            }
        }
    }

    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        recordStateFailureBestEffort()
        fallback
    }

    private fun loadLocked(): LinkedHashMap<String, Sora2SubmissionDetails> {
        cache?.let { return it }
        val loaded = LinkedHashMap<String, Sora2SubmissionDetails>()
        try {
            if (file.isFile && file.length() in 1..MAXIMUM_FILE_BYTES) {
                parse(file.readText(Charsets.UTF_8)).forEach { details ->
                    loaded[key(details.walletId, details.transactionHash)] = details
                }
            }
        } catch (_: Exception) {
            // An unreadable or damaged file is the same as no file: entries fall back to the
            // generic journal presentation and the next write replaces the file.
            loaded.clear()
            recordStateFailureBestEffort()
        }
        cache = loaded
        return loaded
    }

    private fun pruneLocked(entries: LinkedHashMap<String, Sora2SubmissionDetails>) {
        if (entries.size <= MAXIMUM_ENTRIES) return
        entries.values
            .sortedBy(Sora2SubmissionDetails::createdAt)
            .take(entries.size - MAXIMUM_ENTRIES)
            .forEach { entries.remove(key(it.walletId, it.transactionHash)) }
    }

    private fun persistLocked(entries: Map<String, Sora2SubmissionDetails>) {
        try {
            val parent = file.absoluteFile.parentFile
            if (parent != null && !parent.isDirectory) parent.mkdirs()
            val staging = File(parent, ".${file.name}.tmp")
            FileOutputStream(staging).use { output ->
                output.write(serialize(entries.values).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            if (!staging.renameTo(file)) {
                staging.delete()
                recordStateFailureBestEffort()
            }
        } catch (_: Exception) {
            recordStateFailureBestEffort()
        }
    }

    private fun serialize(entries: Collection<Sora2SubmissionDetails>): String {
        val array = JsonArray()
        entries.forEach { details ->
            array.add(
                JsonObject().apply {
                    addProperty(FIELD_WALLET, details.walletId)
                    addProperty(FIELD_HASH, details.transactionHash)
                    addProperty(FIELD_CREATED_AT, details.createdAt)
                    details.activity?.let { activity ->
                        add(
                            FIELD_ACTIVITY,
                            JsonObject().apply {
                                addProperty(FIELD_KIND, activity.kind.name)
                                add(FIELD_TOKENS, activity.tokenIds.toJsonArray())
                                add(FIELD_AMOUNTS, activity.amounts.toJsonArray())
                                activity.fee?.let { addProperty(FIELD_FEE, it) }
                                activity.peer?.let { addProperty(FIELD_PEER, it) }
                                activity.market?.let { addProperty(FIELD_MARKET, it) }
                            },
                        )
                    }
                    details.nodeRefusal?.let { refusal ->
                        add(
                            FIELD_REFUSAL,
                            JsonObject().apply {
                                addProperty(FIELD_REASON, refusal.reason.name)
                                addProperty(FIELD_CODE, refusal.code)
                                refusal.message?.let { addProperty(FIELD_MESSAGE, it) }
                            },
                        )
                    }
                    details.chainFailure?.let { addProperty(FIELD_CHAIN_FAILURE, it) }
                }
            )
        }
        return JsonObject().apply {
            addProperty(FIELD_VERSION, FORMAT_VERSION)
            add(FIELD_ENTRIES, array)
        }.toString()
    }

    private fun parse(text: String): List<Sora2SubmissionDetails> {
        val root = JsonParser.parseString(text).asJsonObject
        if (root.get(FIELD_VERSION)?.asInt != FORMAT_VERSION) return emptyList()
        return root.getAsJsonArray(FIELD_ENTRIES).mapNotNull { element ->
            try {
                parseEntry(element.asJsonObject)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseEntry(entry: JsonObject): Sora2SubmissionDetails? {
        val walletId = entry.string(FIELD_WALLET) ?: return null
        val hash = entry.string(FIELD_HASH) ?: return null
        val createdAt = entry.get(FIELD_CREATED_AT)?.asLong ?: return null
        if (!walletId.isStorableWalletId() || !TRANSACTION_HASH.matches(hash) || createdAt <= 0L) {
            return null
        }
        val activity = entry.getAsJsonObject(FIELD_ACTIVITY)?.let { raw ->
            val kind = Sora2SubmissionActivity.Kind.entries
                .firstOrNull { it.name == raw.string(FIELD_KIND) }
                ?: return@let null
            Sora2SubmissionActivity(
                kind = kind,
                tokenIds = raw.strings(FIELD_TOKENS),
                amounts = raw.strings(FIELD_AMOUNTS),
                fee = raw.string(FIELD_FEE),
                peer = raw.string(FIELD_PEER),
                market = raw.string(FIELD_MARKET),
            ).takeIf(Sora2SubmissionActivity::isWellFormed)
        }
        val refusal = entry.getAsJsonObject(FIELD_REFUSAL)?.let { raw ->
            val reason = TransactionNodeRefusal.entries
                .firstOrNull { it.name == raw.string(FIELD_REASON) }
                ?: return@let null
            Sora2NodeRefusal(
                reason = reason,
                code = raw.get(FIELD_CODE)?.asLong ?: return@let null,
                message = raw.string(FIELD_MESSAGE)?.sanitizedStatusText(),
            )
        }
        return Sora2SubmissionDetails(
            walletId = walletId,
            transactionHash = hash,
            createdAt = createdAt,
            activity = activity,
            nodeRefusal = refusal,
            chainFailure = entry.string(FIELD_CHAIN_FAILURE)?.sanitizedStatusText(),
        )
    }

    private fun Sora2SubmissionActivity.stored(): Sora2SubmissionActivity? =
        copy(duplicateConfirmed = false).takeIf(Sora2SubmissionActivity::isWellFormed)

    private fun List<String>.toJsonArray(): JsonArray = JsonArray().also { array ->
        forEach(array::add)
    }

    private fun JsonObject.string(name: String): String? {
        val value: JsonElement = get(name) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun JsonObject.strings(name: String): List<String> =
        getAsJsonArray(name)?.map { it.asString }.orEmpty()

    private fun String.isStorableWalletId(): Boolean =
        isNotBlank() && length <= MAXIMUM_WALLET_ID_CHARS && none { it.isISOControl() }

    private fun key(walletId: String, transactionHash: String): String =
        "$walletId|$transactionHash"

    private fun recordStateFailureBestEffort() {
        try {
            FirebaseWrapper.recordErrorClass(FirebaseWrapper.PrivacySafeErrorClass.STATE_FAILURE)
        } catch (_: Throwable) {
            // Telemetry never decides what the wallet shows.
        }
    }

    companion object {
        const val FILE_NAME = "sora2_submission_details.json"
        internal const val MAXIMUM_ENTRIES = 256
        private const val MAXIMUM_FILE_BYTES = 1024L * 1024L
        private const val MAXIMUM_WALLET_ID_CHARS = 128
        private const val FORMAT_VERSION = 1
        private const val FIELD_VERSION = "version"
        private const val FIELD_ENTRIES = "entries"
        private const val FIELD_WALLET = "wallet"
        private const val FIELD_HASH = "hash"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_ACTIVITY = "activity"
        private const val FIELD_KIND = "kind"
        private const val FIELD_TOKENS = "tokens"
        private const val FIELD_AMOUNTS = "amounts"
        private const val FIELD_FEE = "fee"
        private const val FIELD_PEER = "peer"
        private const val FIELD_MARKET = "market"
        private const val FIELD_REFUSAL = "refusal"
        private const val FIELD_REASON = "reason"
        private const val FIELD_CODE = "code"
        private const val FIELD_MESSAGE = "message"
        private const val FIELD_CHAIN_FAILURE = "chainFailure"
        private val TRANSACTION_HASH = Regex("^0x[0-9a-f]{64}$")
    }
}

/**
 * Text that came from the network (a node's refusal, a runtime error name) is shown to the user
 * as plain text. Keep it short, on one line and free of control characters.
 */
internal fun String.sanitizedStatusText(): String? =
    map { if (it.isISOControl()) ' ' else it }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAXIMUM_STATUS_TEXT_CHARS)
        .ifEmpty { null }

private const val MAXIMUM_STATUS_TEXT_CHARS = 160
