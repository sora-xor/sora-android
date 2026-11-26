package jp.co.soramitsu.feature_wallet_impl.bridge

import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiBlock
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiDryRunResult
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HashiBridgeServiceTest {

    private val sora = FakeSora()
    private val ethereum = FakeEthereum()
    private val accounts = FakeAccounts()
    private val store = InMemoryStore()
    private var clock = 1_000_000L
    private var ids = 0
    private val service = HashiBridgeService(sora, ethereum, accounts, store, { clock }, { "t${++ids}" }, pollIntervalMillis = 0)

    private val recipient = "0x94b3c5Fc67b0c63beFCC74D7Afcd333baa697B2A"

    private fun refusal(block: suspend () -> Unit): HashiRefusal.Reason? =
        runCatching { runBlocking { block() } }.exceptionOrNull().let { (it as? HashiRefusal)?.reason }

    // --- Availability ---------------------------------------------------------------------------

    @Test
    fun `while paused no new transfer can start and nothing is signed`() = runBlocking {
        sora.blocks = setOf(HashiBlock.SIGNERS_UNVERIFIED)
        assertEquals(HashiRefusal.Reason.PAUSED, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) })
        assertEquals(HashiRefusal.Reason.PAUSED, refusal { service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE) })
        sora.blocks = emptySet()
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.blocks = setOf(HashiBlock.STALLED_REQUESTS)
        assertEquals(HashiRefusal.Reason.PAUSED, refusal { service.sendOutgoing(testAccount, quote, "USDT") })
        assertTrue(sora.signed.isEmpty())
        assertEquals(0, accounts.derivations)
    }

    // --- Outgoing quote ---------------------------------------------------------------------------

    @Test
    fun `outgoing amounts with excess digits, a zero or invalid recipient, and missing balances are refused`() = runBlocking {
        assertEquals(HashiRefusal.Reason.TOO_MANY_DECIMALS, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal("1.0000001"), recipient) })
        assertEquals(HashiRefusal.Reason.AMOUNT_INVALID, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ZERO, recipient) })
        assertEquals(HashiRefusal.Reason.RECIPIENT_INVALID, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, "0x" + "00".repeat(20)) })
        assertEquals(HashiRefusal.Reason.RECIPIENT_INVALID, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, "0x94b3c5fc67b0c63befcc74d7afcd333baa697B2A") })
        sora.transferable = BigInteger.ONE
        assertEquals(HashiRefusal.Reason.INSUFFICIENT_BALANCE, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) })
        sora.transferable = BigInteger.TEN.pow(24)
        sora.xorTransferable = BigInteger.ONE
        assertEquals(HashiRefusal.Reason.INSUFFICIENT_FEE_BALANCE, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) })
        // XOR pays its own fee.
        sora.xorTransferable = BigInteger.TEN.pow(18)
        assertEquals(
            HashiRefusal.Reason.INSUFFICIENT_FEE_BALANCE,
            refusal { service.quoteOutgoing(testAccount, HashiBridgeContract.XOR_ASSET_ID, BigDecimal("0.5"), recipient) },
        )
    }

    @Test
    fun `outgoing Sidechain assets need locked liquidity on SORA and tokens in the Ethereum bridge`() = runBlocking {
        sora.locked[USDT_ID] = BigInteger("999999999999999999")
        assertEquals(HashiRefusal.Reason.NOT_ENOUGH_LOCKED_LIQUIDITY, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) })
        sora.locked[USDT_ID] = BigInteger.TEN.pow(18)
        ethereum.tokenBalances[EthHex.encode(USDT_TOKEN) + EthHex.encode(HashiBridgeContract.bridge)] = BigInteger.valueOf(999_999)
        assertEquals(HashiRefusal.Reason.BRIDGE_LACKS_TOKENS, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) })
        ethereum.tokenBalances[EthHex.encode(USDT_TOKEN) + EthHex.encode(HashiBridgeContract.bridge)] = BigInteger.valueOf(1_000_000)
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        assertEquals(BigInteger.TEN.pow(18), quote.soraAmount)
        assertEquals(BigInteger.valueOf(1_000_000), quote.ethAmount)
        // Claim estimate: 181 000 gas × (2 × 10 gwei + 1 gwei).
        assertEquals(BigInteger.valueOf(181_000).multiply(BigInteger.valueOf(21_000_000_000)), quote.claimCostEstimate)
        assertFalse(quote.ethTooLowToClaim)
    }

    @Test
    fun `Ether payouts go only to addresses without contract code`() = runBlocking {
        ethereum.withCode += EthHex.encode(EthHex.decode(recipient))
        assertEquals(HashiRefusal.Reason.RECIPIENT_HAS_CODE, refusal { service.quoteOutgoing(testAccount, HashiBridgeContract.ETH_ASSET_ID, BigDecimal.ONE, recipient) })
        // A token payout to the same address is fine.
        service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        Unit
    }

    @Test
    fun `a wallet without a recovery phrase has no Ethereum account`() = runBlocking {
        accounts.address = null
        assertEquals(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT, refusal { service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient) })
    }

    @Test
    fun `owned assets whose ERC-20 decimals differ from the SORA precision are not bridgeable`() = runBlocking {
        ethereum.decimals[EthHex.encode(XOR_TOKEN)] = 9
        assertNull(service.resolveAsset(HashiBridgeContract.XOR_ASSET_ID))
        assertNull(service.resolveAsset("0x" + "77".repeat(32)))
    }

    // --- Outgoing send --------------------------------------------------------------------------

    @Test
    fun `a failing dry run stops before broadcast and is kept with its error`() = runBlocking {
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.dryRun = HashiDryRunResult.DispatchFailed("BridgeProxy.NotEnoughLockedLiquidity", 101, 2)
        val error = runCatching { service.sendOutgoing(testAccount, quote, "USDT") }.exceptionOrNull() as HashiRefusal
        assertEquals(HashiRefusal.Reason.DRY_RUN_FAILED, error.reason)
        assertEquals("BridgeProxy.NotEnoughLockedLiquidity", error.detail)
        assertTrue(sora.submitted.isEmpty())
        val saved = service.transfers(testAccount.substrateAddress).single()
        assertEquals(HashiTransferState.FAILED, saved.state)
        assertEquals("BridgeProxy.NotEnoughLockedLiquidity", saved.error)
    }

    @Test
    fun `the extrinsic hash is saved before the single submission and the request hash after finality`() = runBlocking {
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.onSubmit = {
            val saved = store.history.last()
            assertEquals(HashiTransferState.SORA_SUBMITTED, saved.state)
            assertEquals("0x" + "ee".repeat(32), saved.soraExtrinsicHash)
        }
        val record = service.sendOutgoing(testAccount, quote, "USDT")
        assertEquals(1, sora.submitted.size)
        assertEquals(HashiTransferState.WAITING_APPROVALS, record.state)
        assertEquals("0x" + "11".repeat(32), record.requestHash)
        assertEquals(recipient, record.recipient)
        assertEquals("1000000", record.ethAmount)
        assertEquals(store.history.first().state, HashiTransferState.SORA_SIGNED)
    }

    @Test
    fun `a transfer refused or failed on chain is failed with the chain's reason`() = runBlocking {
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.submission = SoraSubmission.FailedOnChain("EthBridge.RecoveryHalted")
        assertEquals("EthBridge.RecoveryHalted", service.sendOutgoing(testAccount, quote, "USDT").error)
        sora.submission = SoraSubmission.NotSent("Payment")
        val notSent = service.sendOutgoing(testAccount, quote, "USDT")
        assertEquals(HashiTransferState.FAILED, notSent.state)
    }

    @Test
    fun `a submission with an unknown result is resolved from the journal on refresh`() = runBlocking {
        val quote = service.quoteOutgoing(testAccount, USDT_ID, BigDecimal.ONE, recipient)
        sora.submission = SoraSubmission.Pending
        val pending = service.sendOutgoing(testAccount, quote, "USDT")
        assertEquals(HashiTransferState.SORA_SUBMITTED, pending.state)
        assertEquals(HashiTransferState.SORA_SUBMITTED, service.refresh(testAccount, pending.id)!!.state)
        sora.journal = SoraSubmission.Included("0x" + "cc".repeat(32))
        val included = service.refresh(testAccount, pending.id)!!
        assertEquals(HashiTransferState.WAITING_APPROVALS, included.state)
        assertEquals("0x" + "cc".repeat(32), included.soraBlockHash)
        assertEquals(1, sora.submitted.size)
    }

    // --- Monitoring ------------------------------------------------------------------------------

    private suspend fun waitingTransfer(asset: String = USDT_ID, amount: BigDecimal = BigDecimal.ONE): HashiTransferRecord {
        val quote = service.quoteOutgoing(testAccount, asset, amount, recipient)
        return service.sendOutgoing(testAccount, quote, "X")
    }

    private val requestHash = ByteArray(32) { 0x11 }

    @Test
    fun `request statuses drive the transfer, Frozen and Broken are not final`() = runBlocking {
        val record = waitingTransfer()
        sora.status(requestHash, HashiRequestStatus.PENDING)
        assertEquals(HashiTransferState.WAITING_APPROVALS, service.refresh(testAccount, record.id)!!.state)
        sora.status(requestHash, HashiRequestStatus.FROZEN)
        assertEquals(HashiTransferState.PAUSED_BY_BRIDGE, service.refresh(testAccount, record.id)!!.state)
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        assertEquals(HashiTransferState.READY_TO_CLAIM, service.refresh(testAccount, record.id)!!.state)
        sora.status(requestHash, HashiRequestStatus.BROKEN, raw = "0x050001")
        val broken = service.refresh(testAccount, record.id)!!
        assertEquals(HashiTransferState.NEEDS_OPERATORS, broken.state)
        assertEquals("0x050001", broken.error)
        assertFalse(broken.state.isFinal)
        sora.status(requestHash, HashiRequestStatus.FAILED, raw = "0x0301")
        assertEquals(HashiTransferState.FAILED, service.refresh(testAccount, record.id)!!.state)
    }

    @Test
    fun `an approved request already paid on Ethereum is done without a claim`() = runBlocking {
        val record = waitingTransfer()
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        ethereum.used(HashiBridgeContract.bridge, requestHash)
        val done = service.refresh(testAccount, record.id)!!
        assertEquals(HashiTransferState.DONE, done.state)
        assertEquals("ALREADY_RECEIVED", done.error)
        assertTrue(ethereum.sent.isEmpty())
    }

    @Test
    fun `refreshing never signs anything`() = runBlocking {
        waitingTransfer()
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
        val signaturesBefore = accounts.derivations
        repeat(3) { service.refreshAll(testAccount) }
        assertEquals(signaturesBefore, accounts.derivations)
        assertTrue(ethereum.sent.isEmpty())
    }

    // --- Claims ---------------------------------------------------------------------------------

    private fun approve(amount: BigInteger = BigInteger.valueOf(1_000_000), token: ByteArray = USDT_TOKEN, to: String = recipient) {
        sora.approved[EthHex.encode(requestHash)] = HashiApprovedRequest(
            currency = HashiCurrency.TokenAddress(token),
            amount = amount,
            to = EthHex.decode(to),
            from = testAccount.substrateAddress.toAccountId().copyOf(20),
            txHash = requestHash,
            signatures = listOf(HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0)),
        )
        sora.status(requestHash, HashiRequestStatus.APPROVALS_READY)
    }

    private suspend fun readyToClaim(): HashiTransferRecord {
        val record = waitingTransfer()
        approve()
        return service.refresh(testAccount, record.id)!!.also { assertEquals(HashiTransferState.READY_TO_CLAIM, it.state) }
    }

    @Test
    fun `claims stay available while new transfers are paused`() = runBlocking {
        val record = readyToClaim()
        sora.blocks = setOf(HashiBlock.SIGNERS_DOWN)
        val quote = service.quoteClaim(testAccount, record.id)
        assertEquals(BigInteger.valueOf(1_000_000), quote.claim.amount)
        assertEquals(BigInteger.valueOf(5), quote.nonce)
        // gasLimit = 100 000 × 1.2.
        assertEquals(BigInteger.valueOf(120_000), quote.fees.gasLimit)
        val sent = service.sendClaim(testAccount, record.id, quote)
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, sent.state)
        assertEquals(1, ethereum.sent.size)
    }

    @Test
    fun `an approved amount other than the expected one stops the claim and keeps both`() = runBlocking {
        val record = readyToClaim()
        approve(amount = BigInteger.valueOf(1_000_001))
        val error = runCatching { service.quoteClaim(testAccount, record.id) }.exceptionOrNull() as HashiRefusal
        assertEquals(HashiRefusal.Reason.AMOUNT_MISMATCH, error.reason)
        assertEquals("1000000/1000001", service.transfer(testAccount.substrateAddress, record.id)!!.approvedAmountMismatch)
        assertEquals(0, accounts.derivations)
    }

    @Test
    fun `a claim that would revert is not sent`() = runBlocking {
        val record = readyToClaim()
        ethereum.revert = "Peer signatures are invalid"
        val error = runCatching { service.quoteClaim(testAccount, record.id) }.exceptionOrNull() as HashiRefusal
        assertEquals(HashiRefusal.Reason.CALL_REVERTED, error.reason)
        assertEquals("Peer signatures are invalid", error.detail)
        assertTrue(ethereum.sent.isEmpty())
    }

    @Test
    fun `the claim is saved before broadcast and done only with the Withdrawal event`() = runBlocking {
        val record = readyToClaim()
        val quote = service.quoteClaim(testAccount, record.id)
        ethereum.onSend = { raw ->
            val saved = store.history.last().step(HashiEthIntent.CLAIM)!!
            assertEquals(raw, saved.latest.raw)
            assertEquals(HashiEthStepState.SIGNED, saved.state)
        }
        val sent = service.sendClaim(testAccount, record.id, quote)
        val step = sent.step(HashiEthIntent.CLAIM)!!
        assertEquals(HashiEthStepState.BROADCAST, step.state)
        assertTrue("the key was wiped", accounts.keysHandedOut.all { key -> key.all { it == 0.toByte() } })
        // A receipt without the event is not success.
        ethereum.confirm(step.latest.hash, logs = emptyList())
        val noEvent = service.refresh(testAccount, record.id)!!
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, noEvent.state)
        assertEquals("EVENT_MISSING", noEvent.error)
    }

    @Test
    fun `a claim with its Withdrawal event is done`() = runBlocking {
        val record = readyToClaim()
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        ethereum.confirm(sent.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.withdrawal(requestHash)))
        assertEquals(HashiTransferState.DONE, service.refresh(testAccount, record.id)!!.state)
    }

    @Test
    fun `VAL is claimed on the VAL master and proven by its mint`() = runBlocking {
        val record = waitingTransfer(HashiBridgeContract.VAL_ASSET_ID, BigDecimal.ONE)
        approve(amount = BigInteger.TEN.pow(18), token = HashiBridgeContract.valToken)
        assertEquals(HashiTransferState.READY_TO_CLAIM, service.refresh(testAccount, record.id)!!.state)
        val quote = service.quoteClaim(testAccount, record.id)
        assertEquals(HashiBridgeContract.VAL_MASTER_ADDRESS.lowercase(), EthHex.encode(quote.claim.contract))
        val sent = service.sendClaim(testAccount, record.id, quote)
        ethereum.confirm(sent.step(HashiEthIntent.CLAIM)!!.latest.hash, listOf(Logs.valMint(EthHex.decode(recipient), BigInteger.TEN.pow(18))))
        assertEquals(HashiTransferState.DONE, service.refresh(testAccount, record.id)!!.state)
        // used() is read on the VAL master.
        ethereum.used(HashiBridgeContract.valMaster, requestHash)
        assertEquals(HashiTransferState.DONE, service.refresh(testAccount, record.id)!!.state)
    }

    @Test
    fun `an unknown broadcast is sent again with the identical bytes, never re-signed`() = runBlocking {
        val record = readyToClaim()
        ethereum.failNextSends()
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val step = sent.step(HashiEthIntent.CLAIM)!!
        assertEquals(HashiEthStepState.SIGNED, step.state)
        assertTrue(ethereum.sent.isEmpty())
        ethereum.sendFailure = null
        val derivations = accounts.derivations
        val refreshed = service.refresh(testAccount, record.id)!!
        assertEquals(listOf(step.latest.raw), ethereum.sent)
        assertEquals(HashiEthStepState.BROADCAST, refreshed.step(HashiEthIntent.CLAIM)!!.state)
        assertEquals(derivations, accounts.derivations)
    }

    @Test
    fun `a node that refuses the bytes ends the step and the claim may be sent again at a new nonce`() = runBlocking {
        val record = readyToClaim()
        ethereum.failNextSends(HashiRpcErrorException(-32000, "insufficient funds for gas * price + value", null))
        val failed = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, failed.state)
        ethereum.sendFailure = null
        ethereum.latestNonce = BigInteger.valueOf(6)
        ethereum.pendingNonce = BigInteger.valueOf(6)
        val retried = service.retryEthereumStep(testAccount, record.id)
        val steps = retried.ethSteps.filter { it.intent == HashiEthIntent.CLAIM }
        assertEquals(listOf("5", "6"), steps.map { it.nonce })
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, retried.state)
    }

    @Test
    fun `nonce too low after a lost answer is not a refusal - the step stays watched and its receipt confirms it`() = runBlocking {
        val record = readyToClaim()
        // The first send reached a node but its answer was lost; the next endpoint says the
        // nonce is used - by this very transaction.
        ethereum.failNextSends(HashiRpcErrorException(-32000, "nonce too low: next nonce 6, tx nonce 5", null))
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val step = sent.step(HashiEthIntent.CLAIM)!!
        assertEquals(HashiTransferState.CLAIM_SUBMITTED, sent.state)
        assertTrue(step.state.isOpen)
        ethereum.sendFailure = null
        ethereum.latestNonce = BigInteger.valueOf(6)
        // Nothing can be signed again for it while it is open.
        assertEquals(HashiRefusal.Reason.NOT_READY, runCatching { service.quoteClaim(testAccount, record.id) }.exceptionOrNull().let { (it as? HashiRefusal)?.reason })
        ethereum.confirm(step.latest.hash, listOf(Logs.withdrawal(requestHash)))
        assertEquals(HashiTransferState.DONE, service.refresh(testAccount, record.id)!!.state)
    }

    @Test
    fun `a step judged replaced whose own attempt turns out mined is watched again, not signed again`() = runBlocking {
        val record = readyToClaim()
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val step = sent.step(HashiEthIntent.CLAIM)!!
        ethereum.latestNonce = BigInteger.valueOf(6)
        service.refresh(testAccount, record.id)
        clock += 11 * 60_000
        assertEquals(HashiEthStepState.REPLACED, service.refresh(testAccount, record.id)!!.step(HashiEthIntent.CLAIM)!!.state)
        // The endpoint that lagged now has the receipt of our own claim.
        ethereum.confirm(step.latest.hash, listOf(Logs.withdrawal(requestHash)))
        val derivations = accounts.derivations
        val refusal = runCatching { service.retryEthereumStep(testAccount, record.id) }.exceptionOrNull() as? HashiRefusal
        assertEquals(HashiRefusal.Reason.NOT_READY, refusal?.reason)
        assertEquals(derivations, accounts.derivations)
        assertEquals(1, ethereum.sent.size)
        assertEquals(HashiTransferState.DONE, service.refresh(testAccount, record.id)!!.state)
    }

    @Test
    fun `a nonce taken by another transaction marks the step replaced, ours are looked up first`() = runBlocking {
        val record = readyToClaim()
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        ethereum.latestNonce = BigInteger.valueOf(6)
        // Seen once is not enough: endpoints can briefly disagree.
        assertEquals(HashiEthStepState.BROADCAST, service.refresh(testAccount, record.id)!!.step(HashiEthIntent.CLAIM)!!.state)
        clock += 61_000
        // A minute is not enough either: an endpoint's transaction index can lag behind its nonce.
        assertEquals(HashiEthStepState.BROADCAST, service.refresh(testAccount, record.id)!!.step(HashiEthIntent.CLAIM)!!.state)
        clock += 10 * 60_000
        val replaced = service.refresh(testAccount, record.id)!!
        assertEquals(HashiEthStepState.REPLACED, replaced.step(HashiEthIntent.CLAIM)!!.state)
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, replaced.state)
        assertNotEquals(sent.state, replaced.state)
    }

    @Test
    fun `speed up keeps the nonce, raises both fees by at least 12_5 percent and watches every hash`() = runBlocking {
        val record = readyToClaim()
        val sent = service.sendClaim(testAccount, record.id, service.quoteClaim(testAccount, record.id))
        val first = sent.step(HashiEthIntent.CLAIM)!!
        val faster = service.speedUp(testAccount, record.id).step(HashiEthIntent.CLAIM)!!
        assertEquals(first.nonce, faster.nonce)
        assertEquals(2, faster.attempts.size)
        val (old, new) = faster.attempts
        assertTrue(BigInteger(new.maxFeePerGas).multiply(BigInteger.valueOf(1000)) >= BigInteger(old.maxFeePerGas).multiply(BigInteger.valueOf(1125)))
        assertTrue(BigInteger(new.maxPriorityFeePerGas).multiply(BigInteger.valueOf(1000)) >= BigInteger(old.maxPriorityFeePerGas).multiply(BigInteger.valueOf(1125)))
        // The first attempt is the one that got mined.
        ethereum.confirm(old.hash, listOf(Logs.withdrawal(requestHash)))
        ethereum.latestNonce = BigInteger.valueOf(6)
        val done = service.refresh(testAccount, record.id)!!
        assertEquals(HashiTransferState.DONE, done.state)
        assertEquals(old.hash, done.step(HashiEthIntent.CLAIM)!!.confirmedHash)
    }

    // --- Incoming -------------------------------------------------------------------------------

    private val destination: ByteArray get() = testAccount.substrateAddress.toAccountId()

    @Test
    fun `incoming checks the token, the locked liquidity and an exact non-zero SORA amount`() = runBlocking {
        ethereum.accepted.clear()
        assertEquals(HashiRefusal.Reason.TOKEN_NOT_ACCEPTED, refusal { service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE) })
        ethereum.accepted += EthHex.encode(USDT_TOKEN)
        assertEquals(HashiRefusal.Reason.TOO_MANY_DECIMALS, refusal { service.quoteIncoming(testAccount, USDT_ID, BigDecimal("1.0000001")) })
        sora.locked[HashiBridgeContract.XOR_ASSET_ID] = BigInteger.TEN.pow(17)
        assertEquals(
            HashiRefusal.Reason.NOT_ENOUGH_LOCKED_LIQUIDITY,
            refusal { service.quoteIncoming(testAccount, HashiBridgeContract.XOR_ASSET_ID, BigDecimal.ONE) },
        )
        // TBCD divides by 10^38: an ordinary amount would credit nothing.
        ethereum.sidechainTokens[HashiBridgeContract.TBCD_ASSET_ID] = ByteArray(20) { 3 }
        ethereum.decimals[EthHex.encode(ByteArray(20) { 3 })] = 18
        assertEquals(HashiRefusal.Reason.AMOUNT_INVALID, refusal { service.quoteIncoming(testAccount, HashiBridgeContract.TBCD_ASSET_ID, BigDecimal("1000")) })
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal("1.5"))
        assertEquals(BigInteger.valueOf(1_500_000), quote.ethAmount)
        assertEquals(BigInteger("1500000000000000000"), quote.soraAmount)
        assertTrue(quote.needsApprove)
        assertFalse(quote.needsApproveZero)
    }

    @Test
    fun `a non-zero lower allowance is reset to zero first, then the exact amount, then the deposit`() = runBlocking {
        ethereum.allowance = BigInteger.valueOf(10)
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        assertTrue(quote.needsApproveZero)
        ethereum.onSend = { raw ->
            val step = store.history.last().ethSteps.last()
            assertEquals(raw, step.latest.raw)
            val record = store.history.last()
            // Each broadcast is confirmed at once, and the allowance follows the approvals.
            ethereum.confirm(
                step.latest.hash,
                if (step.intent == HashiEthIntent.DEPOSIT) listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)) else emptyList(),
            )
            ethereum.allowance = when (step.intent) {
                HashiEthIntent.APPROVE_ZERO -> BigInteger.ZERO
                HashiEthIntent.APPROVE -> BigInteger.valueOf(1_000_000)
                else -> ethereum.allowance
            }
            assertEquals(record.ethAccount, TestEthAccount.ADDRESS)
        }
        val record = service.startIncoming(testAccount, quote, "USDT")
        assertEquals(listOf(HashiEthIntent.APPROVE_ZERO, HashiEthIntent.APPROVE, HashiEthIntent.DEPOSIT), record.ethSteps.map { it.intent })
        assertEquals(EthHex.encode(EthAbi.uintWord(BigInteger.ZERO)), "0x" + record.ethSteps[0].data.takeLast(64))
        assertEquals(HashiTransferState.WAITING_SORA, service.refresh(testAccount, record.id)!!.state)
        // Done only when the incoming TRANSFER request is Done.
        val depositHash = EthHex.decode(record.step(HashiEthIntent.DEPOSIT)!!.latest.hash)
        val incoming = ByteArray(32) { 0x44 }
        sora.incoming[EthHex.encode(depositHash)] = incoming
        sora.status(incoming, HashiRequestStatus.PENDING)
        assertEquals(HashiTransferState.WAITING_SORA, service.refresh(testAccount, record.id)!!.state)
        sora.status(incoming, HashiRequestStatus.DONE)
        val done = service.refresh(testAccount, record.id)!!
        assertEquals(HashiTransferState.DONE, done.state)
        assertEquals(EthHex.encode(incoming), done.incomingRequestHash)
    }

    @Test
    fun `a deposit without its Deposit event is not confirmed`() = runBlocking {
        ethereum.allowance = BigInteger.valueOf(1_000_000)
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        ethereum.onSend = { raw ->
            val step = store.history.last().ethSteps.last()
            assertEquals(raw, step.latest.raw)
            ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(999_999), USDT_TOKEN)))
        }
        val record = service.startIncoming(testAccount, quote, "USDT")
        assertEquals(HashiTransferState.ETHEREUM_STEP_FAILED, record.state)
        assertEquals("EVENT_MISSING", record.error)
    }

    @Test
    fun `after a restart between approval and deposit nothing is signed until the person continues`() = runBlocking {
        val quote = service.quoteIncoming(testAccount, USDT_ID, BigDecimal.ONE)
        ethereum.failNextSends(IOException("offline"))
        val record = service.startIncoming(testAccount, quote, "USDT")
        // The approval is saved, its broadcast unknown; refreshing resends the same bytes only.
        assertEquals(HashiTransferState.APPROVE_SUBMITTED, record.state)
        ethereum.sendFailure = null
        val approval = record.step(HashiEthIntent.APPROVE)!!
        ethereum.confirm(approval.latest.hash, emptyList())
        ethereum.allowance = BigInteger.valueOf(1_000_000)
        val derivations = accounts.derivations
        val ready = service.refresh(testAccount, record.id)!!
        assertEquals(HashiTransferState.READY_TO_DEPOSIT, ready.state)
        assertEquals(derivations, accounts.derivations)
        ethereum.onSend = { raw ->
            val step = store.history.last().ethSteps.last()
            ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.valueOf(1_000_000), USDT_TOKEN)))
            assertEquals(raw, step.latest.raw)
        }
        val continued = service.continueIncoming(testAccount, record.id, waitForConfirmation = true)
        assertEquals(HashiEthStepState.CONFIRMED, continued.step(HashiEthIntent.DEPOSIT)!!.state)
    }

    @Test
    fun `Ether deposits send value without an allowance`() = runBlocking {
        val quote = service.quoteIncoming(testAccount, HashiBridgeContract.ETH_ASSET_ID, BigDecimal("0.01"))
        assertFalse(quote.needsApprove)
        ethereum.onSend = { _ ->
            val step = store.history.last().ethSteps.last()
            ethereum.confirm(step.latest.hash, listOf(Logs.deposit(destination, BigInteger.TEN.pow(16), ByteArray(20))))
        }
        val record = service.startIncoming(testAccount, quote, "ETH")
        val deposit = record.step(HashiEthIntent.DEPOSIT)!!
        assertEquals(BigInteger.TEN.pow(16).toString(), deposit.value)
        assertTrue(deposit.data.startsWith("0xfdfba25d"))
        assertEquals(HashiEthStepState.CONFIRMED, deposit.state)
    }
}
