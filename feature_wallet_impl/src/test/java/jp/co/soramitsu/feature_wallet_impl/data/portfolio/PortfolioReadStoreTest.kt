package jp.co.soramitsu.feature_wallet_impl.data.portfolio

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioReadStoreTest {

    private val dir: File = Files.createTempDirectory("portfolio-reads").toFile()
    private val file = File(dir, PortfolioReadStore.FILE_NAME)

    private fun store() = PortfolioReadStore(file)

    private val xor = SavedBalance(
        token = SavedToken(
            id = "xor", symbol = "XOR", name = "XOR", iconUri = "file:///xor.png", precision = 18,
            price = 0.004, change = 0.05,
        ),
        total = "12.5",
        transferable = "10",
        hidden = false,
    )
    private val saved = SavedPortfolio(
        fiatSymbol = "$",
        wallets = mapOf(
            "cnMain" to SavedWallet(
                name = "Main",
                readAtMillis = 1_000L,
                sora2 = listOf(xor),
                networks = listOf(SavedNetworkBalance("taira", "testu1", "25000")),
            ),
            "cnOther" to SavedWallet(name = "Other", read = false, readAtMillis = 2_000L),
        ),
    )

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `saved reads come back after the app restarts`() = runTest {
        store().save(saved)
        assertEquals(saved, store().load())
        assertEquals(saved, store().loadNow())
    }

    @Test
    fun `the person's scope stays with the picture when balances are saved again`() = runTest {
        val store = store()
        store.saveScope("selected")
        store.save(saved)
        assertEquals("selected", store().load()?.scope)
        store.save(saved.copy(fiatSymbol = "€"))
        assertEquals("selected", store().load()?.scope)
        assertEquals("€", store().load()?.fiatSymbol)
        store.saveScope(null)
        assertNull(store().load()?.scope)
    }

    @Test
    fun `nothing saved and an unreadable file both count as no reads`() = runTest {
        assertNull(store().load())
        file.writeText("{not json")
        assertNull(store().load())
    }

    @Test
    fun `only public balances are written`() {
        val text = PortfolioReadStore.encode(saved)
        assertEquals(saved, PortfolioReadStore.decode(text))
        listOf("seed", "mnemonic", "private", "secret", "pin", "signature").forEach {
            assertFalse(it, text.contains(it, ignoreCase = true))
        }
    }

    @Test
    fun `an entry of a later version still reads`() {
        val text = """{"fiatSymbol":"$","wallets":{"cnMain":{"readAtMillis":5,"future":true}},"more":1}"""
        assertEquals(SavedWallet(readAtMillis = 5L), PortfolioReadStore.decode(text)?.wallets?.get("cnMain"))
    }

    @Test
    fun `a removed wallet's entry goes, and with none left the file goes`() = runTest {
        val store = store()
        store.save(saved)
        store.retain(setOf("cnMain", "cnOther"))
        assertEquals(saved, store.load())
        store.retain(setOf("cnMain"))
        assertEquals(setOf("cnMain"), store().load()?.wallets?.keys)
        store.retain(emptySet())
        assertFalse(file.exists())
        assertNull(store().load())
        assertTrue(store.load() == null)
    }
}
