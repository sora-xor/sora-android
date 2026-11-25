package jp.co.soramitsu.common.hashi

import java.math.BigInteger
import jp.co.soramitsu.common.ethereum.EthAddress
import jp.co.soramitsu.common.ethereum.EthHex

/** The Ethereum call that pays out an approved SORA → Ethereum transfer. */
class HashiClaimCall(
    val method: Method,
    /** The contract the claim is sent to; `used(txHash)` is checked on the same contract. */
    contract: ByteArray,
    data: ByteArray,
    /** The claim's `txHash` (the SORA request hash). */
    txHash: ByteArray,
    val amount: BigInteger,
    recipient: ByteArray,
    /** The token whose payout proves the claim (VAL for a mint, the token or zero for Ether). */
    token: ByteArray?,
) {
    val contract: ByteArray = contract.copyOf()
    val data: ByteArray = data.copyOf()
    val txHash: ByteArray = txHash.copyOf()
    val recipient: ByteArray = recipient.copyOf()
    val token: ByteArray? = token?.copyOf()

    enum class Method { RECEIVE_BY_ETHEREUM_ASSET_ADDRESS, RECEIVE_BY_SIDECHAIN_ASSET_ID, MINT_TOKENS_BY_PEERS }
}

/** The approved amount is not the one the saved transfer expects; nothing is claimed. */
class HashiClaimAmountMismatchException(val expected: BigInteger, val approved: BigInteger) :
    IllegalArgumentException("HASHI_CLAIM_AMOUNT_MISMATCH")

/**
 * Picks the claim call for an approved request and binds it to the saved transfer (spec §8 F):
 *
 * - asset VAL (`0x020004…`) → VAL master `mintTokensByPeers` (`from` last); the currency must be
 *   the VAL token
 * - any other `TokenAddress` → Bridge `receiveByEthereumAssetAddress`; the token must be the one
 *   registered for the asset
 * - `AssetId` → Bridge `receiveBySidechainAssetId`; the id must be the saved `Thischain` asset
 *
 * The request hash and network are checked when the answer is parsed. Here: the amount must equal
 * [expectedAmount] (computed from the saved SORA amount), `to` must be the saved, non-zero
 * recipient. Amount, to, from, txHash, r and s are passed exactly as SORA approved them; v is
 * normalised to 27/28. The decommissioned legacy XOR master is never a target.
 */
object HashiClaimPlanner {

    fun plan(
        asset: HashiBridgeAsset,
        request: HashiApprovedRequest,
        expectedRecipient: ByteArray,
        expectedAmount: BigInteger,
    ): HashiClaimCall {
        require(!EthAddress.equal(expectedRecipient, EthAddress.ZERO)) { "HASHI_CLAIM_RECIPIENT_ZERO" }
        require(EthAddress.equal(request.to, expectedRecipient)) { "HASHI_CLAIM_RECIPIENT_MISMATCH" }
        if (request.amount != expectedAmount) throw HashiClaimAmountMismatchException(expectedAmount, request.amount)
        val v = request.signatures.map { it.contractV }
        val r = request.signatures.map { it.r }
        val s = request.signatures.map { it.s }
        val currency = request.currency
        val claim = when {
            asset.assetId == HashiBridgeContract.VAL_ASSET_ID -> {
                require(currency is HashiCurrency.TokenAddress) { "HASHI_CLAIM_CURRENCY_MISMATCH" }
                require(EthAddress.equal(currency.address, HashiBridgeContract.valToken)) { "HASHI_CLAIM_TOKEN_MISMATCH" }
                require(EthAddress.equal(currency.address, asset.ethToken)) { "HASHI_CLAIM_TOKEN_MISMATCH" }
                HashiClaimCall(
                    method = HashiClaimCall.Method.MINT_TOKENS_BY_PEERS,
                    contract = HashiBridgeContract.valMaster,
                    data = HashiBridgeCalls.mintTokensByPeers(
                        currency.address, request.amount, request.to, request.txHash, v, r, s, request.from,
                    ),
                    txHash = request.txHash,
                    amount = request.amount,
                    recipient = request.to,
                    token = currency.address,
                )
            }
            currency is HashiCurrency.TokenAddress -> {
                require(asset.kind != HashiAssetKind.THISCHAIN) { "HASHI_CLAIM_CURRENCY_MISMATCH" }
                require(EthAddress.equal(currency.address, asset.ethToken)) { "HASHI_CLAIM_TOKEN_MISMATCH" }
                HashiClaimCall(
                    method = HashiClaimCall.Method.RECEIVE_BY_ETHEREUM_ASSET_ADDRESS,
                    contract = HashiBridgeContract.bridge,
                    data = HashiBridgeCalls.receiveByEthereumAssetAddress(
                        currency.address, request.amount, request.to, request.from, request.txHash, v, r, s,
                    ),
                    txHash = request.txHash,
                    amount = request.amount,
                    recipient = request.to,
                    token = currency.address,
                )
            }
            currency is HashiCurrency.AssetId -> {
                // Only SORA-native assets are approved by asset id; anything else is inconsistent.
                require(asset.kind == HashiAssetKind.THISCHAIN) { "HASHI_CLAIM_CURRENCY_MISMATCH" }
                require(EthHex.encode(currency.id) == asset.assetId) { "HASHI_CLAIM_ASSET_MISMATCH" }
                HashiClaimCall(
                    method = HashiClaimCall.Method.RECEIVE_BY_SIDECHAIN_ASSET_ID,
                    contract = HashiBridgeContract.bridge,
                    data = HashiBridgeCalls.receiveBySidechainAssetId(
                        currency.id, request.amount, request.to, request.from, request.txHash, v, r, s,
                    ),
                    txHash = request.txHash,
                    amount = request.amount,
                    recipient = request.to,
                    token = asset.ethToken,
                )
            }
            else -> throw IllegalArgumentException("HASHI_CLAIM_CURRENCY_MISMATCH")
        }
        check(!EthAddress.equal(claim.contract, HashiBridgeContract.legacyXorMaster)) { "HASHI_CLAIM_LEGACY_MASTER" }
        return claim
    }

    /** The web app's fallback gas limit for a claim of [asset], for estimates before approval. */
    fun fallbackGas(asset: HashiBridgeAsset): BigInteger = when {
        asset.assetId == HashiBridgeContract.VAL_ASSET_ID -> HashiBridgeContract.GAS_MINT_TOKENS_BY_PEERS
        asset.kind == HashiAssetKind.THISCHAIN -> HashiBridgeContract.GAS_RECEIVE_BY_SIDECHAIN_ASSET_ID
        asset.isNativeEther -> HashiBridgeContract.GAS_RECEIVE_BY_ETHEREUM_ASSET_ADDRESS_ETH
        else -> HashiBridgeContract.GAS_RECEIVE_BY_ETHEREUM_ASSET_ADDRESS_TOKEN
    }
}
