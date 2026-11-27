package jp.co.soramitsu.feature_wallet_impl.bridge

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_wallet_impl.presentation.bridge.HashiTexts
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each confirmation the person gives signs at most once, whatever happens around it: the same
 * sheet handed over again, another page that confirms in the meantime, a node that refuses the
 * first signature, an endpoint that answers late or differently from one read to the next.
 */
class HashiBridgeConfirmationOnceTest {

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()
    private val accounts = FakeAccounts()
    private val store = InMemoryStore()
    private var clock = 1_000_000L
    private var ids = 0
    private val service = HashiBridgeService(
        sora, ethereum, accounts, store, { clock }, { "t${++ids}" }, pollIntervalMillis = 0, backgroundMonitoring = false,
    )
    private val recipient = "0x94b3c5Fc67b0c63beFCC74D7Afcd333baa697B2A"
    private val million = BigInteger.valueOf(1_000_000)
    private val requestHash = ByteArray(32) { 0x11 }

    private fun refusal(block: suspend () -> Unit): HashiRefusal.Reason? =
        runCatching { runBlocking { block() } }.exceptionOrNull().let { (it as? HashiRefusal)?.reason }

    private suspend fun readyToClaim(approvedAmount: BigInteger = million): HashiTransferRecord {
        sora.requestHash = requestHash
        val sent = service.sendOutgoing(testAccount, service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient), "USDT")
        sora.approved[EthHex.encode(requestHash)] = HashiApprovedRequest(
            currency = HashiCurrency.TokenAddress(USDT_TOKEN),
            amount = approvedAmount,
            to = EthHex.decode(recipient),
            from = testAccount.substrateAddress.toAccountId().copyOf(20),
            txHash = requestHash,
            signatures = listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        return service.refresh(testAccount, sent.id)!!.also { assertEquals(HashiTransferState.READY_TO_CLAIM, it.state) }
    }

    private suspend fun claim(id: String): HashiTransferRecord = service.sendClaim(testAccount, id, service.quoteClaim(testAccount, id))

    private fun stored(id: String) = runBlocking { service.transfer(testAccount.substrateAddress, id)!! }

    @Test
    fun `one transfer sheet starts one transfer, also when it is handed over again after the first went through`() = runBlocking {
        val outgoing = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        val first = service.sendOutgoing(testAccount, outgoing, "USDT")
        // Included with its request: nothing about the SORA side is unresolved any more.
        assertEquals(HashiTransferState.WAITING_APPROVALS, first.state)
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.sendOutgoing(testAccount, outgoing, "USDT") })
        assertEquals(1, sora.signed.size)
        assertEquals(1, sora.submitted.size)

        ethereum.allowance = million
        val incoming = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        val deposit = service.startIncoming(testAccount, incoming, "USDT")
        val step = deposit.step(HashiEthIntent.DEPOSIT)!!
        ethereum.confirm(step.latest.hash, listOf(Logs.deposit(testAccount.substrateAddress.toAccountId(), million, USDT_TOKEN)))
        assertEquals(HashiEthStepState.CONFIRMED, service.refresh(testAccount, deposit.id)!!.step(HashiEthIntent.DEPOSIT)!!.state)
        // The deposit is mined; the same sheet again deposits nothing more.
        ethereum.allowance = million
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.startIncoming(testAccount, incoming, "USDT") })
        assertEquals(1, ethereum.sent.distinct().size)
        assertEquals(1, store.transfers(testAccount.substrateAddress).count { it.direction == HashiDirection.ETHEREUM_TO_SORA })
    }

    @Test
    fun `a claim sheet that finds the claim paid keeps the claim this wallet signed meanwhile on another page`() = runBlocking {
        val record = readyToClaim()
        // While this page's used() read is out, another page claims, and the claim is mined.
        ethereum.onIsUsed = {
            ethereum.onIsUsed = null
            claim(record.id)
            ethereum.used(HashiBridgeContract.bridge, requestHash)
        }
        assertEquals(HashiRefusal.Reason.ALREADY_RECEIVED, refusal { service.quoteClaim(testAccount, record.id) })
        val after = stored(record.id)
        // The other page's claim is still saved (its nonce stays counted); the monitor finishes it.
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, after.state)
        assertEquals(1, after.step(HashiEthIntent.CLAIM)!!.attempts.size)
        assertEquals(1, ethereum.sent.distinct().size)
    }

    @Test
    fun `try again never goes back to a nonce it found taken, whatever a lagging endpoint answers next`() = runBlocking {
        val record = readyToClaim()
        ethereum.failNextSends(HashiRpcErrorException(-32000, "insufficient funds for gas * price + value", null))
        assertEquals(HashiFailure.ETHEREUM_REFUSED, claim(record.id).failure)
        ethereum.sendFailure = null
        // Another payment of this account is pending at 5 now: the quote and the first read under
        // the signing lock see 6; a lagging endpoint would answer 5 to any later read.
        ethereum.pendingNonceAnswers += listOf(BigInteger.valueOf(6), BigInteger.valueOf(6))
        val retried = claim(record.id)
        assertEquals(listOf("5", "6"), retried.ethSteps.filter { it.intent == HashiEthIntent.CLAIM }.map { it.nonce })
    }

    @Test
    fun `a speed-up sheet shown on two pages signs nothing more after the first page's replacement was refused`() = runBlocking {
        val record = readyToClaim()
        claim(record.id)
        val first = service.quoteSpeedUp(testAccount, record.id)
        val second = service.quoteSpeedUp(testAccount, record.id)
        ethereum.failNextSends(HashiRpcErrorException(-32000, "replacement transaction underpriced", null))
        assertEquals(HashiRefusal.Reason.CALL_REVERTED, refusal { service.speedUp(testAccount, record.id, first) })
        ethereum.sendFailure = null
        // The refused bytes were dropped, so the pending one is the latest again; the second
        // sheet was shown before the first confirmation was taken, and signs nothing.
        val derivations = accounts.derivations
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.speedUp(testAccount, record.id, second) })
        assertEquals(derivations, accounts.derivations)
        assertEquals(1, stored(record.id).step(HashiEthIntent.CLAIM)!!.attempts.size)
        assertEquals(1, ethereum.sent.distinct().size)
    }

    @Test
    fun `try again twice from one moment is one confirmation, even when the same bytes come out`() = runBlocking {
        val record = readyToClaim()
        ethereum.failNextSends(HashiRpcErrorException(-32000, "insufficient funds for gas * price + value", null))
        claim(record.id)
        // Two pages show Try again at the same moment, with the same fees at the same free nonce.
        val first = service.quoteClaim(testAccount, record.id)
        val second = service.quoteClaim(testAccount, record.id)
        // The first is refused by the node again: the same signed bytes, the same hash.
        assertEquals(HashiFailure.ETHEREUM_REFUSED, service.sendClaim(testAccount, record.id, first).failure)
        ethereum.sendFailure = null
        val derivations = accounts.derivations
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.sendClaim(testAccount, record.id, second) })
        assertEquals(derivations, accounts.derivations)
        assertTrue(ethereum.sent.isEmpty())
    }

    @Test
    fun `a deposit given up as replaced is settled by its receipt when that shows up late`() = runBlocking {
        ethereum.allowance = million
        val deposit = service.startIncoming(testAccount, service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE), "USDT")
        val step = deposit.step(HashiEthIntent.DEPOSIT)!!
        // The account's nonce moved and no endpoint returns the receipt for ten minutes.
        ethereum.latestNonce = BigInteger.valueOf(6)
        service.refresh(testAccount, deposit.id)
        clock += 11 * 60_000
        assertEquals(HashiFailure.DEPOSIT_UNCONFIRMED, service.refresh(testAccount, deposit.id)!!.failure)
        // Then the receipt shows up, and SORA credits the deposit.
        ethereum.confirm(step.latest.hash, listOf(Logs.deposit(testAccount.substrateAddress.toAccountId(), million, USDT_TOKEN)))
        val incoming = ByteArray(32) { 0x44 }
        sora.incoming[step.latest.hash] = incoming
        sora.status(incoming, HashiRequestStatus.DONE)
        val settled = service.refresh(testAccount, deposit.id)!!
        assertEquals(HashiTransferState.DONE, settled.state)
        assertEquals(null, settled.failure)
        assertEquals(HashiEthStepState.CONFIRMED, settled.step(HashiEthIntent.DEPOSIT)!!.state)
        assertEquals(1, ethereum.sent.distinct().size)
    }

    @Test
    fun `an approval of exactly the confirmed amount stays claimable after the denomination changes`() = runBlocking {
        val record = readyToClaim()
        assertEquals("1000000", record.ethAmount)
        sora.factors[USDT_ID] = BigInteger.TWO
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, claim(record.id).state)
        assertEquals(1, ethereum.sent.distinct().size)
    }

    @Test
    fun `an approval of another amount than confirmed is still refused and nothing is sent`() = runBlocking {
        val record = readyToClaim(approvedAmount = BigInteger.valueOf(1_500_000))
        assertEquals(HashiRefusal.Reason.AMOUNT_MISMATCH, refusal { service.quoteClaim(testAccount, record.id) })
        assertTrue(ethereum.sent.isEmpty())
        assertEquals(0, accounts.derivations)
    }

    @Test
    fun `the gas a sheet shows is rounded up, never below what the transaction can cost`() {
        val texts = HashiTexts(mockk<ResourceManager> { every { getString(any()) } returns "gas" }, NumbersFormatter())
        assertEquals("0.00012346 ETH", texts.ethCeiling(BigInteger("123456789000000")))
        assertEquals("0.00012345 ETH", texts.ethCeiling(BigInteger("123450000000000")))
        assertEquals("0.00012346 ETH", texts.gas(BigInteger("123456789000000")).value)
    }
}
