package jp.co.soramitsu.feature_wallet_impl.bridge

import java.io.File
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAddress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Two SORA accounts made from one recovery phrase have the same Ethereum account, so one queue of
 * Ethereum nonces, while each keeps its transfers in its own file. Which nonce is next, and whether
 * a transaction is still open, is decided from the saved transfers of both, also right after the
 * app starts and whatever a node that lags behind answers.
 */
class HashiBridgeSharedNonceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()

    /** Both accounts get the same Ethereum address, as two accounts of one phrase do. */
    private val accounts = FakeAccounts()
    private var clock = 1_000_000L
    private var ids = 0
    private val first = testAccount
    private val second = SoraAccount(ByteArray(32) { 7 }.toAddress(69.toShort()), "second")
    private val recipient = "0x94b3c5Fc67b0c63beFCC74D7Afcd333baa697B2A"
    private val million = BigInteger.valueOf(1_000_000)
    private val directory: File get() = File(folder.root, "hashi-bridge")

    /** The app right after a start: nothing in memory, every account's transfers on disk. */
    private fun appStart(): HashiBridgeService = HashiBridgeService(
        sora, ethereum, accounts, FileHashiTransferStore(directory), { clock }, { "t${++ids}" }, pollIntervalMillis = 0,
        backgroundMonitoring = false,
    )

    private fun refusal(block: suspend () -> Unit): HashiRefusal.Reason? =
        runCatching { runBlocking { block() } }.exceptionOrNull().let { (it as? HashiRefusal)?.reason }

    private suspend fun HashiBridgeService.readyToClaim(account: SoraAccount, request: Int = 0x11): HashiTransferRecord {
        val requestHash = ByteArray(32) { request.toByte() }
        sora.requestHash = requestHash
        val sent = sendOutgoing(account, quoteOutgoing(account, USDT_ID, BigDecimal.ONE, recipient), "USDT")
        sora.approved[EthHex.encode(requestHash)] = HashiApprovedRequest(
            currency = HashiCurrency.TokenAddress(USDT_TOKEN),
            amount = million,
            to = EthHex.decode(recipient),
            from = account.substrateAddress.toAccountId().copyOf(20),
            txHash = requestHash,
            signatures = listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        return refresh(account, sent.id)!!.also { assertEquals(HashiTransferState.READY_TO_CLAIM, it.state) }
    }

    private suspend fun HashiBridgeService.claim(account: SoraAccount, id: String): HashiTransferRecord =
        sendClaim(account, id, quoteClaim(account, id))

    private suspend fun HashiBridgeService.deposit(account: SoraAccount): HashiTransferRecord =
        startIncoming(account, quoteIncoming(account, USDT_ID, BigDecimal.ONE), "USDT")

    /** A node that lags: it still counts the account's transactions as it did at the start. */
    private fun nodeLags() {
        ethereum.pendingNonce = BigInteger.valueOf(5)
        ethereum.latestNonce = BigInteger.valueOf(5)
    }

    @Test
    fun `while one account's transaction is pending the other account signs nothing, also after a restart`() = runBlocking {
        val app = appStart()
        val claim = app.claim(first, app.readyToClaim(first).id)
        assertEquals("5", claim.step(HashiEthIntent.CLAIM)!!.nonce)
        ethereum.allowance = million
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { app.deposit(second) })

        val restarted = appStart()
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { restarted.deposit(second) })
        // Nor does the first account's own next transfer.
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { restarted.deposit(first) })
        assertEquals(1, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
        assertTrue(restarted.transfers(second.substrateAddress).isEmpty())
    }

    @Test
    fun `a nonce either account saw used is never chosen again, whatever a lagging node answers, across restarts`() = runBlocking {
        val app = appStart()
        val claim = app.claim(first, app.readyToClaim(first).id)
        ethereum.confirm(claim.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.withdrawal(ByteArray(32) { 0x11 })))
        assertEquals(HashiTransferState.DONE, app.refresh(first, claim.id)!!.state)

        // The first account's transfer is finished; its nonce 5 is used for good.
        nodeLags()
        val restarted = appStart()
        ethereum.allowance = million
        val deposit = restarted.deposit(second)
        assertEquals("6", deposit.step(HashiEthIntent.DEPOSIT)!!.nonce)

        // Nonce 6 is then taken by a transaction that is none of the wallet's (another wallet app
        // with the same phrase): seen used for ten minutes, the deposit is given up, never re-signed.
        ethereum.latestNonce = BigInteger.valueOf(7)
        restarted.refresh(second, deposit.id)
        clock += 11 * 60_000
        val replaced = restarted.refresh(second, deposit.id)!!
        assertEquals(HashiFailure.DEPOSIT_UNCONFIRMED, replaced.failure)

        nodeLags()
        val again = appStart()
        val next = again.claim(first, again.readyToClaim(first, request = 0x22).id)
        assertEquals("7", next.step(HashiEthIntent.CLAIM)!!.nonce)
        // Three signatures, three different nonces.
        assertEquals(3, accounts.derivations)
    }

    @Test
    fun `a send whose result is unknown stays watched and holds its nonce against the other account across a restart`() = runBlocking {
        val app = appStart()
        // The node's answer says the nonce is used - possibly by this very claim, sent a moment
        // earlier through an endpoint whose answer was lost.
        ethereum.failNextSends(HashiRpcErrorException(-32000, "nonce too low: next nonce 6, tx nonce 5", null))
        val claim = app.claim(first, app.readyToClaim(first).id)
        assertTrue(claim.step(HashiEthIntent.CLAIM)!!.state.isOpen)
        ethereum.sendFailure = null
        ethereum.allowance = million

        val restarted = appStart()
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { restarted.deposit(second) })
        assertEquals(1, accounts.derivations)

        // It was the claim: its receipt arrives, and the other account's deposit takes the next nonce.
        ethereum.confirm(claim.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.withdrawal(ByteArray(32) { 0x11 })))
        restarted.refreshOpen()
        assertEquals(HashiTransferState.DONE, restarted.transfer(first.substrateAddress, claim.id)!!.state)
        nodeLags()
        assertEquals("6", restarted.deposit(second).step(HashiEthIntent.DEPOSIT)!!.nonce)
    }

    @Test
    fun `a send every node refused frees its nonce for the other account, and the first account then moves on to the next`() = runBlocking {
        val app = appStart()
        ethereum.failNextSends(HashiRpcErrorException(-32000, "insufficient funds for gas * price + value", null))
        val refused = app.claim(first, app.readyToClaim(first).id)
        assertEquals(HashiFailure.ETHEREUM_REFUSED, refused.failure)
        assertEquals("5", refused.step(HashiEthIntent.CLAIM)!!.nonce)
        ethereum.sendFailure = null
        ethereum.allowance = million

        // Nothing of the first account is pending: the second account's deposit uses nonce 5.
        val restarted = appStart()
        val deposit = restarted.deposit(second)
        val step = deposit.step(HashiEthIntent.DEPOSIT)!!
        assertEquals("5", step.nonce)
        ethereum.confirm(step.latest.hash, listOf(Logs.deposit(second.substrateAddress.toAccountId(), million, USDT_TOKEN)))
        restarted.refreshOpen()
        assertEquals(HashiEthStepState.CONFIRMED, restarted.transfer(second.substrateAddress, deposit.id)!!.step(HashiEthIntent.DEPOSIT)!!.state)

        // "Try again" on the first account, with a node that still counts 5: its old nonce is the
        // deposit's now, so the claim is signed at 6, never at 5 again.
        nodeLags()
        val again = appStart()
        val retried = again.claim(first, refused.id)
        assertEquals(listOf("5", "6"), retried.ethSteps.filter { it.intent == HashiEthIntent.CLAIM }.map { it.nonce })
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, retried.state)
    }

    @Test
    fun `the same Ethereum account saved in another letter case is still the same account`() = runBlocking {
        val app = appStart()
        val claim = app.claim(first, app.readyToClaim(first).id)
        // As a file written with the address in lower case would have it.
        FileHashiTransferStore(directory).put(claim.copy(ethAccount = claim.ethAccount.lowercase()))
        ethereum.allowance = million

        val restarted = appStart()
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { restarted.deposit(second) })
        ethereum.confirm(claim.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.withdrawal(ByteArray(32) { 0x11 })))
        restarted.refreshOpen()
        nodeLags()
        assertEquals("6", restarted.deposit(second).step(HashiEthIntent.DEPOSIT)!!.nonce)
    }
}
