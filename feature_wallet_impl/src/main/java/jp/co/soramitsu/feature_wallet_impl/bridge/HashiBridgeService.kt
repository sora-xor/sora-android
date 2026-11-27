package jp.co.soramitsu.feature_wallet_impl.bridge

import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.ethereum.Eip1559Transaction
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.ethereum.SignedEthTransaction
import jp.co.soramitsu.common.hashi.EthCallRevertedException
import jp.co.soramitsu.common.hashi.EthFees
import jp.co.soramitsu.common.hashi.EthReceipt
import jp.co.soramitsu.common.hashi.EthTransactionLookup
import jp.co.soramitsu.common.hashi.HashiAmounts
import jp.co.soramitsu.common.hashi.HashiAssetKind
import jp.co.soramitsu.common.hashi.HashiAvailability
import jp.co.soramitsu.common.hashi.HashiBridgeAsset
import jp.co.soramitsu.common.hashi.HashiBridgeCalls
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiClaimAmountMismatchException
import jp.co.soramitsu.common.hashi.HashiClaimCall
import jp.co.soramitsu.common.hashi.HashiClaimPlanner
import jp.co.soramitsu.common.hashi.HashiDryRunResult
import jp.co.soramitsu.common.hashi.HashiEthereumPolicy
import jp.co.soramitsu.common.hashi.HashiReceiptProof
import jp.co.soramitsu.common.hashi.HashiRegisteredAsset
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.common.hashi.HashiSoraCalls
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the bridge will not do what was asked. Nothing was signed or sent. */
class HashiRefusal(val reason: Reason, val detail: String? = null) : Exception(reason.name) {
    enum class Reason {
        PAUSED,
        NO_ETHEREUM_ACCOUNT,
        NOT_BRIDGEABLE,
        AMOUNT_INVALID,
        TOO_MANY_DECIMALS,
        INSUFFICIENT_BALANCE,
        INSUFFICIENT_FEE_BALANCE,
        INSUFFICIENT_ETH_FOR_GAS,
        RECIPIENT_INVALID,
        RECIPIENT_HAS_CODE,
        NOT_ENOUGH_LOCKED_LIQUIDITY,
        BRIDGE_LACKS_TOKENS,
        TOKEN_NOT_ACCEPTED,
        DRY_RUN_FAILED,
        CALL_REVERTED,
        AMOUNT_MISMATCH,
        NOT_READY,
        ALREADY_RECEIVED,

        /**
         * An earlier transaction of this wallet is not settled yet: a SORA bridge transfer whose
         * result is unknown, or an Ethereum transaction that is still open. The next one waits,
         * so nothing is sent twice and no nonce is used twice.
         */
        EARLIER_TRANSACTION_PENDING,

        /**
         * The Ethereum gas would cost more than the screen showed when the person confirmed.
         * Nothing was signed; the page shows the new cost and asks again.
         */
        COST_CHANGED,
        READ_FAILED,
    }
}

class OutgoingQuote(
    val asset: HashiBridgeAsset,
    val soraAmount: BigInteger,
    val ethAmount: BigInteger,
    val recipient: ByteArray,
    val soraFee: BigInteger,
    /** Estimated Ether cost of the later claim (fallback gas × current max fee). */
    val claimCostEstimate: BigInteger,
    val ethBalance: BigInteger,
) {
    val ethTooLowToClaim: Boolean get() = ethBalance < claimCostEstimate

    /** This confirmation sheet: the service takes it once (a second delivery signs nothing). */
    internal val id: String = UUID.randomUUID().toString()
}

class IncomingQuote(
    val asset: HashiBridgeAsset,
    val ethAmount: BigInteger,
    val soraAmount: BigInteger,
    val needsApproveZero: Boolean,
    val needsApprove: Boolean,
    /** Upper bound of the Ether all steps can cost. */
    val gasCostEstimate: BigInteger,
) {
    /** This confirmation sheet: the service takes it once (a second delivery signs nothing). */
    internal val id: String = UUID.randomUUID().toString()
}

class ClaimQuote(
    val claim: HashiClaimCall,
    /** Exactly the fees the claim is signed with: its sheet shows their maximum cost. */
    val fees: EthFees,
    val nonce: BigInteger,
    /** [HashiTransferRecord.signedHashes] when this was shown. */
    val signedBefore: List<String> = emptyList(),
    /** [HashiTransferRecord.confirmations] when this was shown. */
    val confirmationsBefore: Int = 0,
)

/** The Ethereum steps a deposit still needs, as its page shows them before the PIN. */
class IncomingStepsQuote(
    /** The most Ether the remaining steps may cost together: they are signed within it. */
    val gasCost: BigInteger,
    /** [HashiTransferRecord.signedHashes] when this was shown. */
    val signedBefore: List<String>,
    /** [HashiTransferRecord.confirmations] when this was shown. */
    val confirmationsBefore: Int = 0,
)

/** A faster replacement of one pending transaction, as its page shows it before the PIN. */
class SpeedUpQuote(
    val intent: HashiEthIntent,
    val nonce: BigInteger,
    /** The attempt this replaces: the latest one when the sheet was shown. */
    val replaces: String,
    /** Exactly the fees the replacement is signed with. */
    val fees: EthFees,
    /** [HashiTransferRecord.confirmations] when this was shown. */
    val confirmationsBefore: Int = 0,
)

/**
 * The HASHI bridge flows (spec §3–§5 with amendments §8): validation and pre-flight checks,
 * signing (only from an explicit, PIN-confirmed call), persistence before every broadcast, and
 * monitoring that never signs. Claims of approved withdrawals stay possible while new transfers
 * are paused.
 */
class HashiBridgeService(
    private val sora: HashiSoraGateway,
    private val ethereum: HashiEthereumGateway,
    private val accounts: HashiEthereumAccounts,
    private val store: HashiTransferStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val pollIntervalMillis: Long = 6_000L,
    /** Runs confirmed submissions and deposits to the end even when the screen that started them closes. */
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Off in tests that drive every read themselves. */
    private val backgroundMonitoring: Boolean = true,
) {
    private val mutex = Mutex()

    /**
     * Held from the "no open Ethereum transaction" check until the signed transaction is saved, so
     * two confirmations on different transfers of the same Ethereum account (a claim and a
     * deposit, say) cannot both pass the check and take the same nonce.
     */
    private val ethereumSigning = Mutex()

    /** Held from the "no unresolved SORA transfer" check until the signed transfer is saved. */
    private val soraSigning = Mutex()
    private val nonceUsedSince = mutableMapOf<String, Long>()
    private val rebroadcastAt = mutableMapOf<String, Long>()

    /** Confirmation sheets of new transfers already taken: each one starts at most one transfer. */
    private val takenQuotes: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Transfers whose one SORA submission this process is making right now. */
    private val submitting: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Ethereum attempts whose send this process is making right now: never sent twice at once. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val monitoring = AtomicBoolean(false)
    private val _working = MutableStateFlow<Set<String>>(emptySet())

    /**
     * The transfers a confirmed step is being signed, sent or waited for right now. A second
     * confirmation for the same transfer is refused meanwhile, and its page shows it as busy.
     */
    val working: StateFlow<Set<String>> = _working.asStateFlow()
    @Volatile
    private var registered: List<HashiRegisteredAsset>? = null
    private val resolved: MutableMap<String, HashiBridgeAsset?> = java.util.Collections.synchronizedMap(mutableMapOf())

    fun observe(wallet: String): Flow<List<HashiTransferRecord>> = store.observe(wallet)

    suspend fun transfers(wallet: String): List<HashiTransferRecord> = store.transfers(wallet)

    suspend fun transfer(wallet: String, id: String): HashiTransferRecord? = store.get(wallet, id)

    suspend fun availability(): HashiAvailability = sora.availability()

    suspend fun ethereumAccount(account: SoraAccount): String? = accounts.address(account)

    suspend fun ethBalance(address: String): BigInteger = ethereum.balance(parseAddress(address))

    suspend fun registeredAssets(): List<HashiRegisteredAsset> =
        registered ?: sora.registeredAssets().also { registered = it }

    suspend fun isRegistered(assetId: String): Boolean = registeredAssets().any { it.assetId == assetId.lowercase() }

    /**
     * The asset as both chains know it, or null when it cannot cross: no ERC-20 for a SORA-native
     * asset, or an owned asset whose ERC-20 decimals differ from its SORA precision (the pallet
     * does not convert owned amounts, so they would arrive mis-scaled).
     */
    suspend fun resolveAsset(assetId: String): HashiBridgeAsset? {
        val id = assetId.lowercase()
        if (resolved.containsKey(id)) return resolved[id]
        val registration = registeredAssets().firstOrNull { it.assetId == id }
        if (registration == null) {
            resolved[id] = null
            return null
        }
        val asset = when (registration.kind) {
            HashiAssetKind.THISCHAIN -> ethereum.sidechainToken(EthHex.decode(id))?.let { token ->
                val decimals = ethereum.decimals(token)
                if (decimals == registration.soraPrecision) {
                    HashiBridgeAsset(registration.kind, id, registration.soraPrecision, token, decimals)
                } else {
                    null
                }
            }
            HashiAssetKind.SIDECHAIN -> HashiBridgeAsset(
                registration.kind, id, registration.soraPrecision,
                checkNotNull(registration.ethToken), checkNotNull(registration.ethDecimals),
            )
            HashiAssetKind.SIDECHAIN_OWNED -> {
                val token = checkNotNull(registration.ethToken)
                val decimals = checkNotNull(registration.ethDecimals)
                val known = id == HashiBridgeContract.VAL_ASSET_ID && EthAddress.equal(token, HashiBridgeContract.valToken)
                if (known && decimals == registration.soraPrecision) {
                    HashiBridgeAsset(registration.kind, id, registration.soraPrecision, token, decimals)
                } else {
                    null
                }
            }
        }
        resolved[id] = asset
        return asset
    }

    suspend fun tokenBalance(asset: HashiBridgeAsset, owner: String): BigInteger {
        val address = parseAddress(owner)
        return if (asset.isNativeEther) ethereum.balance(address) else ethereum.tokenBalance(asset.ethToken, address)
    }

    /**
     * Ether to leave in the account when the whole balance is deposited: twice the most the
     * deposit's gas can cost now, since the base fee moves between this and the signature.
     */
    suspend fun etherDepositReserve(): BigInteger =
        HashiEthereumPolicy.fees(ethereum.baseFee(), ethereum.priorityFee(), HashiBridgeContract.GAS_SEND_ETH_TO_SIDECHAIN)
            .maximumCost.shiftLeft(1)

    // --- SORA → Ethereum ----------------------------------------------------------------------

    suspend fun quoteOutgoing(account: SoraAccount, assetId: String, amount: BigDecimal, recipient: String): OutgoingQuote {
        requireOpen()
        requireNoUnresolvedSubmission(account.substrateAddress)
        val asset = resolveAsset(assetId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val ethAccount = accounts.address(account) ?: throw HashiRefusal(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT)
        val soraAmount = outgoingUnits(amount, asset)
        val to = EthAddress.parse(recipient) ?: throw HashiRefusal(HashiRefusal.Reason.RECIPIENT_INVALID)
        if (EthAddress.equal(to, EthAddress.ZERO)) throw HashiRefusal(HashiRefusal.Reason.RECIPIENT_INVALID)
        // Bridge pays Ether with transfer() (2300 gas): a contract recipient could lose it.
        if (asset.isNativeEther && !ethereum.hasNoCode(to)) throw HashiRefusal(HashiRefusal.Reason.RECIPIENT_HAS_CODE)
        val fee = sora.transferFee(account, asset.assetId, to, soraAmount)
        val balance = sora.transferable(account, asset.assetId)
        val isXor = asset.assetId == HashiBridgeContract.XOR_ASSET_ID
        if (soraAmount > balance) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_BALANCE)
        if (isXor && soraAmount.add(fee) > balance) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_FEE_BALANCE)
        if (!isXor && fee > sora.transferable(account, HashiBridgeContract.XOR_ASSET_ID)) {
            throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_FEE_BALANCE)
        }
        val factor = sora.denominationFactor(asset.assetId)
        val ethAmount = HashiAmounts.expectedEthAmount(soraAmount, asset, factor)
        if (asset.kind == HashiAssetKind.SIDECHAIN) {
            if (sora.lockedAssets(EthHex.decode(asset.assetId)) < soraAmount) {
                throw HashiRefusal(HashiRefusal.Reason.NOT_ENOUGH_LOCKED_LIQUIDITY)
            }
            val held = if (asset.isNativeEther) {
                ethereum.balance(HashiBridgeContract.bridge)
            } else {
                ethereum.tokenBalance(asset.ethToken, HashiBridgeContract.bridge)
            }
            if (held < ethAmount) throw HashiRefusal(HashiRefusal.Reason.BRIDGE_LACKS_TOKENS)
        }
        val fees = HashiEthereumPolicy.fees(ethereum.baseFee(), ethereum.priorityFee(), HashiClaimPlanner.fallbackGas(asset))
        return OutgoingQuote(
            asset = asset,
            soraAmount = soraAmount,
            ethAmount = ethAmount,
            recipient = to,
            soraFee = fee,
            claimCostEstimate = fees.maxFeePerGas.multiply(HashiClaimPlanner.fallbackGas(asset)),
            ethBalance = ethereum.balance(parseAddress(ethAccount)),
        )
    }

    /**
     * Signs and sends the SORA transfer and waits for its finality. Called only after the person
     * confirmed it with the PIN. Availability is read again; the signed extrinsic is applied to
     * the current state first and nothing is broadcast when that fails; its hash is saved before
     * the single submission.
     */
    suspend fun sendOutgoing(account: SoraAccount, quote: OutgoingQuote, symbol: String): HashiTransferRecord {
        val (record, prepared) = prepareOutgoing(account, quote, symbol)
        return submitOutgoing(record, prepared, quote.soraFee)
    }

    /**
     * [sendOutgoing] for a screen: returns as soon as the transfer is signed, accepted by the dry
     * run and saved. The single submission and the wait for finality run in [backgroundScope], so
     * leaving the screen cannot interrupt them; the transfer's page follows the saved transfer.
     */
    suspend fun beginOutgoing(account: SoraAccount, quote: OutgoingQuote, symbol: String): HashiTransferRecord {
        val (record, prepared) = prepareOutgoing(account, quote, symbol)
        backgroundScope.launch {
            try {
                submitOutgoing(record, prepared, quote.soraFee)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The wallet's journal has the submission; the next refresh reads its result.
            } finally {
                submitting -= record.id
            }
            resumeMonitoring()
        }
        return record
    }

    /** Signs, saves and dry-runs the transfer. Returns it at [HashiTransferState.SORA_SUBMITTED], not yet sent. */
    private suspend fun prepareOutgoing(
        account: SoraAccount,
        quote: OutgoingQuote,
        symbol: String,
    ): Pair<HashiTransferRecord, PreparedSoraTransfer> = soraSigning.withLock {
        // The same sheet delivered twice (a PIN result handed over again, a retried call) is one
        // confirmation: after the first transfer is included the unresolved check alone would pass.
        if (!takenQuotes.add(quote.id)) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        requireOpen()
        requireNoUnresolvedSubmission(account.substrateAddress)
        // Derived again after the PIN, not taken from the cache: the claim will need this
        // account's key, so an address of a phrase the wallet no longer has must not be used.
        val ethAccount = accounts.verifiedAddress(account) ?: throw HashiRefusal(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT)
        check(HashiSoraCalls.isU128(quote.soraAmount)) { "HASHI_AMOUNT_RANGE_INVALID" }
        val prepared = sora.signTransfer(account, quote.asset.assetId, quote.recipient, quote.soraAmount)
        val record = HashiTransferRecord(
            id = newId(),
            wallet = account.substrateAddress,
            direction = HashiDirection.SORA_TO_ETHEREUM,
            assetId = quote.asset.assetId,
            symbol = symbol,
            soraPrecision = quote.asset.soraPrecision,
            ethDecimals = quote.asset.ethDecimals,
            ethToken = EthHex.encode(quote.asset.ethToken),
            soraAmount = quote.soraAmount.toString(),
            ethAmount = quote.ethAmount.toString(),
            recipient = EthAddress.checksum(quote.recipient),
            ethAccount = ethAccount,
            state = HashiTransferState.SORA_SIGNED,
            soraExtrinsicHash = prepared.extrinsicHash,
            createdAt = now(),
            updatedAt = now(),
        )
        // Reserved before the transfer becomes visible: a refresh meanwhile must not call it
        // "not sent" (and so allow a second transfer) while this process is about to send it.
        submitting += record.id
        try {
            store.put(record)
            val dryRun = try {
                sora.dryRun(prepared)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                save(record.copy(state = HashiTransferState.FAILED, failure = HashiFailure.NOT_SENT, error = "DRY_RUN_UNAVAILABLE"))
                throw HashiRefusal(HashiRefusal.Reason.READ_FAILED, "DRY_RUN_UNAVAILABLE")
            }
            if (dryRun !is HashiDryRunResult.Ok) {
                val detail = when (dryRun) {
                    is HashiDryRunResult.DispatchFailed -> dryRun.error
                    is HashiDryRunResult.Invalid -> dryRun.error
                }
                save(record.copy(state = HashiTransferState.FAILED, failure = HashiFailure.REFUSED_BEFORE_SEND, error = detail))
                throw HashiRefusal(HashiRefusal.Reason.DRY_RUN_FAILED, detail)
            }
            save(record.copy(state = HashiTransferState.SORA_SUBMITTED)) to prepared
        } catch (error: Throwable) {
            submitting -= record.id
            throw error
        }
    }

    /** The one submission of a prepared transfer, and what SORA said about it. */
    private suspend fun submitOutgoing(
        record: HashiTransferRecord,
        prepared: PreparedSoraTransfer,
        reviewedFee: BigInteger,
    ): HashiTransferRecord = try {
        applySubmission(record, sora.submit(prepared, reviewedFee))
    } finally {
        submitting -= record.id
    }

    /**
     * Writes what the submission said onto the transfer as it is saved now, and only while SORA's
     * answer is still the newest news: the monitor may have found the block and the request (and
     * the person may have claimed already) before this call returned, and that is never undone.
     */
    private suspend fun applySubmission(record: HashiTransferRecord, outcome: SoraSubmission): HashiTransferRecord {
        val requestHash = if (outcome is SoraSubmission.Included) {
            try {
                sora.requestHashOf(outcome.blockHash, checkNotNull(record.soraExtrinsicHash))
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Included; the request hash is read again when the transfer is refreshed.
                null
            }
        } else {
            null
        }
        return transition(record) { current ->
            if (!current.state.isSoraUnresolved) return@transition current
            when (outcome) {
                is SoraSubmission.Included -> if (requestHash == null) {
                    current.copy(state = HashiTransferState.SORA_SUBMITTED, soraBlockHash = current.soraBlockHash ?: outcome.blockHash)
                } else {
                    current.copy(
                        state = HashiTransferState.WAITING_APPROVALS,
                        soraBlockHash = outcome.blockHash,
                        requestHash = EthHex.encode(requestHash),
                        error = null,
                    )
                }
                is SoraSubmission.FailedOnChain -> current.copy(
                    state = HashiTransferState.FAILED, failure = HashiFailure.FAILED_ON_SORA, error = outcome.detail,
                )
                is SoraSubmission.NotSent -> current.copy(state = HashiTransferState.FAILED, failure = HashiFailure.NOT_SENT, error = outcome.detail)
                SoraSubmission.Pending -> current.copy(state = HashiTransferState.SORA_SUBMITTED)
            }
        }
    }

    /** Prepares the claim of an approved withdrawal; available while new transfers are paused. */
    suspend fun quoteClaim(account: SoraAccount, recordId: String): ClaimQuote {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        check(record.direction == HashiDirection.SORA_TO_ETHEREUM) { "HASHI_NOT_OUTGOING" }
        if (record.state != HashiTransferState.READY_TO_CLAIM && record.state != HashiTransferState.ETHEREUM_STEP_FAILED) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        }
        requireRepeatable(record)
        val claim = planClaim(record)
        if (ethereum.isUsed(claim.contract, claim.txHash)) {
            markAlreadyReceived(record)
            throw HashiRefusal(HashiRefusal.Reason.ALREADY_RECEIVED)
        }
        val from = parseAddress(record.ethAccount)
        requireNoOpenEthereumStep(record.ethAccount)
        if (claim.token != null && claim.token.contentEquals(EthAddress.ZERO) && !ethereum.hasNoCode(claim.recipient)) {
            throw HashiRefusal(HashiRefusal.Reason.RECIPIENT_HAS_CODE)
        }
        val fees = fees(from, claim.contract, claim.data, BigInteger.ZERO)
        if (ethereum.balance(from) < fees.maximumCost) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        return ClaimQuote(claim, fees, ethereum.pendingNonce(from), record.signedHashes, record.confirmations)
    }

    /** Mined without its proof, or a deposit whose nonce went elsewhere: again could pay twice. */
    private fun requireRepeatable(record: HashiTransferRecord) {
        if (record.state == HashiTransferState.ETHEREUM_STEP_FAILED &&
            (record.failure == HashiFailure.ETHEREUM_UNPROVEN || record.failure == HashiFailure.DEPOSIT_UNCONFIRMED)
        ) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        }
    }

    /**
     * Signs and broadcasts the claim with exactly the fees of [quote], the ones its sheet showed.
     * Called only after the person confirmed it with the PIN. A second confirmation of the same
     * sheet (from this or another screen) signs nothing.
     */
    suspend fun sendClaim(account: SoraAccount, recordId: String, quote: ClaimQuote): HashiTransferRecord {
        if (!beginWork(recordId)) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        return try {
            sendClaimUnlocked(account, recordId, quote)
        } finally {
            endWork(recordId)
        }
    }

    private suspend fun sendClaimUnlocked(account: SoraAccount, recordId: String, quote: ClaimQuote): HashiTransferRecord {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        if (record.step(HashiEthIntent.CLAIM)?.state?.isOpen == true) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        if (record.state != HashiTransferState.READY_TO_CLAIM && record.state != HashiTransferState.ETHEREUM_STEP_FAILED) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        }
        requireRepeatable(record)
        // The same claim at a new nonce only when the old nonce was consumed by another
        // transaction or ours reverted (the step is then REPLACED or REVERTED, never open).
        val again = planClaim(record)
        check(again.data.contentEquals(quote.claim.data)) { "HASHI_CLAIM_CHANGED" }
        if (ethereum.isUsed(again.contract, again.txHash)) {
            markAlreadyReceived(record)
            throw HashiRefusal(HashiRefusal.Reason.ALREADY_RECEIVED)
        }
        return signAndBroadcast(
            account, record, HashiEthIntent.CLAIM, again.contract, BigInteger.ZERO, again.data, quote.fees,
            HashiTransferState.CLAIM_SUBMITTED, signedBefore = quote.signedBefore, confirmationsBefore = quote.confirmationsBefore,
        )
    }

    /**
     * The claim is paid on Ethereum. Written onto the transfer as it is saved now, never over a
     * copy read before the network call: a claim this wallet signed meanwhile (on another page)
     * keeps its step, so its nonce stays counted. Then it is DONE only once that claim is settled.
     */
    private suspend fun markAlreadyReceived(record: HashiTransferRecord) {
        transition(record) { current ->
            when {
                current.state.isFinal -> current
                // Our own claim is out: the monitor reads its receipt and finishes the transfer.
                current.ethSteps.any { it.state.isOpen } -> current
                else -> current.copy(state = HashiTransferState.DONE, error = "ALREADY_RECEIVED", failure = null)
            }
        }
    }

    private suspend fun planClaim(record: HashiTransferRecord): HashiClaimCall {
        val asset = resolveAsset(record.assetId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val requestHash = EthHex.decodeFixed(checkNotNull(record.requestHash), 32)
        val request = sora.approvedRequest(requestHash) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        val factor = sora.denominationFactor(asset.assetId)
        // SORA converted the amount at the denomination of the block that took the transfer. An
        // approval of exactly the Ethereum amount the person confirmed (saved with the transfer)
        // stays claimable after the denomination changes; any other amount must match today's.
        val expected = record.ethUnits.takeIf { it == request.amount }
            ?: HashiAmounts.expectedEthAmount(record.soraUnits, asset, factor)
        return try {
            HashiClaimPlanner.plan(asset, request, parseAddress(record.recipient), expected)
        } catch (mismatch: HashiClaimAmountMismatchException) {
            transition(record) { it.copy(approvedAmountMismatch = "${mismatch.expected}/${mismatch.approved}") }
            throw HashiRefusal(HashiRefusal.Reason.AMOUNT_MISMATCH, "${mismatch.expected}/${mismatch.approved}")
        } catch (error: IllegalArgumentException) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY, error.message)
        }
    }

    // --- Ethereum → SORA ----------------------------------------------------------------------

    suspend fun quoteIncoming(account: SoraAccount, assetId: String, amount: BigDecimal): IncomingQuote {
        requireOpen()
        val asset = resolveAsset(assetId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val ethAccount = accounts.address(account) ?: throw HashiRefusal(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT)
        val from = parseAddress(ethAccount)
        val factor = sora.denominationFactor(asset.assetId)
        if (amount.signum() <= 0) throw HashiRefusal(HashiRefusal.Reason.AMOUNT_INVALID)
        if (HashiAmounts.decimalsOf(amount) > asset.ethDecimals) throw HashiRefusal(HashiRefusal.Reason.TOO_MANY_DECIMALS)
        val ethAmount = HashiAmounts.incomingUnits(amount, asset, factor) ?: throw HashiRefusal(HashiRefusal.Reason.AMOUNT_INVALID)
        val soraAmount = checkNotNull(HashiAmounts.incomingSoraAmount(ethAmount, asset, factor))
        // The pallet keeps balances in u128: a larger credit would fail on SORA after the
        // Ethereum side already took the tokens.
        // The pallet keeps balances in u128: it divides the Ethereum amount by the denomination
        // factor and that must fit u128 before it converts the precision (InvalidAmount after
        // the Ethereum side already took the tokens otherwise).
        if (!HashiSoraCalls.isU128(ethAmount.divide(factor)) || !HashiSoraCalls.isU128(soraAmount)) {
            throw HashiRefusal(HashiRefusal.Reason.AMOUNT_INVALID)
        }
        requireIncomingAccepted(asset, soraAmount)
        val ethBalance = ethereum.balance(from)
        val baseFee = ethereum.baseFee()
        val priority = ethereum.priorityFee()
        var gas = BigInteger.ZERO
        var needsApproveZero = false
        var needsApprove = false
        if (asset.isNativeEther) {
            gas = HashiBridgeContract.GAS_SEND_ETH_TO_SIDECHAIN
            val fees = HashiEthereumPolicy.fees(baseFee, priority, gas)
            if (ethBalance < ethAmount.add(fees.maximumCost)) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_BALANCE)
        } else {
            if (ethereum.tokenBalance(asset.ethToken, from) < ethAmount) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_BALANCE)
            val allowance = ethereum.allowance(asset.ethToken, from, HashiBridgeContract.bridge)
            needsApprove = allowance < ethAmount
            needsApproveZero = needsApprove && allowance.signum() > 0
            if (needsApproveZero) gas = gas.add(HashiBridgeContract.GAS_APPROVE)
            if (needsApprove) gas = gas.add(HashiBridgeContract.GAS_APPROVE)
            gas = gas.add(HashiBridgeContract.GAS_SEND_ERC20_TO_SIDECHAIN)
        }
        val estimate = HashiEthereumPolicy.fees(baseFee, priority, gas).maximumCost
        if (ethBalance < estimate) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        return IncomingQuote(asset, ethAmount, soraAmount, needsApproveZero, needsApprove, estimate)
    }

    /**
     * The checks that decide whether SORA will credit a deposit, read from both chains. Run for
     * the quote and again right before each Ethereum step is signed (a deposit can be continued
     * hours later, and liquidity or the token's registration can change meanwhile).
     */
    private suspend fun requireIncomingAccepted(asset: HashiBridgeAsset, soraAmount: BigInteger) {
        when (asset.kind) {
            HashiAssetKind.SIDECHAIN -> if (!asset.isNativeEther && !ethereum.isAcceptedEthToken(asset.ethToken)) {
                throw HashiRefusal(HashiRefusal.Reason.TOKEN_NOT_ACCEPTED)
            }
            HashiAssetKind.THISCHAIN -> if (EthHex.encode(ethereum.sidechainAssetOfToken(asset.ethToken)) != asset.assetId) {
                throw HashiRefusal(HashiRefusal.Reason.TOKEN_NOT_ACCEPTED)
            }
            HashiAssetKind.SIDECHAIN_OWNED -> Unit
        }
        // Owned assets are unlocked on SORA: without the locked liquidity the credit fails while
        // the Ethereum side already burned or locked the tokens.
        if (asset.kind.isOwned && sora.lockedAssets(EthHex.decode(asset.assetId)) < soraAmount) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_ENOUGH_LOCKED_LIQUIDITY)
        }
    }

    /** The asset exactly as the person confirmed it: the token and decimals saved in [record]. */
    private fun assetOf(record: HashiTransferRecord, kind: HashiAssetKind): HashiBridgeAsset = HashiBridgeAsset(
        kind, record.assetId, record.soraPrecision, EthHex.decodeFixed(record.ethToken, EthAddress.SIZE), record.ethDecimals,
    )

    /**
     * [requireIncomingAccepted] for a saved deposit, against what both chains say now (read
     * again, not from this session's cache): the asset is registered with the same kind, token,
     * decimals and precision the person confirmed, and the saved Ethereum amount still credits
     * exactly the saved SORA amount (the denomination can change). Returns the confirmed asset.
     */
    private suspend fun requireIncomingStillCredited(record: HashiTransferRecord): HashiBridgeAsset {
        val registration = sora.registeredAssets().firstOrNull { it.assetId == record.assetId }
            ?: throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val asset = assetOf(record, registration.kind)
        val registeredToken = registration.ethToken
        val sameToken = when (registration.kind) {
            // A SORA-native asset's ERC-20 and its decimals come from the bridge contract.
            HashiAssetKind.THISCHAIN -> ethereum.sidechainToken(EthHex.decode(record.assetId))?.let { token ->
                EthAddress.equal(token, asset.ethToken) && ethereum.decimals(token) == record.ethDecimals
            } == true
            else -> registeredToken != null && EthAddress.equal(registeredToken, asset.ethToken) &&
                registration.ethDecimals == record.ethDecimals
        }
        if (!sameToken || registration.soraPrecision != record.soraPrecision) throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val factor = sora.denominationFactor(record.assetId)
        val credited = HashiAmounts.incomingSoraAmount(record.ethUnits, asset, factor)
        if (credited == null || credited != record.soraUnits || !HashiSoraCalls.isU128(record.ethUnits.divide(factor))) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE, "CREDIT_CHANGED")
        }
        requireIncomingAccepted(asset, record.soraUnits)
        return asset
    }

    /**
     * Starts a deposit the person confirmed with the PIN: approve 0 (when an older allowance
     * exists), approve the exact amount, then the deposit, each persisted and waited for. When the
     * wait is cut short the transfer stays at its step; the next step needs a new confirmation.
     */
    suspend fun startIncoming(account: SoraAccount, quote: IncomingQuote, symbol: String): HashiTransferRecord {
        requireOpen()
        val record = save(newIncoming(account, quote, symbol))
        if (!beginWork(record.id)) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        return try {
            continueIncomingSteps(account, record.id, waitForConfirmation = true)
        } finally {
            endWork(record.id)
        }
    }

    private suspend fun newIncoming(account: SoraAccount, quote: IncomingQuote, symbol: String): HashiTransferRecord {
        // One deposit per sheet, whoever hands it over a second time.
        if (!takenQuotes.add(quote.id)) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        // Derived again after the PIN (see prepareOutgoing): no deposit from a cached address.
        val ethAccount = accounts.verifiedAddress(account) ?: throw HashiRefusal(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT)
        requireNoOpenEthereumStep(ethAccount)
        return HashiTransferRecord(
            id = newId(),
            wallet = account.substrateAddress,
            direction = HashiDirection.ETHEREUM_TO_SORA,
            assetId = quote.asset.assetId,
            symbol = symbol,
            soraPrecision = quote.asset.soraPrecision,
            ethDecimals = quote.asset.ethDecimals,
            ethToken = EthHex.encode(quote.asset.ethToken),
            soraAmount = quote.soraAmount.toString(),
            ethAmount = quote.ethAmount.toString(),
            recipient = EthHex.encode(account.substrateAddress.toAccountId()),
            ethAccount = ethAccount,
            state = HashiTransferState.READY_TO_DEPOSIT,
            // What the confirmation sheet showed as the Ethereum gas: the steps are signed within it.
            gasBudget = quote.gasCostEstimate.toString(),
            createdAt = now(),
            updatedAt = now(),
        )
    }

    /**
     * [startIncoming] for a screen: the transfer is saved and returned at once, and its confirmed
     * steps run in [backgroundScope]. A refusal on the way is saved on the transfer.
     */
    suspend fun beginIncoming(account: SoraAccount, quote: IncomingQuote, symbol: String): HashiTransferRecord {
        requireOpen()
        val record = save(newIncoming(account, quote, symbol))
        runIncomingInBackground(account, record.id, confirmed = null)
        return record
    }

    /**
     * What continuing (or trying again) a deposit can cost at most, for its page to show before
     * the PIN: the steps the chain's allowance still asks for, at today's fees. Nothing is signed.
     */
    suspend fun quoteContinueIncoming(account: SoraAccount, recordId: String): IncomingStepsQuote {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        check(record.direction == HashiDirection.ETHEREUM_TO_SORA) { "HASHI_NOT_INCOMING" }
        if (record.state != HashiTransferState.READY_TO_DEPOSIT && record.state != HashiTransferState.ETHEREUM_STEP_FAILED) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        }
        requireRepeatable(record)
        requireOpen()
        val asset = requireIncomingStillCredited(record)
        val from = parseAddress(record.ethAccount)
        requireNoOpenEthereumStep(record.ethAccount)
        val baseFee = ethereum.baseFee()
        val priority = ethereum.priorityFee()
        val balance = ethereum.balance(from)
        val destination = EthHex.decodeFixed(record.recipient, 32)
        val cost = if (asset.isNativeEther) {
            val fees = HashiEthereumPolicy.fees(baseFee, priority, HashiBridgeContract.GAS_SEND_ETH_TO_SIDECHAIN)
            if (balance < record.ethUnits.add(fees.maximumCost)) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_BALANCE)
            fees.maximumCost
        } else {
            if (ethereum.tokenBalance(asset.ethToken, from) < record.ethUnits) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_BALANCE)
            val allowance = ethereum.allowance(asset.ethToken, from, HashiBridgeContract.bridge)
            if (allowance >= record.ethUnits) {
                // Only the deposit is left and the chain can price it now (a revert shows here).
                fees(from, HashiBridgeContract.bridge, HashiBridgeCalls.sendErc20ToSidechain(destination, record.ethUnits, asset.ethToken), BigInteger.ZERO)
                    .maximumCost
            } else {
                var gas = HashiBridgeContract.GAS_APPROVE.add(HashiBridgeContract.GAS_SEND_ERC20_TO_SIDECHAIN)
                if (allowance.signum() > 0) gas = gas.add(HashiBridgeContract.GAS_APPROVE)
                HashiEthereumPolicy.fees(baseFee, priority, gas).maximumCost
            }
        }
        if (balance < cost) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        return IncomingStepsQuote(cost, record.signedHashes, record.confirmations)
    }

    /**
     * [continueIncoming] for a screen, in [backgroundScope]. The transfer counts as [working] from
     * this call on, so its page never offers the same step twice. A second confirmation while it
     * runs does nothing.
     */
    fun continueIncomingInBackground(account: SoraAccount, recordId: String, quote: IncomingStepsQuote) =
        runIncomingInBackground(account, recordId, quote)

    private fun runIncomingInBackground(account: SoraAccount, recordId: String, confirmed: IncomingStepsQuote?) {
        if (!beginWork(recordId)) return
        backgroundScope.launch {
            try {
                runConfirmedIncoming(account, recordId, confirmed)
            } finally {
                endWork(recordId)
            }
            resumeMonitoring()
        }
    }

    /**
     * Makes [quote] the gas ceiling of the transfer's next steps: what its sheet showed. Refused
     * when anything was signed for the transfer since the sheet was shown (another screen's
     * confirmation was taken first): this one then signs nothing.
     */
    private suspend fun acceptConfirmation(account: SoraAccount, recordId: String, quote: IncomingStepsQuote) {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        var stale = false
        transition(record) { stored ->
            stale = stored.signedHashes != quote.signedBefore || stored.confirmations != quote.confirmationsBefore
            if (stale) stored else stored.copy(gasBudget = quote.gasCost.toString(), confirmations = stored.confirmations + 1)
        }
        if (stale) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
    }

    private suspend fun runConfirmedIncoming(account: SoraAccount, recordId: String, confirmed: IncomingStepsQuote?) {
        if (confirmed != null) {
            try {
                acceptConfirmation(account, recordId, confirmed)
            } catch (_: HashiRefusal) {
                // Another confirmation was taken first: this one does nothing, and says nothing on
                // a transfer that has moved on.
                return
            }
        }
        try {
            continueIncomingSteps(account, recordId, waitForConfirmation = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Nothing was signed for the refused step. The transfer keeps its place and says why.
            val refusal = error as? HashiRefusal
            try {
                store.get(account.substrateAddress, recordId)?.let { record ->
                    transition(record) { it.copy(refusal = (refusal?.reason ?: HashiRefusal.Reason.READ_FAILED).name, error = refusal?.detail) }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // This runs in the background with no screen to tell: the transfer stays as saved.
            }
        }
    }

    /**
     * Runs the next Ethereum steps of a deposit, after the person confirmed [quote] with the PIN.
     * Each step is chosen from the chain's current allowance, never from what was planned earlier,
     * and signed within the gas the sheet of [quote] showed.
     */
    suspend fun continueIncoming(
        account: SoraAccount,
        recordId: String,
        quote: IncomingStepsQuote,
        waitForConfirmation: Boolean,
    ): HashiTransferRecord {
        if (!beginWork(recordId)) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        return try {
            acceptConfirmation(account, recordId, quote)
            continueIncomingSteps(account, recordId, waitForConfirmation)
        } finally {
            endWork(recordId)
        }
    }

    private suspend fun continueIncomingSteps(account: SoraAccount, recordId: String, waitForConfirmation: Boolean): HashiTransferRecord {
        var record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        check(record.direction == HashiDirection.ETHEREUM_TO_SORA) { "HASHI_NOT_INCOMING" }
        requireRepeatable(record)
        requireOpen()
        // Every step uses the token the person confirmed, never a registration read later.
        var asset = requireIncomingStillCredited(record)
        val from = parseAddress(record.ethAccount)
        // "Try again" after the allowance never reached the amount: one more approval may be signed.
        var approveAgain = record.failure == HashiFailure.ALLOWANCE_NOT_SET
        var allowanceReads = 0
        repeat(MAX_INCOMING_ROUNDS) {
            if (record.ethSteps.any { it.state.isOpen }) {
                if (!waitForConfirmation) return record
                record = awaitOpenSteps(record)
                if (record.ethSteps.any { it.state.isOpen } || record.state == HashiTransferState.ETHEREUM_STEP_FAILED) return record
            }
            if (record.step(HashiEthIntent.DEPOSIT)?.state == HashiEthStepState.CONFIRMED) return record
            val amount = record.ethUnits
            val destination = EthHex.decodeFixed(record.recipient, 32)
            // Read again right before anything is signed: the bridge can pause and liquidity or
            // the token's registration can change while an approval is mined or overnight.
            requireOpen()
            asset = requireIncomingStillCredited(record)
            if (asset.isNativeEther) {
                return deposit(account, record, HashiBridgeContract.bridge, amount, HashiBridgeCalls.sendEthToSidechain(destination), waitForConfirmation)
            }
            // Read again right before each step.
            val allowance = ethereum.allowance(asset.ethToken, from, HashiBridgeContract.bridge)
            if (allowance >= amount) {
                return deposit(
                    account, record, HashiBridgeContract.bridge, BigInteger.ZERO,
                    HashiBridgeCalls.sendErc20ToSidechain(destination, amount, asset.ethToken), waitForConfirmation,
                )
            }
            val intent = if (allowance.signum() > 0) HashiEthIntent.APPROVE_ZERO else HashiEthIntent.APPROVE
            if (record.step(intent)?.state == HashiEthStepState.CONFIRMED && !approveAgain) {
                // This approval is mined but the node that answered does not show its allowance
                // yet (endpoints can be a block apart). Read again before concluding anything.
                allowanceReads += 1
                if (allowanceReads > MAX_ALLOWANCE_READS) {
                    // Onto the transfer as saved now: a step the monitor found meanwhile stays.
                    return transition(record) { current ->
                        if (current.state.isFinal || current.ethSteps.any { it.state.isOpen }) {
                            current
                        } else {
                            current.copy(state = HashiTransferState.ETHEREUM_STEP_FAILED, failure = HashiFailure.ALLOWANCE_NOT_SET, error = null)
                        }
                    }
                }
                delay(pollIntervalMillis)
                return@repeat
            }
            val approved = if (intent == HashiEthIntent.APPROVE_ZERO) BigInteger.ZERO else amount
            record = signAndBroadcast(
                account, record, intent, asset.ethToken, BigInteger.ZERO,
                HashiBridgeCalls.approve(HashiBridgeContract.bridge, approved), null,
                if (intent == HashiEthIntent.APPROVE_ZERO) HashiTransferState.APPROVE_ZERO_SUBMITTED else HashiTransferState.APPROVE_SUBMITTED,
                repeatConfirmed = approveAgain,
            )
            approveAgain = false
            allowanceReads = 0
            if (record.state == HashiTransferState.ETHEREUM_STEP_FAILED) return record
        }
        return record
    }

    private suspend fun deposit(
        account: SoraAccount,
        record: HashiTransferRecord,
        to: ByteArray,
        value: BigInteger,
        data: ByteArray,
        wait: Boolean,
    ): HashiTransferRecord {
        val sent = signAndBroadcast(
            account, record, HashiEthIntent.DEPOSIT, to, value, data, null, HashiTransferState.DEPOSIT_SUBMITTED,
        )
        return if (wait && sent.state == HashiTransferState.DEPOSIT_SUBMITTED) awaitOpenSteps(sent) else sent
    }

    /** Polls the open steps until each is confirmed or failed, or about 15 minutes passed. */
    private suspend fun awaitOpenSteps(start: HashiTransferRecord): HashiTransferRecord {
        var record = start
        repeat(MAX_WAIT_POLLS) {
            record = refreshRecord(record)
            if (record.ethSteps.none { it.state.isOpen }) return record
            delay(pollIntervalMillis)
        }
        return record
    }

    // --- Ethereum steps (spec §8 E) -------------------------------------------------------------

    private suspend fun fees(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger): EthFees =
        feesNow(from, to, data, value).first

    /** The fees of the call at today's prices, and the base fee they were made from. */
    private suspend fun feesNow(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger): Pair<EthFees, BigInteger> {
        val estimate = try {
            ethereum.estimateGas(from, to, data, value)
        } catch (revert: EthCallRevertedException) {
            throw HashiRefusal(HashiRefusal.Reason.CALL_REVERTED, revert.reason)
        }
        val baseFee = ethereum.baseFee()
        return HashiEthereumPolicy.fees(baseFee, ethereum.priorityFee(), estimate) to baseFee
    }

    private suspend fun signAndBroadcast(
        account: SoraAccount,
        record: HashiTransferRecord,
        intent: HashiEthIntent,
        to: ByteArray,
        value: BigInteger,
        data: ByteArray,
        quotedFees: EthFees?,
        nextState: HashiTransferState,
        /** An approval may be signed again after a confirmed one left the allowance too low. */
        repeatConfirmed: Boolean = false,
        /** With [quotedFees]: what was signed for the transfer when the quote's sheet was shown. */
        signedBefore: List<String>? = null,
        /** With [quotedFees]: [HashiTransferRecord.confirmations] when the quote's sheet was shown. */
        confirmationsBefore: Int? = null,
    ): HashiTransferRecord {
        val signed = ethereumSigning.withLock {
            signAndSave(account, record, intent, to, value, data, quotedFees, nextState, repeatConfirmed, signedBefore, confirmationsBefore)
        } ?: return record
        val (saved, step, raw) = signed
        return broadcast(saved, step, raw.raw, raw.hash)
    }

    /**
     * The signature and the save of one step, under [ethereumSigning]. Null when [intent] is
     * already confirmed and not to be repeated.
     *
     * What the step may cost is decided here, against the transfer as it is saved now, never
     * against what a screen still holds: a claim is signed with exactly [quotedFees] and only
     * while nothing was signed since their sheet was shown ([signedBefore]); a deposit's step is
     * signed within the Ether the person confirmed and no step has taken yet
     * ([HashiTransferRecord.gasBudget]). Otherwise nothing is signed.
     */
    private suspend fun signAndSave(
        account: SoraAccount,
        start: HashiTransferRecord,
        intent: HashiEthIntent,
        to: ByteArray,
        value: BigInteger,
        data: ByteArray,
        quotedFees: EthFees?,
        nextState: HashiTransferState,
        repeatConfirmed: Boolean,
        signedBefore: List<String>?,
        confirmationsBefore: Int?,
    ): Triple<HashiTransferRecord, HashiEthStep, SignedEthTransaction>? {
        val record = store.get(start.wallet, start.id) ?: start
        // Another confirmation of the same sheet was taken first: this one signs nothing.
        if (signedBefore != null && record.signedHashes != signedBefore) {
            throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        }
        if (confirmationsBefore != null && record.confirmations != confirmationsBefore) {
            throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
        }
        val from = parseAddress(record.ethAccount)
        // One open transaction per Ethereum account: public nodes do not all report a pending
        // transaction in the pending nonce, so a second one could take the first one's nonce.
        requireNoOpenEthereumStep(record.ethAccount)
        val previous = record.step(intent)
        if (previous != null) {
            check(!previous.state.isOpen) { "HASHI_STEP_STILL_OPEN" }
            if (previous.state == HashiEthStepState.CONFIRMED && !repeatConfirmed) return null
            // A deposit whose nonce another transaction took, or that was mined without the
            // bridge's event, is never signed again: without proof of which transaction used the
            // nonce a second deposit could pay twice. A new transfer is the person's decision.
            if (intent == HashiEthIntent.DEPOSIT && previous.state != HashiEthStepState.CONFIRMED &&
                (previous.failure == NONCE_USED_ELSEWHERE || previous.failure == EVENT_MISSING)
            ) {
                throw HashiRefusal(HashiRefusal.Reason.NOT_READY, "DEPOSIT_NOT_REPEATED")
            }
            // Never again while an earlier attempt is mined or still pending anywhere.
            if (previous.state != HashiEthStepState.CONFIRMED) requireNoAttemptMined(record, previous)
        }
        // The same intent at the SAME nonce while that nonce is still free (the node refused the
        // earlier bytes): at most one of them can ever be mined. A new nonce only once the old
        // one is consumed.
        // Each count is read once, under the signing lock: endpoints can answer differently from one
        // call to the next, and a nonce ruled out below must not come back through a second read.
        val latestCount = ethereum.latestNonce(from)
        val pendingCount = ethereum.pendingNonce(from)
        val usedFloor = usedNonceFloor(record.ethAccount)
        val unsettled = previous?.takeIf { it.state != HashiEthStepState.CONFIRMED }
        val sameNonce = unsettled?.let { old ->
            // Free means: not mined, nothing pending at it (another wallet's payment), not seen used.
            old.nonceValue.takeIf { latestCount <= it && pendingCount <= it && usedFloor <= it }
        }
        val budget = record.gasBudget?.let(::BigInteger)
        val fees = if (quotedFees != null) {
            quotedFees
        } else {
            // A deposit's step: within what the person confirmed and no earlier step has taken.
            val (now, baseFee) = feesNow(from, to, data, value)
            HashiEthereumPolicy.within(now, baseFee, budget ?: BigInteger.ZERO)
                ?: throw HashiRefusal(HashiRefusal.Reason.COST_CHANGED)
        }
        if (ethereum.balance(from) < value.add(fees.maximumCost)) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        // Read now, under the signing lock, never from a quote: another transaction of this
        // account may have taken a nonce since. Never below a nonce this wallet saw used (an
        // endpoint a block behind can still answer the old count).
        // A new nonce is past the old one when that one was found taken (by a payment pending there,
        // say), whatever a lagging endpoint counts.
        val nonce = sameNonce ?: listOfNotNull(pendingCount, usedFloor, unsettled?.nonceValue?.add(BigInteger.ONE)).max()
        val transaction = Eip1559Transaction(
            chainId = HashiBridgeContract.ETHEREUM_CHAIN_ID,
            nonce = nonce,
            maxPriorityFeePerGas = fees.maxPriorityFeePerGas,
            maxFeePerGas = fees.maxFeePerGas,
            gasLimit = fees.gasLimit,
            to = to,
            value = value,
            data = data,
        )
        val signed = accounts.withPrivateKey(account, record.ethAccount) { key -> transaction.sign(key, expectedFrom = from) }
        val step = HashiEthStep(
            intent = intent,
            nonce = nonce.toString(),
            to = EthHex.encode(to),
            value = value.toString(),
            data = EthHex.encode(data),
            gasLimit = fees.gasLimit.toString(),
            attempts = listOf(
                HashiEthAttempt(signed.rawHex, signed.hashHex, fees.maxFeePerGas.toString(), fees.maxPriorityFeePerGas.toString(), now())
            ),
            state = HashiEthStepState.SIGNED,
        )
        // Marked as being sent before the step becomes visible: the monitor must not send the same
        // bytes on its own between the save and this process's broadcast (see [broadcast]).
        inFlight += signed.hashHex
        // Saved before the broadcast: an unknown result is resolved by sending these bytes again.
        // Onto the transfer as it is saved now (the monitor may have written a status meanwhile);
        // what was signed for it cannot have changed, every signature is made under this lock.
        val saved = try {
            transition(record) { stored ->
                stored.withStep(step).copy(
                    state = nextState,
                    error = null,
                    failure = null,
                    refusal = null,
                    // What this step can cost is taken from what was confirmed.
                    gasBudget = if (quotedFees != null) stored.gasBudget else budget?.subtract(fees.maximumCost)?.max(BigInteger.ZERO)?.toString(),
                    // The sheet is used up with its signature, whatever the node says to the bytes.
                    confirmations = if (confirmationsBefore != null) stored.confirmations + 1 else stored.confirmations,
                )
            }
        } catch (error: Throwable) {
            inFlight -= signed.hashHex
            throw error
        }
        return Triple(saved, step, signed)
    }

    private fun HashiTransferRecord.stepLike(step: HashiEthStep): HashiEthStep? =
        ethSteps.lastOrNull { it.intent == step.intent && it.nonce == step.nonce }

    /**
     * Sends the saved bytes of [step]'s latest attempt. Every write here goes through
     * [transition], on the transfer as it is saved now.
     */
    private suspend fun broadcast(record: HashiTransferRecord, step: HashiEthStep, raw: ByteArray, hash: ByteArray): HashiTransferRecord {
        val attemptHash = EthHex.encode(hash)
        // While this send is out, the monitor does not send the same bytes on its own: an
        // answer to one of the two must never be read as the fate of the other.
        inFlight += attemptHash
        return try {
            ethereum.send(raw, hash)
            transition(record) { current ->
                val inStore = current.stepLike(step)
                if (inStore != null && inStore.state == HashiEthStepState.SIGNED) current.withStep(inStore.copy(state = HashiEthStepState.BROADCAST)) else current
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: HashiRpcErrorException) {
            when {
                // "nonce too low" can be this very transaction, mined after an earlier send whose
                // answer was lost: unknown, never "nothing is pending". The receipts decide.
                error.message.contains("nonce", ignoreCase = true) -> transition(record) { it }
                // A speed-up the node refused: nothing is removed or closed. Every attempt stays
                // watched (the monitor may already have seen one of them mined meanwhile).
                step.attempts.size > 1 -> {
                    // The node refused these new bytes and no other endpoint can have them (see
                    // EthereumRpcClient): drop them so the earlier, affordable bytes are the ones
                    // sent again. Nothing changes once the step is no longer open.
                    transition(record) { current ->
                        val inStore = current.stepLike(step)
                        val kept = inStore?.attempts?.filterNot { it.hash == attemptHash }.orEmpty()
                        if (inStore == null || !inStore.state.isOpen || kept.isEmpty() || inStore.confirmedHash == attemptHash) {
                            current
                        } else {
                            current.withStep(inStore.copy(attempts = kept, state = HashiEthStepState.BROADCAST))
                        }
                    }
                    throw HashiRefusal(HashiRefusal.Reason.CALL_REVERTED, error.message)
                }
                // The node refused the only attempt (funds, fee): nothing is pending.
                else -> transition(record) { current ->
                    val inStore = current.stepLike(step)
                    if (inStore == null || inStore.state != HashiEthStepState.SIGNED || inStore.attempts.size != 1) {
                        current
                    } else {
                        current.withStep(inStore.copy(state = HashiEthStepState.REPLACED, failure = error.message)).copy(
                            state = HashiTransferState.ETHEREUM_STEP_FAILED, failure = HashiFailure.ETHEREUM_REFUSED, error = error.message,
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Unknown: the saved bytes are sent again when the transfer is refreshed.
            transition(record) { it }
        } finally {
            inFlight -= attemptHash
        }
    }

    /** The fees of [step]'s latest attempt. */
    private fun feesOf(step: HashiEthStep): EthFees =
        EthFees(BigInteger(step.latest.maxPriorityFeePerGas), BigInteger(step.latest.maxFeePerGas), BigInteger(step.gasLimit))

    /**
     * A faster approval or deposit is still one: only while SORA would credit the deposit, and a
     * deposit only while the allowance still covers it.
     */
    private suspend fun requireStillWanted(record: HashiTransferRecord, step: HashiEthStep, from: ByteArray) {
        if (record.direction != HashiDirection.ETHEREUM_TO_SORA) return
        requireOpen()
        val asset = requireIncomingStillCredited(record)
        if (step.intent == HashiEthIntent.DEPOSIT && !asset.isNativeEther &&
            ethereum.allowance(asset.ethToken, from, HashiBridgeContract.bridge) < record.ethUnits
        ) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY, "ALLOWANCE_CHANGED")
        }
    }

    /**
     * What replacing the transfer's pending transaction with a faster one costs at most, for its
     * page to show before the PIN: the same transaction at the same nonce with both fees at least
     * 12.5 % higher, and never below what the network asks now. Nothing is signed.
     */
    suspend fun quoteSpeedUp(account: SoraAccount, recordId: String): SpeedUpQuote {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        val step = record.ethSteps.lastOrNull { it.state.isOpen } ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        val from = parseAddress(record.ethAccount)
        requireStillWanted(record, step, from)
        val old = feesOf(step)
        val current = HashiEthereumPolicy.fees(ethereum.baseFee(), ethereum.priorityFee(), old.gasLimit)
        val fees = HashiEthereumPolicy.speedUp(old, EthFees(current.maxPriorityFeePerGas, current.maxFeePerGas, old.gasLimit))
        check(HashiEthereumPolicy.isValidSpeedUp(old, fees)) { "HASHI_SPEED_UP_INVALID" }
        if (ethereum.balance(from) < BigInteger(step.value).add(fees.maximumCost)) {
            throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        }
        return SpeedUpQuote(step.intent, step.nonceValue, step.latest.hash, fees, record.confirmations)
    }

    /**
     * Replaces the pending transaction [quote] was made for with the same one at exactly the fees
     * its sheet showed. Called only after a PIN confirmation. Decided under [ethereumSigning]
     * against the transfer as it is saved now: when the transaction is no longer pending, or
     * another speed-up was signed since the sheet was shown, nothing is signed. So two
     * confirmations (two screens, a second tap) can never add up to more than one sheet showed.
     */
    suspend fun speedUp(account: SoraAccount, recordId: String, quote: SpeedUpQuote): HashiTransferRecord {
        val (saved, next, signed) = ethereumSigning.withLock {
            val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
            val step = record.ethSteps.lastOrNull { it.state.isOpen && it.intent == quote.intent && it.nonceValue == quote.nonce }
                ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
            if (step.latest.hash != quote.replaces || record.confirmations != quote.confirmationsBefore) {
                throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
            }
            val from = parseAddress(record.ethAccount)
            requireStillWanted(record, step, from)
            val fees = quote.fees
            check(HashiEthereumPolicy.isValidSpeedUp(feesOf(step), fees)) { "HASHI_SPEED_UP_INVALID" }
            val value = BigInteger(step.value)
            if (ethereum.balance(from) < value.add(fees.maximumCost)) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
            val transaction = Eip1559Transaction(
                HashiBridgeContract.ETHEREUM_CHAIN_ID, step.nonceValue, fees.maxPriorityFeePerGas, fees.maxFeePerGas,
                fees.gasLimit, EthHex.decode(step.to), value, EthHex.decode(step.data),
            )
            val signed = accounts.withPrivateKey(account, record.ethAccount) { key -> transaction.sign(key, expectedFrom = from) }
            val attempt = HashiEthAttempt(signed.rawHex, signed.hashHex, fees.maxFeePerGas.toString(), fees.maxPriorityFeePerGas.toString(), now())
            // Added to the step as it is saved now: if the monitor found it mined (or replaced)
            // during the reads above, the new attempt is dropped unsent.
            var added: HashiEthStep? = null
            inFlight += signed.hashHex
            val saved = try {
                transition(record) { stored ->
                    val inStore = stored.stepLike(step)
                    if (inStore == null || !inStore.state.isOpen || inStore.latest.hash != quote.replaces ||
                        stored.confirmations != quote.confirmationsBefore
                    ) {
                        stored
                    } else {
                        inStore.copy(attempts = inStore.attempts + attempt, state = HashiEthStepState.SIGNED).let { next ->
                            added = next
                            // The sheet is used up with its signature, even if the node refuses the bytes.
                            stored.withStep(next).copy(confirmations = stored.confirmations + 1)
                        }
                    }
                }
            } catch (error: Throwable) {
                inFlight -= signed.hashHex
                throw error
            }
            if (added == null) inFlight -= signed.hashHex
            Triple(saved, added, signed)
        }
        return broadcast(saved, next ?: return saved, signed.raw, signed.hash)
    }

    // --- Monitoring (never signs) ---------------------------------------------------------------

    /**
     * Keeps reading every transfer that is not final, of every account, until none is left: after
     * the app starts, when the bridge screen opens, and after each confirmed step. It only reads
     * (and re-sends saved bytes whose broadcast is unknown); it never signs. One loop at a time.
     */
    fun resumeMonitoring() {
        if (!backgroundMonitoring || !monitoring.compareAndSet(false, true)) return
        backgroundScope.launch {
            var again = false
            try {
                var round = 0
                while (refreshOpen()) {
                    delay(monitorDelayMillis(round))
                    round += 1
                }
            } finally {
                monitoring.set(false)
                // A transfer that began while the loop was ending is picked up by a new loop.
                again = try {
                    store.openTransfers().isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }
            if (again) resumeMonitoring()
        }
    }

    /** One read of every transfer that is not final. False when there is none. */
    suspend fun refreshOpen(): Boolean {
        val open = try {
            store.openTransfers()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return false
        }
        open.forEach { record ->
            try {
                refreshRecord(record)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Read again on the next round.
            }
        }
        return open.isNotEmpty()
    }

    /** Every few seconds at first, then slower: a bridge that is down can take days. */
    private fun monitorDelayMillis(round: Int): Long = when {
        round < MONITOR_FAST_ROUNDS -> pollIntervalMillis
        round < MONITOR_FAST_ROUNDS + MONITOR_MEDIUM_ROUNDS -> pollIntervalMillis * 5
        else -> pollIntervalMillis * 20
    }

    suspend fun refresh(account: SoraAccount, recordId: String): HashiTransferRecord? {
        val record = store.get(account.substrateAddress, recordId) ?: return null
        return refreshRecord(record)
    }

    /** Refreshes every transfer that is not final. Failures leave a transfer as it was. */
    suspend fun refreshAll(account: SoraAccount) {
        store.transfers(account.substrateAddress).filterNot { it.state.isFinal }.forEach { record ->
            try {
                refreshRecord(record)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Read again on the next refresh.
            }
        }
    }

    private suspend fun refreshRecord(start: HashiTransferRecord): HashiTransferRecord = mutex.withLock {
        val record = store.get(start.wallet, start.id) ?: start
        var next = record
        record.ethSteps.filter { it.state.isOpen }.forEach { step -> next = watchStep(next, step) }
        // A deposit given up because its nonce was seen used with no receipt of ours is still
        // looked up (receipts only, nothing is sent): a receipt that shows up late settles it, so a
        // deposit SORA credits is never left "could not be confirmed".
        record.ethSteps
            .filter { it.intent == HashiEthIntent.DEPOSIT && it.state == HashiEthStepState.REPLACED && it.failure == NONCE_USED_ELSEWHERE }
            .forEach { step -> next = settleByReceipt(next, step) }
        next = when (next.direction) {
            HashiDirection.SORA_TO_ETHEREUM -> refreshOutgoing(next)
            HashiDirection.ETHEREUM_TO_SORA -> refreshIncoming(next)
        }
        if (next != record) saveUnlocked(next) else next
    }

    private suspend fun refreshOutgoing(record: HashiTransferRecord): HashiTransferRecord {
        when (record.state) {
            HashiTransferState.SORA_SIGNED, HashiTransferState.SORA_SUBMITTED -> {
                val notSent = record.copy(state = HashiTransferState.FAILED, failure = HashiFailure.NOT_SENT)
                val hash = record.soraExtrinsicHash ?: return notSent
                if (record.soraBlockHash != null && record.requestHash == null) {
                    val requestHash = sora.requestHashOf(record.soraBlockHash, hash) ?: return record
                    return record.copy(state = HashiTransferState.WAITING_APPROVALS, requestHash = EthHex.encode(requestHash), error = null)
                }
                val outcome = sora.submissionOutcome(record.wallet, hash)
                if (outcome == null) {
                    // The wallet's journal takes a transaction before it is handed to the network.
                    // No journal entry therefore means it was never sent: the app stopped between
                    // signing and sending. While this process is still about to send it, wait.
                    // In memory only, so no clock change can end it early; a new process starts empty.
                    return if (record.id in submitting) record else notSent
                }
                return when (outcome) {
                    is SoraSubmission.Included -> {
                        val requestHash = sora.requestHashOf(outcome.blockHash, hash)
                        if (requestHash == null) {
                            record.copy(soraBlockHash = outcome.blockHash, state = HashiTransferState.SORA_SUBMITTED)
                        } else {
                            record.copy(
                                state = HashiTransferState.WAITING_APPROVALS,
                                soraBlockHash = outcome.blockHash,
                                requestHash = EthHex.encode(requestHash),
                                error = null,
                            )
                        }
                    }
                    is SoraSubmission.FailedOnChain -> record.copy(
                        state = HashiTransferState.FAILED, failure = HashiFailure.FAILED_ON_SORA, error = outcome.detail,
                    )
                    is SoraSubmission.NotSent -> notSent.copy(error = outcome.detail)
                    SoraSubmission.Pending -> record.copy(state = HashiTransferState.SORA_SUBMITTED)
                }
            }
            HashiTransferState.WAITING_APPROVALS,
            HashiTransferState.PAUSED_BY_BRIDGE,
            HashiTransferState.NEEDS_OPERATORS,
            HashiTransferState.READY_TO_CLAIM,
            HashiTransferState.ETHEREUM_STEP_FAILED -> {
                val requestHash = EthHex.decodeFixed(record.requestHash ?: return record, 32)
                val value = sora.requestStatus(requestHash) ?: return record
                val withStatus = record.copy(requestStatus = value.status.wireName)
                return when (value.status) {
                    HashiRequestStatus.APPROVALS_READY, HashiRequestStatus.DONE -> {
                        // used() on the claim contract: VAL is minted by the VAL master.
                        val contract = if (record.assetId == HashiBridgeContract.VAL_ASSET_ID) {
                            HashiBridgeContract.valMaster
                        } else {
                            HashiBridgeContract.bridge
                        }
                        when {
                            ethereum.isUsed(contract, requestHash) -> withStatus.copy(
                                state = HashiTransferState.DONE,
                                error = if (record.step(HashiEthIntent.CLAIM)?.state == HashiEthStepState.CONFIRMED) null else "ALREADY_RECEIVED",
                            )
                            record.state == HashiTransferState.ETHEREUM_STEP_FAILED -> withStatus
                            else -> withStatus.copy(state = HashiTransferState.READY_TO_CLAIM)
                        }
                    }
                    HashiRequestStatus.PENDING -> withStatus.copy(state = HashiTransferState.WAITING_APPROVALS)
                    // Not final: Frozen can return to ApprovalsReady, Broken waits for operators.
                    HashiRequestStatus.FROZEN -> withStatus.copy(state = HashiTransferState.PAUSED_BY_BRIDGE)
                    HashiRequestStatus.BROKEN -> withStatus.copy(state = HashiTransferState.NEEDS_OPERATORS, error = value.raw)
                    HashiRequestStatus.FAILED -> withStatus.copy(
                        state = HashiTransferState.FAILED, failure = HashiFailure.FAILED_BY_BRIDGE, error = value.raw,
                    )
                }
            }
            HashiTransferState.CLAIM_SUBMITTED -> {
                val claim = record.step(HashiEthIntent.CLAIM) ?: return record
                return when (claim.state) {
                    HashiEthStepState.CONFIRMED -> record.copy(state = HashiTransferState.DONE, error = null, failure = null)
                    HashiEthStepState.REVERTED, HashiEthStepState.REPLACED -> record.copy(
                        state = HashiTransferState.ETHEREUM_STEP_FAILED, failure = failureOf(claim), error = claim.failure,
                    )
                    else -> record
                }
            }
            else -> return record
        }
    }

    private suspend fun refreshIncoming(record: HashiTransferRecord): HashiTransferRecord {
        val deposit = record.step(HashiEthIntent.DEPOSIT)
        val failed = record.ethSteps.lastOrNull { it.state == HashiEthStepState.REVERTED || it.state == HashiEthStepState.REPLACED }
        return when {
            deposit?.state == HashiEthStepState.CONFIRMED -> {
                val depositHash = EthHex.decodeFixed(checkNotNull(deposit.confirmedHash), 32)
                // A late receipt can confirm a deposit that was shown as unconfirmed.
                val settled = if (record.failure == HashiFailure.DEPOSIT_UNCONFIRMED) record.copy(failure = null, error = null) else record
                // Done only when the INCOMING TRANSFER request is Done (spec §8 H); the load
                // request can be Done while the transfer is still pending.
                val incoming = settled.incomingRequestHash?.let { EthHex.decodeFixed(it, 32) } ?: sora.incomingRequestHash(depositHash)
                if (incoming == null) {
                    settled.copy(state = HashiTransferState.WAITING_SORA)
                } else {
                    val value = sora.requestStatus(incoming)
                    val base = settled.copy(incomingRequestHash = EthHex.encode(incoming), requestStatus = value?.status?.wireName)
                    when (value?.status) {
                        HashiRequestStatus.DONE -> base.copy(state = HashiTransferState.DONE, error = null, failure = null)
                        HashiRequestStatus.FAILED -> base.copy(
                            state = HashiTransferState.FAILED, failure = HashiFailure.FAILED_BY_BRIDGE, error = value.raw,
                        )
                        HashiRequestStatus.FROZEN -> base.copy(state = HashiTransferState.PAUSED_BY_BRIDGE)
                        HashiRequestStatus.BROKEN -> base.copy(state = HashiTransferState.NEEDS_OPERATORS, error = value.raw)
                        else -> base.copy(state = HashiTransferState.WAITING_SORA)
                    }
                }
            }
            record.ethSteps.any { it.state.isOpen } -> record
            failed != null && record.ethSteps.last().state.let { it == HashiEthStepState.REVERTED || it == HashiEthStepState.REPLACED } ->
                record.copy(
                    state = HashiTransferState.ETHEREUM_STEP_FAILED,
                    failure = failureOf(record.ethSteps.last()),
                    error = record.ethSteps.last().failure,
                )
            // After a confirmed approval the deposit waits for the person, unless the approval was
            // found not to have set the allowance (then "Try again" is what is offered).
            record.ethSteps.isNotEmpty() && record.ethSteps.last().state == HashiEthStepState.CONFIRMED &&
                record.ethSteps.last().intent != HashiEthIntent.DEPOSIT &&
                record.failure != HashiFailure.ALLOWANCE_NOT_SET -> record.copy(state = HashiTransferState.READY_TO_DEPOSIT)
            else -> record
        }
    }

    /**
     * Reads the receipts of every attempt of an open step. A receipt with status 1 and the
     * contract's event confirms it; status 0 means it reverted. No receipt while the nonce is
     * already used means another transaction took the nonce. Otherwise the saved bytes of the
     * latest attempt are sent again (the same transaction; never a new signature).
     */
    private suspend fun watchStep(record: HashiTransferRecord, step: HashiEthStep): HashiTransferRecord {
        for (attempt in step.attempts.asReversed()) {
            val receipt = ethereum.receipt(EthHex.decodeFixed(attempt.hash, 32)) ?: continue
            val proven = receipt.status == 1 && proves(record, step, receipt)
            val next = when {
                proven -> step.copy(state = HashiEthStepState.CONFIRMED, confirmedHash = attempt.hash, failure = null)
                receipt.status == 1 -> step.copy(state = HashiEthStepState.REVERTED, confirmedHash = attempt.hash, failure = EVENT_MISSING)
                else -> step.copy(state = HashiEthStepState.REVERTED, confirmedHash = attempt.hash, failure = "REVERTED")
            }
            return record.withStep(next)
        }
        val from = parseAddress(record.ethAccount)
        val key = step.latest.hash
        if (ethereum.latestNonce(from) > step.nonceValue) {
            // Receipts of ours are looked up first, so a used nonce here is another transaction.
            // It must be seen for a while before it counts: two endpoints can briefly disagree,
            // and a step marked replaced may be signed again at a new nonce.
            val firstSeen = nonceUsedSince.getOrPut(key) { now() }
            return if (now() - firstSeen >= REPLACED_AFTER_MILLIS) {
                nonceUsedSince.remove(key)
                record.withStep(step.copy(state = HashiEthStepState.REPLACED, failure = NONCE_USED_ELSEWHERE))
            } else {
                record
            }
        }
        nonceUsedSince.remove(key)
        if (key in inFlight) return record
        // The same bytes again: at once when the first broadcast's result is unknown, otherwise
        // at most once a minute while no receipt shows up.
        val last = rebroadcastAt[key]
        if (step.state == HashiEthStepState.BROADCAST && last != null && now() - last < REBROADCAST_MILLIS) return record
        if (step.state == HashiEthStepState.BROADCAST && last == null) {
            rebroadcastAt[key] = now()
            return record
        }
        rebroadcastAt[key] = now()
        return try {
            ethereum.send(EthHex.decode(step.latest.raw), EthHex.decodeFixed(step.latest.hash, 32))
            record.withStep(step.copy(state = HashiEthStepState.BROADCAST))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            record
        }
    }

    /** [step] confirmed or reverted by a receipt of one of its attempts, else unchanged. Never sends. */
    private suspend fun settleByReceipt(record: HashiTransferRecord, step: HashiEthStep): HashiTransferRecord {
        for (attempt in step.attempts.asReversed()) {
            val receipt = ethereum.receipt(EthHex.decodeFixed(attempt.hash, 32)) ?: continue
            val next = when {
                receipt.status == 1 && proves(record, step, receipt) -> step.copy(state = HashiEthStepState.CONFIRMED, confirmedHash = attempt.hash, failure = null)
                receipt.status == 1 -> step.copy(state = HashiEthStepState.REVERTED, confirmedHash = attempt.hash, failure = EVENT_MISSING)
                else -> step.copy(state = HashiEthStepState.REVERTED, confirmedHash = attempt.hash, failure = "REVERTED")
            }
            return record.withStep(next)
        }
        return record
    }

    /**
     * Before the same step is signed again: no earlier attempt of it may be mined with status 1
     * or still pending anywhere. Every endpoint is asked (at least two must answer): one that
     * lags must not make a mined deposit look absent. A mined or pending attempt puts the step
     * back under watch, and nothing new is signed.
     */
    private suspend fun requireNoAttemptMined(record: HashiTransferRecord, step: HashiEthStep) {
        // Newest first; the first live attempt decides (a later read failing must not hide it).
        val live = step.attempts.asReversed().any { attempt ->
            when (val found = ethereum.lookupEverywhere(EthHex.decodeFixed(attempt.hash, 32))) {
                EthTransactionLookup.Pending -> true
                is EthTransactionLookup.Mined -> found.status == 1
                EthTransactionLookup.Unknown -> false
            }
        }
        if (!live) return
        transition(record) { current ->
            val inStore = current.stepLike(step)
            if (inStore != null && (inStore.state == HashiEthStepState.REPLACED || inStore.state == HashiEthStepState.REVERTED)) {
                current.withStep(inStore.copy(state = HashiEthStepState.BROADCAST, failure = null, confirmedHash = null))
                    .copy(
                        state = current.state.takeUnless { it == HashiTransferState.ETHEREUM_STEP_FAILED } ?: stepState(inStore.intent),
                        failure = null,
                    )
            } else {
                current
            }
        }
        throw HashiRefusal(HashiRefusal.Reason.NOT_READY, "ATTEMPT_MINED_OR_PENDING")
    }

    /** The transfer state while a step of [intent] is open. */
    private fun stepState(intent: HashiEthIntent): HashiTransferState = when (intent) {
        HashiEthIntent.APPROVE_ZERO -> HashiTransferState.APPROVE_ZERO_SUBMITTED
        HashiEthIntent.APPROVE -> HashiTransferState.APPROVE_SUBMITTED
        HashiEthIntent.DEPOSIT -> HashiTransferState.DEPOSIT_SUBMITTED
        HashiEthIntent.CLAIM -> HashiTransferState.CLAIM_SUBMITTED
    }

    /**
     * One past the highest nonce of [ethAccount] this wallet saw used on the chain, or 0: by a
     * receipt of its own (mined or reverted), or by another transaction that took a step's place.
     * Read from the saved transfers of every SORA account whose Ethereum account this is (accounts
     * made from one phrase share it), final ones included, so it also holds after a restart.
     */
    private suspend fun usedNonceFloor(ethAccount: String): BigInteger =
        store.allTransfers()
            .filter { it.ethAccount.equals(ethAccount, ignoreCase = true) }
            .flatMap { it.ethSteps }
            .filter {
                it.state == HashiEthStepState.CONFIRMED || it.state == HashiEthStepState.REVERTED ||
                    (it.state == HashiEthStepState.REPLACED && it.failure == NONCE_USED_ELSEWHERE)
            }
            .maxOfOrNull { it.nonceValue.add(BigInteger.ONE) } ?: BigInteger.ZERO

    /**
     * Applies [change] to the transfer as it is saved now, under the store lock. Every write after
     * a network call goes through here, so a slower call can never put back an older copy over
     * what the monitor found meanwhile (a mined step, a new attempt).
     */
    private suspend fun transition(record: HashiTransferRecord, change: (HashiTransferRecord) -> HashiTransferRecord): HashiTransferRecord =
        mutex.withLock {
            val current = store.get(record.wallet, record.id) ?: record
            val next = change(current)
            if (next == current) current else saveUnlocked(next)
        }

    private fun failureOf(step: HashiEthStep): HashiFailure = when {
        step.state == HashiEthStepState.REVERTED && step.failure == EVENT_MISSING -> HashiFailure.ETHEREUM_UNPROVEN
        step.intent == HashiEthIntent.DEPOSIT && step.failure == NONCE_USED_ELSEWHERE -> HashiFailure.DEPOSIT_UNCONFIRMED
        step.state == HashiEthStepState.REVERTED -> HashiFailure.ETHEREUM_REVERTED
        step.failure == NONCE_USED_ELSEWHERE -> HashiFailure.ETHEREUM_REPLACED
        else -> HashiFailure.ETHEREUM_REFUSED
    }

    private suspend fun proves(record: HashiTransferRecord, step: HashiEthStep, receipt: EthReceipt): Boolean = when (step.intent) {
        HashiEthIntent.APPROVE_ZERO, HashiEthIntent.APPROVE -> true
        HashiEthIntent.DEPOSIT -> HashiReceiptProof.provesDeposit(
            receipt,
            destination = EthHex.decodeFixed(record.recipient, 32),
            amount = record.ethUnits,
            token = EthHex.decodeFixed(record.ethToken, EthAddress.SIZE),
        )
        HashiEthIntent.CLAIM -> HashiReceiptProof.provesClaim(receipt, claimProofOf(record))
    }

    /** The parts of the claim its receipt must show, from the saved transfer. */
    private fun claimProofOf(record: HashiTransferRecord): HashiClaimCall {
        val isVal = record.assetId == HashiBridgeContract.VAL_ASSET_ID
        return HashiClaimCall(
            method = if (isVal) HashiClaimCall.Method.MINT_TOKENS_BY_PEERS else HashiClaimCall.Method.RECEIVE_BY_ETHEREUM_ASSET_ADDRESS,
            contract = if (isVal) HashiBridgeContract.valMaster else HashiBridgeContract.bridge,
            data = ByteArray(0),
            txHash = EthHex.decodeFixed(checkNotNull(record.requestHash), 32),
            amount = record.ethUnits,
            recipient = parseAddress(record.recipient),
            token = EthHex.decodeFixed(record.ethToken, EthAddress.SIZE),
        )
    }

    // --- Helpers ----------------------------------------------------------------------------

    private fun beginWork(recordId: String): Boolean {
        var taken = false
        _working.update { current ->
            taken = recordId !in current
            current + recordId
        }
        return taken
    }

    private fun endWork(recordId: String) = _working.update { it - recordId }

    /**
     * A bridge transfer of this wallet whose SORA submission is still unresolved blocks the next
     * one, so the same transfer is never sent twice by mistake (after a restart, a lost
     * connection, or an impatient second tap). Its result is read again first.
     */
    private suspend fun requireNoUnresolvedSubmission(wallet: String) {
        fun unresolved(records: List<HashiTransferRecord>) = records.filter { it.state.isSoraUnresolved && it.soraBlockHash == null }
        val pending = unresolved(store.transfers(wallet))
        if (pending.isEmpty()) return
        pending.forEach { record ->
            try {
                refreshRecord(record)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Still unresolved.
            }
        }
        if (unresolved(store.transfers(wallet)).isNotEmpty()) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
    }

    /**
     * No Ethereum transaction of [ethAccount] may be open, in any transfer of any SORA account
     * that shares it, when a new one is signed.
     */
    private suspend fun requireNoOpenEthereumStep(ethAccount: String) {
        // A saved transfer that cannot be read could hold an open transaction of this account.
        if (!store.isComplete()) throw HashiRefusal(HashiRefusal.Reason.READ_FAILED, "HASHI_STORE_UNREADABLE")
        val open = store.openTransfers().any { record ->
            record.ethAccount.equals(ethAccount, ignoreCase = true) && record.ethSteps.any { it.state.isOpen }
        }
        if (open) throw HashiRefusal(HashiRefusal.Reason.EARLIER_TRANSACTION_PENDING)
    }

    private suspend fun requireOpen() {
        val availability = sora.availability()
        if (!availability.newTransfersAllowed) throw HashiRefusal(HashiRefusal.Reason.PAUSED, availability.blocks.joinToString())
    }

    private fun outgoingUnits(amount: BigDecimal, asset: HashiBridgeAsset): BigInteger {
        if (amount.signum() <= 0) throw HashiRefusal(HashiRefusal.Reason.AMOUNT_INVALID)
        if (HashiAmounts.decimalsOf(amount) > HashiAmounts.outgoingScale(asset)) throw HashiRefusal(HashiRefusal.Reason.TOO_MANY_DECIMALS)
        val units = HashiAmounts.outgoingUnits(amount, asset) ?: throw HashiRefusal(HashiRefusal.Reason.AMOUNT_INVALID)
        if (!HashiSoraCalls.isU128(units)) throw HashiRefusal(HashiRefusal.Reason.AMOUNT_INVALID)
        return units
    }

    private fun parseAddress(address: String): ByteArray = EthAddress.parse(address) ?: error("HASHI_ADDRESS_INVALID")

    private suspend fun save(record: HashiTransferRecord): HashiTransferRecord = mutex.withLock { saveUnlocked(record) }

    private suspend fun saveUnlocked(record: HashiTransferRecord): HashiTransferRecord {
        val stamped = record.copy(updatedAt = now())
        store.put(stamped)
        return stamped
    }

    private companion object {
        const val MAX_WAIT_POLLS = 150
        const val MAX_INCOMING_ROUNDS = 16
        const val MAX_ALLOWANCE_READS = 5
        /** Ten minutes: an endpoint whose transaction index lags must not make a mined step look replaced. */
        const val REPLACED_AFTER_MILLIS = 10 * 60_000L
        const val REBROADCAST_MILLIS = 60_000L
        const val NONCE_USED_ELSEWHERE = "NONCE_USED_ELSEWHERE"
        const val EVENT_MISSING = "EVENT_MISSING"
        const val MONITOR_FAST_ROUNDS = 50
        const val MONITOR_MEDIUM_ROUNDS = 60
    }
}
