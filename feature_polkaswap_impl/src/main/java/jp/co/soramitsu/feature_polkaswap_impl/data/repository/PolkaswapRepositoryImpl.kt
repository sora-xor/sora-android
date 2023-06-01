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

package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import androidx.room.withTransaction
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.PoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculateMinAmount
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculatePooledValue
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.PoolLocalMapper
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.poolTBCReserves
import jp.co.soramitsu.sora.substrate.runtime.reservesKey
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.depositLiquidity
import jp.co.soramitsu.sora.substrate.substrate.initializePool
import jp.co.soramitsu.sora.substrate.substrate.register
import jp.co.soramitsu.sora.substrate.substrate.removeLiquidity
import jp.co.soramitsu.sora.substrate.substrate.swap
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip

@ExperimentalCoroutinesApi
class PolkaswapRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
    private val blockExplorerManager: BlockExplorerManager,
    private val soraConfigManager: SoraConfigManager,
) : PolkaswapRepository {

    override suspend fun poolFavoriteOn(ids: StringPair, account: SoraAccount) {
        db.poolDao().poolFavoriteOn(ids.first, ids.second, account.substrateAddress)
    }

    override suspend fun poolFavoriteOff(ids: StringPair, account: SoraAccount) {
        db.poolDao().poolFavoriteOff(ids.first, ids.second, account.substrateAddress)
    }

    override suspend fun updatePoolPosition(pools: Map<StringPair, Int>, account: SoraAccount) {
        db.withTransaction {
            pools.entries.forEach {
                db.poolDao().updatePoolPosition(
                    it.key.first,
                    it.key.second,
                    it.value,
                    account.substrateAddress
                )
            }
        }
    }

    override suspend fun getPoolBaseTokens(): List<PoolDex> {
        return db.poolDao().getPoolBaseTokens().map {
            PoolDex(it.base.dexId, it.base.tokenId, it.token.symbol)
        }
    }

    override fun getPolkaswapDisclaimerVisibility(): Flow<Boolean> {
        return datasource.getDisclaimerVisibility()
    }

    override suspend fun setPolkaswapDisclaimerVisibility(v: Boolean) {
        datasource.saveDisclaimerVisibility(v)
    }

    override suspend fun updateAccountPools(address: String) {
        // pool base tokens
        val baseTokens = wsConnection.getPoolBaseTokens()
        db.withTransaction {
            db.poolDao().clearPoolBaseTokens()
            db.poolDao().insertPoolBaseTokens(
                baseTokens.map {
                    PoolBaseTokenLocal(
                        tokenId = it.second,
                        dexId = it.first,
                    )
                }
            )
        }
        // pools
        val pools = mutableListOf<PoolLocal>()
        val tokensIds = wsConnection.getUserPoolsTokenIds(address)
        val poolsLocal = db.poolDao().getPools(address).first()
        var count = poolsLocal.size
        val poolsPositionAndFavorite = poolsLocal.associate {
            (it.assetId + it.assetIdBase) to (it.sortOrder to it.favorite)
        }
        tokensIds.forEach { pair ->
            val xorPrecision =
                requireNotNull(db.assetDao().getPrecisionOfToken(pair.first))

            wsConnection.getUserPoolsData(address, pair.first, pair.second)
                .forEach { poolDataDto ->
                    if (db.assetDao().getWhitelistOfToken(poolDataDto.assetId).isNullOrEmpty()
                            .not()
                    ) {
                        db.assetDao().getPrecisionOfToken(poolDataDto.assetId)
                            ?.let { tokenPrecision ->
                                val poolLocal =
                                    PoolLocal(
                                        poolDataDto.assetId,
                                        pair.first,
                                        address,
                                        mapBalance(poolDataDto.reservesFirst, xorPrecision),
                                        mapBalance(poolDataDto.reservesSecond, tokenPrecision),
                                        mapBalance(poolDataDto.totalIssuance, xorPrecision),
                                        getPoolStrategicBonusAPY(
                                            poolDataDto.assetId,
                                            pair.first,
                                            address
                                        ),
                                        mapBalance(poolDataDto.poolProvidersBalance, xorPrecision),
                                        poolsPositionAndFavorite[poolDataDto.assetId + pair.first]?.second
                                            ?: true,
                                        poolsPositionAndFavorite[poolDataDto.assetId + pair.first]?.first
                                            ?: ++count,
                                        poolDataDto.reservesAccount,
                                    )

                                pools.add(poolLocal)
                            }
                    }
                }
        }

        db.withTransaction {
            db.poolDao().clearTable(address)
            db.poolDao().insert(pools)
        }
        blockExplorerManager.updatePoolsSbApy(address)
    }

    private suspend fun getPoolStrategicBonusAPY(
        tokenId: String,
        baseTokenId: String,
        address: String
    ): BigDecimal? {
        var result = db.poolDao().getPoolOf(tokenId, baseTokenId, address)?.strategicBonusApy
        if (result != null) return result
        result = blockExplorerManager.getTempApy(tokenId)?.sbApy?.toBigDecimal()
        if (result != null) return result
        return wsConnection.getPoolReserveAccount(baseTokenId, tokenId.fromHex())?.let {
            val id = runtimeManager.toSoraAddress(it)
            blockExplorerManager.getTempApy(id)?.sbApy?.toBigDecimal()
        }
    }

    override fun subscribeToPoolsAssets(address: String): Flow<String> {
        val flow = flow {
            val keys = wsConnection.getUserPoolsTokenIdsKeys(address)
            val flows = keys.map {
                substrateCalls.observeStorage(it)
            }
            val combined = if (flows.isEmpty()) flowOf("") else combine(flows) {
                it.getOrElse(0) { "" }
            }
            emitAll(combined)
        }
        return flow
    }

    override fun subscribeToPoolsData(address: String): Flow<String> = flow {
        val tokensPair = wsConnection.getUserPoolsTokenIds(address)
        val r = if (tokensPair.isEmpty()) {
            flowOf("")
        } else {
            val flows = tokensPair.map { tokenPair ->
                tokenPair.second.map {
                    subscribeToPoolData(address, tokenPair.first, it)
                }
            }.flatten()
            if (flows.isEmpty())
                flowOf("")
            else
                combine(flows) {
                    it.getOrElse(0) { "" }
                }
        }
        emitAll(r)
    }

    private suspend fun subscribeToPoolData(
        address: String,
        baseTokenId: String,
        tokenId: ByteArray
    ): Flow<String> {
        val reservesAccount = wsConnection.getPoolReserveAccount(
            baseTokenId,
            tokenId
        )
        val reservesKey =
            runtimeManager.getRuntimeSnapshot().reservesKey(baseTokenId, tokenId)
        val reservesFlow = substrateCalls.observeStorage(reservesKey)

        val totalIssuanceKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.TOTAL_ISSUANCES.storageName)
                .storageKey(runtimeManager.getRuntimeSnapshot(), reservesAccount)
        val totalIssuanceFlow = substrateCalls.observeStorage(totalIssuanceKey)

        val poolProvidersKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.POOL_PROVIDERS.storageName)
                .storageKey(
                    runtimeManager.getRuntimeSnapshot(),
                    reservesAccount,
                    address.toAccountId()
                )
        val poolProvidersFlow = substrateCalls.observeStorage(poolProvidersKey)

        val resultFlow = reservesFlow
            .zip(totalIssuanceFlow) { reservesString, totalIssuanceString ->
                Pair(reservesString, totalIssuanceString)
            }
            .zip(poolProvidersFlow) { _, poolProvidersString ->
                poolProvidersString
            }

        return resultFlow
    }

    override fun getPoolData(
        address: String,
        baseTokenId: String,
        tokenId: String
    ): Flow<PoolData?> {
        return db.poolDao().getPool(tokenId, baseTokenId, address).map {
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
            it?.let { poolLocal ->
                mapPoolLocalToData(selectedCurrency, poolLocal)
            }
        }
    }

    override suspend fun getPoolsCache(address: String): List<PoolData> {
        val poolsLocal = db.poolDao().getPoolsList(address)
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return poolsLocal.map { poolLocal ->
            mapPoolLocalToData(selectedCurrency, poolLocal)
        }
    }

    override fun subscribePoolFlow(address: String): Flow<List<PoolData>> {
        return db.poolDao().getPools(address).map { pools ->
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
            pools.map { poolLocal ->
                mapPoolLocalToData(selectedCurrency, poolLocal)
            }
        }.debounce(500)
    }

    private suspend fun mapPoolLocalToData(
        fiatCurrencyLocal: SoraCurrency,
        poolLocal: PoolLocal
    ): PoolData {
        val token = assetLocalToAssetMapper.map(
            db.assetDao().getToken(poolLocal.assetId, fiatCurrencyLocal.code),
        )
        val baseToken = assetLocalToAssetMapper.map(
            db.assetDao().getToken(poolLocal.assetIdBase, fiatCurrencyLocal.code),
        )
        return PoolLocalMapper.mapLocal(poolLocal, baseToken, token)
    }

    override fun subscribeLocalPoolReserves(
        address: String,
        baseTokenId: String,
        assetId: String
    ): Flow<LiquidityData?> {
        return db.poolDao().getPool(assetId, baseTokenId, address).map { pool ->
            if (pool == null) {
                null
            } else {
                val firstPooled = calculatePooledValue(
                    pool.reservesFirst,
                    pool.poolProvidersBalance,
                    pool.totalIssuance
                )
                val secondPooled = calculatePooledValue(
                    pool.reservesSecond,
                    pool.poolProvidersBalance,
                    pool.totalIssuance
                )

                LiquidityData(
                    pool.reservesFirst,
                    pool.reservesSecond,
                    firstPooled,
                    secondPooled,
                    pool.strategicBonusApy?.toDouble()?.times(100)
                )
            }
        }
    }

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String, dexId: Int): Boolean {
        return wsConnection.isSwapAvailable(tokenId1, tokenId2, dexId)
    }

    override suspend fun getAvailableSources(
        tokenId1: String,
        tokenId2: String,
        dexId: Int
    ): List<Market> =
        wsConnection.fetchAvailableSources(tokenId1, tokenId2, dexId).let {
            marketMapper.mapMarket(it)
        }

    override fun observePoolXYKReserves(
        baseTokenId: String,
        tokenId: String
    ): Flow<String> = flow {
        val key = runtimeManager.getRuntimeSnapshot()
            .reservesKey(
                baseTokenId = baseTokenId,
                tokenId = tokenId.fromHex()
            )
        emitAll(substrateCalls.observeStorage(key))
    }

    override fun observePoolTBCReserves(tokenId: String): Flow<String> = flow {
        val key =
            runtimeManager.getRuntimeSnapshot().poolTBCReserves(tokenId.fromHex())
        emitAll(substrateCalls.observeStorage(key))
    }

    override suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal? {
        val amount2 = mapBalance(
            BigDecimal.ONE,
            tokenId1.precision
        )
        val markets = emptyList<Market>()
        val fee = extrinsicManager.calcFee(
            from = address,
        ) {
            swap(
                dexId = 0,
                inputAssetId = tokenId1.id,
                outputAssetId = tokenId1.id,
                amount = amount2,
                limit = amount2,
                filter = marketMapper.mapMarketsToFilter(markets),
                markets = marketMapper.mapMarketsToStrings(markets),
                desired = WithDesired.INPUT,
            )
        }
        return fee?.let {
            mapBalance(it, tokenId1.precision)
        }
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token,
        address: String,
    ): BigDecimal? {
        val fee = extrinsicManager.calcFee(
            from = address
        ) {
            removeLiquidity(
                dexId = getPoolBaseTokenDexId(tokenId1.id),
                outputAssetIdA = tokenId1.id,
                outputAssetIdB = tokenId2.id,
                markerAssetDesired = mapBalance(BigDecimal.ONE, tokenId1.precision),
                outputAMin = mapBalance(BigDecimal.ONE, tokenId1.precision),
                outputBMin = mapBalance(BigDecimal.ONE, tokenId1.precision)
            )
        }
        return fee?.let {
            mapBalance(it, tokenId1.precision)
        }
    }

    override suspend fun calcAddLiquidityNetworkFee(
        address: String,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): BigDecimal? {
        val amountFromMin = calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = calculateMinAmount(tokenToAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(tokenFrom.id)
        val fee = extrinsicManager.calcFee(
            from = address,
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        dexId = dexId,
                        tokenFrom.id, tokenTo.id
                    )
                }
                initializePool(
                    dexId = dexId,
                    tokenFrom.id, tokenTo.id
                )
            }

            depositLiquidity(
                dexId = dexId,
                tokenFrom.id,
                tokenTo.id,
                mapBalance(tokenFromAmount, tokenFrom.precision),
                mapBalance(tokenToAmount, tokenTo.precision),
                mapBalance(amountFromMin, tokenFrom.precision),
                mapBalance(amountToMin, tokenTo.precision)
            )
        }
        return fee?.let {
            mapBalance(it, tokenFrom.precision)
        }
    }

    override suspend fun observeAddLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        tokenFrom: Token,
        tokenTo: Token,
        tokenFromAmount: BigDecimal,
        tokenToAmount: BigDecimal,
        pairEnabled: Boolean,
        pairPresented: Boolean,
        slippageTolerance: Double
    ): ExtrinsicSubmitStatus {
        val amountFromMin = calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = calculateMinAmount(tokenToAmount, slippageTolerance)
        val dexId = getPoolBaseTokenDexId(tokenFrom.id)
        return extrinsicManager.submitAndWatchExtrinsic(
            from = address,
            keypair = keypair,
            useBatchAll = !pairPresented,
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        dexId = dexId,
                        tokenFrom.id, tokenTo.id
                    )
                }
                initializePool(
                    dexId = dexId,
                    tokenFrom.id, tokenTo.id
                )
            }

            depositLiquidity(
                dexId = dexId,
                tokenFrom.id,
                tokenTo.id,
                mapBalance(tokenFromAmount, tokenFrom.precision),
                mapBalance(tokenToAmount, tokenTo.precision),
                mapBalance(amountFromMin, tokenFrom.precision),
                mapBalance(amountToMin, tokenTo.precision)
            )
        }
    }

    override suspend fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
        dexId: Int,
    ): ExtrinsicSubmitStatus {
        val (amount2, limit2) = if (swapVariant == WithDesired.INPUT) mapBalance(
            amount,
            tokenId1.precision
        ) to mapBalance(limit, tokenId2.precision) else mapBalance(
            amount,
            tokenId2.precision
        ) to mapBalance(limit, tokenId1.precision)
        return extrinsicManager.submitAndWatchExtrinsic(
            from = address,
            keypair = keypair,
        ) {
            swap(
                dexId = dexId,
                inputAssetId = tokenId1.id,
                outputAssetId = tokenId2.id,
                amount = amount2,
                limit = limit2,
                filter = marketMapper.mapMarketsToFilter(markets),
                markets = marketMapper.mapMarketsToStrings(markets),
                desired = swapVariant,
            )
        }
    }

    override suspend fun getSwapQuote(
        tokenId1: String,
        tokenId2: String,
        amount: BigDecimal,
        swapVariant: WithDesired,
        markets: List<Market>,
        feeToken: Token,
        dexId: Int,
    ): SwapQuote? {
        return wsConnection.getSwapFees(
            tokenId1,
            tokenId2,
            mapBalance(amount, feeToken.precision),
            swapVariant.backString,
            marketMapper.mapMarketsToStrings(markets),
            marketMapper.mapMarketsToFilter(markets),
            dexId,
        )?.let {
            SwapQuote(
                mapBalance(it.amount, feeToken.precision),
                mapBalance(it.fee, feeToken.precision),
                it.route,
            )
        }
    }

    override fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String,
        accountAddress: String
    ): Flow<Boolean> {
        return db.poolDao().getPool(outputAssetId, inputAssetId, accountAddress).map { pool ->
            val dexId = getPoolBaseTokenDexId(inputAssetId)
            if (pool != null) {
                true
            } else {
                wsConnection.isPairEnabled(inputAssetId, outputAssetId, dexId)
            }
        }
    }

    override fun isPairPresentedInNetwork(
        baseTokenId: String,
        tokenId: String,
        accountAddress: String
    ): Flow<Boolean> {
        return db.poolDao().getPool(tokenId, baseTokenId, accountAddress).map { pool ->
            if (pool != null) {
                true
            } else {
                runCatching {
                    val reserveAccount = wsConnection.getPoolReserveAccount(
                        baseTokenId,
                        tokenId.fromHex()
                    )
                    reserveAccount != null
                }
                    .getOrElse {
                        false
                    }
            }
        }
    }

    override suspend fun getRemotePoolReserves(
        address: String,
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData {
        return if (presented || enabled) {
            val (reservesFirst, reservesSecond) = wsConnection.getPoolReserves(
                tokenFrom.id,
                tokenTo.id
            ) ?: (BigInteger.ZERO to BigInteger.ZERO)

            LiquidityData(
                firstReserves = mapBalance(reservesFirst, tokenFrom.precision),
                secondReserves = mapBalance(reservesSecond, tokenTo.precision),
                firstPooled = mapBalance(BigInteger.ZERO, tokenFrom.precision),
                secondPooled = mapBalance(BigInteger.ZERO, tokenTo.precision),
                sbApy = getPoolStrategicBonusAPY(tokenTo.id, tokenFrom.id, address)?.toDouble()
                    ?.times(100)
            )
        } else {
            LiquidityData()
        }
    }

    override suspend fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): ExtrinsicSubmitStatus = extrinsicManager.submitAndWatchExtrinsic(
        from = address,
        keypair = keypair,
    ) {
        removeLiquidity(
            dexId = getPoolBaseTokenDexId(token1.id),
            outputAssetIdA = token1.id,
            outputAssetIdB = token2.id,
            markerAssetDesired = mapBalance(markerAssetDesired, token1.precision),
            outputAMin = mapBalance(firstAmountMin, token1.precision),
            outputBMin = mapBalance(secondAmountMin, token2.precision)
        )
    }

    private suspend fun getPoolBaseTokenDexId(tokenId: String): Int {
        return db.poolDao().getPoolBaseToken(tokenId)?.dexId ?: 0
    }
}
