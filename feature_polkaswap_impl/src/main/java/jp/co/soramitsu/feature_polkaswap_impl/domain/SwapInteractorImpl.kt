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

package jp.co.soramitsu.feature_polkaswap_impl.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.SuspendableProperty
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.ext.isZero
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.feature_account_api.domain.interfaces.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_assets_api.data.AssetsRepository
import jp.co.soramitsu.feature_blockexplorer_api.data.TransactionHistoryRepository
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionBuilder
import jp.co.soramitsu.feature_blockexplorer_api.presentation.txhistory.TransactionStatus
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapExtrinsicRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.SwapInteractor
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapDetails
import kotlin.math.max
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

@ExperimentalCoroutinesApi
class SwapInteractorImpl(
    private val assetsRepository: AssetsRepository,
    private val credentialsRepository: CredentialsRepository,
    private val userRepository: UserRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val polkaswapRepository: PolkaswapRepository,
    private val polkaswapExtrinsicRepository: PolkaswapExtrinsicRepository,
    private val polkaswapSubscriptionRepository: PolkaswapSubscriptionRepository,
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
                polkaswapExtrinsicRepository.calcSwapNetworkFee(
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
            polkaswapSubscriptionRepository.getSwapQuote(
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

    override fun observePoolReserves(): Flow<String> = flow {
        while (true) {
            emit("observePoolReserves")
            delay(5000)
        }
    }

//    override fun observePoolReserves(): Flow<String> {
//        return poolReservesFlowToken.asStateFlow().filterNotNull()
//            .combine(selectedSwapMarket.asStateFlow().filterNotNull()) { tokens, market ->
//                tokens to market
//            }.flatMapLatest { (tokens, market) ->
//                val flows = mutableListOf<Flow<String>>()
//                if (market == Market.XYK || market == Market.SMART) {
//                    val tfrom = tokens.first
//                    val tto = tokens.second
//                    val dexs = getPoolDexList()
//                    if (!dexs.hasToken(tfrom) && !dexs.hasToken(tto)) {
//                        dexs.forEach { dex ->
//                            flows.add(
//                                polkaswapSubscriptionRepository.observePoolXYKReserves(dex.tokenId, tfrom)
//                            )
//                            flows.add(
//                                polkaswapSubscriptionRepository.observePoolXYKReserves(dex.tokenId, tto)
//                            )
//                        }
//                    } else if (dexs.hasToken(tfrom) && dexs.hasToken(tto)) {
//                        flows.add(
//                            polkaswapSubscriptionRepository.observePoolXYKReserves(tto, tfrom)
//                        )
//                        flows.add(
//                            polkaswapSubscriptionRepository.observePoolXYKReserves(tfrom, tto)
//                        )
//                    } else {
//                        val (inDex, inNot) = if (dexs.hasToken(tfrom)) tfrom to tto else tto to tfrom
//                        flows.add(
//                            polkaswapSubscriptionRepository.observePoolXYKReserves(inDex, inNot)
//                        )
//                        dexs.filter { dex ->
//                            dex.tokenId != inDex
//                        }.forEach { dex ->
//                            flows.add(
//                                polkaswapSubscriptionRepository.observePoolXYKReserves(dex.tokenId, inDex)
//                            )
//                            flows.add(
//                                polkaswapSubscriptionRepository.observePoolXYKReserves(dex.tokenId, inNot)
//                            )
//                        }
//                    }
//                    flows.add(
//                        polkaswapSubscriptionRepository.observePoolXYKReserves(
//                            tokens.first,
//                            tokens.second
//                        )
//                    )
//                }
//                if (market == Market.TBC || market == Market.SMART) {
//                    flows.add(polkaswapSubscriptionRepository.observePoolTBCReserves(tokens.first))
//                }
//                flows.merge()
//            }.debounce(500)
//    }

    override suspend fun fetchAvailableSources(tokenId1: String, tokenId2: String): Set<Market>? {
        poolReservesFlowToken.value = tokenId1 to tokenId2
        val allBaseDexs = getPoolDexList()
        val swapsAvailable = allBaseDexs.map {
            polkaswapSubscriptionRepository.isSwapAvailable(tokenId1, tokenId2, it.dexId)
        }
        if (swapsAvailable.none { it }) return null
        availableMarkets.clear()
        allBaseDexs.forEachIndexed { index, poolDex ->
            availableMarkets[poolDex] =
                if (swapsAvailable[index]) polkaswapSubscriptionRepository.getAvailableSources(
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
        val result = polkaswapExtrinsicRepository.observeSwap(
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
