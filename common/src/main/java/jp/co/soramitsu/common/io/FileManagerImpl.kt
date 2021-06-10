package jp.co.soramitsu.common.io

import android.content.Context

class FileManagerImpl(private val context: Context) : FileManager {
    override val internalCacheDir: String
        get() = context.cacheDir.absolutePath

    override fun readAssetFile(fileName: String): String =
        context.assets.open(fileName).bufferedReader().use { it.readText() }
}
