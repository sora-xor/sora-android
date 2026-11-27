package jp.co.soramitsu.feature_wallet_impl.bridge

import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiBlock
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bridge does when it is interrupted: the app stopping after the SORA transfer was signed
 * or sent, after an Ethereum broadcast, or in the middle of a claim or a deposit. On the next start
 * the saved transfers are read again and show their true status; nothing is signed and nothing is
 * sent twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HashiBridgeRecoveryTest {

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()
    private val accounts = FakeAccounts()
    private val store = InMemoryStore()
    private var clock = 1_000_000L
    private var ids = 0

    /** A service as a fresh app start has it: the saved transfers and nothing in memory. */
    private fun appStart(scope: CoroutineScope? = null, monitoring: Boolean = false): HashiBridgeService =
        if (scope == null) {
            HashiBridgeService(
                sora, ethereum, accounts, store, { clock }, { "t${++ids}" }, pollIntervalMillis = 0,
                backgroundMonitoring = monitoring,
            )
        } else {
            HashiBridgeService(
                sora, ethereum, accounts, store, { clock }, { "t${++ids}" }, pollIntervalMillis = 6_000,
                backgroundScope = scope, backgroundMonitoring = monitoring,
            )
        }

    private val recipient = "0x94b3c5Fc67b0c63beFCC74D7Afcd333baa697B2A"
    private val requestHash = ByteArray(32) { 0x11 }
    private val destination: ByteArray get() = testAccount.substrateAddress.toAccountId()

    private fun refusal(block: suspend () -> Unit): HashiRefusal.Reason? =
        runCatching { runBlocking { block() } }.exceptionOrNull().let { (it as? HashiRefusal)?.reason }

    private fun approve(amount: BigInteger = BigInteger.valueOf(1_000_000)) {
        sora.approved[EthHex.encode(requestHash)] = HashiApprovedRequest(
            currency = HashiCurrency.TokenAddress(USDT_TOKEN),
            amount = amount,
            to = EthHex.decode(recipient),
            from = destination.copyOf(20),
            txHash = requestHash,
            signatures = listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
    }

    private suspend fun HashiBridgeService.readyToClaim(): HashiTransferRecord {
        val sent = sendOutgoing(testAccount, quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient), "USDT")
        approve()
        return refresh(testAccount, sent.id)!!.also { assertEquals(HashiTransferState.READY_TO_CLAIM, it.state) }
    }

    // --- The SORA transfer -----------------------------------------------------------------------

    @Test
    fun `stopped between signing and sending - the transfer is shown as not sent and nothing is sent later`() = runBlocking {
        val first = appStart()
        val quote = first.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        // The app stops before the wallet's journal takes the transaction.
        sora.journalTakesSubmissions = false
        sora.submitInterruption = CancellationException("process death")
        assertTrue(runCatching { first.sendOutgoing(testAccount, quote, "USDT") }.exceptionOrNull() is CancellationException)
        val saved = store.transfers(testAccount.substrateAddress).single()
        assertEquals(HashiTransferState.SORA_SUBMITTED, saved.state)
        val submissions = sora.submitted.size

        val restarted = appStart()
        val shown = restarted.refresh(testAccount, saved.id)!!
        assertEquals(HashiTransferState.FAILED, shown.state)
        assertEquals(HashiFailure.NOT_SENT, shown.failure)
        assertEquals(submissions, sora.submitted.size)
        assertEquals(1, sora.signed.size)
        // A new transfer is possible again.
        sora.submitInterruption = null
        sora.journalTakesSubmissions = true
        restarted.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        Unit
    }

    @Test
    fun `stopped after the SORA transfer was handed over - its true status comes from the journal and the claim is offered`() = runBlocking {
        val first = appStart()
        val quote = first.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.submitInterruption = CancellationException("process death")
        assertTrue(runCatching { first.sendOutgoing(testAccount, quote, "USDT") }.exceptionOrNull() is CancellationException)
        val saved = store.transfers(testAccount.substrateAddress).single()
        assertEquals(HashiTransferState.SORA_SUBMITTED, saved.state)
        assertNull(saved.requestHash)

        val restarted = appStart()
        // The journal does not know the result yet: still being sent, and no second transfer meanwhile.
        assertEquals(HashiTransferState.SORA_SUBMITTED, restarted.refresh(testAccount, saved.id)!!.state)
        assertEquals(
            HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING,
            refusal { restarted.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) },
        )
        // Finalized: the request hash is read from its block, then the bridge's approval.
        sora.journal = SoraSubmission.Included("0x" + "cc".repeat(32))
        val included = restarted.refresh(testAccount, saved.id)!!
        assertEquals(HashiTransferState.WAITING_APPROVALS, included.state)
        assertEquals(EthHex.encode(requestHash), included.requestHash)
        approve()
        assertEquals(HashiTransferState.READY_TO_CLAIM, restarted.refresh(testAccount, saved.id)!!.state)
        // One signature and one submission in all: nothing was sent again.
        assertEquals(1, sora.signed.size)
        assertEquals(1, sora.submitted.size)
        assertEquals(0, accounts.derivations)
    }

    @Test
    fun `a transfer that failed in its block or expired unsent says which`() = runBlocking {
        val first = appStart()
        val quote = first.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.submission = SoraSubmission.Pending
        val pending = first.sendOutgoing(testAccount, quote, "USDT")
        sora.journal = SoraSubmission.FailedOnChain("EthBridge.RequestsQueueFull")
        val failed = appStart().refresh(testAccount, pending.id)!!
        assertEquals(HashiFailure.FAILED_ON_SORA, failed.failure)
        assertEquals("EthBridge.RequestsQueueFull", failed.error)

        sora.journal = null
        val second = first.sendOutgoing(testAccount, first.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient), "USDT")
        sora.journal = SoraSubmission.NotSent("EXPIRED_NOT_INCLUDED")
        val expired = appStart().refresh(testAccount, second.id)!!
        assertEquals(HashiTransferState.FAILED, expired.state)
        assertEquals(HashiFailure.NOT_SENT, expired.failure)
    }

    @Test
    fun `the screen gets the transfer back as soon as it is signed and checked, and the submission finishes without it`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val service = appStart(scope)
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        val record = service.beginOutgoing(testAccount, quote, "USDT")
        // Signed, accepted by the dry run and saved; not handed over yet.
        assertEquals(HashiTransferState.SORA_SUBMITTED, record.state)
        assertTrue(sora.submitted.isEmpty())
        // While this process is about to send it, a refresh must not call it "not sent".
        assertEquals(HashiTransferState.SORA_SUBMITTED, service.refresh(testAccount, record.id)!!.state)
        advanceUntilIdle()
        assertEquals(1, sora.submitted.size)
        assertEquals(HashiTransferState.WAITING_APPROVALS, service.transfer(testAccount.substrateAddress, record.id)!!.state)
        scope.cancel()
    }

    // --- The claim ---------------------------------------------------------------------------------

    @Test
    fun `stopped mid-claim before the broadcast answered - the saved bytes are sent again, nothing is signed again`() = runBlocking {
        val first = appStart()
        val record = first.readyToClaim()
        ethereum.failNextSends(IOException("connection lost"))
        val sent = first.sendClaim(testAccount, record.id, first.quoteClaim(testAccount, record.id))
        val step = sent.step(HashiEthIntent.CLAIM)!!
        assertEquals(HashiEthStepState.SIGNED, step.state)
        assertTrue(ethereum.sent.isEmpty())
        val derivations = accounts.derivations

        ethereum.sendFailure = null
        val restarted = appStart()
        assertTrue(restarted.refreshOpen())
        assertEquals(listOf(step.latest.raw), ethereum.sent)
        // A second claim cannot be signed while this one is open.
        assertEquals(HashiRefusal.Reason.NOT_READY, refusal { restarted.quoteClaim(testAccount, record.id) })
        ethereum.confirm(step.latest.hash, listOf(Logs.withdrawal(requestHash)))
        assertTrue(restarted.refreshOpen())
        assertEquals(HashiTransferState.DONE, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        assertEquals(derivations, accounts.derivations)
        assertFalse(restarted.refreshOpen())
    }

    @Test
    fun `stopped after the claim was broadcast - it is watched, re-sent with the same bytes at most once a minute, never re-signed`() = runBlocking {
        val first = appStart()
        val record = first.readyToClaim()
        val sent = first.sendClaim(testAccount, record.id, first.quoteClaim(testAccount, record.id))
        val raw = sent.step(HashiEthIntent.CLAIM)!!.latest.raw
        assertEquals(listOf(raw), ethereum.sent)
        val derivations = accounts.derivations

        val restarted = appStart()
        restarted.refreshOpen()
        restarted.refreshOpen()
        assertEquals(listOf(raw), ethereum.sent)
        clock += 61_000
        restarted.refreshOpen()
        assertEquals(listOf(raw, raw), ethereum.sent)
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        // Paid on Ethereum in the meantime (the receipt of our own transaction).
        ethereum.confirm(sent.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.withdrawal(requestHash)))
        restarted.refreshOpen()
        assertEquals(HashiTransferState.DONE, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        assertEquals(derivations, accounts.derivations)
    }

    @Test
    fun `a claim found already paid after a restart is done and is not claimed again`() = runBlocking {
        val first = appStart()
        val record = first.readyToClaim()
        ethereum.used(HashiBridgeContract.bridge, requestHash)
        val restarted = appStart()
        assertEquals(HashiRefusal.Reason.ALREADY_RECEIVED, refusal { restarted.quoteClaim(testAccount, record.id) })
        assertEquals(HashiTransferState.DONE, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        assertTrue(ethereum.sent.isEmpty())
        assertEquals(0, accounts.derivations)
    }

    // --- One transaction at a time ---------------------------------------------------------------

    @Test
    fun `no second Ethereum transaction is signed while one of the account is open`() = runBlocking {
        val service = appStart()
        val record = service.readyToClaim()
        service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val derivations = accounts.derivations
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, refusal { service.startIncoming(testAccount, quote, "USDT") })
        assertEquals(derivations, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
        // Speeding up the open one is still possible: it is the same transaction at the same nonce.
        service.speedUpConfirmed(testAccount, record.id)
        assertEquals(2, ethereum.sent.size)
    }

    @Test
    fun `two confirmations on one Ethereum account at once - the second waits for the first and is refused, no nonce is used twice`() = runTest {
        val service = appStart()
        val record = service.readyToClaim()
        val claimQuote = service.quoteClaim(testAccount, record.id)
        val depositQuote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        // The claim is being signed (its key is being derived) when the deposit is confirmed.
        val keyHeld = CompletableDeferred<Unit>()
        accounts.beforeKey = { keyHeld.await() }
        val claim = async { service.sendClaim(testAccount, record.id, claimQuote) }
        runCurrent()
        accounts.beforeKey = null
        val deposit = async { runCatching { service.startIncoming(testAccount, depositQuote, "USDT") } }
        runCurrent()
        keyHeld.complete(Unit)
        advanceUntilIdle()
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, claim.await().state)
        assertEquals(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING, (deposit.await().exceptionOrNull() as? HashiRefusal)?.reason)
        // One transaction signed and sent: the claim.
        assertEquals(1, ethereum.sent.size)
        assertEquals(1, accounts.derivations)
    }

    @Test
    fun `a step that is running is not started a second time`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val service = appStart(scope)
        ethereum.allowance = BigInteger.valueOf(1_000_000)
        ethereum.onSend = { raw ->
            val step = store.history.last().ethSteps.last()
            assertEquals(raw, step.latest.raw)
            ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)))
        }
        val record = service.beginIncoming(testAccount, service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE), "USDT")
        // The page shows it as busy from the first moment, before anything ran.
        assertTrue(record.id in service.working.value)
        assertEquals(
            HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING,
            runCatching { service.continueConfirmed(testAccount, record.id, waitForConfirmation = false) }
                .exceptionOrNull().let { (it as? HashiRefusal)?.reason },
        )
        service.continueIncomingInBackground(testAccount, record.id, service.quoteContinueIncoming(testAccount, record.id))
        advanceUntilIdle()
        assertTrue(service.working.value.isEmpty())
        val done = service.transfer(testAccount.substrateAddress, record.id)!!
        assertEquals(listOf(HashiEthIntent.DEPOSIT), done.ethSteps.map { it.intent })
        assertEquals(1, ethereum.sent.size)
        assertEquals(1, accounts.derivations)
        scope.cancel()
    }

    // --- Deposits ---------------------------------------------------------------------------------

    @Test
    fun `stopped after the approval - the deposit waits for the person and the approval is not repeated`() = runBlocking {
        val first = appStart()
        ethereum.failNextSends(IOException("connection lost"))
        val record = first.startIncoming(testAccount, first.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE), "USDT")
        val approval = record.step(HashiEthIntent.APPROVE)!!
        assertEquals(HashiEthStepState.SIGNED, approval.state)
        val derivations = accounts.derivations

        ethereum.sendFailure = null
        val restarted = appStart()
        restarted.refreshOpen()
        assertEquals(listOf(approval.latest.raw), ethereum.sent)
        ethereum.confirm(approval.latest.hash, emptyList())
        ethereum.allowance = BigInteger.valueOf(1_000_000)
        restarted.refreshOpen()
        val ready = restarted.transfer(testAccount.substrateAddress, record.id)!!
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, ready.state)
        assertEquals(derivations, accounts.derivations)
        // Only the person's confirmation signs the deposit, and only the deposit.
        ethereum.onSend = { _ ->
            val step = store.history.last().ethSteps.last()
            ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)))
        }
        val continued = restarted.continueConfirmed(testAccount, record.id, waitForConfirmation = true)
        assertEquals(listOf(HashiEthIntent.APPROVE, HashiEthIntent.DEPOSIT), continued.ethSteps.map { it.intent })
        assertEquals(derivations + 1, accounts.derivations)
    }

    @Test
    fun `continuing a deposit later reads the bridge again - paused or no longer accepting the token, nothing is signed`() = runBlocking {
        val service = appStart()
        ethereum.failNextSends(IOException("connection lost"))
        val record = service.startIncoming(testAccount, service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE), "USDT")
        val approval = record.step(HashiEthIntent.APPROVE)!!
        ethereum.sendFailure = null
        service.refreshOpen()
        ethereum.confirm(approval.latest.hash, emptyList())
        ethereum.allowance = BigInteger.valueOf(1_000_000)
        service.refreshOpen()
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, service.transfer(testAccount.substrateAddress, record.id)!!.state)
        val derivations = accounts.derivations
        val sent = ethereum.sent.size

        // Overnight the bridge stopped accepting the token.
        ethereum.accepted.clear()
        assertEquals(
            HashiRefusal.Reason.TOKEN_NOT_ACCEPTED,
            refusal { service.continueConfirmed(testAccount, record.id, waitForConfirmation = true) },
        )
        // Or the bridge was paused.
        ethereum.accepted += EthHex.encode(USDT_TOKEN)
        sora.blocks = setOf(HashiBlock.SIGNERS_DOWN)
        assertEquals(HashiRefusal.Reason.PAUSED, refusal { service.continueConfirmed(testAccount, record.id, waitForConfirmation = true) })
        assertEquals(derivations, accounts.derivations)
        assertEquals(sent, ethereum.sent.size)
    }

    @Test
    fun `stopped after the deposit was broadcast - the SORA credit is followed and the deposit is never sent anew`() = runBlocking {
        val first = appStart()
        ethereum.allowance = BigInteger.valueOf(1_000_000)
        // The deposit is broadcast; the app stops before any receipt.
        val record = first.continueConfirmed(
            testAccount,
            first.let {
                val quote = it.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
                it.startIncomingWithoutWaiting(quote)
            },
            waitForConfirmation = false,
        )
        val deposit = record.step(HashiEthIntent.DEPOSIT)!!
        assertEquals(HashiEthStepState.BROADCAST, deposit.state)
        val derivations = accounts.derivations

        val restarted = appStart()
        restarted.refreshOpen()
        assertEquals(HashiTransferState.DEPOSIT_SUBMITTED, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        ethereum.confirm(deposit.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)))
        restarted.refreshOpen()
        assertEquals(HashiTransferState.WAITING_SORA, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        val incoming = ByteArray(32) { 0x44 }
        sora.incoming[deposit.latest.hash] = incoming
        sora.status(incoming, HashiRequestStatus.DONE)
        restarted.refreshOpen()
        assertEquals(HashiTransferState.DONE, restarted.transfer(testAccount.substrateAddress, record.id)!!.state)
        assertEquals(1, ethereum.sent.size)
        assertEquals(derivations, accounts.derivations)
        // A finished deposit offers nothing to continue, and nothing is signed.
        assertEquals(HashiRefusal.Reason.NOT_READY, refusal { restarted.continueConfirmed(testAccount, record.id, waitForConfirmation = false) })
        assertEquals(derivations, accounts.derivations)
    }

    /** A new deposit saved at its first state, as the screen's call leaves it before any step ran. */
    private suspend fun HashiBridgeService.startIncomingWithoutWaiting(quote: IncomingQuote): String {
        ethereum.failNextSends(HashiRpcErrorException(-32000, "insufficient funds for gas", null))
        // The first attempt is refused by the node: nothing is pending, the transfer exists.
        val failed = startIncoming(testAccount, quote, "USDT")
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, failed.state)
        assertEquals(HashiFailure.ETHEREUM_REFUSED, failed.failure)
        ethereum.sendFailure = null
        ethereum.latestNonce = ethereum.latestNonce.add(BigInteger.ONE)
        ethereum.pendingNonce = ethereum.latestNonce
        return failed.id
    }

    @Test
    fun `an approval that is mined but not yet visible on the answering node is read again, not signed again`() = runBlocking {
        val service = appStart()
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        ethereum.onSend = { _ ->
            val step = store.history.last().ethSteps.last()
            if (step.intent == HashiEthIntent.APPROVE) {
                ethereum.confirm(step.latest.hash, emptyList())
                ethereum.allowance = BigInteger.valueOf(1_000_000)
                // Two answers from a node one block behind.
                ethereum.staleAllowances += listOf(BigInteger.ZERO, BigInteger.ZERO)
            } else {
                ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)))
            }
        }
        val record = service.startIncoming(testAccount, quote, "USDT")
        assertEquals(listOf(HashiEthIntent.APPROVE, HashiEthIntent.DEPOSIT), record.ethSteps.map { it.intent })
        assertEquals(HashiEthStepState.CONFIRMED, record.step(HashiEthIntent.DEPOSIT)!!.state)
    }

    @Test
    fun `an allowance that never appears stops the deposit, and Try again may approve once more`() = runBlocking {
        val service = appStart()
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        ethereum.onSend = { _ -> ethereum.confirm(store.history.last().ethSteps.last().latest.hash, emptyList()) }
        val stuck = service.startIncoming(testAccount, quote, "USDT")
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, stuck.state)
        assertEquals(HashiFailure.ALLOWANCE_NOT_SET, stuck.failure)
        assertEquals(listOf(HashiEthIntent.APPROVE), stuck.ethSteps.map { it.intent })
        // Reading it again does not turn it into "continue".
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, service.refresh(testAccount, stuck.id)!!.state)
        // Try again (after the PIN): one more approval, and this time the allowance is there.
        ethereum.latestNonce = BigInteger.valueOf(6)
        ethereum.onSend = { _ ->
            val step = store.history.last().ethSteps.last()
            if (step.intent == HashiEthIntent.APPROVE) {
                ethereum.confirm(step.latest.hash, emptyList())
                ethereum.allowance = BigInteger.valueOf(1_000_000)
            } else {
                ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)))
            }
        }
        val retried = service.retryConfirmed(testAccount, stuck.id)
        assertEquals(
            listOf(HashiEthIntent.APPROVE, HashiEthIntent.APPROVE, HashiEthIntent.DEPOSIT),
            retried.ethSteps.map { it.intent },
        )
        assertEquals(HashiEthStepState.CONFIRMED, retried.step(HashiEthIntent.DEPOSIT)!!.state)
        assertNull(retried.failure)
    }

    @Test
    fun `a step refused after the confirmation closed leaves the transfer in place and says why`() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val service = appStart(scope)
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        // The Ether for gas is gone by the time the step runs.
        ethereum.balances[EthHex.encode(TestEthAccount.address)] = BigInteger.ONE
        val record = service.beginIncoming(testAccount, quote, "USDT")
        advanceUntilIdle()
        val saved = service.transfer(testAccount.substrateAddress, record.id)!!
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, saved.state)
        assertEquals(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS.name, saved.refusal)
        assertTrue(saved.ethSteps.isEmpty())
        assertEquals(0, accounts.derivations)
        assertTrue(service.working.value.isEmpty())
        scope.cancel()
    }

    // --- Monitoring ------------------------------------------------------------------------------

    @Test
    fun `after a start every open transfer is read until it is final, and reading never signs`() = runTest {
        val first = appStart()
        val record = first.readyToClaim()
        val other = SoraAccountsForTest.second
        // A transfer of another account on this device is followed too.
        store.put(record.copy(id = "other", wallet = other, state = HashiTransferState.WAITING_APPROVALS))
        val derivations = accounts.derivations

        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val restarted = appStart(scope, monitoring = true)
        restarted.resumeMonitoring()
        restarted.resumeMonitoring()
        runCurrent()
        // Both are read; the approved one stays ready (claiming needs the person).
        assertEquals(HashiTransferState.READY_TO_CLAIM, store.get(testAccount.substrateAddress, record.id)!!.state)
        assertEquals(HashiTransferState.READY_TO_CLAIM, store.get(other, "other")!!.state)
        // Paid on Ethereum (by this wallet on another device, say): both become final and the loop ends.
        ethereum.used(HashiBridgeContract.bridge, requestHash)
        advanceUntilIdle()
        assertEquals(HashiTransferState.DONE, store.get(testAccount.substrateAddress, record.id)!!.state)
        assertEquals(HashiTransferState.DONE, store.get(other, "other")!!.state)
        assertTrue(store.openTransfers().isEmpty())
        assertEquals(derivations, accounts.derivations)
        assertTrue(ethereum.sent.isEmpty())
        scope.cancel()
    }
}

internal object SoraAccountsForTest {
    const val second = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
}
