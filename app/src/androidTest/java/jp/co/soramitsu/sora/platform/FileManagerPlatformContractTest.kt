package jp.co.soramitsu.sora.platform

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import jp.co.soramitsu.common.io.FileManagerImpl
import java.io.File
import java.io.IOException
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real Android storage and shareable-URI contracts, confined to a newly created fixture directory. */
@RunWith(AndroidJUnit4::class)
class FileManagerPlatformContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "file-manager-fixture-${java.util.UUID.randomUUID()}").apply { mkdirs() }
    private fun isolated(external: Boolean = false, media: Boolean = false) = object : ContextWrapper(context) {
        override fun getCacheDir() = File(root, "cache").apply { mkdirs() }
        override fun getFilesDir() = File(root, "files").apply { mkdirs() }
        override fun getExternalCacheDir(): File? = if (external) File(cacheDir, "external").apply { mkdirs() } else null
        override fun getExternalMediaDirs(): Array<File> = if (media) arrayOf(File(root, "media").apply { mkdirs() }) else emptyArray()
    }
    @After fun cleanFixture() { root.deleteRecursively() }
    @Test fun cacheAndPrivateFilesPreserveBytesAndDistinguishEmptyFromMissing() {
        val manager = FileManagerImpl(isolated())
        assertNull(manager.readInternalCacheFile("absent")); assertNull(manager.readInternalCacheFileAsByteArray("absent"))
        manager.writeInternalCacheFile("empty", "")
        assertNull(manager.readInternalCacheFile("empty")); assertNull(manager.readInternalCacheFileAsByteArray("empty"))
        assertEquals("", manager.readInternalCacheFile("empty", 1))
        val text = "wallet fixture á界😀"
        manager.writeInternalCacheFile("utf8", text)
        assertEquals(text, manager.readInternalCacheFile("utf8"))
        assertEquals(text, manager.readInternalCacheFile("utf8", text.toByteArray().size))
        manager.writeInternalCacheFile("binary", byteArrayOf(0, 1, -1))
        assertArrayEquals(byteArrayOf(0, 1, -1), manager.readInternalCacheFileAsByteArray("binary"))
        assertFalse(manager.existsInternalFile("private")); manager.writeInternalFile("private", text)
        assertTrue(manager.existsInternalFile("private")); assertEquals(text, manager.readInternalFile("private"))
        val asset = context.assets.open("default_types.json").bufferedReader().use { it.readText() }
        assertEquals(asset, manager.readAssetFile("default_types.json"))
        assertThrows(IOException::class.java) { manager.readAssetFile("default_types.json", 1) }
    }
    @Test fun sharedTextOverwritesAndBitmapRoundTripsThroughTheRealFileProvider() {
        for (external in listOf(false, true)) {
            val manager = FileManagerImpl(isolated(external))
            manager.writeExternalCacheText("share.txt", "old")
            val uri = manager.writeExternalCacheText("share.txt", "new 界😀")
            assertEquals("content", uri.scheme)
            assertEquals("new 界😀", context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            val bitmap = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
            val bitmapUri = manager.writeExternalCacheBitmap(bitmap, "image.png", Bitmap.CompressFormat.PNG, 100)
            val decoded = context.contentResolver.openInputStream(bitmapUri)!!.use(BitmapFactory::decodeStream)
            assertEquals(2, decoded!!.width); assertEquals(3, decoded.height); assertEquals(Color.GREEN, decoded.getPixel(1, 2))
            bitmap.recycle(); decoded.recycle()
        }
    }
    @Test fun logLocationPrefersMediaThenExternalThenInternalCache() {
        assertEquals(File(root, "media/logs.txt").absolutePath, FileManagerImpl(isolated(external = true, media = true)).logStorageDir)
        assertEquals(File(root, "cache/external/logs.txt").absolutePath, FileManagerImpl(isolated(external = true)).logStorageDir)
        assertEquals(File(root, "cache/logs.txt").absolutePath, FileManagerImpl(isolated()).logStorageDir)
    }
    @Test fun boundedAtomicWritesRejectInvalidUnicodeWithoutReplacingThePreviousEntry() {
        val manager = FileManagerImpl(isolated())
        val valid = "aé界😀"; val bytes = valid.toByteArray().size
        manager.writeInternalCacheFileAtomically("atomic", valid, bytes)
        assertEquals(valid, manager.readInternalCacheFileAtomically("atomic", bytes))
        for (invalid in listOf("\uD800", "\uD800x", "\uDC00")) {
            assertThrows(IOException::class.java) { manager.writeInternalCacheFileAtomically("atomic", invalid, 100) }
            assertEquals(valid, manager.readInternalCacheFileAtomically("atomic"))
        }
        assertThrows(IOException::class.java) { manager.writeInternalCacheFileAtomically("atomic", valid, bytes - 1) }
        assertThrows(IOException::class.java) { manager.readInternalCacheFileAtomically("atomic", bytes - 1) }
        assertEquals(valid, manager.readInternalCacheFileAtomically("atomic"))
    }
    @Test fun cacheListingAndDeletionNeverFollowDirectoriesOrLinks() {
        val local = isolated(); val manager = FileManagerImpl(local)
        manager.writeInternalCacheFile("runtime-kept", "fixture")
        manager.writeInternalCacheFile("other", "untouched")
        val directory = File(local.cacheDir, "runtime-directory").apply { mkdir() }
        val link = File(local.cacheDir, "runtime-link")
        Os.symlink(File(local.cacheDir, "other").absolutePath, link.absolutePath)
        try {
            assertEquals(listOf("runtime-kept"), manager.listInternalCacheFileNames("runtime-", 256))
            assertFalse(manager.deleteInternalCacheFile("runtime-link")); assertFalse(manager.deleteInternalCacheFile(directory.name))
            assertThrows(IOException::class.java) { manager.readInternalCacheFile(link.name, 99) }
            assertThrows(IOException::class.java) { manager.readInternalCacheFile(directory.name, 99) }
            assertThrows(IOException::class.java) { manager.readInternalCacheFile("runtime-kept", 1) }
            for (name in listOf("", "../escape", "nested/name")) {
                assertThrows(IllegalArgumentException::class.java) { manager.readInternalCacheFile(name, 99) }
                assertThrows(IllegalArgumentException::class.java) { manager.listInternalCacheFileNames(name, 10) }
            }
            assertTrue(manager.deleteInternalCacheFile("runtime-kept")); assertFalse(manager.deleteInternalCacheFile("runtime-kept"))
            assertEquals("untouched", manager.readInternalCacheFile("other"))
        } finally { java.nio.file.Files.deleteIfExists(link.toPath()) }
    }
}
