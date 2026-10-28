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

package jp.co.soramitsu.feature_polkaswap_impl.domain

import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.androidfoundation.format.isZero
import jp.co.soramitsu.androidfoundation.format.safeDivide
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.TransactionOutcome
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculateAddLiquidityAmount
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.estimateAddingShareOfPool
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.Transaction
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.Sora2SubmissionActivity
import jp.co.soramitsu.sora.substrate.substrate.sora2TransactionOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@ExperimentalCoroutinesApi
class PoolsInteractorImpl(
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val polkaswapRepository: PolkaswapRepository,
    private val polkaswapSubscriptionRepository: PolkaswapSubscriptionRepository,
    private val polkaswapExtrinsicRepository: PolkaswapExtrinsicRepository,
    private val assetsRepository: AssetsRepository,
    private val transactionBuilder: TransactionBuilder,
) : PolkaswapInteractorImpl(polkaswapRepository), PoolsInteractor {

    override suspend fun poolFavoriteOn(ids: StringPair) {
        val curAccount = userRepository.getCurSoraAccount()
        polkaswapRepository.poolFavoriteOn(ids, curAccount)
    }

    override suspend fun poolFavoriteOff(ids: StringPair) {
        val curAccount = userRepository.getCurSoraAccount()
        polkaswapRepository.poolFavoriteOff(ids, curAccount)
    }

    override suspend fun updatePoolPosition(pools: Map<StringPair, Int>) {
        val curAccount = userRepository.getCurSoraAccount()
        polkaswapRepository.updatePoolPosition(pools, curAccount)
    }

    override suspend fun fetchRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token
    ): BigDecimal {
        // Fee estimates belong to the current account and token pair. A missing estimate
        // must remain retryable, and a previous review must not become the next one's fee.
        return polkaswapExtrinsicRepository.calcRemoveLiquidityNetworkFee(
            tokenId1.id to tokenId1.precision,
            tokenId2.id to tokenId2.precision,
            userRepository.getCurSoraAccount().substrateAddress,
        ) ?: BigDecimal.ZERO
    }

    private suspend fun getLiquidityNetworkFee(
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal {
        val user = userRepository.getCurSoraAccount().substrateAddress
        val result = polkaswapExtrinsicRepository.calcAddLiquidityNetworkFee(
            user,
            tokenFrom.id to tokenFrom.precision,
            tokenTo.id to tokenTo.precision,
            tokenFromAmount,
            tokenToAmount,
            pairEnabled,
            pairPresented,
            slippageTolerance,
        )
        return result ?: BigDecimal.ZERO
    }

    @OptIn(FlowPreview::class)
    override fun subscribePoolsChangesOfAccount(address: String): Flow<String> {
        return polkaswapSubscriptionRepository.subscribeToPoolsAssets(address)
            .debounce(300)
            .flatMapLatest {
                polkaswapSubscriptionRepository.subscribeToPoolsData(address)
            }
    }

    override fun subscribePoolsChanges(): Flow<String> =
        userRepository.flowCurSoraAccount().flatMapLatest { soraAccount ->
            subscribePoolsChangesOfAccount(soraAccount.substrateAddress)
        }

    override suspend fun calcLiquidityDetails(
        tokenFrom: Token,
        tokenTo: Token,
        reservesFrom: BigDecimal,
        reservesTo: BigDecimal,
        pooledTo: BigDecimal,
        baseAmount: BigDecimal,
        targetAmount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
        pairEnabled: Boolean,
        pairPresented: Boolean
    ): LiquidityDetails {
        val resultAmount = if (!pairPresented || reservesFrom.isZero() || reservesTo.isZero()) {
            targetAmount
        } else {
            calculateAddLiquidityAmount(
                baseAmount,
                reservesFrom,
                reservesTo,
                tokenFrom.precision,
                tokenTo.precision,
                desired
            )
        }

        val networkFee = getLiquidityNetworkFee(
            tokenFrom,
            tokenTo,
            tokenFromAmount = if (desired == WithDesired.INPUT) baseAmount else resultAmount,
            tokenToAmount = if (desired == WithDesired.OUTPUT) baseAmount else resultAmount,
            pairEnabled = pairEnabled,
            pairPresented = pairPresented,
            slippageTolerance = slippageTolerance
        )

        val perFirst = baseAmount.safeDivide(resultAmount)
        val perSecond = resultAmount.safeDivide(baseAmount)

        val shareOfPool = estimateAddingShareOfPool(
            if (desired == WithDesired.INPUT) resultAmount else baseAmount,
            pooledTo,
            reservesTo
        )

        return LiquidityDetails(
            baseAmount = baseAmount,
            targetAmount = resultAmount,
            perFirst = if (desired == WithDesired.INPUT) perFirst else perSecond,
            perSecond = if (desired == WithDesired.OUTPUT) perFirst else perSecond,
            networkFee = networkFee,
            shareOfPool = shareOfPool,
            pairPresented = pairPresented,
            pairEnabled = pairEnabled
        )
    }

    override suspend fun observeAddLiquidity(
        tokenFrom: Token,
        tokenTo: Token,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        enabled: Boolean,
        presented: Boolean,
        slippageTolerance: Double,
        confirmedDuplicate: Boolean,
    ): TransactionOutcome {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val networkFee = getLiquidityNetworkFee(
            tokenFrom,
            tokenTo,
            amountFrom,
            amountTo,
            enabled,
            presented,
            slippageTolerance
        )

        val outcome = sora2TransactionOutcome {
            polkaswapExtrinsicRepository.observeAddLiquidity(
                soraAccount.substrateAddress,
                keypair,
                tokenFrom,
                tokenTo,
                amountFrom,
                amountTo,
                enabled,
                presented,
                slippageTolerance,
                Sora2SubmissionActivity.liquidity(
                    add = true,
                    token1Id = tokenFrom.id,
                    token2Id = tokenTo.id,
                    amount1 = amountFrom,
                    amount2 = amountTo,
                    fee = networkFee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(soraAccount.substrateAddress) {
            transactionBuilder.buildLiquidity(
                txHash = outcome.txHash,
                blockHash = (outcome as? TransactionOutcome.Confirmed)?.blockHash,
                fee = networkFee,
                status = TransactionStatus.PENDING,
                date = Date().time,
                token1 = tokenFrom,
                token2 = tokenTo,
                amount1 = amountFrom,
                amount2 = amountTo,
                type = TransactionLiquidityType.ADD,
            )
        }
        return outcome
    }

    /** Every outcome is listed in Activity with what it was; its status comes from the journal. */
    private fun saveToActivityBestEffort(walletId: String, build: () -> Transaction) {
        try {
            transactionHistoryRepository.saveTransaction(walletId, build())
        } catch (_: Exception) {
            // Activity is rebuilt from the durable journal; it never changes a result.
        }
    }

    override suspend fun updatePools() {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return polkaswapSubscriptionRepository.updateAccountPools(address)
    }

    override fun subscribePoolsCacheOfCurAccount(): Flow<List<CommonUserPoolData>> =
        userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.subscribePools(it.substrateAddress)
        }

    override suspend fun getPoolsCacheOfCurAccount(): List<CommonUserPoolData> {
        return polkaswapRepository.getPoolsCacheOfAccount(userRepository.getCurSoraAccount().substrateAddress)
    }

    override suspend fun getPoolOfCurAccount(ids: StringPair): CommonUserPoolData? {
        return polkaswapRepository.getPoolsCacheOfAccount(userRepository.getCurSoraAccount().substrateAddress)
            .firstOrNull {
                it.basic.baseToken.id == ids.first && it.basic.targetToken.id == ids.second
            }
    }

    override suspend fun getBasicPool(poolIds: StringPair): BasicPoolData? {
        return polkaswapRepository.getBasicPool(poolIds.first, poolIds.second)
    }

    override suspend fun getBasicPools(): List<BasicPoolData> {
        return polkaswapRepository.getBasicPools()
    }

    override fun subscribePoolsCacheOfAccount(account: SoraAccount): Flow<List<CommonUserPoolData>> {
        return polkaswapRepository.subscribePools(account.substrateAddress)
    }

    override fun subscribePoolCacheOfCurAccount(
        tokenFromId: String,
        tokenToId: String,
    ): Flow<CommonPoolData?> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.subscribePoolOfAccount(it.substrateAddress, tokenFromId, tokenToId)
        }
    }

    override fun subscribeReservesCache(
        baseTokenId: String,
        assetId: String
    ): Flow<LiquidityData?> =
        userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.subscribeLocalPoolReserves(
                it.substrateAddress,
                baseTokenId,
                assetId,
            )
        }

    override fun isPairEnabled(inputAssetId: String, outputAssetId: String): Flow<Boolean> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(
            polkaswapSubscriptionRepository.isPairEnabled(
                inputAssetId,
                outputAssetId,
                address,
            )
        )
    }

    override fun isPairPresentedInNetwork(
        baseTokenId: String,
        tokenId: String,
    ): Flow<Boolean> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(
            polkaswapSubscriptionRepository.isPairPresentedInNetwork(
                baseTokenId,
                tokenId,
                address,
            )
        )
    }

    override suspend fun getLiquidityData(
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean,
    ): LiquidityData {
        return polkaswapSubscriptionRepository.getRemotePoolReserves(
            tokenFrom.id,
            tokenFrom.precision,
            tokenTo.id,
            tokenTo.precision,
            enabled,
            presented,
        )
    }

    override suspend fun removeLiquidity(
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        networkFee: BigDecimal,
        confirmedDuplicate: Boolean,
    ): TransactionOutcome {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val outcome = sora2TransactionOutcome {
            polkaswapExtrinsicRepository.observeRemoveLiquidity(
                soraAccount.substrateAddress,
                keypair,
                token1,
                token2,
                markerAssetDesired,
                firstAmountMin,
                secondAmountMin,
                Sora2SubmissionActivity.liquidity(
                    add = false,
                    token1Id = token1.id,
                    token2Id = token2.id,
                    amount1 = firstAmountMin,
                    amount2 = secondAmountMin,
                    fee = networkFee,
                ).copy(duplicateConfirmed = confirmedDuplicate),
            )
        }
        saveToActivityBestEffort(soraAccount.substrateAddress) {
            transactionBuilder.buildLiquidity(
                txHash = outcome.txHash,
                blockHash = (outcome as? TransactionOutcome.Confirmed)?.blockHash,
                fee = networkFee,
                status = TransactionStatus.PENDING,
                date = Date().time,
                token1 = token1,
                token2 = token2,
                amount1 = firstAmountMin,
                amount2 = secondAmountMin,
                type = TransactionLiquidityType.WITHDRAW,
            )
        }
        return outcome
    }

    override suspend fun getRewardToken(): Token {
        return requireNotNull(assetsRepository.getToken(SubstrateOptionsProvider.pswapAssetId))
    }
}
