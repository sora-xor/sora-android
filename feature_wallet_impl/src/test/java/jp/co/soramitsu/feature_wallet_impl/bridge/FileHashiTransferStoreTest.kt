package jp.co.soramitsu.feature_wallet_impl.bridge

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileHashiTransferStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun record(id: String, wallet: String = "cnA", createdAt: Long = 1L) = HashiTransferRecord(
        id = id, wallet = wallet, direction = HashiDirection.SORA_TO_ETHEREUM, assetId = "0x02", symbol = "XOR",
        soraPrecision = 18, ethDecimals = 18, ethToken = "0x00", soraAmount = "1", ethAmount = "1",
        recipient = "0x01", ethAccount = "0x02", state = HashiTransferState.SORA_SIGNED,
        ethSteps = listOf(
            HashiEthStep(
                HashiEthIntent.CLAIM, "3", "0x0a", "0", "0x", "21000",
                listOf(HashiEthAttempt("0x02f8", "0xaa", "10", "1", 5L)), HashiEthStepState.SIGNED,
            )
        ),
        createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun `transfers survive a restart, per account, with the signed raw transactions`() = runBlocking {
        val directory = File(folder.root, "hashi-bridge")
        val first = FileHashiTransferStore(directory)
        first.put(record("a", createdAt = 1))
        first.put(record("b", createdAt = 2))
        first.put(record("c", wallet = "cnB"))
        first.put(record("a", createdAt = 1).copy(state = HashiTransferState.SORA_SUBMITTED))
        first.putEthereumAddress("cnA", "0x9858EfFD232B4033E47d90003D41EC34EcaEda94")

        val reopened = FileHashiTransferStore(directory)
        assertEquals(listOf("b", "a"), reopened.transfers("cnA").map { it.id })
        assertEquals(HashiTransferState.SORA_SUBMITTED, reopened.get("cnA", "a")!!.state)
        assertEquals("0x02f8", reopened.get("cnA", "a")!!.ethSteps.single().latest.raw)
        assertNull(reopened.get("cnA", "c"))
        assertEquals(listOf("c"), reopened.transfers("cnB").map { it.id })
        assertEquals("0x9858EfFD232B4033E47d90003D41EC34EcaEda94", reopened.ethereumAddress("cnA"))
        assertNull(reopened.ethereumAddress("cnB"))
        assertEquals(listOf("b", "a"), reopened.observe("cnA").first().map { it.id })
        // Only the account files are left: no temporary file, and no address in a file name.
        val names = directory.list()!!.toList()
        assertTrue(names.all { it.endsWith(".json") })
        assertFalse(names.any { it.contains("cnA") })
    }
}
