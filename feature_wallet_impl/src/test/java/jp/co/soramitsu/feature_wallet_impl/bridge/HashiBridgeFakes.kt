package jp.co.soramitsu.feature_wallet_impl.bridge

import java.io.IOException
import java.math.BigInteger
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.ethereum.EthAbi
import jp.co.soramitsu.common.ethereum.EthHex
import jp.co.soramitsu.common.hashi.EthCallRevertedException
import jp.co.soramitsu.common.hashi.EthLog
import jp.co.soramitsu.common.hashi.EthReceipt
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiAssetKind
import jp.co.soramitsu.common.hashi.HashiAvailability
import jp.co.soramitsu.common.hashi.HashiBlock
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiDryRunResult
import jp.co.soramitsu.common.hashi.HashiReceiptProof
import jp.co.soramitsu.common.hashi.HashiRegisteredAsset
import jp.co.soramitsu.common.hashi.HashiRequestStatus
import jp.co.soramitsu.common.hashi.HashiRequestStatusValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The public test account of the vectors ("abandon … about"). */
internal object TestEthAccount {
    val privateKey: ByteArray = EthHex.decode("0x1ab42cc412b618bdea3a599e3c9bae199ebf030895b039e9db1e30dafb12b727")
    const val ADDRESS = "0x9858EfFD232B4033E47d90003D41EC34EcaEda94"
    val address: ByteArray = EthHex.decode(ADDRESS)
}

internal val testAccount = SoraAccount("cnSBot6vTtpckdo7HQhjk2hiAAKfDJX34DZsyLVs7B5Y2Gv6T", "test")

internal const val USDT_ID = "0x0083a6b3fbc6edae06f115c8953ddd7cbfba0b74579d6ea190f96853073b76f4"
internal val USDT_TOKEN: ByteArray = EthHex.decode("0xdac17f958d2ee523a2206206994597c13d831ec7")
internal val XOR_TOKEN: ByteArray = EthHex.decode("0x1D03EbBd6bE00B805702dDe42313dcF44aC1a6Eb")

internal class FakeSora : HashiSoraGateway {
    var blocks: Set<HashiBlock> = emptySet()
    val registered = mutableListOf(
        HashiRegisteredAsset(HashiAssetKind.SIDECHAIN, USDT_ID, 18, USDT_TOKEN, 6),
        HashiRegisteredAsset(HashiAssetKind.THISCHAIN, HashiBridgeContract.XOR_ASSET_ID, 18, null, null),
        HashiRegisteredAsset(HashiAssetKind.SIDECHAIN_OWNED, HashiBridgeContract.VAL_ASSET_ID, 18, HashiBridgeContract.valToken, 18),
        HashiRegisteredAsset(HashiAssetKind.SIDECHAIN, HashiBridgeContract.ETH_ASSET_ID, 18, ByteArray(20), 18),
        HashiRegisteredAsset(HashiAssetKind.THISCHAIN, HashiBridgeContract.TBCD_ASSET_ID, 18, null, null),
    )
    val statuses = mutableMapOf<String, HashiRequestStatusValue>()
    val approved = mutableMapOf<String, HashiApprovedRequest>()
    val incoming = mutableMapOf<String, ByteArray>()
    val locked = mutableMapOf<String, BigInteger>()
    var transferable: BigInteger = BigInteger.TEN.pow(24)
    var xorTransferable: BigInteger = BigInteger.TEN.pow(24)
    var fee: BigInteger = BigInteger.TEN.pow(18)
    var dryRun: HashiDryRunResult = HashiDryRunResult.Ok
    var submission: SoraSubmission = SoraSubmission.Included("0x" + "bb".repeat(32))
    var journal: SoraSubmission? = null
    var requestHash: ByteArray? = ByteArray(32) { 0x11 }
    val signed = mutableListOf<PreparedSoraTransfer>()
    val submitted = mutableListOf<PreparedSoraTransfer>()
    var onSubmit: () -> Unit = {}

    fun status(hash: ByteArray, status: HashiRequestStatus, raw: String = "0x%02x".format(status.ordinal)) {
        statuses[EthHex.encode(hash)] = HashiRequestStatusValue(status, raw)
    }

    override suspend fun availability() = HashiAvailability(blocks, 142, 1000L, null)
    override suspend fun registeredAssets() = registered.toList()
    override suspend fun approvedRequest(requestHash: ByteArray) = approved[EthHex.encode(requestHash)]
    override suspend fun requestStatus(requestHash: ByteArray) = statuses[EthHex.encode(requestHash)]
    override suspend fun incomingRequestHash(ethTxHash: ByteArray) = incoming[EthHex.encode(ethTxHash)]
    override suspend fun lockedAssets(assetId: ByteArray) = locked[EthHex.encode(assetId)] ?: BigInteger.TEN.pow(30)
    override suspend fun denominationFactor(assetId: String): BigInteger =
        if (assetId == HashiBridgeContract.TBCD_ASSET_ID) BigInteger.TEN.pow(38) else BigInteger.ONE
    override suspend fun transferable(account: SoraAccount, assetId: String) =
        if (assetId == HashiBridgeContract.XOR_ASSET_ID) xorTransferable else transferable
    override suspend fun transferFee(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger) = fee
    override suspend fun signTransfer(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger) =
        PreparedSoraTransfer("0x" + "ee".repeat(32), "0x1234", Any()).also { signed += it }
    override suspend fun dryRun(prepared: PreparedSoraTransfer) = dryRun
    override suspend fun submit(prepared: PreparedSoraTransfer, reviewedFee: BigInteger): SoraSubmission {
        onSubmit()
        submitted += prepared
        return submission
    }
    override suspend fun submissionOutcome(wallet: String, extrinsicHash: String) = journal
    override suspend fun requestHashOf(blockHash: String, extrinsicHash: String) = requestHash
}

internal class FakeEthereum : HashiEthereumGateway {
    val balances = mutableMapOf<String, BigInteger>()
    val tokenBalances = mutableMapOf<String, BigInteger>()
    var allowance: BigInteger = BigInteger.ZERO
    val sidechainTokens = mutableMapOf(HashiBridgeContract.XOR_ASSET_ID to XOR_TOKEN)
    val decimals = mutableMapOf(EthHex.encode(XOR_TOKEN) to 18)
    val accepted = mutableSetOf(EthHex.encode(USDT_TOKEN))
    val used = mutableSetOf<String>()
    val withCode = mutableSetOf<String>()
    var pendingNonce: BigInteger = BigInteger.valueOf(5)
    var latestNonce: BigInteger = BigInteger.valueOf(5)
    var revert: String? = null
    var baseFee: BigInteger = BigInteger.valueOf(10_000_000_000)
    var priority: BigInteger = BigInteger.valueOf(1_000_000_000)
    val sent = mutableListOf<String>()
    var sendFailure: Exception? = null
    var onSend: (String) -> Unit = {}
    val receipts = mutableMapOf<String, EthReceipt>()

    override suspend fun balance(address: ByteArray) = balances[EthHex.encode(address)] ?: BigInteger.TEN.pow(20)
    override suspend fun tokenBalance(token: ByteArray, owner: ByteArray) =
        tokenBalances[EthHex.encode(token) + EthHex.encode(owner)] ?: BigInteger.TEN.pow(30)
    override suspend fun allowance(token: ByteArray, owner: ByteArray, spender: ByteArray) = allowance
    override suspend fun decimals(token: ByteArray) = decimals.getValue(EthHex.encode(token))
    override suspend fun sidechainToken(assetId: ByteArray) = sidechainTokens[EthHex.encode(assetId)]
    override suspend fun sidechainAssetOfToken(token: ByteArray): ByteArray =
        sidechainTokens.entries.firstOrNull { it.value.contentEquals(token) }?.key?.let(EthHex::decode) ?: ByteArray(32)
    override suspend fun isAcceptedEthToken(token: ByteArray) = EthHex.encode(token) in accepted
    override suspend fun isUsed(contract: ByteArray, txHash: ByteArray) = EthHex.encode(contract) + EthHex.encode(txHash) in used
    override suspend fun hasNoCode(address: ByteArray) = EthHex.encode(address) !in withCode
    override suspend fun pendingNonce(address: ByteArray) = pendingNonce
    override suspend fun latestNonce(address: ByteArray) = latestNonce
    override suspend fun estimateGas(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger): BigInteger {
        revert?.let { throw EthCallRevertedException(it, "execution reverted: $it") }
        return BigInteger.valueOf(100_000)
    }
    override suspend fun baseFee() = baseFee
    override suspend fun priorityFee() = priority
    override suspend fun send(raw: ByteArray, hash: ByteArray) {
        onSend(EthHex.encode(raw))
        sendFailure?.let { throw it }
        if (EthHex.encode(raw) !in sent) pendingNonce = pendingNonce.add(BigInteger.ONE)
        sent += EthHex.encode(raw)
    }
    override suspend fun receipt(hash: ByteArray) = receipts[EthHex.encode(hash)]

    fun used(contract: ByteArray, txHash: ByteArray) {
        used += EthHex.encode(contract) + EthHex.encode(txHash)
    }

    fun confirm(hash: String, logs: List<EthLog>, status: Int = 1) {
        receipts[hash] = EthReceipt(status, BigInteger.valueOf(100), BigInteger.valueOf(50_000), logs, EthHex.decode(hash))
    }

    fun failNextSends(error: Exception = IOException("timeout")) {
        sendFailure = error
    }
}

internal object Logs {
    fun withdrawal(txHash: ByteArray) = EthLog(HashiBridgeContract.bridge, listOf(HashiReceiptProof.WITHDRAWAL_TOPIC), txHash)

    fun deposit(destination: ByteArray, amount: BigInteger, token: ByteArray) = EthLog(
        HashiBridgeContract.bridge,
        listOf(HashiReceiptProof.DEPOSIT_TOPIC),
        destination + EthAbi.uintWord(amount) + ByteArray(12) + token + ByteArray(32),
    )

    fun valMint(to: ByteArray, amount: BigInteger) = EthLog(
        HashiBridgeContract.valToken,
        listOf(HashiReceiptProof.TRANSFER_TOPIC, ByteArray(32), ByteArray(12) + to),
        EthAbi.uintWord(amount),
    )
}

internal class FakeAccounts(var address: String? = TestEthAccount.ADDRESS) : HashiEthereumAccounts {
    var derivations = 0
    val keysHandedOut = mutableListOf<ByteArray>()

    override suspend fun address(account: SoraAccount) = address

    override suspend fun <T> withPrivateKey(account: SoraAccount, expectedAddress: String, block: (ByteArray) -> T): T {
        derivations += 1
        check(expectedAddress == address) { "HASHI_ETHEREUM_ACCOUNT_MISMATCH" }
        val key = TestEthAccount.privateKey.copyOf()
        keysHandedOut += key
        try {
            return block(key)
        } finally {
            key.fill(0)
        }
    }
}

internal class InMemoryStore : HashiTransferStore {
    private val state = MutableStateFlow<Map<String, HashiTransferRecord>>(emptyMap())
    private val addresses = mutableMapOf<String, String>()
    val history = mutableListOf<HashiTransferRecord>()

    override suspend fun transfers(wallet: String) = state.value.values.filter { it.wallet == wallet }.sortedByDescending { it.createdAt }
    override fun observe(wallet: String): Flow<List<HashiTransferRecord>> = state.map { all -> all.values.filter { it.wallet == wallet } }
    override suspend fun get(wallet: String, id: String) = state.value[id]?.takeIf { it.wallet == wallet }
    override suspend fun put(record: HashiTransferRecord) {
        history += record
        state.value = state.value + (record.id to record)
    }
    override suspend fun ethereumAddress(wallet: String) = addresses[wallet]
    override suspend fun putEthereumAddress(wallet: String, address: String) {
        addresses[wallet] = address
    }
}
