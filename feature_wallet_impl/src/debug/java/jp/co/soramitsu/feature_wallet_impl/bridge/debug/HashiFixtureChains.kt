package jp.co.soramitsu.feature_wallet_impl.bridge.debug

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import javax.inject.Singleton
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.EthCallRevertedException
import jp.co.soramitsu.common.hashi.EthLog
import jp.co.soramitsu.common.hashi.EthReceipt
import jp.co.soramitsu.common.hashi.EthTransactionLookup
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiAssetKind
import jp.co.soramitsu.common.hashi.HashiAvailability
import jp.co.soramitsu.common.hashi.HashiBlock
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiCurrency
import jp.co.soramitsu.common.hashi.HashiDryRunResult
import jp.co.soramitsu.common.hashi.HashiPeerSignature
import jp.co.soramitsu.common.hashi.HashiReceiptProof
import jp.co.soramitsu.common.hashi.HashiRegisteredAsset
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRequestStatusValue
import jp.co.soramitsu.common.hashi.HashiRpcErrorException
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiBridgeTestChains
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiEthereumGateway
import jp.co.soramitsu.feature_wallet_impl.bridge.HashiSoraGateway
import jp.co.soramitsu.feature_wallet_impl.bridge.PreparedSoraTransfer
import jp.co.soramitsu.feature_wallet_impl.bridge.SoraSubmission
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * DEBUG BUILDS ONLY: this file is in the `debug` source set, so no release build has it.
 *
 * A pretend SORA bridge pallet and a pretend Ethereum chain for tests that drive the real app
 * over adb: the real bridge screens, the real [jp.co.soramitsu.feature_wallet_impl.bridge.HashiBridgeService],
 * the real transfer store, the real PIN screen and the real Ethereum key derivation and signing
 * (of the wallet on the test device, a public test phrase). Signed transactions go to this
 * pretend chain, never to a network.
 *
 * It is on only while `no_backup/hashi-fixture/controls.json` exists when the app starts; a
 * test puts it there (`adb shell run-as … `). Without the file a debug build talks to the real
 * chains, like a release build. While it is on, the bridge keeps its transfers in
 * `no_backup/hashi-fixture/transfers`, never in the real store: a transaction signed for the
 * pretend chain is never re-sent to Ethereum after the file is removed.
 *
 * Everything the pretend chains know is kept in `chain.json` beside it and written after every
 * change, so stopping the app in the middle of a transfer and starting it again continues from
 * where the chains were. The test reads that file for its counters: how often a SORA transfer was
 * handed to the network, and every distinct Ethereum transaction that was sent.
 */
@Module
@InstallIn(SingletonComponent::class)
object HashiFixtureModule {
    @Provides
    @Singleton
    fun provideHashiBridgeTestChains(@ApplicationContext context: Context): HashiBridgeTestChains =
        HashiFixtureChains(File(context.noBackupFilesDir, "hashi-fixture"))
}

class HashiFixtureChains(private val directory: File) : HashiBridgeTestChains {
    /** Decided once per process, so the gateways and the transfer store always agree. */
    private val on: Boolean by lazy { File(directory, CONTROLS).isFile }

    override fun gateways(): Pair<HashiSoraGateway, HashiEthereumGateway>? {
        if (!on) return null
        val world = FixtureWorld(directory)
        return FixtureSora(world) to FixtureEthereum(world)
    }

    /** The pretend chains' transfers live beside them, never in the real store. */
    override fun transfersDirectory(): File? = if (on) File(directory, TRANSFERS) else null

    companion object {
        const val CONTROLS = "controls.json"
        const val CHAIN = "chain.json"
        const val TRANSFERS = "transfers"
    }
}

/** What the test sets from outside (read again on every call; every field has a default). */
@Serializable
internal data class FixtureControls(
    /** [HashiBlock] names: why new transfers are off. */
    val blocks: List<String> = emptyList(),
    /** The bridge status cannot be read at all. */
    val statusUnreadable: Boolean = false,
    /** A submitted SORA transfer is not put in a block until this is false. */
    val holdSora: Boolean = false,
    /** A registered request stays `Pending` until this is false. */
    val holdApprovals: Boolean = false,
    /** Sent Ethereum transactions stay in the pool until this is false. */
    val holdMining: Boolean = false,
    /** A mined deposit is not credited on SORA until this is false. */
    val holdCredit: Boolean = false,
    /** `Frozen`, `Broken` or `Failed` for every outgoing request, instead of its own status. */
    val requestStatus: String? = null,
    /** The node's words when it refuses every send (nothing enters the pool). */
    val refuseSend: String? = null,
    /** The node takes a send into its pool but the answer is lost (the app sees a timeout). */
    val loseSendAnswer: Boolean = false,
    /** `used()` answers true for every request: somebody already paid the claim. */
    val alreadyPaid: Boolean = false,
    /** The next mined transactions of these kinds (approve, deposit, claim) get status 0. */
    val revert: List<String> = emptyList(),
    val baseFeeGwei: Long = 10,
    /** Gas the node estimates, by kind; a test raises one to make a step dearer than expected. */
    val gas: Map<String, Long> = emptyMap(),
    /** What the SORA dry run answers instead of Ok, e.g. `EthBridge.TransferLimitReached`. */
    val dryRunError: String? = null,
    /** The allowance the Ethereum account starts with (token units), before any approval. */
    val initialAllowance: String = "0",
    val ethBalanceWei: String = "1000000000000000000",
    val lockedLow: Boolean = false,
    val soraBlockMillis: Long = 1_500,
    val approvalMillis: Long = 3_000,
    val miningMillis: Long = 3_000,
    val creditMillis: Long = 3_000,
)

@Serializable
internal data class FixtureRequest(
    val extrinsicHash: String,
    val assetId: String,
    val soraAmount: String,
    val recipient: String,
    val from: String,
    val includedAt: Long,
)

@Serializable
internal data class FixtureTransaction(
    val hash: String,
    val raw: String,
    val nonce: Long,
    val to: String,
    val data: String,
    val value: String,
    val maxFeePerGas: String,
    val maxPriorityFeePerGas: String,
    val gasLimit: String,
    /** approve, deposit or claim. */
    val kind: String,
    val acceptedAt: Long,
    /** How many times these very bytes were handed over (the same transaction each time). */
    val sends: Int = 1,
    /** 1 or 0 once mined. */
    val status: Int? = null,
    val minedAt: Long? = null,
    /** Dropped because another transaction at its nonce was mined. */
    val replaced: Boolean = false,
)

@Serializable
internal data class FixtureChain(
    val soraSigned: Int = 0,
    /** Every hand-over of a SORA transfer to the network, in order (a duplicate would show twice). */
    val soraSubmitted: List<String> = emptyList(),
    val soraSubmittedAt: Map<String, Long> = emptyMap(),
    /** Extrinsic hash → block hash. */
    val soraIncluded: Map<String, String> = emptyMap(),
    /** Extrinsic hash → what the signed transfer asks for. */
    val soraTransfers: Map<String, FixtureRequest> = emptyMap(),
    /** Request hash → the outgoing request. */
    val requests: Map<String, FixtureRequest> = emptyMap(),
    val ethNonce: Long = 5,
    /** Every `eth_sendRawTransaction` call, answered or not. */
    val ethSendCalls: Int = 0,
    val ethRefusals: Int = 0,
    val ethTransactions: List<FixtureTransaction> = emptyList(),
    val allowance: String? = null,
    /** Request hashes paid on Ethereum. */
    val paid: List<String> = emptyList(),
    /** Deposit transaction hash → incoming request hash. */
    val incoming: Map<String, String> = emptyMap(),
)

/** Both pretend chains: one state, one lock, one file. */
internal class FixtureWorld(private val directory: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val lock = Any()
    private var chain: FixtureChain = File(directory, HashiFixtureChains.CHAIN).takeIf { it.isFile }
        ?.let { runCatching { json.decodeFromString(FixtureChain.serializer(), it.readText()) }.getOrNull() }
        ?: FixtureChain()

    fun now(): Long = System.currentTimeMillis()

    fun controls(): FixtureControls = runCatching {
        json.decodeFromString(FixtureControls.serializer(), File(directory, HashiFixtureChains.CONTROLS).readText())
    }.getOrDefault(FixtureControls())

    /** Reads the chains after letting them move on (blocks, approvals, mining) to the present. */
    fun <T> read(block: (FixtureChain, FixtureControls) -> T): T = synchronized(lock) {
        val controls = controls()
        advance(controls)
        block(chain, controls)
    }

    fun <T> change(block: (FixtureChain, FixtureControls) -> Pair<FixtureChain, T>): T = synchronized(lock) {
        val controls = controls()
        advance(controls)
        val (next, result) = block(chain, controls)
        save(next)
        result
    }

    private fun save(next: FixtureChain) {
        if (next == chain) return
        chain = next
        directory.mkdirs()
        val temporary = File(directory, HashiFixtureChains.CHAIN + ".tmp")
        temporary.writeText(json.encodeToString(FixtureChain.serializer(), next))
        if (!temporary.renameTo(File(directory, HashiFixtureChains.CHAIN))) throw IOException("FIXTURE_SAVE")
    }

    /** Time passes on both chains: SORA blocks, and Ethereum blocks that mine the pool. */
    private fun advance(controls: FixtureControls) {
        var next = chain
        val time = now()
        // SORA: a handed-over transfer enters a block and registers its request.
        if (!controls.holdSora) {
            next.soraSubmitted.distinct().filterNot { it in next.soraIncluded }.forEach { hash ->
                val at = next.soraSubmittedAt[hash] ?: time
                if (time - at < controls.soraBlockMillis) return@forEach
                val signed = next.soraTransfers[hash] ?: return@forEach
                val request = signed.copy(includedAt = time)
                next = next.copy(
                    soraIncluded = next.soraIncluded + (hash to sha("block:$hash")),
                    requests = next.requests + (sha("request:$hash") to request),
                )
            }
        }
        // Ethereum: the transaction at the account's nonce with the highest fee is mined.
        if (!controls.holdMining) {
            while (true) {
                val candidates = next.ethTransactions.filter { it.status == null && !it.replaced && it.nonce == next.ethNonce }
                val best = candidates.maxByOrNull { BigInteger(it.maxFeePerGas) } ?: break
                if (time - candidates.minOf { it.acceptedAt } < controls.miningMillis) break
                val status = if (best.kind in controls.revert) 0 else 1
                var allowance = next.allowance ?: controls.initialAllowance
                var paid = next.paid
                var incoming = next.incoming
                if (status == 1) {
                    when (best.kind) {
                        KIND_APPROVE -> allowance = BigInteger(1, EthHex.decode(best.data).copyOfRange(36, 68)).toString()
                        KIND_DEPOSIT -> {
                            allowance = "0"
                            incoming = incoming + (best.hash to sha("incoming:${best.hash}"))
                        }
                        KIND_CLAIM -> paid = paid + next.requests.keys.filter { best.data.contains(it.removePrefix("0x")) }
                    }
                }
                next = next.copy(
                    ethNonce = next.ethNonce + 1,
                    allowance = allowance,
                    paid = paid.distinct(),
                    incoming = incoming,
                    ethTransactions = next.ethTransactions.map { transaction ->
                        when {
                            transaction.hash == best.hash -> transaction.copy(status = status, minedAt = time)
                            transaction.nonce == best.nonce && transaction.status == null -> transaction.copy(replaced = true)
                            else -> transaction
                        }
                    },
                )
            }
        }
        save(next)
    }

    companion object {
        const val KIND_APPROVE = "approve"
        const val KIND_DEPOSIT = "deposit"
        const val KIND_CLAIM = "claim"

        fun sha(text: String): String =
            EthHex.encode(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    }
}

private val XOR_TOKEN: ByteArray = EthHex.decode("0x40fd72257597aa14c7231a7b1aaa29fce868f677")
private val USDT_TOKEN: ByteArray = EthHex.decode("0xdac17f958d2ee523a2206206994597c13d831ec7")
private const val USDT_ASSET_ID = "0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4"

internal class FixtureSora(private val world: FixtureWorld) : HashiSoraGateway {

    override suspend fun availability(): HashiAvailability = world.read { _, controls ->
        if (controls.statusUnreadable) throw IOException("FIXTURE_STATUS_UNREADABLE")
        HashiAvailability(controls.blocks.map(HashiBlock::valueOf).toSet(), 142, 28_500_000L, null)
    }

    override suspend fun registeredAssets(): List<HashiRegisteredAsset> = listOf(
        HashiRegisteredAsset(HashiAssetKind.THISCHAIN, HashiBridgeContract.XOR_ASSET_ID, 18, null, null),
        HashiRegisteredAsset(HashiAssetKind.SIDECHAIN_OWNED, HashiBridgeContract.VAL_ASSET_ID, 18, HashiBridgeContract.valToken, 18),
        HashiRegisteredAsset(HashiAssetKind.SIDECHAIN, USDT_ASSET_ID, 18, USDT_TOKEN, 6),
        HashiRegisteredAsset(HashiAssetKind.SIDECHAIN, HashiBridgeContract.ETH_ASSET_ID, 18, ByteArray(20), 18),
    )

    private fun statusOf(request: FixtureRequest, chain: FixtureChain, controls: FixtureControls, hash: String, now: Long): HashiRequestStatus {
        controls.requestStatus?.let { name -> HashiRequestStatus.values().firstOrNull { it.wireName == name }?.let { return it } }
        return when {
            hash in chain.paid -> HashiRequestStatus.APPROVALS_READY
            controls.holdApprovals || now - request.includedAt < controls.approvalMillis -> HashiRequestStatus.PENDING
            else -> HashiRequestStatus.APPROVALS_READY
        }
    }

    override suspend fun approvedRequest(requestHash: ByteArray): HashiApprovedRequest? = world.read { chain, controls ->
        val hash = EthHex.encode(requestHash)
        val request = chain.requests[hash] ?: return@read null
        if (statusOf(request, chain, controls, hash, world.now()) != HashiRequestStatus.APPROVALS_READY) return@read null
        val currency = if (request.assetId == HashiBridgeContract.XOR_ASSET_ID) {
            HashiCurrency.AssetId(EthHex.decode(request.assetId))
        } else {
            HashiCurrency.TokenAddress(if (request.assetId == USDT_ASSET_ID) USDT_TOKEN else HashiBridgeContract.valToken)
        }
        // USDT has 6 decimals on Ethereum and 18 on SORA; XOR and VAL are 18 on both.
        val amount = if (request.assetId == USDT_ASSET_ID) BigInteger(request.soraAmount).divide(BigInteger.TEN.pow(12)) else BigInteger(request.soraAmount)
        HashiApprovedRequest(
            currency = currency,
            amount = amount,
            to = EthHex.decode(request.recipient),
            from = EthHex.decode(request.from).copyOf(20),
            txHash = requestHash,
            signatures = listOf(
                HashiPeerSignature(ByteArray(32) { 1 }, ByteArray(32) { 2 }, 0),
                HashiPeerSignature(ByteArray(32) { 3 }, ByteArray(32) { 4 }, 1),
            ),
        )
    }

    override suspend fun requestStatus(requestHash: ByteArray): HashiRequestStatusValue? = world.read { chain, controls ->
        val hash = EthHex.encode(requestHash)
        val outgoing = chain.requests[hash]
        if (outgoing != null) {
            val status = statusOf(outgoing, chain, controls, hash, world.now())
            // Failed and Broken carry the pallet's error bytes.
            val raw = if (status == HashiRequestStatus.FAILED || status == HashiRequestStatus.BROKEN) "0x031f0b" else "0x00"
            return@read HashiRequestStatusValue(status, raw)
        }
        val deposit = chain.incoming.entries.firstOrNull { it.value == hash }?.key ?: return@read null
        val minedAt = chain.ethTransactions.firstOrNull { it.hash == deposit }?.minedAt ?: return@read null
        val done = !controls.holdCredit && world.now() - minedAt >= controls.creditMillis
        HashiRequestStatusValue(if (done) HashiRequestStatus.DONE else HashiRequestStatus.PENDING, "0x00")
    }

    override suspend fun incomingRequestHash(ethTxHash: ByteArray): ByteArray? = world.read { chain, _ ->
        chain.incoming[EthHex.encode(ethTxHash)]?.let(EthHex::decode)
    }

    override suspend fun lockedAssets(assetId: ByteArray): BigInteger = world.read { _, controls ->
        if (controls.lockedLow) BigInteger.ONE else BigInteger.TEN.pow(30)
    }

    override suspend fun denominationFactor(assetId: String): BigInteger = BigInteger.ONE

    override suspend fun transferable(account: SoraAccount, assetId: String): BigInteger = BigInteger.TEN.pow(21)

    /** The fee runtime 142 quotes for the bridge transfer: 1.000016 XOR. */
    override suspend fun transferFee(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger): BigInteger =
        BigInteger("1000016000000000000")

    override suspend fun signTransfer(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger): PreparedSoraTransfer =
        world.change<PreparedSoraTransfer> { chain, _ ->
            val number = chain.soraSigned + 1
            val hash = FixtureWorld.sha("extrinsic:$number:$assetId:${EthHex.encode(to)}:$amount")
            val request = FixtureRequest(
                extrinsicHash = hash,
                assetId = assetId,
                soraAmount = amount.toString(),
                recipient = EthHex.encode(to),
                from = EthHex.encode(account.substrateAddress.toAccountId()),
                includedAt = 0,
            )
            chain.copy(soraSigned = number, soraTransfers = chain.soraTransfers + (hash to request)) to
                PreparedSoraTransfer(hash, "0x" + "5a".repeat(140), Unit)
        }

    override suspend fun dryRun(prepared: PreparedSoraTransfer): HashiDryRunResult = world.read { _, controls ->
        controls.dryRunError?.let { HashiDryRunResult.DispatchFailed(it, null, null) } ?: HashiDryRunResult.Ok
    }

    /** Hands the transfer to the pretend network (counted), then waits for its block like the app's signer does. */
    override suspend fun submit(prepared: PreparedSoraTransfer, reviewedFee: BigInteger): SoraSubmission {
        world.change<Unit> { chain, _ ->
            chain.copy(
                soraSubmitted = chain.soraSubmitted + prepared.extrinsicHash,
                soraSubmittedAt = chain.soraSubmittedAt + (prepared.extrinsicHash to world.now()),
            ) to Unit
        }
        repeat(MAX_INCLUSION_POLLS) {
            world.read { chain, _ -> chain.soraIncluded[prepared.extrinsicHash] }?.let { return SoraSubmission.Included(it) }
            delay(POLL_MILLIS)
        }
        return SoraSubmission.Pending
    }

    /** The wallet's journal: null until the transfer was handed over, then pending, then its block. */
    override suspend fun submissionOutcome(wallet: String, extrinsicHash: String): SoraSubmission? = world.read { chain, _ ->
        when {
            extrinsicHash !in chain.soraSubmitted -> null
            else -> chain.soraIncluded[extrinsicHash]?.let { SoraSubmission.Included(it) } ?: SoraSubmission.Pending
        }
    }

    override suspend fun requestHashOf(blockHash: String, extrinsicHash: String): ByteArray? = world.read { chain, _ ->
        chain.requests.entries.firstOrNull { it.value.extrinsicHash == extrinsicHash }?.key?.let(EthHex::decode)
    }

    private companion object {
        const val POLL_MILLIS = 500L
        const val MAX_INCLUSION_POLLS = 240
    }
}

internal class FixtureEthereum(private val world: FixtureWorld) : HashiEthereumGateway {

    private val approveSelector = EthHex.encode(EthAbi.selector("approve(address,uint256)"))
    private val depositSelector = EthHex.encode(EthAbi.selector("sendERC20ToSidechain(bytes32,uint256,address)"))
    private val etherDepositSelector = EthHex.encode(EthAbi.selector("sendEthToSidechain(bytes32)"))

    private fun kindOf(data: ByteArray): String = when (if (data.size >= 4) EthHex.encode(data.copyOfRange(0, 4)) else "") {
        approveSelector -> FixtureWorld.KIND_APPROVE
        depositSelector, etherDepositSelector -> FixtureWorld.KIND_DEPOSIT
        else -> FixtureWorld.KIND_CLAIM
    }

    override suspend fun balance(address: ByteArray): BigInteger = world.read { _, controls ->
        if (EthAddress.equal(address, HashiBridgeContract.bridge)) BigInteger.TEN.pow(24) else BigInteger(controls.ethBalanceWei)
    }

    override suspend fun tokenBalance(token: ByteArray, owner: ByteArray): BigInteger = BigInteger.TEN.pow(24)

    override suspend fun allowance(token: ByteArray, owner: ByteArray, spender: ByteArray): BigInteger = world.read { chain, controls ->
        BigInteger(chain.allowance ?: controls.initialAllowance)
    }

    override suspend fun decimals(token: ByteArray): Int = if (EthAddress.equal(token, USDT_TOKEN)) 6 else 18

    override suspend fun sidechainToken(assetId: ByteArray): ByteArray? =
        XOR_TOKEN.takeIf { EthHex.encode(assetId) == HashiBridgeContract.XOR_ASSET_ID }

    override suspend fun sidechainAssetOfToken(token: ByteArray): ByteArray =
        if (EthAddress.equal(token, XOR_TOKEN)) EthHex.decode(HashiBridgeContract.XOR_ASSET_ID) else ByteArray(32)

    override suspend fun isAcceptedEthToken(token: ByteArray): Boolean = true

    override suspend fun isUsed(contract: ByteArray, txHash: ByteArray): Boolean = world.read { chain, controls ->
        controls.alreadyPaid || EthHex.encode(txHash) in chain.paid
    }

    override suspend fun hasNoCode(address: ByteArray): Boolean = true

    /** The mined count plus the transactions waiting in the pool right behind it. */
    override suspend fun pendingNonce(address: ByteArray): BigInteger = world.read { chain, _ ->
        var next = chain.ethNonce
        while (chain.ethTransactions.any { it.status == null && !it.replaced && it.nonce == next }) next += 1
        BigInteger.valueOf(next)
    }

    override suspend fun latestNonce(address: ByteArray): BigInteger = world.read { chain, _ -> BigInteger.valueOf(chain.ethNonce) }

    override suspend fun estimateGas(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger): BigInteger = world.read { chain, controls ->
        val kind = kindOf(data)
        if (kind == FixtureWorld.KIND_CLAIM && (controls.alreadyPaid || chain.requests.keys.any { it in chain.paid && EthHex.encode(data).contains(it.removePrefix("0x")) })) {
            throw EthCallRevertedException("txHash already used", "execution reverted: txHash already used")
        }
        val default = when (kind) {
            FixtureWorld.KIND_APPROVE -> 46_000L
            // More than the bridge's usual 53 000: the step is fitted into what the sheet showed.
            FixtureWorld.KIND_DEPOSIT -> if (value.signum() > 0) 26_000L else 70_000L
            else -> 150_000L
        }
        BigInteger.valueOf(controls.gas[kind] ?: default)
    }

    override suspend fun baseFee(): BigInteger = world.read { _, controls -> BigInteger.valueOf(controls.baseFeeGwei).multiply(GWEI) }

    override suspend fun priorityFee(): BigInteger = GWEI

    override suspend fun send(raw: ByteArray, hash: ByteArray) {
        val lost = world.change<Exception?> { chain, controls ->
            val counted = chain.copy(ethSendCalls = chain.ethSendCalls + 1)
            controls.refuseSend?.let { words ->
                return@change counted.copy(ethRefusals = counted.ethRefusals + 1) to HashiRpcErrorException(-32000, words, null)
            }
            val hashHex = EthHex.encode(hash)
            val known = counted.ethTransactions.firstOrNull { it.hash == hashHex }
            if (known != null) {
                // The same bytes again: the node already has this transaction.
                return@change counted.copy(
                    ethTransactions = counted.ethTransactions.map { if (it.hash == hashHex) it.copy(sends = it.sends + 1) else it },
                ) to null
            }
            val fields = decode(raw)
            if (fields.nonce < counted.ethNonce) {
                return@change counted.copy(ethRefusals = counted.ethRefusals + 1) to
                    HashiRpcErrorException(-32000, "nonce too low: next nonce ${counted.ethNonce}, tx nonce ${fields.nonce}", null)
            }
            val rival = counted.ethTransactions.filter { it.status == null && !it.replaced && it.nonce == fields.nonce }
                .maxByOrNull { BigInteger(it.maxFeePerGas) }
            if (rival != null) {
                val enough = fields.maxFeePerGas.multiply(THOUSAND) >= BigInteger(rival.maxFeePerGas).multiply(BUMP) &&
                    fields.maxPriorityFeePerGas.multiply(THOUSAND) >= BigInteger(rival.maxPriorityFeePerGas).multiply(BUMP)
                if (!enough) {
                    return@change counted.copy(ethRefusals = counted.ethRefusals + 1) to
                        HashiRpcErrorException(-32000, "replacement transaction underpriced", null)
                }
            }
            val transaction = FixtureTransaction(
                hash = hashHex,
                raw = EthHex.encode(raw),
                nonce = fields.nonce,
                to = EthHex.encode(fields.to),
                data = EthHex.encode(fields.data),
                value = fields.value.toString(),
                maxFeePerGas = fields.maxFeePerGas.toString(),
                maxPriorityFeePerGas = fields.maxPriorityFeePerGas.toString(),
                gasLimit = fields.gasLimit.toString(),
                kind = kindOf(fields.data),
                acceptedAt = world.now(),
            )
            counted.copy(ethTransactions = counted.ethTransactions + transaction) to
                (if (controls.loseSendAnswer) IOException("FIXTURE_ANSWER_LOST") else null)
        }
        lost?.let { throw it }
    }

    override suspend fun receipt(hash: ByteArray): EthReceipt? = world.read { chain, _ ->
        val transaction = chain.ethTransactions.firstOrNull { it.hash == EthHex.encode(hash) && it.status != null } ?: return@read null
        val data = EthHex.decode(transaction.data)
        val logs = when {
            transaction.status != 1 -> emptyList()
            transaction.kind == FixtureWorld.KIND_DEPOSIT -> {
                val ether = BigInteger(transaction.value).signum() > 0
                val destination = data.copyOfRange(4, 36)
                val amount = if (ether) BigInteger(transaction.value) else BigInteger(1, data.copyOfRange(36, 68))
                val token = if (ether) ByteArray(20) else data.copyOfRange(80, 100)
                listOf(
                    EthLog(
                        HashiBridgeContract.bridge,
                        listOf(HashiReceiptProof.DEPOSIT_TOPIC),
                        destination + EthAbi.uintWord(amount) + ByteArray(12) + token + ByteArray(32),
                    )
                )
            }
            transaction.kind == FixtureWorld.KIND_CLAIM -> chain.requests.keys
                .filter { transaction.data.contains(it.removePrefix("0x")) }
                .map { EthLog(HashiBridgeContract.bridge, listOf(HashiReceiptProof.WITHDRAWAL_TOPIC), EthHex.decode(it)) }
            else -> emptyList()
        }
        EthReceipt(transaction.status ?: 0, BigInteger.valueOf(21_000_000L + transaction.nonce), BigInteger(transaction.gasLimit), logs, hash)
    }

    override suspend fun lookupEverywhere(hash: ByteArray): EthTransactionLookup = world.read { chain, _ ->
        val transaction = chain.ethTransactions.firstOrNull { it.hash == EthHex.encode(hash) }
        when {
            transaction == null || (transaction.replaced && transaction.status == null) -> EthTransactionLookup.Unknown
            transaction.status != null -> EthTransactionLookup.Mined(transaction.status)
            else -> EthTransactionLookup.Pending
        }
    }

    private class Fields(
        val nonce: Long,
        val maxPriorityFeePerGas: BigInteger,
        val maxFeePerGas: BigInteger,
        val gasLimit: BigInteger,
        val to: ByteArray,
        val value: BigInteger,
        val data: ByteArray,
    )

    /** `0x02 ‖ rlp([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, yParity, r, s])`. */
    private fun decode(raw: ByteArray): Fields {
        require(raw.isNotEmpty() && raw[0] == 2.toByte()) { "FIXTURE_NOT_EIP1559" }
        val items = RlpReader(raw, 1).list()
        require(items.size == 12 && BigInteger(1, items[0]) == BigInteger.ONE) { "FIXTURE_TRANSACTION_INVALID" }
        return Fields(
            nonce = BigInteger(1, items[1]).toLong(),
            maxPriorityFeePerGas = BigInteger(1, items[2]),
            maxFeePerGas = BigInteger(1, items[3]),
            gasLimit = BigInteger(1, items[4]),
            to = items[5],
            value = BigInteger(1, items[6]),
            data = items[7],
        )
    }

    /** Just enough RLP to read the wallet's own transactions back: one list of strings (a nested list is skipped). */
    private class RlpReader(private val bytes: ByteArray, private var position: Int) {

        fun list(): List<ByteArray> {
            val (start, length, isList) = header()
            require(isList) { "FIXTURE_RLP_NOT_A_LIST" }
            val end = start + length
            position = start
            val items = mutableListOf<ByteArray>()
            while (position < end) {
                val (itemStart, itemLength, nested) = header()
                items += if (nested) ByteArray(0) else bytes.copyOfRange(itemStart, itemStart + itemLength)
                position = itemStart + itemLength
            }
            return items
        }

        /** Where the payload starts, its length, and whether it is a list. */
        private fun header(): Triple<Int, Int, Boolean> {
            val first = bytes[position].toInt() and 0xff
            return when {
                first < 0x80 -> Triple(position, 1, false)
                first <= 0xb7 -> Triple(position + 1, first - 0x80, false)
                first <= 0xbf -> long(first - 0xb7, false)
                first <= 0xf7 -> Triple(position + 1, first - 0xc0, true)
                else -> long(first - 0xf7, true)
            }
        }

        private fun long(lengthBytes: Int, isList: Boolean): Triple<Int, Int, Boolean> {
            val length = BigInteger(1, bytes.copyOfRange(position + 1, position + 1 + lengthBytes)).toInt()
            return Triple(position + 1 + lengthBytes, length, isList)
        }
    }

    private companion object {
        val GWEI: BigInteger = BigInteger.TEN.pow(9)
        val THOUSAND: BigInteger = BigInteger.valueOf(1000)
        val BUMP: BigInteger = BigInteger.valueOf(1125)
    }
}
