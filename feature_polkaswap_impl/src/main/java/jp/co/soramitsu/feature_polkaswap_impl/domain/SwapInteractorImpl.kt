/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_polkaswap_impl.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.interfaces.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import jp.co.soramitsu.sora.substrate.models.WithDesired
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.merge

@ExperimentalCoroutinesApi
class SwapInteractorImpl(
    private val assetsRepository: AssetsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val polkaswapRepository: PolkaswapRepository,
    private val transactionBuilder: TransactionBuilder,
) : PolkaswapInteractorImpl(polkaswapRepository), SwapInteractor {

    private var swapResult = SuspendableProperty<Boolean>(1)
    private var selectedSwapMarket = MutableStateFlow(Market.SMART)
    private var poolReservesFlowToken = MutableStateFlow<Pair<String, String>?>(null)
    private val availableMarkets = mutableMapOf<PoolDex, List<Market>>()
    private var swapNetworkFee: BigDecimal? = null

    override suspend fun fetchSwapNetworkFee(feeToken: Token): BigDecimal {
        return swapNetworkFee ?: (
            (
                polkaswapRepository.calcSwapNetworkFee(
                    feeToken,
                    userRepository.getCurSoraAccount().substrateAddress,
                ) ?: BigDecimal.ZERO
                ).also {
                swapNetworkFee = it
            }
            )
    }

    override suspend fun calcDetails(
        tokenFrom: Token,
        tokenTo: Token,
        feeToken: Token,
        amount: BigDecimal,
        desired: WithDesired,
        slippageTolerance: Double,
    ): SwapDetails? {
        val curMarkets =
            if (selectedSwapMarket.value == Market.SMART) emptyList() else listOf(selectedSwapMarket.value)
        val dexs = availableMarkets.mapNotNull {
            if (it.value.contains(selectedSwapMarket.value)) it.key else null
        }
        val quotes = dexs.mapNotNull {
            polkaswapRepository.getSwapQuote(
                tokenFrom.id,
                tokenTo.id,
                amount,
                desired,
                curMarkets,
                feeToken,
                it.dexId,
            )?.let { quote ->
                quote to it
            }
        }.filter {
            it.first.amount.isZero().not()
        }

        if (quotes.isEmpty()) return null
        val swapQuote = if (desired == WithDesired.INPUT) quotes.maxBy {
            it.first.amount
        } else quotes.minBy {
            it.first.amount
        }

        val minMax =
            (swapQuote.first.amount * BigDecimal.valueOf(slippageTolerance / 100)).let {
                if (desired == WithDesired.INPUT)
                    swapQuote.first.amount - it
                else
                    swapQuote.first.amount + it
            }

        val scale = max(swapQuote.first.amount.scale(), amount.scale())
        val networkFee = fetchSwapNetworkFee(feeToken)
        return SwapDetails(
            swapQuote.first.amount,
            amount.divide(swapQuote.first.amount, scale, RoundingMode.HALF_EVEN),
            swapQuote.first.amount.divide(amount, scale, RoundingMode.HALF_EVEN),
            minMax,
            swapQuote.first.fee,
            networkFee,
            swapQuote.second,
            swapQuote.first.route?.mapNotNull {
                assetsRepository.getToken(it)?.symbol
            },
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
                    val tfrom = it.first.first
                    val tto = it.first.second
                    val dexs = getPoolDexList()
                    if (!dexs.hasToken(tfrom) && !dexs.hasToken(tto)) {
                        dexs.forEach { dex ->
                            flows.add(
                                polkaswapRepository.observePoolXYKReserves(dex.tokenId, tfrom)
                            )
                            flows.add(
                                polkaswapRepository.observePoolXYKReserves(dex.tokenId, tto)
                            )
                        }
                    } else if (dexs.hasToken(tfrom) && dexs.hasToken(tto)) {
                        flows.add(
                            polkaswapRepository.observePoolXYKReserves(tto, tfrom)
                        )
                        flows.add(
                            polkaswapRepository.observePoolXYKReserves(tfrom, tto)
                        )
                    } else {
                        val (inDex, inNot) = if (dexs.hasToken(tfrom)) tfrom to tto else tto to tfrom
                        flows.add(
                            polkaswapRepository.observePoolXYKReserves(inDex, inNot)
                        )
                        dexs.filter { dex ->
                            dex.tokenId != inDex
                        }.forEach { dex ->
                            flows.add(
                                polkaswapRepository.observePoolXYKReserves(dex.tokenId, inDex)
                            )
                            flows.add(
                                polkaswapRepository.observePoolXYKReserves(dex.tokenId, inNot)
                            )
                        }
                    }
                    flows.add(
                        polkaswapRepository.observePoolXYKReserves(
                            it.first.first,
                            it.first.second
                        )
                    )
                }
                if (it.second == Market.TBC || it.second == Market.SMART) {
                    flows.add(polkaswapRepository.observePoolTBCReserves(it.first.first))
                }
                flows.merge()
            }.debounce(500)
    }

    override suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): Set<Market>? {
        poolReservesFlowToken.value = tokenId1 to tokenId2
        val allBaseDexs = getPoolDexList()
        val swapsAvailable = allBaseDexs.map {
            polkaswapRepository.isSwapAvailable(tokenId1, tokenId2, it.dexId)
        }
        if (swapsAvailable.none { it }) return null
        availableMarkets.clear()
        allBaseDexs.forEachIndexed { index, poolDex ->
            availableMarkets[poolDex] =
                if (swapsAvailable[index]) polkaswapRepository.getAvailableSources(
                    tokenId1,
                    tokenId2,
                    poolDex.dexId
                ).let { markets ->
                    buildList {
                        add(Market.SMART)
                        addAll(markets)
                    }
                } else emptyList()
        }
        val allMarkets: Set<Market> = buildSet {
            availableMarkets.values.forEach { markets ->
                addAll(markets)
            }
        }
        if (!allMarkets.contains(selectedSwapMarket.value)
        ) {
            selectedSwapMarket.value = allMarkets.firstOrNull() ?: Market.SMART
        }
        return allMarkets
    }

    override fun getPolkaswapDisclaimerVisibility(): Flow<Boolean> =
        polkaswapRepository.getPolkaswapDisclaimerVisibility()

    override suspend fun setPolkaswapDisclaimerVisibility(v: Boolean) {
        polkaswapRepository.setPolkaswapDisclaimerVisibility(v)
    }

    override suspend fun swap(
        tokenInput: Token,
        tokenOutput: Token,
        desired: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        networkFee: BigDecimal,
        liquidityFee: BigDecimal,
        dexId: Int,
        amount2: BigDecimal,
    ): String {
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
            dexId,
        )
        swapResult.set(result.success)
        if (result.success) {
            transactionHistoryRepository.saveTransaction(
                transactionBuilder.buildSwap(
                    txHash = result.txHash,
                    blockHash = result.blockHash,
                    fee = networkFee,
                    status = TransactionStatus.PENDING,
                    date = Date().time,
                    tokenFrom = tokenInput,
                    tokenTo = tokenOutput,
                    amountFrom = if (desired == WithDesired.INPUT) amount else amount2,
                    amountTo = if (desired == WithDesired.INPUT) amount2 else amount,
                    market = selectedSwapMarket.value,
                    liquidityFee = liquidityFee,
                )
            )
        }
        return if (result.success) result.txHash else ""
    }

    private fun List<PoolDex>.hasToken(id: String): Boolean = find { it.tokenId == id } != null
}
