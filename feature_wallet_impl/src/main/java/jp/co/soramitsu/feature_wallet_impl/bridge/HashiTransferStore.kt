package jp.co.soramitsu.feature_wallet_impl.bridge

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import jp.co.soramitsu.common.ethereum.EthHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The bridge transfers of each SORA account and the Ethereum address of each account. */
interface HashiTransferStore {
    suspend fun transfers(wallet: String): List<HashiTransferRecord>
    fun observe(wallet: String): Flow<List<HashiTransferRecord>>
    suspend fun get(wallet: String, id: String): HashiTransferRecord?

    /** The transfers of every account that are not final yet. */
    suspend fun openTransfers(): List<HashiTransferRecord>

    /**
     * False while a saved account file cannot be read: the open Ethereum transactions in it (and
     * so the nonces in use) are unknown, and nothing new may be signed on Ethereum.
     */
    suspend fun isComplete(): Boolean = true

    /** Every saved transfer of every account, final ones included. */
    suspend fun allTransfers(): List<HashiTransferRecord> = openTransfers()

    /** Writes [record] durably before returning (a crash right after keeps it). */
    suspend fun put(record: HashiTransferRecord)

    suspend fun ethereumAddress(wallet: String): String?
    suspend fun putEthereumAddress(wallet: String, address: String)
}

/**
 * One JSON file per SORA account in `no_backup/hashi-bridge/` (not in cloud backups), replaced
 * atomically: written to a temporary file, synced, then renamed over the old one. The data is
 * public chain data plus the signed raw Ethereum transactions of the account's own transfers.
 */
class FileHashiTransferStore(private val directory: File) : HashiTransferStore {

    @Serializable
    private data class AccountFile(
        val version: Int = 1,
        val ethereumAddress: String? = null,
        val transfers: List<HashiTransferRecord> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, MutableStateFlow<AccountFile>>()
    private var allLoaded = false
    private var unreadableFiles = 0

    private fun fileOf(wallet: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(wallet.toByteArray(Charsets.UTF_8))
        return File(directory, EthHex.encode(digest, withPrefix = false).take(32) + ".json")
    }

    private suspend fun state(wallet: String): MutableStateFlow<AccountFile> = cache[wallet] ?: run {
        val file = fileOf(wallet)
        val loaded = withContext(Dispatchers.IO) {
            if (file.isFile) json.decodeFromString(AccountFile.serializer(), file.readText()) else AccountFile()
        }
        MutableStateFlow(loaded).also { cache[wallet] = it }
    }

    private suspend fun write(wallet: String, content: AccountFile) = withContext(Dispatchers.IO) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("HASHI_STORE_DIRECTORY")
        val target = fileOf(wallet)
        val temporary = File(directory, target.name + ".tmp")
        temporary.outputStream().use { stream ->
            stream.write(json.encodeToString(AccountFile.serializer(), content).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("HASHI_STORE_RENAME")
        }
    }

    override suspend fun transfers(wallet: String): List<HashiTransferRecord> = mutex.withLock {
        state(wallet).value.transfers.sortedByDescending { it.createdAt }
    }

    override fun observe(wallet: String): Flow<List<HashiTransferRecord>> = flow {
        val flow = mutex.withLock { state(wallet) }
        emitAll(flow.map { file -> file.transfers.sortedByDescending { it.createdAt } })
    }

    override suspend fun get(wallet: String, id: String): HashiTransferRecord? = mutex.withLock {
        state(wallet).value.transfers.firstOrNull { it.id == id }
    }

    /**
     * Every account's file is read once per process and then answered from memory. A file that
     * cannot be read is left alone (never rewritten) and read again on the next call; until it
     * reads, [isComplete] is false.
     */
    override suspend fun openTransfers(): List<HashiTransferRecord> = mutex.withLock {
        if (!allLoaded) {
            // null is an I/O error, never "no files" (unless the folder does not exist yet).
            val listed = withContext(Dispatchers.IO) {
                directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
                    ?.toList() ?: if (directory.exists()) null else emptyList()
            }
            val files = listed.orEmpty()
            val known = cache.keys.map(::fileOf).toSet()
            var unreadable = if (listed == null) 1 else 0
            files.filterNot { it in known }.forEach { file ->
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { json.decodeFromString(AccountFile.serializer(), file.readText()) }.getOrNull()
                }
                if (loaded == null) {
                    unreadable += 1
                    return@forEach
                }
                val wallet = loaded.transfers.firstOrNull()?.wallet
                if (wallet != null && fileOf(wallet) == file && wallet !in cache) {
                    cache[wallet] = MutableStateFlow(loaded)
                }
            }
            unreadableFiles = unreadable
            allLoaded = unreadable == 0
        }
        cache.values.flatMap { flow -> flow.value.transfers.filterNot { it.state.isFinal } }
    }

    override suspend fun allTransfers(): List<HashiTransferRecord> {
        openTransfers()
        return mutex.withLock { cache.values.flatMap { flow -> flow.value.transfers } }
    }

    override suspend fun isComplete(): Boolean {
        openTransfers()
        return mutex.withLock { unreadableFiles == 0 }
    }

    override suspend fun put(record: HashiTransferRecord) = mutex.withLock {
        val flow = state(record.wallet)
        val others = flow.value.transfers.filterNot { it.id == record.id }
        val next = flow.value.copy(transfers = others + record)
        write(record.wallet, next)
        flow.value = next
    }

    override suspend fun ethereumAddress(wallet: String): String? = mutex.withLock { state(wallet).value.ethereumAddress }

    override suspend fun putEthereumAddress(wallet: String, address: String) = mutex.withLock {
        val flow = state(wallet)
        val next = flow.value.copy(ethereumAddress = address)
        write(wallet, next)
        flow.value = next
    }
}
