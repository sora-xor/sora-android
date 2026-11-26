package jp.co.soramitsu.feature_wallet_impl.bridge

import java.math.BigInteger
import java.math.RoundingMode
import java.time.Instant
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.ethereum.EthereumKeyDerivation
import jp.co.soramitsu.common.hashi.EthReceipt
import jp.co.soramitsu.common.hashi.EthereumRpcClient
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiAvailability
import jp.co.soramitsu.common.hashi.HashiBridgeContract
import jp.co.soramitsu.common.hashi.HashiDryRunResult
import jp.co.soramitsu.common.hashi.HashiRegisteredAsset
import jp.co.soramitsu.common.hashi.HashiRequestStatusValue
import jp.co.soramitsu.common.hashi.HashiSignerReportSource
import jp.co.soramitsu.common.hashi.HashiSignerReports
import jp.co.soramitsu.common.hashi.HashiSoraRpcClient
import jp.co.soramitsu.common.network.BoundedHttpTextClient
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.Sora2PendingSubmissionLocal
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.sora.substrate.runtime.Sora2BoundedRuntimeRpcClient
import jp.co.soramitsu.sora.substrate.substrate.DefinitelyNotSubmitted
import jp.co.soramitsu.sora.substrate.substrate.EthBridgeChainSupport
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicRefusedByNodeException
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicSubmissionUnknown
import jp.co.soramitsu.sora.substrate.substrate.canonicalExtrinsicHash
import jp.co.soramitsu.sora.substrate.substrate.transferToSidechain
import kotlinx.coroutines.CancellationException

/** SORA mainnet through the wallet's reviewed endpoint, signing and submission paths. */
class AppHashiSoraGateway(
    private val rpc: HashiSoraRpcClient,
    private val signerReports: HashiSignerReportSource,
    private val chainSupport: EthBridgeChainSupport,
    private val extrinsicManager: ExtrinsicManager,
    private val runtimeRpcClient: Sora2BoundedRuntimeRpcClient,
    private val credentials: CredentialsRepository,
    private val assetsInteractor: AssetsInteractor,
    private val database: AppDatabase,
) : HashiSoraGateway {

    override suspend fun availability(): HashiAvailability = rpc.availability(signerReports, Instant.now())

    override suspend fun registeredAssets(): List<HashiRegisteredAsset> = rpc.registeredAssets()

    override suspend fun approvedRequest(requestHash: ByteArray): HashiApprovedRequest? = rpc.approvedRequest(requestHash)

    override suspend fun requestStatus(requestHash: ByteArray): HashiRequestStatusValue? = rpc.requestStatusValue(requestHash)

    override suspend fun incomingRequestHash(ethTxHash: ByteArray): ByteArray? = rpc.incomingRequestHash(ethTxHash)

    override suspend fun lockedAssets(assetId: ByteArray): BigInteger = rpc.lockedAssets(assetId)

    override suspend fun denominationFactor(assetId: String): BigInteger = rpc.denominationFactor(assetId)

    override suspend fun transferable(account: SoraAccount, assetId: String): BigInteger {
        check(assetsInteractor.getCurSoraAccount().substrateAddress == account.substrateAddress) { "HASHI_ACCOUNT_NOT_SELECTED" }
        val asset = assetsInteractor.getAssetOrThrow(assetId)
        return asset.balance.transferable.setScale(asset.token.precision, RoundingMode.DOWN).unscaledValue().max(BigInteger.ZERO)
    }

    override suspend fun transferFee(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger): BigInteger =
        extrinsicManager.calcFee(account.substrateAddress) {
            transferToSidechain(assetId, to, amount, HashiBridgeContract.NETWORK_ID)
        }?.takeIf { it.signum() > 0 } ?: error("HASHI_FEE_UNAVAILABLE")

    override suspend fun signTransfer(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger): PreparedSoraTransfer {
        val keypair = credentials.retrieveKeyPair(account)
        val prepared = extrinsicManager.prepareExtrinsic(account.substrateAddress, keypair) {
            transferToSidechain(assetId, to, amount, HashiBridgeContract.NETWORK_ID)
        }
        return PreparedSoraTransfer(prepared.transactionHash, prepared.encoded, prepared)
    }

    override suspend fun dryRun(prepared: PreparedSoraTransfer): HashiDryRunResult =
        rpc.dryRun(prepared.encoded, chainSupport.moduleErrorNamer())

    override suspend fun submit(prepared: PreparedSoraTransfer, reviewedFee: BigInteger): SoraSubmission {
        val extrinsic = prepared.handle as ExtrinsicManager.PreparedExtrinsic
        return try {
            val status = extrinsicManager.submitPreparedAndWait(extrinsic) {
                // The fee of these exact bytes must still be the one the person confirmed.
                check(runtimeRpcClient.queryPartialFee(extrinsic.encoded) == reviewedFee) { "SORA2_TRANSFER_FEE_CHANGED" }
            }
            if (status.success) {
                SoraSubmission.Included(checkNotNull(status.blockHash) { "HASHI_BLOCK_HASH_MISSING" })
            } else {
                SoraSubmission.FailedOnChain(status.failureDetail)
            }
        } catch (refused: ExtrinsicRefusedByNodeException) {
            // The node refused it explicitly: nothing was broadcast.
            SoraSubmission.NotSent(refused.refusal.toString())
        } catch (error: Throwable) {
            when {
                error is CancellationException -> throw error
                // Sent; the final status is resolved from the wallet's journal by its hash.
                error is ExtrinsicSubmissionUnknown -> SoraSubmission.Pending
                // Stopped before transport (a changed fee, a runtime change): nothing was sent.
                error is DefinitelyNotSubmitted -> SoraSubmission.NotSent(error.cause?.message ?: "NOT_SENT")
                else -> throw error
            }
        }
    }

    override suspend fun submissionOutcome(wallet: String, extrinsicHash: String): SoraSubmission? {
        val canonical = extrinsicHash.canonicalExtrinsicHash()
        val row = database.walletIdentityDao().getSora2PendingSubmission("sora2:" + canonical.removePrefix("0x")) ?: return null
        check(row.walletId == wallet) { "HASHI_JOURNAL_WALLET_MISMATCH" }
        return when (row.state) {
            Sora2PendingSubmissionLocal.STATE_FINALIZED_SUCCESS -> row.terminalBlockHash?.let { SoraSubmission.Included(it) } ?: SoraSubmission.Pending
            Sora2PendingSubmissionLocal.STATE_FINALIZED_FAILURE -> SoraSubmission.FailedOnChain(null)
            Sora2PendingSubmissionLocal.STATE_EXPIRED_NOT_INCLUDED -> SoraSubmission.NotSent("EXPIRED_NOT_INCLUDED")
            else -> SoraSubmission.Pending
        }
    }

    override suspend fun requestHashOf(blockHash: String, extrinsicHash: String): ByteArray? =
        chainSupport.requestHashOf(rpc, blockHash, extrinsicHash)
}

class AppHashiEthereumGateway(private val client: EthereumRpcClient) : HashiEthereumGateway {
    override suspend fun balance(address: ByteArray) = client.balance(address)
    override suspend fun tokenBalance(token: ByteArray, owner: ByteArray) = client.erc20Balance(token, owner)
    override suspend fun allowance(token: ByteArray, owner: ByteArray, spender: ByteArray) = client.erc20Allowance(token, owner, spender)
    override suspend fun decimals(token: ByteArray) = client.erc20Decimals(token)
    override suspend fun sidechainToken(assetId: ByteArray) = client.sidechainToken(assetId)
    override suspend fun sidechainAssetOfToken(token: ByteArray) = client.sidechainAssetOfToken(token)
    override suspend fun isAcceptedEthToken(token: ByteArray) = client.isAcceptedEthToken(token)
    override suspend fun isUsed(contract: ByteArray, txHash: ByteArray) = client.isUsed(contract, txHash)
    override suspend fun hasNoCode(address: ByteArray) = client.hasNoCode(address)
    override suspend fun pendingNonce(address: ByteArray) = client.pendingNonce(address)
    override suspend fun latestNonce(address: ByteArray) = client.latestNonce(address)
    override suspend fun estimateGas(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger) =
        client.estimateGas(from, to, data, value)
    override suspend fun baseFee() = client.latestBaseFee()
    override suspend fun priorityFee() = client.maxPriorityFeePerGas()
    override suspend fun send(raw: ByteArray, hash: ByteArray) {
        client.sendRawTransaction(raw, hash)
    }
    override suspend fun receipt(hash: ByteArray): EthReceipt? = client.receipt(hash)
}

/**
 * The Ethereum account of a SORA wallet: derived from the stored recovery phrase only after the
 * phrase is proven to re-derive the SORA account (`retrieveBackupMaterial`). The address is cached;
 * the private key is derived for one signature and wiped right after. A wallet without a phrase
 * (raw seed or JSON import) or a watch-only one has no Ethereum account.
 */
class AppHashiEthereumAccounts(
    private val credentials: CredentialsRepository,
    private val store: HashiTransferStore,
) : HashiEthereumAccounts {

    override suspend fun address(account: SoraAccount): String? {
        store.ethereumAddress(account.substrateAddress)?.let { return it }
        val key = deriveOrNull(account) ?: return null
        return try {
            key.checksumAddress.also { store.putEthereumAddress(account.substrateAddress, it) }
        } finally {
            key.destroy()
        }
    }

    override suspend fun <T> withPrivateKey(account: SoraAccount, expectedAddress: String, block: (ByteArray) -> T): T {
        val key = deriveOrNull(account) ?: error("HASHI_NO_ETHEREUM_ACCOUNT")
        try {
            check(key.checksumAddress == expectedAddress) { "HASHI_ETHEREUM_ACCOUNT_MISMATCH" }
            return block(key.privateKey)
        } finally {
            key.destroy()
        }
    }

    private suspend fun deriveOrNull(account: SoraAccount): EthereumKeyDerivation.DerivedEthereumKey? {
        if (credentials.isExplicitWatchOnly(account)) return null
        val material = credentials.retrieveBackupMaterial(account)
        if (!material.hasPhrase) return null
        return EthereumKeyDerivation.derive(material.phraseWords.joinToString(" "))
    }
}

/** The operators' signer report from the app's remote config document, read fresh each time. */
class RemoteConfigSignerReports(private val http: BoundedHttpTextClient) : HashiSignerReportSource {
    override suspend fun configJson(): String = http.getUtf8(HashiSignerReports.CONFIG_URL, MAXIMUM_CONFIG_BYTES)

    private companion object {
        const val MAXIMUM_CONFIG_BYTES = 4 * 1024 * 1024
    }
}
