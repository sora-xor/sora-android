package jp.co.soramitsu.feature_wallet_impl.data.nexus

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jp.co.soramitsu.common.nexus.NexusTransferHistoryItem
import jp.co.soramitsu.common.nexus.WalletNetworkId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkActivityRepositoryTest {
    private val portfolio = mockk<NexusPortfolioRepository>()
    private val repository = NetworkActivityRepositoryImpl(portfolio)

    @Test
    fun `activity includes live network transfers and excludes address-only networks`() = runTest {
        val transfers = listOf(transfer("a", 20), transfer("b", 10))
        val live = balance().copy(confirmedTransfers = transfers, historyHasMore = true)
        val addressOnly = balance().copy(networkId = WalletNetworkId.MINAMOTO, networkLive = false)
        every { portfolio.observeCurrentWallet() } returns flowOf(listOf(addressOnly, live))

        val result = repository.observeCurrentWallet().first().single()

        assertEquals(live.walletId, result.walletId)
        assertEquals(live.networkId, result.networkId)
        assertEquals(live.networkName, result.networkName)
        assertEquals(live.address, result.address)
        assertEquals(live.isTestnet, result.isTestnet)
        assertEquals(transfers, result.confirmedTransfers)
        assertTrue(result.hasMore)
    }

    @Test
    fun `activity preserves qualified pending rows and prioritizes history errors`() = runTest {
        val pending = listOf(pending("first"), pending("second").copy(transactionHash = null))
        every { portfolio.observeCurrentWallet() } returns flowOf(listOf(balance().copy(
            pendingTransactions = pending,
            historyErrorCode = "NEXUS_HISTORY_UNAVAILABLE",
            errorCode = "NEXUS_BALANCE_UNAVAILABLE",
        )))

        val result = repository.observeCurrentWallet().first().single()

        assertEquals(pending.map { it.localId }, result.pendingTransactions.map { it.localId })
        assertEquals(pending.map { it.transactionHash }, result.pendingTransactions.map { it.transactionHash })
        val row = result.pendingTransactions.first()
        assertEquals(pending.first().amount, row.amount)
        assertEquals(pending.first().recipient, row.recipient)
        assertEquals(pending.first().state, row.state)
        assertEquals(pending.first().submissionIsAmbiguous, row.submissionIsAmbiguous)
        assertEquals("NEXUS_HISTORY_UNAVAILABLE", result.errorCode)
    }

    @Test
    fun `recovery rows produce a diagnostic without becoming visible transactions`() = runTest {
        val transfer = transfer("a", 10)
        every { portfolio.observeCurrentWallet() } returns flowOf(listOf(balance().copy(
            confirmedTransfers = listOf(transfer),
            recoveryPendingTransactions = listOf(pending("recovery")),
        )))

        val result = repository.observeCurrentWallet().first().single()

        assertEquals(listOf(transfer), result.confirmedTransfers)
        assertTrue(result.pendingTransactions.isEmpty())
        assertEquals("NEXUS_RECOVERY_REQUIRED", result.errorCode)
    }

    @Test
    fun `account switch clearing and portfolio diagnostics are preserved`() = runTest {
        val old = balance().copy(confirmedTransfers = listOf(transfer("a", 10)))
        val current = balance().copy(walletId = "second-wallet", address = "second-address",
            errorCode = "NEXUS_BALANCE_UNAVAILABLE")
        every { portfolio.observeCurrentWallet() } returns flowOf(listOf(old), emptyList(), listOf(current))

        val results = repository.observeCurrentWallet().toList()

        assertTrue(results[1].isEmpty())
        assertEquals("second-wallet", results.last().single().walletId)
        assertEquals("second-address", results.last().single().address)
        assertTrue(results.last().single().confirmedTransfers.isEmpty())
        assertEquals("NEXUS_BALANCE_UNAVAILABLE", results.last().single().errorCode)
    }

    @Test
    fun `refresh and pagination delegate to the qualified portfolio reader`() {
        every { portfolio.refresh() } returns Unit
        every { portfolio.loadMoreHistory("wallet", WalletNetworkId.TAIRA) } returns Unit

        repository.refresh()
        repository.loadMore("wallet", WalletNetworkId.TAIRA)

        verify(exactly = 1) { portfolio.refresh() }
        verify(exactly = 1) { portfolio.loadMoreHistory("wallet", WalletNetworkId.TAIRA) }
    }

    private fun balance() = NexusPortfolioBalance(
        walletId = "wallet",
        networkId = WalletNetworkId.TAIRA,
        networkName = "Taira",
        isTestnet = true,
        address = "account",
        xorQuantity = "1",
        assetDefinitionId = "xor",
        explorerBaseUrl = "https://explorer.example",
        sendAvailable = false,
        pendingTransactions = emptyList(),
        confirmedTransfers = emptyList(),
        historyErrorCode = null,
        errorCode = null,
    )

    private fun transfer(hash: String, timestamp: Long) = NexusTransferHistoryItem(
        transactionHash = hash.repeat(64),
        timestampMillis = timestamp,
        amount = "1",
        sender = "sender",
        receiver = "account",
    )

    private fun pending(id: String) = NexusPendingTransaction(
        localId = id,
        transactionHash = "a".repeat(64),
        assetDefinitionId = "xor",
        amount = "2",
        recipient = "recipient",
        state = "SUBMITTED",
        submissionIsAmbiguous = true,
    )
}
