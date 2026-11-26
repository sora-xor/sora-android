package jp.co.soramitsu.feature_wallet_impl.bridge

import java.math.BigInteger
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.hashi.EthReceipt
import jp.co.soramitsu.common.hashi.EthTransactionLookup
import jp.co.soramitsu.common.hashi.HashiApprovedRequest
import jp.co.soramitsu.common.hashi.HashiAvailability
import jp.co.soramitsu.common.hashi.HashiDryRunResult
import jp.co.soramitsu.common.hashi.HashiRegisteredAsset
import jp.co.soramitsu.common.hashi.HashiRequestStatusValue

/** A signed SORA transfer that was not sent yet. [handle] is the signing layer's own object. */
class PreparedSoraTransfer(val extrinsicHash: String, val encoded: String, val handle: Any)

/** What SORA says about a submitted extrinsic. */
sealed class SoraSubmission {
    class Included(val blockHash: String) : SoraSubmission()
    class FailedOnChain(val detail: String?) : SoraSubmission()

    /** Refused before broadcast (by the node or a pre-transport check): nothing was sent. */
    class NotSent(val detail: String?) : SoraSubmission()

    /** Sent, final status not known yet. */
    object Pending : SoraSubmission()
}

/** SORA mainnet as the bridge needs it. Every read failure throws. */
interface HashiSoraGateway {
    suspend fun availability(): HashiAvailability
    suspend fun registeredAssets(): List<HashiRegisteredAsset>
    suspend fun approvedRequest(requestHash: ByteArray): HashiApprovedRequest?
    suspend fun requestStatus(requestHash: ByteArray): HashiRequestStatusValue?
    suspend fun incomingRequestHash(ethTxHash: ByteArray): ByteArray?
    suspend fun lockedAssets(assetId: ByteArray): BigInteger
    suspend fun denominationFactor(assetId: String): BigInteger

    /** Transferable balance in SORA units of [assetId] for [account]. */
    suspend fun transferable(account: SoraAccount, assetId: String): BigInteger

    /** The network fee (XOR units) of the exact transfer. */
    suspend fun transferFee(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger): BigInteger

    /** Signs the transfer with the account's SORA key; nothing is sent. */
    suspend fun signTransfer(account: SoraAccount, assetId: String, to: ByteArray, amount: BigInteger): PreparedSoraTransfer

    suspend fun dryRun(prepared: PreparedSoraTransfer): HashiDryRunResult

    /** Sends [prepared] once and waits for finality; the fee must still be [reviewedFee]. */
    suspend fun submit(prepared: PreparedSoraTransfer, reviewedFee: BigInteger): SoraSubmission

    /** The wallet's journal of a submitted extrinsic (after a restart); null when it has none. */
    suspend fun submissionOutcome(wallet: String, extrinsicHash: String): SoraSubmission?

    suspend fun requestHashOf(blockHash: String, extrinsicHash: String): ByteArray?
}

/** Ethereum mainnet as the bridge needs it. */
interface HashiEthereumGateway {
    suspend fun balance(address: ByteArray): BigInteger
    suspend fun tokenBalance(token: ByteArray, owner: ByteArray): BigInteger
    suspend fun allowance(token: ByteArray, owner: ByteArray, spender: ByteArray): BigInteger
    suspend fun decimals(token: ByteArray): Int
    suspend fun sidechainToken(assetId: ByteArray): ByteArray?
    suspend fun sidechainAssetOfToken(token: ByteArray): ByteArray
    suspend fun isAcceptedEthToken(token: ByteArray): Boolean
    suspend fun isUsed(contract: ByteArray, txHash: ByteArray): Boolean
    suspend fun hasNoCode(address: ByteArray): Boolean
    suspend fun pendingNonce(address: ByteArray): BigInteger
    suspend fun latestNonce(address: ByteArray): BigInteger
    suspend fun estimateGas(from: ByteArray, to: ByteArray, data: ByteArray, value: BigInteger): BigInteger
    suspend fun baseFee(): BigInteger
    suspend fun priorityFee(): BigInteger
    suspend fun send(raw: ByteArray, hash: ByteArray)
    suspend fun receipt(hash: ByteArray): EthReceipt?

    /** What every endpoint knows of [hash]; throws when too few answer to call it absent. */
    suspend fun lookupEverywhere(hash: ByteArray): EthTransactionLookup
}

/** The wallet's Ethereum account (spec §1). */
interface HashiEthereumAccounts {
    /** The checksummed address of [account]'s Ethereum account, or null when it has no phrase. */
    suspend fun address(account: SoraAccount): String?

    /**
     * Derives the private key, checks it is [expectedAddress]'s, runs [block] and wipes the key.
     * The key never leaves [block].
     */
    suspend fun <T> withPrivateKey(account: SoraAccount, expectedAddress: String, block: (ByteArray) -> T): T

    /**
     * [address] derived again from the stored phrase now (not from the cache), or null when the
     * account can no longer derive it (re-imported from a raw seed, phrase unreadable). Used
     * before a transfer whose claim this account will have to sign.
     */
    suspend fun verifiedAddress(account: SoraAccount): String? = address(account)
}
