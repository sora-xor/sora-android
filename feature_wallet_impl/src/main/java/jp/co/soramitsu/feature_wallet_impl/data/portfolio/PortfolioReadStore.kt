package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import android.content.Context
import androidx.core.util.AtomicFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A token as the app priced it when it was last read. */
@Serializable
data class SavedToken(
    val id: String,
    val symbol: String,
    val name: String,
    val iconUri: String? = null,
    val precision: Int,
    val price: Double? = null,
    val change: Double? = null,
)

/** One token's public balance as it was last read for a wallet. */
@Serializable
data class SavedBalance(
    val token: SavedToken,
    val total: String,
    val transferable: String,
    val hidden: Boolean = false,
)

/** XOR on another network as it was last read for a wallet. */
@Serializable
data class SavedNetworkBalance(
    val network: String,
    val address: String,
    val quantity: String,
)

/**
 * A wallet's last known public balances. [readAtMillis] is when they were read, if known. [read]
 * is false for a wallet that had not been read at all when this was saved: it holds "unknown",
 * not nothing.
 */
@Serializable
data class SavedWallet(
    val name: String = "",
    val read: Boolean = true,
    val readAtMillis: Long? = null,
    val sora2: List<SavedBalance> = emptyList(),
    val networks: List<SavedNetworkBalance> = emptyList(),
)

/** Everything the Portfolio view shows on a cold launch, before anything else has answered. */
@Serializable
data class SavedPortfolio(
    val fiatSymbol: String? = null,
    /** The scope's key, or null while the person has never chosen one. */
    val scope: String? = null,
    val xor: SavedToken? = null,
    val wallets: Map<String, SavedWallet> = emptyMap(),
    /** When the newest of the saved prices was read, if known. */
    val pricesReadAtMillis: Long? = null,
)

/**
 * The last balances read for every wallet, so a cold launch shows them at once and refreshes
 * quietly. It holds public chain data only (addresses, token ids, amounts, prices) and the wallet
 * names, never a key, seed, PIN or signature. It is its own file in the no-backup directory, so
 * these frequent writes never touch the preference store that holds wallet credentials, and it
 * leaves the device in no backup. A wallet's entry goes with the wallet ([retain]).
 */
@Singleton
class PortfolioReadStore internal constructor(private val file: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.noBackupFilesDir, FILE_NAME))

    private val atomic = AtomicFile(file)

    /** What is on disk, so an unchanged picture is not written again. */
    private var written: String? = null

    /** The saved reads as they are now; null when there are none or they cannot be read back. */
    fun loadNow(): SavedPortfolio? = synchronized(this) {
        runCatching {
            if (!file.exists()) return@runCatching null
            val text = atomic.readFully().toString(Charsets.UTF_8)
            decode(text)?.also { written = text }
        }.getOrNull()
    }

    suspend fun load(): SavedPortfolio? = withContext(Dispatchers.IO) { loadNow() }

    /** Saves the picture; the person's scope stays as it was stored. */
    suspend fun save(saved: SavedPortfolio) = withContext(Dispatchers.IO) {
        synchronized(this@PortfolioReadStore) {
            val scope = loadNowLocked()?.scope
            write(saved.copy(scope = scope))
        }
    }

    /** Stores the scope with the picture, so the first frame already has the right one. */
    suspend fun saveScope(scope: String?) = withContext(Dispatchers.IO) {
        synchronized(this@PortfolioReadStore) {
            val saved = loadNowLocked() ?: SavedPortfolio()
            if (saved.scope != scope) write(saved.copy(scope = scope))
        }
    }

    /** Drops every wallet not in [walletIds]; with none left, the file itself goes. */
    suspend fun retain(walletIds: Set<String>) = withContext(Dispatchers.IO) {
        synchronized(this@PortfolioReadStore) {
            val saved = loadNowLocked() ?: return@synchronized
            val kept = saved.wallets.filterKeys { it in walletIds }
            when {
                kept.isEmpty() -> deleteLocked()
                kept.size != saved.wallets.size -> write(saved.copy(wallets = kept))
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(this@PortfolioReadStore) { deleteLocked() }
    }

    private fun loadNowLocked(): SavedPortfolio? = written?.let(::decode) ?: loadNow()

    private fun deleteLocked() {
        atomic.delete()
        written = null
    }

    private fun write(saved: SavedPortfolio) {
        val text = encode(saved)
        if (text == written) return
        file.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(text.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
            written = text
        } catch (error: IOException) {
            // A display cache: without it the next launch reads everything again.
            atomic.failWrite(stream)
        }
    }

    companion object {
        internal const val FILE_NAME = "portfolio_reads_v1.json"

        private val json = Json { ignoreUnknownKeys = true }

        internal fun encode(saved: SavedPortfolio): String =
            json.encodeToString(SavedPortfolio.serializer(), saved)

        /** Null when the text is not a saved portfolio: an unreadable file counts as none. */
        internal fun decode(text: String): SavedPortfolio? =
            runCatching { json.decodeFromString(SavedPortfolio.serializer(), text) }.getOrNull()
    }
}
