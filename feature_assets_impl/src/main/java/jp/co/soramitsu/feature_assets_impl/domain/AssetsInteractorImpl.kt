/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_assets_impl.domain

import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Asset
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.Flavor
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.orZero
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_assets_api.data.models.XorAssetBalance
import jp.co.soramitsu.feature_assets_api.domain.interfaces.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionTransferType
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
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
        amount: BigDecimal
    ): BigDecimal? {
        return userRepository.getCurSoraAccount().let {
            assetsRepository.calcTransactionFee(it.substrateAddress, to, token, amount)
        }
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

    override suspend fun getVisibleAssets(): List<Asset> {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return assetsRepository.getAssetsFavorite(address)
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

    override suspend fun observeTransfer(
        to: String,
        token: Token,
        amount: BigDecimal,
        fee: BigDecimal
    ): String {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val status = assetsRepository.observeTransfer(
            keypair,
            soraAccount.substrateAddress,
            to,
            token,
            amount,
            fee
        )
        if (status.success) {
            transactionHistoryRepository.saveTransaction(
                transactionBuilder.buildTransfer(
                    txHash = status.txHash,
                    blockHash = status.blockHash,
                    fee = fee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    amount = amount,
                    peer = to,
                    type = TransactionTransferType.OUTGOING,
                    token = token,
                )
            )
        }
        return if (status.success) status.txHash else ""
    }

    override fun subscribeAssetOfCurAccount(tokenId: String): Flow<Asset> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            assetsRepository.subscribeAsset(it.substrateAddress, tokenId)
        }
    }

    override fun subscribeAssetsActiveOfCurAccount(): Flow<List<Asset>> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            assetsRepository.subscribeAssetsActive(it.substrateAddress)
        }
    }

    override fun subscribeAssetsFavoriteOfAccount(soraAccount: SoraAccount): Flow<List<Asset>> =
        assetsRepository.subscribeAssetsFavorite(soraAccount.substrateAddress)

    override fun subscribeAssetsVisibleOfCurAccount(): Flow<List<Asset>> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            assetsRepository.subscribeAssetsVisible(it.substrateAddress)
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

    override suspend fun updateBalancesVisibleAssets() {
        assetsRepository.updateBalancesVisibleAssets(userRepository.getCurSoraAccount().substrateAddress)
    }

    override suspend fun updateWhitelistBalances(update: Boolean) {
        val soraAccount = userRepository.getCurSoraAccount()
        assetsRepository.updateWhitelistBalances(soraAccount.substrateAddress, update)

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
