package jp.co.soramitsu.core_db

import android.content.Context
import android.os.storage.StorageManager
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Space that is held only for speed and can be given back before a person is asked to free
 * some: what this app keeps in its own cache folder (token icons, the image and network caches,
 * the node's runtime description; all of it is fetched or built again when missing, and Android
 * may clear that folder at any time anyway), working copies left there by an upgrade or repair
 * that was killed, and, on request, what the phone itself holds as cache.
 *
 * Nothing else is looked at: the wallet database, the preferences, the wrapped key, the upgrade
 * backups and the files set aside by a repair live in other folders and are never touched here.
 */
internal object DisposableStorage {

    /**
     * Kept: a recovery file the person made for support may be on its way to another app right
     * now, and it is a copy of wallet files they asked for.
     */
    const val RECOVERY_EXPORTS = "wallet-recovery-exports"

    /**
     * Working copies of [WalletUpgradeBackup] itself. Each is made and removed inside one
     * locked step, so one found by the next locked step was left by a process that died.
     */
    private val OWN_WORKING_COPIES = listOf("wallet-journal-replay-", "wallet-backup-validation-")

    /**
     * Deletes the files in [cacheDirectory] and returns their bytes. Folders are kept (whoever
     * made one may expect it to exist), except the upgrade's own stale working copies, which go
     * entirely. Links are removed as links and never followed. Anything that cannot be deleted
     * is left where it is: this never fails.
     *
     * [walletLocations] are the folders and files that hold wallet data. They are never inside
     * an app's cache folder; if one were (a misconfigured or tampered installation), nothing is
     * deleted at all.
     */
    fun reclaimCache(cacheDirectory: File, walletLocations: List<File>): Long {
        val cache = runCatching { cacheDirectory.toPath().toAbsolutePath().normalize() }.getOrNull() ?: return 0L
        if (!Files.isDirectory(cache, LinkOption.NOFOLLOW_LINKS)) return 0L
        val real = runCatching { cache.toRealPath() }.getOrNull() ?: return 0L
        val guarded = walletLocations.any { location ->
            val path = runCatching { location.toPath().toAbsolutePath().normalize() }.getOrNull()
                ?: return 0L
            val realPath = runCatching { path.toRealPath() }.getOrDefault(path)
            path.startsWith(cache) || realPath.startsWith(real) || cache.startsWith(path) || real.startsWith(realPath)
        }
        if (guarded) return 0L
        var freed = 0L
        val entries = runCatching { Files.newDirectoryStream(cache).use { it.toList() } }.getOrDefault(emptyList())
        for (entry in entries) {
            val name = entry.fileName?.toString() ?: continue
            if (name == RECOVERY_EXPORTS) continue
            freed += deleteFilesUnder(entry, removeFolders = OWN_WORKING_COPIES.any(name::startsWith))
        }
        return freed
    }

    private fun deleteFilesUnder(root: Path, removeFolders: Boolean): Long {
        var freed = 0L
        runCatching {
            // walkFileTree does not follow links: a link is visited as a file and removed itself.
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                    val size = if (attributes.isRegularFile) attributes.size() else 0L
                    if (runCatching { Files.delete(file) }.isSuccess) freed += size
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, error: IOException): FileVisitResult =
                    FileVisitResult.CONTINUE

                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (removeFolders) runCatching { Files.delete(directory) }
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return freed
    }

    /**
     * Asks Android to make [bytes] free on the volume that holds [directory]. Android does that
     * by clearing cached files (of any app; caches are disposable by contract), this app's cache
     * folder included. True when it says the space is there. Never throws; a phone that cannot
     * do it answers false.
     *
     * Not asked while a recovery file the person made for support is in the cache folder
     * ([holdsRecoveryExport]): Android's clearing does not know to keep it, and it may be on its
     * way to another app. Only the app's own giving back ([reclaimCache], which keeps it) runs then.
     */
    fun askSystemFor(context: Context, directory: File, bytes: Long): Boolean = try {
        val storage = context.getSystemService(StorageManager::class.java)
        if (storage == null || bytes <= 0L || holdsRecoveryExport(context.cacheDir)) {
            false
        } else {
            storage.allocateBytes(storage.getUuidForPath(directory), bytes)
            true
        }
    } catch (_: Throwable) {
        // IOException: not that much can be freed. Anything else: this phone cannot do it.
        false
    }

    /**
     * True when [cacheDirectory] holds a recovery file ([RECOVERY_EXPORTS]: a finished archive or
     * one being written), or when that cannot be known.
     */
    fun holdsRecoveryExport(cacheDirectory: File): Boolean = runCatching {
        val exports = File(cacheDirectory, RECOVERY_EXPORTS).toPath()
        if (!Files.exists(exports, LinkOption.NOFOLLOW_LINKS)) return@runCatching false
        Files.newDirectoryStream(exports).use { entries ->
            entries.any { entry ->
                val name = entry.fileName?.toString().orEmpty()
                RECOVERY_ARCHIVE_PREFIXES.any(name::startsWith) &&
                    Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)
            }
        }
    }.getOrDefault(true)

    /** The recovery archive, and its staging copy while it is written (`WalletUpgradeBackup`). */
    private val RECOVERY_ARCHIVE_PREFIXES = listOf("sora-wallet-recovery-", ".sora-wallet-recovery-")
}
