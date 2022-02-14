/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.domain

import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapDetails
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.blake2b256String
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

@ExperimentalCoroutinesApi
class PolkaswapInteractorImpl(
    private val credentialsRepository: CredentialsRepository,
    private val coroutineManager: CoroutineManager,
    private val polkaswapRepository: PolkaswapRepository,
    private val walletRepository: WalletRepository,
) : PolkaswapInteractor {

    private var swapResult = SuspendableProperty<Boolean>(1)
    private var selectedSwapMarket = MutableStateFlow(Market.SMART)
    private var poolReservesFlowToken = MutableStateFlow<Pair<String, String>?>(null)
    private val availableMarkets = mutableListOf<Market>()
    private var swapNetworkFee: BigDecimal? = null

    override suspend fun fetchNetworkFee(feeToken: Token): BigDecimal {
        return swapNetworkFee ?: (
            polkaswapRepository.calcSwapNetworkFee(
                feeToken,
                credentialsRepository.getAddress(),
            ).also {
                swapNetworkFee = it
            }
            )
    }

    override fun subscribePoolsChanges(): Flow<String> = flow {
        val address = credentialsRepository.getAddress()
        val flow = polkaswapRepository.subscribeToPoolsAssets(address).flatMapLatest {
            polkaswapRepository.subscribeToPoolsData(address)
        }
        emitAll(flow)
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
        val networkFee = swapNetworkFee ?: (fetchNetworkFee(feeToken).also { swapNetworkFee = it })
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
                    if (it.first.first != OptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolXYKReserves(it.first.first))
                    }
                    if (it.first.second != OptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolXYKReserves(it.first.second))
                    }
                }
                if (it.second == Market.TBC || it.second == Market.SMART) {
                    if (it.first.first != OptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolTBCReserves(it.first.first))
                    }
                    if (it.first.second != OptionsProvider.feeAssetId) {
                        flows.add(polkaswapRepository.observePoolTBCReserves(it.first.second))
                    }
                    flows.add(polkaswapRepository.observePoolTBCReserves(OptionsProvider.feeAssetId))
                }
                flows.merge()
            }.debounce(500)
    }

    override suspend fun updatePools() {
        val address = credentialsRepository.getAddress()
        return polkaswapRepository.updateAccountPools(address)
    }

    override fun subscribePoolsCache(): Flow<List<PoolData>> = flow {
        val address = credentialsRepository.getAddress()
        emitAll(polkaswapRepository.subscribePoolFlow(address))
    }

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean =
        polkaswapRepository.isSwapAvailable(tokenId1, tokenId2)

    override suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): List<Market>? {
        if (!isSwapAvailable(tokenId1, tokenId2)) return null
        return polkaswapRepository.getAvailableSources(tokenId1, tokenId2).also {
            availableMarkets.clear()
            availableMarkets.addAll(it)

            if (selectedSwapMarket.value != Market.SMART && !availableMarkets.contains(selectedSwapMarket.value)) {
                selectedSwapMarket.value = (availableMarkets + Market.SMART).first()
            }
        }
    }

    override fun getPolkaswapDisclaimerVisibility(): Flow<Boolean> =
        polkaswapRepository.getPolkaswapDisclaimerVisibility()

    override suspend fun setPolkaswapDisclaimerVisibility(v: Boolean) {
        polkaswapRepository.setPolkaswapDisclaimerVisibility(v)
    }

    override suspend fun getAvailableSources(): List<Market> =
        availableMarkets + Market.SMART

    override suspend fun swap(
        tokenInput: Token,
        tokenOutput: Token,
        desired: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        networkFee: BigDecimal,
        liquidityFee: BigDecimal,
    ): Boolean {
        val address = credentialsRepository.getAddress()
        val keypair = credentialsRepository.retrieveKeyPair()
        val result = polkaswapRepository.observeSwap(
            tokenInput,
            tokenOutput,
            keypair,
            address,
            if (selectedSwapMarket.value == Market.SMART) emptyList() else listOf(selectedSwapMarket.value),
            desired,
            amount,
            limit,
        )
            .catch {
                FirebaseWrapper.recordException(it)
                emit("" to ExtrinsicStatusResponse.ExtrinsicStatusPending(""))
            }
            .map {
                if (it.first.isNotEmpty()) {
                    polkaswapRepository.saveSwap(
                        txHash = it.first,
                        status = it.second,
                        fee = networkFee,
                        eventSuccess = null,
                        tokenIdFrom = tokenInput.id,
                        tokenIdTo = tokenOutput.id,
                        amountFrom = if (desired == WithDesired.INPUT) amount else limit,
                        amountTo = if (desired == WithDesired.INPUT) limit else amount,
                        market = selectedSwapMarket.value,
                        liquidityFee = liquidityFee,
                    )
                }
                Triple(
                    it.first,
                    (it.second as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    it.second.subscription
                )
            }
            .transformWhile { value ->
                emit(value.first.isNotEmpty())

                value.second?.let { blockHash ->
                    val txHash = value.first
                    val blockResponse = walletRepository.getBlock(blockHash)
                    val extrinsicId = blockResponse.block.extrinsics.indexOfFirst { s ->
                        s.blake2b256String() == txHash
                    }.toLong()
                    val isSuccess = walletRepository.isTxSuccessful(extrinsicId, blockHash, txHash)
                    walletRepository.updateTransactionSuccess(txHash, isSuccess)
                }
                value.second.isNullOrEmpty() && value.first.isNotEmpty()
            }.stateIn(coroutineManager.applicationScope).first()
        swapResult.set(result)
        return result
    }
}
