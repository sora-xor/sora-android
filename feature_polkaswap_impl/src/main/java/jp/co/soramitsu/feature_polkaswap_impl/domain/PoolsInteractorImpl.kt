/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.domain

import java.math.BigDecimal
import java.util.Date
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculateAddLiquidityAmount
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.estimateAddingShareOfPool
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionLiquidityType
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.sora.substrate.models.WithDesired
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val transactionBuilder: TransactionBuilder,
) : PolkaswapInteractorImpl(polkaswapRepository), PoolsInteractor {

    private var removeLiquidityNetworkFee: BigDecimal? = null

    private var addLiquidityForPresentedPairNetworkFee: BigDecimal? = null
    private var addLiquidityForEnabledPairNetworkFee: BigDecimal? = null
    private var addLiquidityForDisabledPairNetworkFee: BigDecimal? = null

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
        return removeLiquidityNetworkFee ?: (
            (
                polkaswapRepository.calcRemoveLiquidityNetworkFee(
                    tokenId1,
                    tokenId2,
                    userRepository.getCurSoraAccount().substrateAddress,
                ) ?: BigDecimal.ZERO
                ).also {
                removeLiquidityNetworkFee = it
            }
            )
    }

    private suspend fun fetchAddLiquidityNetworkFee(
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal {
        val user = userRepository.getCurSoraAccount().substrateAddress
        val result = polkaswapRepository.calcAddLiquidityNetworkFee(
            user,
            tokenFrom,
            tokenTo,
            tokenFromAmount,
            tokenToAmount,
            pairEnabled,
            pairPresented,
            slippageTolerance,
        )
        return result ?: BigDecimal.ZERO
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
        suspend fun getFee(): BigDecimal {
            return fetchAddLiquidityNetworkFee(
                tokenFrom = tokenFrom,
                tokenTo = tokenTo,
                tokenFromAmount = tokenFromAmount,
                tokenToAmount = tokenToAmount,
                pairEnabled = pairEnabled,
                pairPresented = pairPresented,
                slippageTolerance = slippageTolerance,
            )
        }

        val result = when {
            !pairPresented && pairEnabled -> {
                addLiquidityForEnabledPairNetworkFee
                    ?: getFee().also {
                        addLiquidityForEnabledPairNetworkFee = it
                    }
            }

            !pairPresented && !pairEnabled -> {
                addLiquidityForDisabledPairNetworkFee
                    ?: getFee().also {
                        addLiquidityForDisabledPairNetworkFee = it
                    }
            }

            else -> {
                addLiquidityForPresentedPairNetworkFee
                    ?: getFee().also {
                        addLiquidityForPresentedPairNetworkFee = it
                    }
            }
        }
        return result
    }

    override fun subscribePoolsChangesOfAccount(address: String): Flow<String> {
        return polkaswapRepository.subscribeToPoolsAssets(address)
            .debounce(300)
            .flatMapLatest {
                polkaswapRepository.subscribeToPoolsData(address)
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
        slippageTolerance: Double
    ): String {
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

        val status = polkaswapRepository.observeAddLiquidity(
            soraAccount.substrateAddress,
            keypair,
            tokenFrom,
            tokenTo,
            amountFrom,
            amountTo,
            enabled,
            presented,
            slippageTolerance
        )
        if (status.success) {
            transactionHistoryRepository.saveTransaction(
                transactionBuilder.buildLiquidity(
                    txHash = status.txHash,
                    blockHash = status.blockHash,
                    fee = networkFee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    token1 = tokenFrom,
                    token2 = tokenTo,
                    amount1 = amountFrom,
                    amount2 = amountTo,
                    type = TransactionLiquidityType.ADD,
                )
            )
        }
        return if (status.success) status.txHash else ""
    }

    override suspend fun updatePools() {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return polkaswapRepository.updateAccountPools(address)
    }

    override fun subscribePoolsCache(): Flow<List<PoolData>> =
        userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.subscribePoolFlow(it.substrateAddress)
        }

    override suspend fun getPoolsCache(): List<PoolData> {
        return polkaswapRepository.getPoolsCache(userRepository.getCurSoraAccount().substrateAddress)
    }

    override fun subscribePoolsCacheOfAccount(account: SoraAccount): Flow<List<PoolData>> {
        return polkaswapRepository.subscribePoolFlow(account.substrateAddress)
    }

    override fun subscribePoolCache(tokenFromId: String, tokenToId: String): Flow<PoolData?> {
        return userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.getPoolData(it.substrateAddress, tokenFromId, tokenToId)
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
                assetId
            )
        }

    override fun isPairEnabled(inputAssetId: String, outputAssetId: String): Flow<Boolean> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(
            polkaswapRepository.isPairEnabled(
                inputAssetId,
                outputAssetId,
                address
            )
        )
    }

    override fun isPairPresentedInNetwork(
        baseTokenId: String,
        tokenId: String
    ): Flow<Boolean> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(polkaswapRepository.isPairPresentedInNetwork(baseTokenId, tokenId, address))
    }

    override suspend fun getLiquidityData(
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData {
        return polkaswapRepository.getRemotePoolReserves(
            userRepository.getCurSoraAccount().substrateAddress,
            tokenFrom,
            tokenTo,
            enabled,
            presented
        )
    }

    override suspend fun removeLiquidity(
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal,
        networkFee: BigDecimal
    ): String {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val status = polkaswapRepository.observeRemoveLiquidity(
            soraAccount.substrateAddress,
            keypair,
            token1,
            token2,
            markerAssetDesired,
            firstAmountMin,
            secondAmountMin
        )
        if (status.success) {
            transactionHistoryRepository.saveTransaction(
                transactionBuilder.buildLiquidity(
                    txHash = status.txHash,
                    blockHash = status.blockHash,
                    fee = networkFee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    token1 = token1,
                    token2 = token2,
                    amount1 = firstAmountMin,
                    amount2 = secondAmountMin,
                    type = TransactionLiquidityType.WITHDRAW,
                )
            )
        }
        return if (status.success) status.txHash else ""
    }
}
