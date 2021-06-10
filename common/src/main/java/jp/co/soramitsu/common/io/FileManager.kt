package jp.co.soramitsu.common.io

interface FileManager {
    val internalCacheDir: String
    fun readAssetFile(fileName: String): String
}
