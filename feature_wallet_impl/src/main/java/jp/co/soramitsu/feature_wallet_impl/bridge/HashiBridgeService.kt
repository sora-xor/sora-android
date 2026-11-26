package jp.co.soramitsu.feature_wallet_impl.bridge

import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.ethereum.Eip1559Transaction
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.EthCallRevertedException
import jp.co.soramitsu.common.hashi.EthFees
import jp.co.soramitsu.common.hashi.EthReceipt
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
}

class IncomingQuote(
    val asset: HashiBridgeAsset,
    val ethAmount: BigInteger,
    val soraAmount: BigInteger,
    val needsApproveZero: Boolean,
    val needsApprove: Boolean,
    /** Upper bound of the Ether all steps can cost. */
    val gasCostEstimate: BigInteger,
)

class ClaimQuote(val claim: HashiClaimCall, val fees: EthFees, val nonce: BigInteger)

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
    /** Runs confirmed multi-step deposits to the end even when the screen that started them closes. */
    private val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val mutex = Mutex()
    private val nonceUsedSince = mutableMapOf<String, Long>()
    private val rebroadcastAt = mutableMapOf<String, Long>()
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

    // --- SORA → Ethereum ----------------------------------------------------------------------

    suspend fun quoteOutgoing(account: SoraAccount, assetId: String, amount: BigDecimal, recipient: String): OutgoingQuote {
        requireOpen()
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
     * Signs and sends the SORA transfer. Called only after the person confirmed it with the PIN.
     * Availability is read again; the signed extrinsic is applied to the current state first and
     * nothing is broadcast when that fails; its hash is saved before the single submission.
     */
    suspend fun sendOutgoing(account: SoraAccount, quote: OutgoingQuote, symbol: String): HashiTransferRecord {
        requireOpen()
        val ethAccount = accounts.address(account) ?: throw HashiRefusal(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT)
        check(HashiSoraCalls.isU128(quote.soraAmount)) { "HASHI_AMOUNT_RANGE_INVALID" }
        val prepared = sora.signTransfer(account, quote.asset.assetId, quote.recipient, quote.soraAmount)
        var record = HashiTransferRecord(
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
        store.put(record)
        val dryRun = try {
            sora.dryRun(prepared)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            save(record.copy(state = HashiTransferState.FAILED, error = "DRY_RUN_UNAVAILABLE"))
            throw HashiRefusal(HashiRefusal.Reason.READ_FAILED, "DRY_RUN_UNAVAILABLE")
        }
        if (dryRun !is HashiDryRunResult.Ok) {
            val detail = when (dryRun) {
                is HashiDryRunResult.DispatchFailed -> dryRun.error
                is HashiDryRunResult.Invalid -> dryRun.error
                else -> null
            }
            save(record.copy(state = HashiTransferState.FAILED, error = detail))
            throw HashiRefusal(HashiRefusal.Reason.DRY_RUN_FAILED, detail)
        }
        record = save(record.copy(state = HashiTransferState.SORA_SUBMITTED))
        return applySubmission(record, sora.submit(prepared, quote.soraFee))
    }

    private suspend fun applySubmission(record: HashiTransferRecord, outcome: SoraSubmission): HashiTransferRecord = when (outcome) {
        is SoraSubmission.Included -> {
            val requestHash = sora.requestHashOf(outcome.blockHash, checkNotNull(record.soraExtrinsicHash))
            if (requestHash == null) {
                save(record.copy(state = HashiTransferState.SORA_SUBMITTED, soraBlockHash = outcome.blockHash, error = "REQUEST_HASH_MISSING"))
            } else {
                save(
                    record.copy(
                        state = HashiTransferState.WAITING_APPROVALS,
                        soraBlockHash = outcome.blockHash,
                        requestHash = EthHex.encode(requestHash),
                        error = null,
                    )
                )
            }
        }
        is SoraSubmission.FailedOnChain -> save(record.copy(state = HashiTransferState.FAILED, error = outcome.detail ?: "FAILED_ON_CHAIN"))
        is SoraSubmission.NotSent -> save(record.copy(state = HashiTransferState.FAILED, error = outcome.detail ?: "NOT_SENT"))
        SoraSubmission.Pending -> save(record.copy(state = HashiTransferState.SORA_SUBMITTED))
    }

    /** Prepares the claim of an approved withdrawal; available while new transfers are paused. */
    suspend fun quoteClaim(account: SoraAccount, recordId: String): ClaimQuote {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        check(record.direction == HashiDirection.SORA_TO_ETHEREUM) { "HASHI_NOT_OUTGOING" }
        if (record.state != HashiTransferState.READY_TO_CLAIM && record.state != HashiTransferState.ETHEREUM_STEP_FAILED) {
            throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        }
        val claim = planClaim(record)
        if (ethereum.isUsed(claim.contract, claim.txHash)) {
            save(record.copy(state = HashiTransferState.DONE, error = "ALREADY_RECEIVED"))
            throw HashiRefusal(HashiRefusal.Reason.ALREADY_RECEIVED)
        }
        val from = parseAddress(record.ethAccount)
        if (claim.token != null && claim.token.contentEquals(EthAddress.ZERO) && !ethereum.hasNoCode(claim.recipient)) {
            throw HashiRefusal(HashiRefusal.Reason.RECIPIENT_HAS_CODE)
        }
        val fees = fees(from, claim.contract, claim.data, BigInteger.ZERO)
        if (ethereum.balance(from) < fees.maximumCost) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        return ClaimQuote(claim, fees, ethereum.pendingNonce(from))
    }

    /** Signs and broadcasts the claim. Called only after the person confirmed it with the PIN. */
    suspend fun sendClaim(account: SoraAccount, recordId: String, quote: ClaimQuote): HashiTransferRecord {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        if (record.step(HashiEthIntent.CLAIM)?.state?.isOpen == true) throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        // The same claim at a new nonce only when the old nonce was consumed by another
        // transaction or ours reverted (the step is then REPLACED or REVERTED, never open).
        val again = planClaim(record)
        check(again.data.contentEquals(quote.claim.data)) { "HASHI_CLAIM_CHANGED" }
        if (ethereum.isUsed(again.contract, again.txHash)) {
            save(record.copy(state = HashiTransferState.DONE, error = "ALREADY_RECEIVED"))
            throw HashiRefusal(HashiRefusal.Reason.ALREADY_RECEIVED)
        }
        return signAndBroadcast(
            account, record, HashiEthIntent.CLAIM, again.contract, BigInteger.ZERO, again.data, quote.fees, quote.nonce,
            HashiTransferState.CLAIM_SUBMITTED,
        )
    }

    private suspend fun planClaim(record: HashiTransferRecord): HashiClaimCall {
        val asset = resolveAsset(record.assetId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val requestHash = EthHex.decodeFixed(checkNotNull(record.requestHash), 32)
        val request = sora.approvedRequest(requestHash) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        val factor = sora.denominationFactor(asset.assetId)
        val expected = HashiAmounts.expectedEthAmount(record.soraUnits, asset, factor)
        return try {
            HashiClaimPlanner.plan(asset, request, parseAddress(record.recipient), expected)
        } catch (mismatch: HashiClaimAmountMismatchException) {
            save(record.copy(approvedAmountMismatch = "${mismatch.expected}/${mismatch.approved}"))
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
     * Starts a deposit the person confirmed with the PIN: approve 0 (when an older allowance
     * exists), approve the exact amount, then the deposit, each persisted and waited for. When the
     * wait is cut short the transfer stays at its step; the next step needs a new confirmation.
     */
    suspend fun startIncoming(account: SoraAccount, quote: IncomingQuote, symbol: String): HashiTransferRecord {
        requireOpen()
        val record = save(newIncoming(account, quote, symbol))
        return continueIncoming(account, record.id, waitForConfirmation = true)
    }

    private suspend fun newIncoming(account: SoraAccount, quote: IncomingQuote, symbol: String): HashiTransferRecord {
        val ethAccount = accounts.address(account) ?: throw HashiRefusal(HashiRefusal.Reason.NO_ETHEREUM_ACCOUNT)
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
        backgroundScope.launch { runConfirmedIncoming(account, record.id) }
        return record
    }

    /** [continueIncoming] for a screen, in [backgroundScope]. */
    fun continueIncomingInBackground(account: SoraAccount, recordId: String) {
        backgroundScope.launch { runConfirmedIncoming(account, recordId) }
    }

    private suspend fun runConfirmedIncoming(account: SoraAccount, recordId: String) {
        try {
            continueIncoming(account, recordId, waitForConfirmation = true)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val reason = (error as? HashiRefusal)?.let { refusal -> refusal.detail?.let { "${refusal.reason}: $it" } ?: refusal.reason.name }
            store.get(account.substrateAddress, recordId)?.let { save(it.copy(error = reason ?: error.message)) }
        }
    }

    /**
     * Runs the next Ethereum step of a deposit (after an explicit confirmation). Each step is
     * chosen from the chain's current allowance, never from what was planned earlier.
     */
    suspend fun continueIncoming(account: SoraAccount, recordId: String, waitForConfirmation: Boolean): HashiTransferRecord {
        var record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        check(record.direction == HashiDirection.ETHEREUM_TO_SORA) { "HASHI_NOT_INCOMING" }
        requireOpen()
        val asset = resolveAsset(record.assetId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_BRIDGEABLE)
        val from = parseAddress(record.ethAccount)
        repeat(MAX_INCOMING_STEPS) {
            if (record.ethSteps.any { it.state.isOpen }) {
                if (!waitForConfirmation) return record
                record = awaitOpenSteps(record)
                if (record.ethSteps.any { it.state.isOpen } || record.state == HashiTransferState.ETHEREUM_STEP_FAILED) return record
            }
            if (record.step(HashiEthIntent.DEPOSIT)?.state == HashiEthStepState.CONFIRMED) return record
            val amount = record.ethUnits
            val destination = EthHex.decodeFixed(record.recipient, 32)
            if (asset.isNativeEther) {
                return deposit(account, record, HashiBridgeContract.bridge, amount, HashiBridgeCalls.sendEthToSidechain(destination), waitForConfirmation)
            }
            // Read again right before each step.
            val allowance = ethereum.allowance(asset.ethToken, from, HashiBridgeContract.bridge)
            record = when {
                allowance >= amount -> return deposit(
                    account, record, HashiBridgeContract.bridge, BigInteger.ZERO,
                    HashiBridgeCalls.sendErc20ToSidechain(destination, amount, asset.ethToken), waitForConfirmation,
                )
                allowance.signum() > 0 -> signAndBroadcast(
                    account, record, HashiEthIntent.APPROVE_ZERO, asset.ethToken, BigInteger.ZERO,
                    HashiBridgeCalls.approve(HashiBridgeContract.bridge, BigInteger.ZERO), null, null,
                    HashiTransferState.APPROVE_ZERO_SUBMITTED,
                )
                else -> signAndBroadcast(
                    account, record, HashiEthIntent.APPROVE, asset.ethToken, BigInteger.ZERO,
                    HashiBridgeCalls.approve(HashiBridgeContract.bridge, amount), null, null,
                    HashiTransferState.APPROVE_SUBMITTED,
                )
            }
            if (record.state == HashiTransferState.ETHEREUM_STEP_FAILED) return record
        }
        // The allowance did not reach the amount after the approvals: stop instead of approving again.
        return save(record.copy(state = HashiTransferState.ETHEREUM_STEP_FAILED, error = "ALLOWANCE_NOT_SET"))
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
            account, record, HashiEthIntent.DEPOSIT, to, value, data, null, null, HashiTransferState.DEPOSIT_SUBMITTED,
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

    private suspend fun fees(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger): EthFees {
        val estimate = try {
            ethereum.estimateGas(from, to, data, value)
        } catch (revert: EthCallRevertedException) {
            throw HashiRefusal(HashiRefusal.Reason.CALL_REVERTED, revert.reason)
        }
        return HashiEthereumPolicy.fees(ethereum.baseFee(), ethereum.priorityFee(), estimate)
    }

    private suspend fun signAndBroadcast(
        account: SoraAccount,
        record: HashiTransferRecord,
        intent: HashiEthIntent,
        to: ByteArray,
        value: BigInteger,
        data: ByteArray,
        quotedFees: EthFees?,
        quotedNonce: BigInteger?,
        nextState: HashiTransferState,
    ): HashiTransferRecord {
        val from = parseAddress(record.ethAccount)
        val previous = record.step(intent)
        if (previous != null) {
            check(!previous.state.isOpen) { "HASHI_STEP_STILL_OPEN" }
            if (previous.state == HashiEthStepState.CONFIRMED) return record
            // A new nonce only once the old one is provably consumed by a transaction that is
            // none of ours (REPLACED) or by ours with status 0 (REVERTED).
            check(ethereum.latestNonce(from) > previous.nonceValue) { "HASHI_OLD_NONCE_NOT_CONSUMED" }
        }
        val fees = quotedFees ?: fees(from, to, data, value)
        if (ethereum.balance(from) < value.add(fees.maximumCost)) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        val nonce = quotedNonce?.takeIf { previous == null } ?: ethereum.pendingNonce(from)
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
        // Saved before the broadcast: an unknown result is resolved by sending these bytes again.
        val saved = save(record.withStep(step).copy(state = nextState, error = null))
        return broadcast(saved, step, signed.raw, signed.hash)
    }

    private suspend fun broadcast(record: HashiTransferRecord, step: HashiEthStep, raw: ByteArray, hash: ByteArray): HashiTransferRecord =
        try {
            ethereum.send(raw, hash)
            save(record.withStep(step.copy(state = HashiEthStepState.BROADCAST)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: HashiRpcErrorException) {
            // The node refused these exact bytes (nonce, funds, fee): nothing is pending.
            save(
                record.withStep(step.copy(state = HashiEthStepState.REPLACED, failure = error.message))
                    .copy(state = HashiTransferState.ETHEREUM_STEP_FAILED, error = error.message)
            )
        } catch (_: Exception) {
            // Unknown: the saved bytes are sent again when the transfer is refreshed.
            record
        }

    /**
     * Replaces an open step with the same transaction at the same nonce and fees at least 12.5 %
     * higher. Called only after a PIN confirmation.
     */
    suspend fun speedUp(account: SoraAccount, recordId: String): HashiTransferRecord {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        val step = record.ethSteps.lastOrNull { it.state.isOpen } ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        val from = parseAddress(record.ethAccount)
        val old = EthFees(BigInteger(step.latest.maxPriorityFeePerGas), BigInteger(step.latest.maxFeePerGas), BigInteger(step.gasLimit))
        val current = HashiEthereumPolicy.fees(ethereum.baseFee(), ethereum.priorityFee(), BigInteger(step.gasLimit))
        val fees = HashiEthereumPolicy.speedUp(old, current.let { EthFees(it.maxPriorityFeePerGas, it.maxFeePerGas, old.gasLimit) })
        check(HashiEthereumPolicy.isValidSpeedUp(old, fees)) { "HASHI_SPEED_UP_INVALID" }
        val value = BigInteger(step.value)
        if (ethereum.balance(from) < value.add(fees.maximumCost)) throw HashiRefusal(HashiRefusal.Reason.INSUFFICIENT_ETH_FOR_GAS)
        val transaction = Eip1559Transaction(
            HashiBridgeContract.ETHEREUM_CHAIN_ID, step.nonceValue, fees.maxPriorityFeePerGas, fees.maxFeePerGas,
            fees.gasLimit, EthHex.decode(step.to), value, EthHex.decode(step.data),
        )
        val signed = accounts.withPrivateKey(account, record.ethAccount) { key -> transaction.sign(key, expectedFrom = from) }
        val next = step.copy(
            attempts = step.attempts + HashiEthAttempt(
                signed.rawHex, signed.hashHex, fees.maxFeePerGas.toString(), fees.maxPriorityFeePerGas.toString(), now(),
            ),
            state = HashiEthStepState.SIGNED,
        )
        val saved = save(record.withStep(next))
        return broadcast(saved, next, signed.raw, signed.hash)
    }

    /** Sends the same claim, approval or deposit again at a new nonce after it reverted or was replaced. */
    suspend fun retryEthereumStep(account: SoraAccount, recordId: String): HashiTransferRecord {
        val record = store.get(account.substrateAddress, recordId) ?: throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        if (record.state != HashiTransferState.ETHEREUM_STEP_FAILED) throw HashiRefusal(HashiRefusal.Reason.NOT_READY)
        return when (record.direction) {
            HashiDirection.SORA_TO_ETHEREUM -> sendClaim(account, recordId, quoteClaim(account, recordId))
            HashiDirection.ETHEREUM_TO_SORA -> continueIncoming(account, recordId, waitForConfirmation = true)
        }
    }

    // --- Monitoring (never signs) ---------------------------------------------------------------

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
        next = when (next.direction) {
            HashiDirection.SORA_TO_ETHEREUM -> refreshOutgoing(next)
            HashiDirection.ETHEREUM_TO_SORA -> refreshIncoming(next)
        }
        if (next != record) saveUnlocked(next) else next
    }

    private suspend fun refreshOutgoing(record: HashiTransferRecord): HashiTransferRecord {
        when (record.state) {
            HashiTransferState.SORA_SIGNED, HashiTransferState.SORA_SUBMITTED -> {
                val hash = record.soraExtrinsicHash ?: return record.copy(state = HashiTransferState.FAILED, error = "NOT_SENT")
                if (record.soraBlockHash != null && record.requestHash == null) {
                    val requestHash = sora.requestHashOf(record.soraBlockHash, hash) ?: return record
                    return record.copy(state = HashiTransferState.WAITING_APPROVALS, requestHash = EthHex.encode(requestHash), error = null)
                }
                val outcome = sora.submissionOutcome(record.wallet, hash)
                if (outcome == null) {
                    // Signed but never handed to the journal (the app stopped before sending).
                    return if (now() - record.updatedAt > NOT_SENT_AFTER_MILLIS) {
                        record.copy(state = HashiTransferState.FAILED, error = "NOT_SENT")
                    } else {
                        record
                    }
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
                    is SoraSubmission.FailedOnChain -> record.copy(state = HashiTransferState.FAILED, error = outcome.detail ?: "FAILED_ON_CHAIN")
                    is SoraSubmission.NotSent -> record.copy(state = HashiTransferState.FAILED, error = outcome.detail ?: "NOT_SENT")
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
                    HashiRequestStatus.FAILED -> withStatus.copy(state = HashiTransferState.FAILED, error = value.raw)
                }
            }
            HashiTransferState.CLAIM_SUBMITTED -> {
                val claim = record.step(HashiEthIntent.CLAIM) ?: return record
                return when (claim.state) {
                    HashiEthStepState.CONFIRMED -> record.copy(state = HashiTransferState.DONE, error = null)
                    HashiEthStepState.REVERTED, HashiEthStepState.REPLACED -> record.copy(
                        state = HashiTransferState.ETHEREUM_STEP_FAILED, error = claim.failure,
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
                // Done only when the INCOMING TRANSFER request is Done (spec §8 H); the load
                // request can be Done while the transfer is still pending.
                val incoming = record.incomingRequestHash?.let { EthHex.decodeFixed(it, 32) } ?: sora.incomingRequestHash(depositHash)
                if (incoming == null) {
                    record.copy(state = HashiTransferState.WAITING_SORA)
                } else {
                    val value = sora.requestStatus(incoming)
                    val base = record.copy(incomingRequestHash = EthHex.encode(incoming), requestStatus = value?.status?.wireName)
                    when (value?.status) {
                        HashiRequestStatus.DONE -> base.copy(state = HashiTransferState.DONE, error = null)
                        HashiRequestStatus.FAILED -> base.copy(state = HashiTransferState.FAILED, error = value.raw)
                        HashiRequestStatus.FROZEN -> base.copy(state = HashiTransferState.PAUSED_BY_BRIDGE)
                        HashiRequestStatus.BROKEN -> base.copy(state = HashiTransferState.NEEDS_OPERATORS, error = value.raw)
                        else -> base.copy(state = HashiTransferState.WAITING_SORA)
                    }
                }
            }
            record.ethSteps.any { it.state.isOpen } -> record
            failed != null && record.ethSteps.last().state.let { it == HashiEthStepState.REVERTED || it == HashiEthStepState.REPLACED } ->
                record.copy(state = HashiTransferState.ETHEREUM_STEP_FAILED, error = record.ethSteps.last().failure)
            record.ethSteps.isNotEmpty() && record.ethSteps.last().state == HashiEthStepState.CONFIRMED &&
                record.ethSteps.last().intent != HashiEthIntent.DEPOSIT -> record.copy(state = HashiTransferState.READY_TO_DEPOSIT)
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
                receipt.status == 1 -> step.copy(state = HashiEthStepState.REVERTED, confirmedHash = attempt.hash, failure = "EVENT_MISSING")
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
                record.withStep(step.copy(state = HashiEthStepState.REPLACED, failure = "NONCE_USED_ELSEWHERE"))
            } else {
                record
            }
        }
        nonceUsedSince.remove(key)
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
        const val MAX_INCOMING_STEPS = 4
        const val REPLACED_AFTER_MILLIS = 60_000L
        const val REBROADCAST_MILLIS = 60_000L
        const val NOT_SENT_AFTER_MILLIS = 10 * 60_000L
    }
}
