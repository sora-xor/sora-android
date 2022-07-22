/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.LiquidityDetails
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.TransactionHistoryRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionBuilder
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionLiquidityType
import jp.co.soramitsu.feature_wallet_api.domain.model.TransactionStatus
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateAddLiquidityAmount
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.estimateAddingShareOfPool
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import kotlin.math.max

@ExperimentalCoroutinesApi
class PolkaswapInteractorImpl(
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val coroutineManager: CoroutineManager,
    private val polkaswapRepository: PolkaswapRepository,
    private val walletRepository: WalletRepository,
) : PolkaswapInteractor {

    private var swapResult = SuspendableProperty<Boolean>(1)
    private var selectedSwapMarket = MutableStateFlow(Market.SMART)
    private var poolReservesFlowToken = MutableStateFlow<Pair<String, String>?>(null)
    private val availableMarkets = mutableListOf<Market>()
    private var swapNetworkFee: BigDecimal? = null
    private var removeLiquidityNetworkFee: BigDecimal? = null

    private var addLiquidityForPresentedPairNetworkFee: BigDecimal? = null
    private var addLiquidityForEnabledPairNetworkFee: BigDecimal? = null
    private var addLiquidityForDisabledPairNetworkFee: BigDecimal? = null

    override suspend fun fetchSwapNetworkFee(feeToken: Token): BigDecimal {
        return swapNetworkFee ?: (
            polkaswapRepository.calcSwapNetworkFee(
                feeToken,
                userRepository.getCurSoraAccount().substrateAddress,
            ).also {
                swapNetworkFee = it
            }
            )
    }

    override suspend fun fetchRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token
    ): BigDecimal {
        return removeLiquidityNetworkFee ?: (
            polkaswapRepository.calcRemoveLiquidityNetworkFee(
                tokenId1,
                tokenId2,
                userRepository.getCurSoraAccount().substrateAddress,
            ).also {
                removeLiquidityNetworkFee = it
            }
            )
    }

    override suspend fun fetchAddLiquidityNetworkFee(
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Float
    ): BigDecimal = polkaswapRepository.calcAddLiquidityNetworkFee(
        userRepository.getCurSoraAccount().substrateAddress,
        tokenFrom,
        tokenTo,
        tokenFromAmount,
        tokenToAmount,
        pairEnabled,
        pairPresented,
        slippageTolerance.toDouble(),
    )

    private suspend fun getLiquidityNetworkFee(
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Float
    ): BigDecimal =
        when {
            !pairPresented && pairEnabled -> {
                addLiquidityForEnabledPairNetworkFee
                    ?: fetchAddLiquidityNetworkFee(
                        tokenFrom,
                        tokenTo,
                        tokenFromAmount,
                        tokenToAmount,
                        pairEnabled,
                        pairPresented,
                        slippageTolerance
                    ).also {
                        addLiquidityForEnabledPairNetworkFee = it
                    }
            }
            !pairPresented && !pairEnabled -> {
                addLiquidityForDisabledPairNetworkFee
                    ?: fetchAddLiquidityNetworkFee(
                        tokenFrom,
                        tokenTo,
                        tokenFromAmount,
                        tokenToAmount,
                        pairEnabled,
                        pairPresented,
                        slippageTolerance
                    ).also {
                        addLiquidityForDisabledPairNetworkFee = it
                    }
            }
            else -> {
                addLiquidityForPresentedPairNetworkFee
                    ?: fetchAddLiquidityNetworkFee(
                        tokenFrom,
                        tokenTo,
                        tokenFromAmount,
                        tokenToAmount,
                        pairEnabled,
                        pairPresented,
                        slippageTolerance
                    ).also {
                        addLiquidityForPresentedPairNetworkFee = it
                    }
            }
        }

    override fun subscribePoolsChanges(): Flow<String> =
        userRepository.flowCurSoraAccount().flatMapLatest { soraAccount ->
            polkaswapRepository.subscribeToPoolsAssets(soraAccount.substrateAddress).flatMapLatest {
                polkaswapRepository.subscribeToPoolsData(soraAccount.substrateAddress)
            }
        }

    override suspend fun calcDetails(
        tokenFrom: Token,
        tokenTo: Token,
        feeToken: Token,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Float,
    ): SwapDetails? {
        val curMarkets =
            if (selectedSwapMarket.value == Market.SMART) emptyList() else listOf(selectedSwapMarket.value)
        val swapQuote = polkaswapRepository.getSwapQuote(
            tokenFrom.id,
            tokenTo.id,
            amount,
            desired,
            curMarkets,
        ) ?: return null

        if (swapQuote.amount == BigDecimal.ZERO) return null

        val minMax =
            (swapQuote.amount * BigDecimal.valueOf(slippageTolerance.toDouble() / 100)).let {
                if (desired == WithDesired.INPUT)
                    swapQuote.amount - it
                else
                    swapQuote.amount + it
            }

        val scale = max(swapQuote.amount.scale(), amount.scale())
        val networkFee =
            swapNetworkFee ?: (fetchSwapNetworkFee(feeToken).also { swapNetworkFee = it })
        poolReservesFlowToken.value = tokenFrom.id to tokenTo.id
        return SwapDetails(
            swapQuote.amount,
            amount.divide(swapQuote.amount, scale, RoundingMode.HALF_EVEN),
            swapQuote.amount.divide(amount, scale, RoundingMode.HALF_EVEN),
            minMax,
            swapQuote.fee,
            networkFee,
        )
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
        slippageTolerance: Float,
        pairEnabled: Boolean,
        pairPresented: Boolean
    ): LiquidityDetails {
        val resultAmount = if (!pairPresented) {
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
        slippageTolerance: Float
    ): Boolean {
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
            slippageTolerance.toDouble()
        )
        if (status.success) {
            transactionHistoryRepository.saveTransaction(
                TransactionBuilder.buildLiquidity(
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
        return status.success
    }

    override fun setSwapMarket(market: Market) {
        selectedSwapMarket.value = market
    }

    override fun checkSwapBalances(
        fromToken: Token,
        fromTokenBalance: BigDecimal,
        fromAmount: BigDecimal,
        swapFee: BigDecimal,
        feeBalance: BigDecimal,
        feeToken: Token,
        toToken: Token,
        toTokenBalance: BigDecimal,
        toAmount: BigDecimal,
        desired: WithDesired,
        swapDetails: SwapDetails,
    ): Token? {
        if (fromAmount > fromTokenBalance) return fromToken
        if (fromToken.id == feeToken.id && fromAmount + swapFee > feeBalance) return feeToken
        if (swapFee > feeBalance) {
            val toAmountFuture =
                toTokenBalance + if (desired == WithDesired.INPUT) swapDetails.minmax else toAmount
            return if (toToken.id == feeToken.id && toAmountFuture > swapFee) null else feeToken
        }
        return null
    }

    override fun checkLiquidityBalance(
        fromTokenBalance: BigDecimal,
        fromAmount: BigDecimal,
        toTokenBalance: BigDecimal,
        toAmount: BigDecimal,
        networkFee: BigDecimal
    ): Boolean = (fromAmount + networkFee) <= fromTokenBalance && toAmount <= toTokenBalance

    override fun observeSelectedMarket(): Flow<Market> =
        selectedSwapMarket.asStateFlow()

    override fun observeSwap(): Flow<Boolean> =
        swapResult.observe()

    override fun observePoolReserves(): Flow<String> {
        return poolReservesFlowToken.asStateFlow().filterNotNull()
            .combine(selectedSwapMarket.asStateFlow().filterNotNull()) { tokens, market ->
                tokens to market
            }.flatMapLatest {
                val flows = mutableListOf<Flow<String>>()
                if (it.second == Market.XYK || it.second == Market.SMART) {
                    if (it.first.first != SubstrateOptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolXYKReserves(it.first.first))
                    }
                    if (it.first.second != SubstrateOptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolXYKReserves(it.first.second))
                    }
                }
                if (it.second == Market.TBC || it.second == Market.SMART) {
                    if (it.first.first != SubstrateOptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolTBCReserves(it.first.first))
                    }
                    if (it.first.second != SubstrateOptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolTBCReserves(it.first.second))
                    }
                    flows.add(polkaswapRepository.observePoolTBCReserves(SubstrateOptionsProvider.feeAssetId))
                }
                flows.merge()
            }.debounce(500)
    }

    override suspend fun updatePools() {
        val address = userRepository.getCurSoraAccount().substrateAddress
        return polkaswapRepository.updateAccountPools(address)
    }

    override fun getPoolData(assetId: String): Flow<PoolData?> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(polkaswapRepository.getPoolData(address, assetId))
    }

    override suspend fun updatePool(tokenId: String) {
        polkaswapRepository.updateAccountPool(
            userRepository.getCurSoraAccount().substrateAddress,
            tokenId
        )
    }

    override fun subscribePoolsCache(): Flow<List<PoolData>> =
        userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.subscribePoolFlow(it.substrateAddress)
        }

    override fun subscribeReservesCache(assetId: String): Flow<LiquidityData?> =
        userRepository.flowCurSoraAccount().flatMapLatest {
            polkaswapRepository.subscribeLocalPoolReserves(it.substrateAddress, assetId)
        }

    override suspend fun getPoolStrategicBonusAPY(tokenId: String): Double? =
        PolkaswapFormulas.calculateStrategicBonusAPY(
            polkaswapRepository.getPoolStrategicBonusAPY(tokenId)
        )

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean =
        polkaswapRepository.isSwapAvailable(tokenId1, tokenId2)

    override suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<Market>? {
        if (!isSwapAvailable(tokenId1, tokenId2)) return null
        val sources = polkaswapRepository.getAvailableSources(tokenId1, tokenId2)
        availableMarkets.clear()
        availableMarkets.addAll(sources)
        if (availableMarkets.isEmpty()) {
            if ((
                tokenId1.equals(SubstrateOptionsProvider.xstTokenId, true) &&
                    SubstrateOptionsProvider.xstPoolTokens.contains(tokenId2)
                ) ||
                (
                    tokenId2.equals(SubstrateOptionsProvider.xstTokenId, true) &&
                        SubstrateOptionsProvider.xstPoolTokens.contains(tokenId1)
                    )
            ) {
                availableMarkets.add(Market.SMART)
            }
        } else {
            availableMarkets.add(Market.SMART)
        }
        if (!availableMarkets.contains(selectedSwapMarket.value)
        ) {
            selectedSwapMarket.value = availableMarkets.firstOrNull() ?: Market.SMART
        }
        return availableMarkets
    }

    override fun getPolkaswapDisclaimerVisibility(): Flow<Boolean> =
        polkaswapRepository.getPolkaswapDisclaimerVisibility()

    override suspend fun setPolkaswapDisclaimerVisibility(v: Boolean) {
        polkaswapRepository.setPolkaswapDisclaimerVisibility(v)
    }

    override suspend fun getAvailableSources(): List<Market> =
        availableMarkets

    override suspend fun swap(
        tokenInput: Token,
        tokenOutput: Token,
        desired: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        networkFee: BigDecimal,
        liquidityFee: BigDecimal,
    ): Boolean {
        val soraAccount = userRepository.getCurSoraAccount()
        val keypair = credentialsRepository.retrieveKeyPair(soraAccount)
        val result = polkaswapRepository.observeSwap(
            tokenInput,
            tokenOutput,
            keypair,
            soraAccount.substrateAddress,
            if (selectedSwapMarket.value == Market.SMART) emptyList() else listOf(selectedSwapMarket.value),
            desired,
            amount,
            limit,
        )
        swapResult.set(result.success)
        if (result.success) {
            transactionHistoryRepository.saveTransaction(
                TransactionBuilder.buildSwap(
                    txHash = result.txHash,
                    blockHash = result.blockHash,
                    fee = networkFee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    tokenFrom = tokenInput,
                    tokenTo = tokenOutput,
                    amountFrom = amount,
                    amountTo = limit,
                    market = selectedSwapMarket.value,
                    liquidityFee = liquidityFee,
                )
            )
        }
        return result.success
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

    override fun isPairPresentedInNetwork(tokenId: String): Flow<Boolean> = flow {
        val address = userRepository.getCurSoraAccount().substrateAddress
        emitAll(polkaswapRepository.isPairPresentedInNetwork(tokenId, address))
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
    ): Boolean {
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
                TransactionBuilder.buildLiquidity(
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
        return status.success
    }
}
