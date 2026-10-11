package jp.co.soramitsu.sora.substrate.substrate

import java.io.File
import java.math.BigDecimal
import jp.co.soramitsu.common.domain.TransactionNodeRefusal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The Activity details of journaled transactions live in one small file. Losing it may only make
 * an entry less descriptive, so the store must survive a restart, never throw, and never grow
 * without bound.
 */
class FileSora2SubmissionDetailsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var clock = 1_000L
    private val wallet = "cnWalletOne"
    private val hash = "0x" + "ab".repeat(32)
    private val swap = Sora2SubmissionActivity.swap(
        tokenFromId = "0x0200000000000000000000000000000000000000000000000000000000000000",
        tokenToId = "0x0200040000000000000000000000000000000000000000000000000000000000",
        amountFrom = BigDecimal("10"),
        amountTo = BigDecimal("2.5"),
        fee = BigDecimal("0.0007"),
        market = "SMART",
    )

    private fun file() = File(folder.root, FileSora2SubmissionDetailsStore.FILE_NAME)

    private fun store(target: File = file()) = FileSora2SubmissionDetailsStore(
        file = target,
        ioDispatcher = Dispatchers.Unconfined,
        now = { clock },
    )

    @Test
    fun `what a transaction does survives a restart`() = runTest {
        store().recordActivity(wallet, hash, swap.copy(duplicateConfirmed = true))

        val restored = store().get(wallet, hash)

        assertEquals(wallet, restored?.walletId)
        assertEquals(hash, restored?.transactionHash)
        assertEquals(1_000L, restored?.createdAt)
        // The one-time "send again" answer is never stored.
        assertEquals(swap, restored?.activity)
        assertEquals(listOf(restored), store().list(wallet))
        assertEquals(emptyList<Sora2SubmissionDetails>(), store().list("cnAnotherWallet"))
    }

    @Test
    fun `a refusal and a chain failure are added to the same entry`() = runTest {
        val store = store()
        store.recordActivity(wallet, hash, swap)
        store.recordNodeRefusal(
            wallet,
            hash,
            Sora2NodeRefusal(TransactionNodeRefusal.CANNOT_PAY_FEE, 1010, "Inability\nto pay\u0007 fees"),
        )
        store.recordChainFailure(wallet, hash, "LiquidityProxy.SlippageNotTolerated")

        val restored = checkNotNull(store().get(wallet, hash))

        assertEquals(swap, restored.activity)
        assertEquals(TransactionNodeRefusal.CANNOT_PAY_FEE, restored.nodeRefusal?.reason)
        assertEquals(1010L, restored.nodeRefusal?.code)
        // Text from the network is kept on one line without control characters.
        assertEquals("Inability to pay fees", restored.nodeRefusal?.message)
        assertEquals("LiquidityProxy.SlippageNotTolerated", restored.chainFailure)
    }

    @Test
    fun `a damaged file is the same as no file and is replaced by the next write`() = runTest {
        file().writeText("{\"version\":1,\"entries\":[{\"wallet\":")

        assertNull(store().get(wallet, hash))
        assertEquals(emptyList<Sora2SubmissionDetails>(), store().list(wallet))

        store().recordActivity(wallet, hash, swap)
        assertEquals(swap, store().get(wallet, hash)?.activity)
    }

    @Test
    fun `entries that are not well formed are dropped one by one`() = runTest {
        store().recordActivity(wallet, hash, swap)
        val good = file().readText()
        val entry = good.substringAfter("\"entries\":[").substringBeforeLast("]}")
        file().writeText(
            "{\"version\":1,\"entries\":[" +
                "{\"wallet\":\"cnX\",\"hash\":\"not-a-hash\",\"createdAt\":5}," +
                "{\"wallet\":\"cnX\",\"hash\":\"0x" + "cd".repeat(32) + "\",\"createdAt\":-1}," +
                entry + "]}"
        )

        assertEquals(swap, store().get(wallet, hash)?.activity)
        assertEquals(emptyList<Sora2SubmissionDetails>(), store().list("cnX"))
    }

    @Test
    fun `a description that does not fit its limits is not stored`() = runTest {
        store().recordActivity(
            wallet,
            hash,
            swap.copy(amounts = listOf("1e5", "drop table")),
        )

        val restored = checkNotNull(store().get(wallet, hash))
        assertNull(restored.activity)
    }

    @Test
    fun `an unwritable location never throws and keeps the entry for this run`() = runTest {
        val blocker = folder.newFile("blocker")
        val store = store(File(blocker, "cannot/exist.json"))

        store.recordActivity(wallet, hash, swap)

        assertEquals(swap, store.get(wallet, hash)?.activity)
        // Nothing reached the disk, and a new process simply starts without the details.
        assertNull(store(File(blocker, "cannot/exist.json")).get(wallet, hash))
    }

    @Test
    fun `only old entries without a journal row are dropped`() = runTest {
        val store = store()
        val oldOrphan = "0x" + "01".repeat(32)
        val oldJournaled = "0x" + "02".repeat(32)
        val fresh = "0x" + "03".repeat(32)
        store.recordActivity(wallet, oldOrphan, swap)
        store.recordActivity(wallet, oldJournaled, swap)
        store.recordActivity("cnAnotherWallet", oldOrphan, swap)
        clock = 10_000L
        // Recorded a moment ago: its journal row may not have been visible to the reader yet.
        store.recordActivity(wallet, fresh, swap)

        store.retainOnly(wallet, setOf(oldJournaled), createdBefore = 5_000L)

        assertNull(store().get(wallet, oldOrphan))
        assertTrue(store().get(wallet, oldJournaled) != null)
        assertTrue(store().get(wallet, fresh) != null)
        // Another wallet's entries are not touched.
        assertTrue(store().get("cnAnotherWallet", oldOrphan) != null)
    }

    @Test
    fun `the file is bounded to the newest entries`() = runTest {
        val store = store()
        repeat(FileSora2SubmissionDetailsStore.MAXIMUM_ENTRIES + 5) { index ->
            clock = 1_000L + index
            store.recordActivity(wallet, "0x" + index.toString(16).padStart(64, '0'), swap)
        }

        val kept = store().list(wallet)

        assertEquals(FileSora2SubmissionDetailsStore.MAXIMUM_ENTRIES, kept.size)
        assertFalse(kept.any { it.createdAt < 1_005L })
        assertTrue(file().length() < 1024L * 1024L)
    }

    @Test
    fun `entries older than a month are dropped at the next write`() = runTest {
        val store = store()
        val old = "0x" + "0a".repeat(32)
        store.recordActivity("cnRemovedWallet", old, swap)
        clock += FileSora2SubmissionDetailsStore.MAXIMUM_AGE_MILLIS + 1L

        store.recordActivity(wallet, hash, swap)

        assertNull(store().get("cnRemovedWallet", old))
        assertTrue(store().get(wallet, hash) != null)
    }

    @Test
    fun `removing an entry removes only that transaction`() = runTest {
        val store = store()
        val other = "0x" + "ef".repeat(32)
        store.recordActivity(wallet, hash, swap)
        store.recordActivity(wallet, other, swap)

        store.remove(wallet, hash)

        assertNull(store().get(wallet, hash))
        assertTrue(store().get(wallet, other) != null)
    }

    @Test
    fun `same action means same kind tokens and counterparty whatever the amount`() {
        val transfer = Sora2SubmissionActivity.transfer("0x02", BigDecimal.ONE, "cnBob", null)

        assertTrue(swap.isSameActionAs(swap.copy(amounts = listOf("1", "2"), fee = null)))
        assertFalse(swap.isSameActionAs(swap.copy(tokenIds = swap.tokenIds.reversed())))
        assertFalse(
            swap.isSameActionAs(swap.copy(kind = Sora2SubmissionActivity.Kind.LIQUIDITY_ADD))
        )
        assertTrue(transfer.isSameActionAs(transfer.copy(amounts = listOf("7"))))
        assertFalse(transfer.isSameActionAs(transfer.copy(peer = "cnCarol")))
    }
}
