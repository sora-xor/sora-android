/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_assets_impl.domain

import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.androidfoundation.coroutine.CoroutineManager
import jp.co.soramitsu.androidfoundation.format.isZero
import jp.co.soramitsu.androidfoundation.format.orZero
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.XorAssetBalance
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.sora2TransactionOutcome
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class AssetsInteractorImpl constructor(
    private val assetsRepository: AssetsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val coroutineManager: CoroutineManager,
    private val transactionBuilder: TransactionBuilder,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val userRepository: UserRepository,
) : AssetsInteractor {

    override suspend fun calcTransactionFee(
        to: String,
        token: Token,
        amount: BigDecimal,
        expectedWalletId: String,
    ): BigDecimal? {
        val selected = userRepository.getCurSoraAccount()
        check(selected.substrateAddress == expectedWalletId) {
            "SORA2_SELECTED_WALLET_CHANGED"
        }
        return assetsRepository.calcTransactionFee(
            selected.substrateAddress,
            to,
            token,
            amount,
        )
    }

    override suspend fun isNotEnoughXorLeftAfterTransaction(
        networkFeeInXor: BigDecimal,
        xorChange: BigDecimal?,
    ): Boolean {
        val xorAssetBalanceAmount =
            getAssetOrThrow(SubstrateOptionsProvider.feeAssetId).balance.transferable

        return xorAssetBalanceAmount.minus(xorChange.orZero()).minus(networkFeeInXor) <= networkFeeInXor
    }

    override suspend fun getAccountName(): String = userRepository.getCurSoraAccount().accountName

    override suspend fun getAssetOrThrow(assetId: String): Asset {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return assetsRepository.getAsset(assetId, address)
            ?: throw NoSuchElementException("$assetId not found")
    }

    override suspend fun getCurSoraAccount(): SoraAccount =
        userRepository.getCurSoraAccount()

    override suspend fun getPublicKeyHex(withPrefix: Boolean): String {
        return userRepository.getCurSoraAccount().substrateAddress.toAccountId()
            .toHexString(withPrefix)
    }

    override suspend fun getWhitelistAssets(): List<Asset> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return assetsRepository.getAssetsWhitelist(address)
    }

    override suspend fun getXorBalance(precision: Int): XorAssetBalance {
        return userRepository.getCurSoraAccount()
            .let { account -> assetsRepository.getXORBalance(account.substrateAddress, precision) }
    }

    override fun flowCurSoraAccount(): Flow<SoraAccount> =
        userRepository.flowCurSoraAccount()

    override suspend fun isWhitelistedToken(tokenId: String): Boolean {
        return assetsRepository.isWhitelistedToken(tokenId)
    }

    override suspend fun getTokensList(): List<Token> {
        return assetsRepository.tokensList()
    }

    /**
     * Signs and submits one transfer and reports what is known about it afterwards. All four
     * outcomes are returned, never thrown: confirmed, failed on chain, refused by the node before
     * broadcast, or submitted and still waiting. An exception from here means the transfer never
     * reached the network.
     */
    override suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal,
        expectedWalletId: String,
        confirmedDuplicate: Boolean,
    ): TransactionOutcome {
        val soraAccount = userRepository.getCurSoraAccount()
        check(soraAccount.substrateAddress == expectedWalletId) {
            "SORA2_SELECTED_WALLET_CHANGED"
        }
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val outcome = try {
            sora2TransactionOutcome {
                assetsRepository.observeTransfer(
                    keypair,
                    soraAccount.substrateAddress,
                    to,
                    token,
                    amount,
                    fee,
                    validateSelectedWallet = {
                        check(
                            userRepository.getCurSoraAccount().substrateAddress ==
                                expectedWalletId
                        ) { "SORA2_SELECTED_WALLET_CHANGED" }
                    },
                    confirmedDuplicate = confirmedDuplicate,
                )
            }
        } finally {
            keypair.privateKey.fill(0)
            keypair.nonce.fill(0)
        }
        // Every outcome is listed in Activity as this transfer; its status (pending, confirmed,
        // failed, not sent) comes from the durable journal, not from what is saved here.
        persistTransferHistoryBestEffort(
            walletId = soraAccount.substrateAddress,
            transactionHash = outcome.txHash,
            blockHash = (outcome as? TransactionOutcome.Confirmed)?.blockHash,
            to = to,
            token = token,
            amount = amount,
            fee = fee,
        )
        return outcome
    }

    private fun persistTransferHistoryBestEffort(
        walletId: String,
        transactionHash: String,
        blockHash: String?,
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal,
    ) {
        try {
            transactionHistoryRepository.saveTransaction(
                walletId,
                transactionBuilder.buildTransfer(
                    txHash = transactionHash,
                    blockHash = blockHash,
                    fee = fee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    amount = amount,
                    peer = to,
                    type = TransactionTransferType.OUTGOING,
                    token = token,
                )
            )
        } catch (_: Throwable) {
            // The chain result is authoritative. Local history and telemetry are best-effort and
            // must never turn a confirmed or submission-unknown transfer into a retryable failure.
            try {
                FirebaseWrapper.recordErrorClass(
                    FirebaseWrapper.PrivacySafeErrorClass.STATE_FAILURE
                )
            } catch (_: Throwable) {
                // Telemetry is outside the transaction result boundary.
            }
        }
    }

    override fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset?> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            subscribeAssetOfAccount(it, tokenId)
        }
    }

    override fun subscribeAssetOfAccount(
        soraAccount: SoraAccount,
        tokenId: String,
    ): Flow<Asset?> = assetsRepository.subscribeAsset(soraAccount.substrateAddress, tokenId)

    override fun subscribeAssetsActiveOfCurAccount(): Flow<List<Asset>> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            assetsRepository.subscribeAssetsActive(it.substrateAddress)
        }
    }

    override suspend fun toggleVisibilityOfToken(tokenId: String, visibility: Boolean) {
        val curAccount = userRepository.getCurSoraAccount()
        assetsRepository.toggleVisibilityOfToken(tokenId, visibility, curAccount)
    }

    override suspend fun tokenFavoriteOff(assetIds: List<String>) {
        val curAccount = userRepository.getCurSoraAccount()
        return assetsRepository.hideAssets(assetIds, curAccount)
    }

    override suspend fun tokenFavoriteOn(assetIds: List<String>) {
        val curAccount = userRepository.getCurSoraAccount()
        return assetsRepository.displayAssets(assetIds, curAccount)
    }

    override suspend fun transfer(to: String, token: Token, amount: BigDecimal): Result<String> {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        return assetsRepository.transfer(keypair, soraAccount.substrateAddress, to, token, amount)
    }

    override suspend fun updateAssetPositions(assetPositions: Map<String, Int>) {
        val curAccount = userRepository.getCurSoraAccount()
        assetsRepository.updateAssetPositions(assetPositions, curAccount)
    }

    override suspend fun updateBalanceVisibleAssets() {
        val soraAccount = userRepository.getCurSoraAccount()
        assetsRepository.updateBalancesVisibleAssets(soraAccount.substrateAddress)
    }

    override suspend fun fetchBalance(
        address: String,
        ids: List<String>,
        precision: Int,
    ): List<BigDecimal> {
        return assetsRepository.fetchBalance(address, ids).map {
            mapBalance(it, precision)
        }
    }

    override suspend fun updateWhitelistBalances() {
        val soraAccount = userRepository.getCurSoraAccount()
        assetsRepository.updateWhitelistBalances(soraAccount.substrateAddress)

        if (needFakeBalance()) {
            coroutineManager.applicationScope.launch(coroutineManager.io) {
                addFakeBalance(soraAccount)
            }
        }
    }

    private suspend fun needFakeBalance(): Boolean {
        if (!BuildUtils.isFlavors(Flavor.SORALUTION)) return false
        val xorBalance = getAssetOrThrow(SubstrateOptionsProvider.feeAssetId)
            .balance.transferable.orZero()
        return xorBalance.isZero()
    }

    private suspend fun addFakeBalance(soraAccount: SoraAccount) {
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val assetsIds = AssetHolder.getIds().subList(0, AssetHolder.getIds().lastIndex)
        return assetsRepository.addFakeBalance(keypair, soraAccount, assetsIds)
    }
}
