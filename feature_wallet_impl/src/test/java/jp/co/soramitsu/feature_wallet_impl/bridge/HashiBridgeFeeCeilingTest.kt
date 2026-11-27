package jp.co.soramitsu.feature_wallet_impl.bridge

import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a person confirms is the most the wallet may spend: every Ethereum step is signed within
 * the gas its sheet showed, decided against the saved transfer under the signing lock, and one
 * sheet is taken once — also when two pages show it and both are confirmed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HashiBridgeFeeCeilingTest {

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()
    private val accounts = FakeAccounts()
    private val store = InMemoryStore()
    private var clock = 1_000_000L
    private var ids = 0
    private val service = HashiBridgeService(sora, ethereum, accounts, store, { clock }, { "t${++ids}" }, pollIntervalMillis = 0)

    private val recipient = "0x94b3c5Fc67b0c63beFCC74D7Afcd333baa697B2A"
    private val requestHash = ByteArray(32) { 0x11 }
    private val destination: ByteArray get() = testAccount.substrateAddress.toAccountId()
    private val million = BigInteger.valueOf(1_000_000)

    private fun refusal(block: suspend () -> Unit): HashiRefusal.Reason? =
        runCatching { runBlocking { block() } }.exceptionOrNull().let { (it as? HashiRefusal)?.reason }

    private suspend fun HashiBridgeService.readyToClaim(): HashiTransferRecord {
        val sent = sendOutgoing(testAccount, quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient), "USDT")
        sora.approved[EthHex.encode(requestHash)] = HashiApprovedRequest(
            currency = HashiCurrency.TokenAddress(USDT_TOKEN),
            amount = million,
            to = EthHex.decode(recipient),
            from = destination.copyOf(20),
            txHash = requestHash,
            signatures = listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        return refresh(testAccount, sent.id)!!.also { assertEquals(HashiTransferState.READY_TO_CLAIM, it.state) }
    }

    private suspend fun stored(id: String) = service.transfer(testAccount.substrateAddress, id)!!

    private fun costOf(attempt: HashiEthAttempt, step: HashiEthStep) = BigInteger(attempt.maxFeePerGas).multiply(BigInteger(step.gasLimit))

    // --- Speed-up -------------------------------------------------------------------------------

    @Test
    fun `a speed-up is signed with exactly the fees its sheet showed, whatever the network asks by then`() = runBlocking {
        val record = service.readyToClaim()
        service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val quote = service.quoteSpeedUp(testAccount, record.id)
        // Gas triples between the sheet and the PIN.
        ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(3))
        val step = service.speedUp(testAccount, record.id, quote).step(HashiEthIntent.CLAIM)!!
        assertEquals(2, step.attempts.size)
        assertEquals(quote.fees.maxFeePerGas.toString(), step.latest.maxFeePerGas)
        assertEquals(quote.fees.maxPriorityFeePerGas.toString(), step.latest.maxPriorityFeePerGas)
        assertEquals(quote.fees.maximumCost, costOf(step.latest, step))
    }

    @Test
    fun `one speed-up sheet confirmed twice signs one replacement`() = runBlocking {
        val record = service.readyToClaim()
        service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val quote = service.quoteSpeedUp(testAccount, record.id)
        service.speedUp(testAccount, record.id, quote)
        val derivations = accounts.derivations
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.speedUp(testAccount, record.id, quote) })
        assertEquals(derivations, accounts.derivations)
        assertEquals(2, stored(record.id).step(HashiEthIntent.CLAIM)!!.attempts.size)
        // Claim and one replacement: nothing else left the wallet.
        assertEquals(2, ethereum.sent.size)
    }

    @Test
    fun `two pages show a speed-up and both are confirmed at once - one replacement is signed, at what its own sheet showed`() = runTest {
        val record = service.readyToClaim()
        service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val first = service.quoteSpeedUp(testAccount, record.id)
        // The second page opened its sheet a little later, at a higher price.
        ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(2))
        val second = service.quoteSpeedUp(testAccount, record.id)
        assertTrue(second.fees.maximumCost > first.fees.maximumCost)
        val derivations = accounts.derivations

        // The first confirmation is signing (its key is being derived) when the second arrives.
        val keyHeld = CompletableDeferred<Unit>()
        accounts.beforeKey = { keyHeld.await() }
        val one = async { service.speedUp(testAccount, record.id, first) }
        runCurrent()
        accounts.beforeKey = null
        val two = async { runCatching { service.speedUp(testAccount, record.id, second) } }
        runCurrent()
        keyHeld.complete(Unit)
        advanceUntilIdle()

        val step = one.await().step(HashiEthIntent.CLAIM)!!
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, (two.await().exceptionOrNull() as? HashiRefusal)?.reason)
        assertEquals(derivations + 1, accounts.derivations)
        assertEquals(2, stored(record.id).step(HashiEthIntent.CLAIM)!!.attempts.size)
        // The most that can be paid is what the first page showed; the second page's higher
        // figure was never signed.
        assertEquals(first.fees.maximumCost, costOf(step.latest, step))
        assertEquals(2, ethereum.sent.size)
    }

    @Test
    fun `a speed-up sheet for a transaction that is mined meanwhile signs nothing`() = runBlocking {
        val record = service.readyToClaim()
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val quote = service.quoteSpeedUp(testAccount, record.id)
        ethereum.confirm(sent.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.withdrawal(requestHash)))
        assertEquals(HashiTransferState.DONE, service.refresh(testAccount, record.id)!!.state)
        val derivations = accounts.derivations
        assertEquals(HashiRefusal.Reason.NOT_READY, refusal { service.speedUp(testAccount, record.id, quote) })
        assertEquals(derivations, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
    }

    // --- Claim ------------------------------------------------------------------------------------

    @Test
    fun `a claim sheet confirmed from two pages at once signs one claim, with the fees it showed`() = runTest {
        val record = service.readyToClaim()
        val quote = service.quoteClaim(testAccount, record.id)
        val other = service.quoteClaim(testAccount, record.id)
        ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(3))

        val keyHeld = CompletableDeferred<Unit>()
        accounts.beforeKey = { keyHeld.await() }
        val one = async { service.sendClaim(testAccount, record.id, quote) }
        runCurrent()
        accounts.beforeKey = null
        // While the first is being signed, and again after it was sent.
        val during = async { runCatching { service.sendClaim(testAccount, record.id, other) } }
        runCurrent()
        keyHeld.complete(Unit)
        advanceUntilIdle()
        val claim = one.await().step(HashiEthIntent.CLAIM)!!
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, (during.await().exceptionOrNull() as? HashiRefusal)?.reason)
        val after = runCatching { service.sendClaim(testAccount, record.id, other) }.exceptionOrNull() as? HashiRefusal
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, after?.reason)
        assertEquals(1, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
        assertEquals(quote.fees.maximumCost, costOf(claim.latest, claim))
    }

    @Test
    fun `a claim sheet shown before another one was confirmed and refused by the node signs nothing`() = runBlocking {
        val record = service.readyToClaim()
        val stale = service.quoteClaim(testAccount, record.id)
        ethereum.failNextSends(HashiRpcErrorException(-32000, "insufficient funds for gas * price + value", null))
        val failed = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, failed.state)
        ethereum.sendFailure = null
        val derivations = accounts.derivations
        // The other page still shows its old sheet: something was signed since, so it is not taken.
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.sendClaim(testAccount, record.id, stale) })
        assertEquals(derivations, accounts.derivations)
        assertTrue(ethereum.sent.isEmpty())
        // "Try again" shows a new sheet, and that one is taken.
        val retried = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, retried.state)
        assertEquals(derivations + 1, accounts.derivations)
    }

    // --- Deposit ----------------------------------------------------------------------------------

    /** Each approval is mined as soon as it is sent and sets the allowance; the deposit is mined with its event. */
    private fun mineEverything(afterEach: () -> Unit = {}) {
        ethereum.onSend = { _ ->
            val step = store.history.last().ethSteps.last()
            when (step.intent) {
                HashiEthIntent.APPROVE_ZERO -> {
                    ethereum.confirm(step.latest.hash, emptyList())
                    ethereum.allowance = BigInteger.ZERO
                }
                HashiEthIntent.APPROVE -> {
                    ethereum.confirm(step.latest.hash, emptyList())
                    ethereum.allowance = million
                }
                else -> ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, million, USDT_TOKEN)))
            }
            afterEach()
        }
    }

    @Test
    fun `approve 0, approve and the deposit together never cost more than the gas the sheet showed`() = runBlocking {
        // An older, lower allowance: all three steps are needed, and gas rises 10 % after each.
        ethereum.allowance = BigInteger.valueOf(5)
        mineEverything { ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(11)).divide(BigInteger.TEN) }
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        assertTrue(quote.needsApproveZero && quote.needsApprove)
        val record = service.startIncoming(testAccount, quote, "USDT")
        assertEquals(listOf(HashiEthIntent.APPROVE_ZERO, HashiEthIntent.APPROVE, HashiEthIntent.DEPOSIT), record.ethSteps.map { it.intent })
        assertEquals(HashiEthStepState.CONFIRMED, record.step(HashiEthIntent.DEPOSIT)!!.state)
        val signed = record.ethSteps.fold(BigInteger.ZERO) { sum, step -> sum.add(step.maximumCostOfAllAttempts) }
        assertTrue("signed $signed, shown ${quote.gasCostEstimate}", signed <= quote.gasCostEstimate)
        // What is left of the confirmation is what the steps did not take.
        assertEquals(quote.gasCostEstimate.subtract(signed).toString(), record.gasBudget)
        assertEquals(3, accounts.derivations)
    }

    @Test
    fun `a step that needs more gas than expected is fitted into what was confirmed`() = runBlocking {
        ethereum.allowance = million
        // This token's deposit needs 40 % more gas than the bridge's usual figure.
        ethereum.gasEstimate = { BigInteger.valueOf(74_200) }
        mineEverything()
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        val record = service.startIncoming(testAccount, quote, "USDT")
        val deposit = record.step(HashiEthIntent.DEPOSIT)!!
        assertEquals(HashiEthStepState.CONFIRMED, deposit.state)
        assertEquals(BigInteger.valueOf(89_040).toString(), deposit.gasLimit)
        assertTrue(deposit.maximumCost <= quote.gasCostEstimate)
        // The price limit came down, but it still leaves room above today's base fee and the tip.
        val floor = ethereum.baseFee.multiply(BigInteger.valueOf(9)).divide(BigInteger.valueOf(8)).add(ethereum.priority)
        assertTrue(BigInteger(deposit.latest.maxFeePerGas) >= floor)
        assertTrue(BigInteger(deposit.latest.maxFeePerGas) < ethereum.baseFee.shiftLeft(1).add(ethereum.priority))
    }

    @Test
    fun `when the gas no longer fits what was confirmed nothing is signed, and continuing shows the new cost first`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val service = HashiBridgeService(
            sora, ethereum, accounts, store, { clock }, { "t${++ids}" }, pollIntervalMillis = 6_000,
            backgroundScope = scope, backgroundMonitoring = false,
        )
        // After the approval is mined gas is three times as expensive.
        mineEverything { if (store.history.last().ethSteps.last().intent == HashiEthIntent.APPROVE) ethereum.baseFee = ethereum.baseFee.multiply(BigInteger.valueOf(3)) }
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        val record = service.beginIncoming(testAccount, quote, "USDT")
        advanceUntilIdle()
        val stopped = service.transfer(testAccount.substrateAddress, record.id)!!
        assertEquals(listOf(HashiEthIntent.APPROVE), stopped.ethSteps.map { it.intent })
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, stopped.state)
        assertEquals(HashiRefusal.Reason.COST_CHANGED.name, stopped.refusal)
        assertEquals(1, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
        assertTrue(service.working.value.isEmpty())

        // The page offers "Continue"; its sheet shows today's cost, which is more than was left.
        val again = service.quoteContinueIncoming(testAccount, record.id)
        assertTrue(again.gasCost > BigInteger(stopped.gasBudget!!))
        val done = service.continueIncoming(testAccount, record.id, again, waitForConfirmation = true)
        val deposit = done.step(HashiEthIntent.DEPOSIT)!!
        assertEquals(HashiEthStepState.CONFIRMED, deposit.state)
        assertTrue(deposit.maximumCost <= again.gasCost)
        assertNull(done.refusal)
        scope.cancel()
    }

    @Test
    fun `one continue sheet shown on two pages is taken once`() = runBlocking {
        // An approval is mined; the app was closed before the deposit.
        ethereum.failNextSends(java.io.IOException("connection lost"))
        val record = service.startIncoming(testAccount, service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE), "USDT")
        ethereum.sendFailure = null
        service.refreshOpen()
        ethereum.confirm(record.step(HashiEthIntent.APPROVE)!!.latest.hash, emptyList())
        ethereum.allowance = million
        service.refreshOpen()
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, stored(record.id).state)

        val one = service.quoteContinueIncoming(testAccount, record.id)
        val two = service.quoteContinueIncoming(testAccount, record.id)
        val sent = service.continueIncoming(testAccount, record.id, one, waitForConfirmation = false)
        assertEquals(HashiTransferState.DEPOSIT_SUBMITTED, sent.state)
        val derivations = accounts.derivations
        val deposits = ethereum.sent.size
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.continueIncoming(testAccount, record.id, two, waitForConfirmation = false) })
        // Also once the deposit is mined: the old sheet is for a transfer that has moved on.
        ethereum.confirm(sent.step(HashiEthIntent.DEPOSIT)!!.latest.hash, listOf(Logs.deposit(destination, million, USDT_TOKEN)))
        service.refreshOpen()
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.continueIncoming(testAccount, record.id, two, waitForConfirmation = false) })
        assertEquals(derivations, accounts.derivations)
        assertEquals(deposits, ethereum.sent.size)
        assertTrue(stored(record.id).step(HashiEthIntent.DEPOSIT)!!.maximumCost <= one.gasCost)
    }
}
