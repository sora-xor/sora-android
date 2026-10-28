package jp.co.soramitsu.common.domain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.util.Base64
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistTokensManagerTest {
    private val files = mockk<FileManager>(relaxed = true)
    private val rest = mockk<RestClient>()
    private val xor = AssetHolder.XOR_ASSET_ID
    private val other = "0x" + "ab".repeat(32)
    private val third = "0x" + "cd".repeat(32)
    private val cache = "whitelist_tokens.json"

    private fun manager(cached: String? = null): WhitelistTokensManager {
        every { files.readInternalFile(cache) } returns cached
        every { files.existsInternalFile(cache) } returns (cached != null)
        return WhitelistTokensManager(rest, files)
    }

    private fun row(id: String, icon: String): JsonObject = JsonObject(mapOf(
        "address" to JsonPrimitive(id), "icon" to JsonPrimitive(icon),
    ))

    private fun response(vararg rows: JsonObject) {
        coEvery { rest.get<JsonArray>(any()) } returns JsonArray(rows.toList())
    }

    private fun stored(id: String = other, type: String = "svg") = """[{"id":"$id","type":"$type"}]"""

    @Test
    fun `first launch has built in tokens before a remote request`() {
        assertEquals(AssetHolder.getIds(), manager().whitelistIds)
        verify(exactly = 0) { files.writeInternalFile(any(), any()) }
    }

    @Test
    fun `valid cached tokens remain available offline`() = runTest {
        val manager = manager(stored())
        coEvery { rest.get<JsonArray>(any()) } throws IOException("offline")
        manager.updateWhitelistStorage()
        assertEquals(listOf(other), manager.whitelistIds)
        verify(exactly = 0) { files.writeInternalFile(any(), any()) }
    }

    @Test
    fun `corrupt cache recovers to built in tokens without overwriting the file`() {
        for (cached in listOf("", "[{", "null", "{}", "[{\"id\":42,\"type\":\"svg\"}]")) {
            assertEquals(cached, AssetHolder.getIds(), manager(cached).whitelistIds)
        }
        verify(exactly = 0) { files.writeInternalFile(any(), any()) }
    }

    @Test
    fun `unreadable cache does not prevent initialization`() {
        every { files.readInternalFile(cache) } throws IOException("read failed")
        assertEquals(AssetHolder.getIds(), WhitelistTokensManager(rest, files).whitelistIds)
    }

    @Test
    fun `unsafe cached identifiers and icon suffixes are rejected`() {
        for (cached in listOf(stored("../entry"), stored(type = "../svg"), stored(type = "gif"))) {
            assertEquals(AssetHolder.getIds(), manager(cached).whitelistIds)
        }
    }

    @Test
    fun `a valid empty cache is not confused with corruption`() {
        assertTrue(manager("[]").whitelistIds.isEmpty())
    }

    @Test
    fun `successful refresh decodes SVG and PNG and persists the same membership`() = runTest {
        val manager = manager()
        response(row(other, "data:image/svg+xml,%3Csvg%3E%2B%3C%2Fsvg%3E"), row(xor, "data:image/png;base64,AQID"))
        manager.updateWhitelistStorage()
        assertEquals(listOf(other, xor), manager.whitelistIds)
        verify { files.writeInternalCacheFile("$other.svg", "<svg>+</svg>") }
        verify { files.writeInternalCacheFile("$xor.png", match<ByteArray> { it.contentEquals(byteArrayOf(1, 2, 3)) }) }
        verify { files.writeInternalFile(cache, match { Json.parseToJsonElement(it).toString() == """[{"id":"$other","type":"svg"},{"id":"$xor","type":"png"}]""" }) }
    }

    @Test
    fun `base64 SVG content is decoded before caching`() = runTest {
        val manager = manager()
        val svg = "<svg>世界</svg>"
        response(row(other, "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())))
        manager.updateWhitelistStorage()
        verify { files.writeInternalCacheFile("$other.svg", svg) }
    }

    @Test
    fun `malformed icon does not prevent later valid tokens from loading`() = runTest {
        val manager = manager(stored())
        response(row(xor, "data:image/png;base64,!"), row(third, "data:image/svg+xml,%3Csvg%2F%3E"))
        manager.updateWhitelistStorage()
        assertEquals(listOf(third), manager.whitelistIds)
    }

    @Test
    fun `unsafe remote identifiers are skipped before file writes`() = runTest {
        val manager = manager()
        response(row("../escape", "data:image/svg+xml,%3Csvg%2F%3E"), row(other, "data:image/svg+xml,%3Csvg%2F%3E"))
        manager.updateWhitelistStorage()
        assertEquals(listOf(other), manager.whitelistIds)
        verify(exactly = 0) { files.writeInternalCacheFile("../escape.svg", any<String>()) }
    }

    @Test
    fun `refresh failure retains the previous list even if its backing file disappeared`() = runTest {
        val manager = manager(stored())
        every { files.existsInternalFile(cache) } returns false
        coEvery { rest.get<JsonArray>(any()) } throws IOException("offline")
        manager.updateWhitelistStorage()
        assertEquals(listOf(other), manager.whitelistIds)
    }

    @Test
    fun `failed persistence never publishes unpersisted membership`() = runTest {
        val manager = manager(stored())
        response(row(third, "data:image/svg+xml,%3Csvg%2F%3E"))
        every { files.writeInternalFile(cache, any()) } throws IOException("disk full")
        manager.updateWhitelistStorage()
        assertEquals(listOf(other), manager.whitelistIds)
    }

    @Test
    fun `cancelled refresh propagates cancellation without changing membership`() = runTest {
        val manager = manager(stored())
        val cancellation = CancellationException("screen closed")
        coEvery { rest.get<JsonArray>(any()) } throws cancellation
        assertSame(cancellation, runCatching { manager.updateWhitelistStorage() }.exceptionOrNull())
        assertEquals(listOf(other), manager.whitelistIds)
        verify(exactly = 0) { files.writeInternalFile(any(), any()) }
    }

    @Test
    fun `unrecognized image media types cannot be admitted through a prefix match`() = runTest {
        val manager = manager()
        response(row(other, "data:image/svg-not-really,%3Csvg%2F%3E"), row(xor, "data:image/png-extra;base64,AQID"))
        manager.updateWhitelistStorage()
        assertTrue(manager.whitelistIds.isEmpty())
    }
}
