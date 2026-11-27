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
import kotlinx.serialization.json.JsonNull
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
    private val blacklistCache = "blacklist_tokens.json"
    private val svg = "data:image/svg+xml,%3Csvg%2F%3E"

    private fun manager(cached: String? = null, blacklist: String? = null): WhitelistTokensManager {
        every { files.readInternalFile(cache) } returns cached
        every { files.readInternalFile(blacklistCache) } returns blacklist
        return WhitelistTokensManager(rest, files)
    }

    private fun ids(vararg ids: String) = JsonArray(ids.map { JsonPrimitive(it) })

    // Declared after response(), so it answers the blacklist request only.
    private fun blacklisted(answer: JsonArray) {
        coEvery { rest.get<JsonArray>(match { it.url == "https://whitelist.polkaswap2.io/blacklist.json" }) } returns answer
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
    fun `SVGs with a charset parameter load, as the live list sends XOR and PSWAP`() = runTest {
        val manager = manager()
        response(row(xor, "data:image/svg+xml;charset=utf8,%3Csvg%2F%3E"), row(other, "data:image/svg+xml;Charset=UTF-8,%3Csvg%2F%3E"))
        manager.updateWhitelistStorage()
        assertEquals(listOf(xor, other), manager.whitelistIds)
        verify { files.writeInternalCacheFile("$xor.svg", "<svg/>") }
    }

    @Test
    fun `other media type parameters are still rejected`() = runTest {
        val manager = manager()
        response(row(other, "data:image/svg+xml;charset=latin1,%3Csvg%2F%3E"))
        manager.updateWhitelistStorage()
        assertTrue(manager.whitelistIds.isEmpty())
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

    @Test
    fun `the bundled blacklist applies until a list is cached or fetched`() {
        val manager = manager()
        assertEquals(
            setOf(
                "0x00b3e666ba302d608b659994fab741ba78904161e158f1421db05318588fe017",
                "0x00dadad1a20270d9246dd7ccc92730f32565baa4265cf30a23ebd15c9235e818",
            ),
            manager.blacklistIds,
        )
        assertEquals(AssetHolder.getIds(), manager.whitelistIds)
    }

    @Test
    fun `a cached blacklist replaces the bundled one and an empty cached one clears it`() {
        assertEquals(setOf(other), manager(blacklist = """["$other"]""").blacklistIds)
        assertTrue(manager(blacklist = "[]").blacklistIds.isEmpty())
    }

    @Test
    fun `a damaged cached blacklist is ignored as a whole`() {
        val damaged = listOf(
            "[", "{}", "null", "[42]", "[null]", """["$other",42]""", """["0x12"]""",
            """[["$other"]]""", """["../$other"]""", """[{"id":"$other"}]""",
        )
        for (cached in damaged) {
            assertEquals(cached, WhitelistTokensManager.BUNDLED_BLACKLIST, manager(blacklist = cached).blacklistIds)
        }
        verify(exactly = 0) { files.writeInternalFile(any(), any()) }
    }

    @Test
    fun `a fetched blacklist is saved and applied`() = runTest {
        val manager = manager()
        response()
        blacklisted(ids(third, other))
        manager.updateWhitelistStorage()
        assertEquals(setOf(third, other), manager.blacklistIds)
        verify { files.writeInternalFile(blacklistCache, """["$third","$other"]""") }
    }

    @Test
    fun `only 0x and 64 ASCII lower case hex digits make an id, and a list without one is rejected`() = runTest {
        val manager = manager(blacklist = """["$other"]""")
        response()
        val invalidIds = listOf(
            "0x" + "CD".repeat(32), // upper case
            "0X" + "cd".repeat(32), // upper case prefix
            "0x" + "\u0663".repeat(64), // Arabic-Indic digit three: Char.isDigit says yes
            "0x" + "\uFF11".repeat(64), // fullwidth digit one
            "0x" + "cd".repeat(31) + "c\u0661", // one non-ASCII digit at the end
            " $third", "$third ", "0x" + "cd".repeat(33), "",
        )
        for (id in invalidIds) {
            blacklisted(ids(id))
            manager.updateWhitelistStorage()
            assertEquals(id, setOf(other), manager.blacklistIds)
            // One invalid id rejects the whole list, valid ids beside it included.
            blacklisted(ids(third, id))
            manager.updateWhitelistStorage()
            assertEquals(id, setOf(other), manager.blacklistIds)
            // The same rule for a cached list.
            assertEquals(id, WhitelistTokensManager.BUNDLED_BLACKLIST, manager(blacklist = ids(id).toString()).blacklistIds)
        }
        verify(exactly = 0) { files.writeInternalFile(blacklistCache, any()) }
        blacklisted(ids())
        manager.updateWhitelistStorage()
        assertTrue(manager.blacklistIds.isEmpty())
    }

    @Test
    fun `an empty blacklist from the server is valid and clears the list`() = runTest {
        val manager = manager()
        response()
        blacklisted(ids())
        manager.updateWhitelistStorage()
        assertTrue(manager.blacklistIds.isEmpty())
        verify { files.writeInternalFile(blacklistCache, "[]") }
    }

    @Test
    fun `a malformed, failed or unsaved blacklist fetch keeps the last list`() = runTest {
        val manager = manager(blacklist = """["$other"]""")
        response()
        val malformed = listOf(
            JsonArray(listOf(JsonPrimitive(third), JsonPrimitive(42))),
            JsonArray(listOf(JsonNull)),
            ids("0x12"),
            JsonArray(listOf(row(third, svg))),
        )
        for (answer in malformed) {
            blacklisted(answer)
            manager.updateWhitelistStorage()
            assertEquals(setOf(other), manager.blacklistIds)
        }
        verify(exactly = 0) { files.writeInternalFile(blacklistCache, any()) }

        coEvery { rest.get<JsonArray>(match { it.url.endsWith("/blacklist.json") }) } throws IOException("offline")
        manager.updateWhitelistStorage()
        assertEquals(setOf(other), manager.blacklistIds)

        blacklisted(ids(third))
        every { files.writeInternalFile(blacklistCache, any()) } throws IOException("disk full")
        manager.updateWhitelistStorage()
        assertEquals(setOf(other), manager.blacklistIds)
    }

    @Test
    fun `a failed whitelist fetch still refreshes the blacklist`() = runTest {
        val manager = manager(stored())
        coEvery { rest.get<JsonArray>(any()) } throws IOException("whitelist unreachable")
        blacklisted(ids(third))
        manager.updateWhitelistStorage()
        assertEquals(listOf(other), manager.whitelistIds)
        assertEquals(setOf(third), manager.blacklistIds)
    }

    @Test
    fun `a cancelled blacklist fetch propagates cancellation without changing the list`() = runTest {
        val manager = manager(blacklist = """["$other"]""")
        response()
        val cancellation = CancellationException("screen closed")
        coEvery { rest.get<JsonArray>(match { it.url.endsWith("/blacklist.json") }) } throws cancellation
        assertSame(cancellation, runCatching { manager.updateWhitelistStorage() }.exceptionOrNull())
        assertEquals(setOf(other), manager.blacklistIds)
        verify(exactly = 0) { files.writeInternalFile(blacklistCache, any()) }
    }

    @Test
    fun `the blacklist wins over the whitelist and the built in tokens`() = runTest {
        assertEquals(AssetHolder.getIds() - xor, manager(blacklist = """["$xor"]""").whitelistIds)
        val bothCached = """[{"id":"$other","type":"svg"},{"id":"$third","type":"svg"}]"""
        assertEquals(listOf(third), manager(bothCached, blacklist = """["$other"]""").whitelistIds)

        val manager = manager()
        response(row(other, svg), row(third, svg))
        blacklisted(ids(other))
        manager.updateWhitelistStorage()
        assertEquals(listOf(third), manager.whitelistIds)

        // A token the server takes off the blacklist is a whitelisted token again.
        blacklisted(ids())
        manager.updateWhitelistStorage()
        assertEquals(listOf(other, third), manager.whitelistIds)
    }
}
