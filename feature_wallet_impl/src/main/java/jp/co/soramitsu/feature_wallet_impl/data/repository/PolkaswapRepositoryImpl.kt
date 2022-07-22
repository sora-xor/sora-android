/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateMinAmount
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculatePooledValue
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateShareOfPool
import jp.co.soramitsu.sora.substrate.models.ExtrinsicSubmitStatus
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.runtime.accountPoolsKey
import jp.co.soramitsu.sora.substrate.runtime.reservesKey
import jp.co.soramitsu.sora.substrate.substrate.ExtrinsicManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.sora.substrate.substrate.depositLiquidity
import jp.co.soramitsu.sora.substrate.substrate.initializePool
import jp.co.soramitsu.sora.substrate.substrate.register
import jp.co.soramitsu.sora.substrate.substrate.removeLiquidity
import jp.co.soramitsu.sora.substrate.substrate.swap
import jp.co.soramitsu.xnetworking.subquery.SubQueryClient
import jp.co.soramitsu.xnetworking.subquery.history.SubQueryHistoryItem
import jp.co.soramitsu.xnetworking.subquery.history.sora.SoraSubqueryResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.zip
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

@ExperimentalCoroutinesApi
class PolkaswapRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
    private val resourceManager: ResourceManager,
    private val subQueryClient: SubQueryClient<SoraSubqueryResponse, SubQueryHistoryItem>,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val extrinsicManager: ExtrinsicManager,
    private val substrateCalls: SubstrateCalls,
    private val runtimeManager: RuntimeManager,
) : PolkaswapRepository {

    override fun getPolkaswapDisclaimerVisibility(): Flow<Boolean> {
        return datasource.getDisclaimerVisibility()
    }

    override suspend fun setPolkaswapDisclaimerVisibility(v: Boolean) {
        datasource.saveDisclaimerVisibility(v)
    }

    override suspend fun updateAccountPools(address: String) {
        val xorPrecision =
            requireNotNull(db.assetDao().getPrecisionOfToken(SubstrateOptionsProvider.feeAssetId))
        val pools = mutableListOf<PoolLocal>()
        val tokensIds = wsConnection.getUserPoolsTokenIds(address)

        wsConnection.getUserPoolsData(address, tokensIds)
            .forEach { poolDataDto ->
                if (db.assetDao().getWhitelistOfToken(poolDataDto.assetId).isNullOrEmpty().not()) {
                    db.assetDao().getPrecisionOfToken(poolDataDto.assetId)?.let { tokenPrecision ->
                        pools.add(
                            PoolLocal(
                                poolDataDto.assetId,
                                address,
                                mapBalance(poolDataDto.reservesFirst, xorPrecision),
                                mapBalance(poolDataDto.reservesSecond, tokenPrecision),
                                mapBalance(poolDataDto.totalIssuance, xorPrecision),
                                getSbApy()?.firstOrNull { it.tokenId == poolDataDto.assetId }?.sbApy?.toBigDecimal(),
                                mapBalance(poolDataDto.poolProvidersBalance, xorPrecision)
                            )
                        )
                    }
                }
            }

        db.withTransaction {
            db.poolDao().clearTable()
            db.poolDao().insert(pools)
        }
    }

    override suspend fun updateAccountPool(
        address: String,
        tokenId: String
    ) {
        wsConnection.getUserPoolsTokenIds(address).firstOrNull {
            it.contentEquals(tokenId.fromHex())
        } ?: return

        val xorPrecision =
            requireNotNull(db.assetDao().getPrecisionOfToken(SubstrateOptionsProvider.feeAssetId))
        val poolDataDto =
            wsConnection.getUserPoolData(address, tokenId.fromHex())

        if (db.assetDao().getWhitelistOfToken(tokenId).isNullOrEmpty()
            .not() && poolDataDto != null
        ) {
            db.assetDao().getPrecisionOfToken(tokenId)?.let { tokenPrecision ->
                db.poolDao().updatePool(
                    poolDataDto.assetId,
                    address,
                    mapBalance(poolDataDto.reservesFirst, xorPrecision),
                    mapBalance(poolDataDto.reservesSecond, tokenPrecision),
                    mapBalance(poolDataDto.totalIssuance, xorPrecision),
                    getSbApy()?.firstOrNull { it.tokenId == poolDataDto.assetId }?.sbApy?.toBigDecimal(),
                    mapBalance(poolDataDto.poolProvidersBalance, xorPrecision)
                )
            }
        }
    }

    override suspend fun getPoolStrategicBonusAPY(tokenId: String): Double? =
        getSbApy()?.firstOrNull { it.tokenId == tokenId }?.sbApy

    private suspend fun getSbApy() = runCatching { subQueryClient.getSpApy() }.getOrNull()

    override fun subscribeToPoolsAssets(address: String): Flow<String> {
        val poolsStorageKey = runtimeManager.getRuntimeSnapshot().accountPoolsKey(address)
        return substrateCalls.observeStorage(poolsStorageKey)
    }

    override suspend fun subscribeToPoolsData(address: String): Flow<String> {
        val tokens = wsConnection.getUserPoolsTokenIds(address)
        return if (tokens.isEmpty()) {
            flowOf("")
        } else {
            tokens.map { tokenId ->
                subscribeToPoolData(address, tokenId)
            }.merge()
        }
    }

    private suspend fun subscribeToPoolData(address: String, tokenId: ByteArray): Flow<String> {
        val reservesAccount = wsConnection.getPoolReserveAccount(
            tokenId
        )
        val reservesKey = runtimeManager.getRuntimeSnapshot().reservesKey(runtimeManager, tokenId)
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

    override fun getPoolData(address: String, tokenId: String): Flow<PoolData?> {
        return db.poolDao().getPool(tokenId, address).map {
            it?.let {
                val xorPooled = it.reservesFirst * it.poolProvidersBalance / it.totalIssuance
                val secondPooled = it.reservesSecond * it.poolProvidersBalance / it.totalIssuance
                val share = it.poolProvidersBalance / it.totalIssuance * BigDecimal(100)
                val strategicBonusApy = it.strategicBonusApy?.multiply(BigDecimal(100))

                val token = assetLocalToAssetMapper.map(
                    db.assetDao().getToken(it.assetId),
                    resourceManager
                )

                PoolData(
                    token,
                    xorPooled,
                    it.reservesFirst,
                    secondPooled,
                    it.reservesSecond,
                    strategicBonusApy,
                    share.toDouble(),
                    it.totalIssuance,
                    it.poolProvidersBalance
                )
            }
        }
    }

    override fun subscribePoolFlow(address: String): Flow<List<PoolData>> {
        return db.poolDao().getPools(address).map { pools ->
            pools.map { poolData ->
                val xorPooled = calculatePooledValue(
                    poolData.reservesFirst,
                    poolData.poolProvidersBalance,
                    poolData.totalIssuance
                )
                val secondPooled = calculatePooledValue(
                    poolData.reservesSecond,
                    poolData.poolProvidersBalance,
                    poolData.totalIssuance
                )

                val share = calculateShareOfPool(
                    poolData.poolProvidersBalance,
                    poolData.totalIssuance
                )

                val strategicBonusApy = poolData.strategicBonusApy?.multiply(BigDecimal(100))

                val token = assetLocalToAssetMapper.map(
                    db.assetDao().getToken(poolData.assetId),
                    resourceManager
                )

                PoolData(
                    token,
                    xorPooled,
                    poolData.reservesFirst,
                    secondPooled,
                    poolData.reservesSecond,
                    strategicBonusApy,
                    share.toDouble(),
                    poolData.totalIssuance,
                    poolData.poolProvidersBalance
                )
            }
        }
    }

    override fun subscribeLocalPoolReserves(
        address: String,
        assetId: String
    ): Flow<LiquidityData?> {
        return db.poolDao().getPool(assetId, address).map { pool ->
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

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean {
        return wsConnection.isSwapAvailable(tokenId1, tokenId2)
    }

    override suspend fun getAvailableSources(tokenId1: String, tokenId2: String): List<Market> =
        wsConnection.fetchAvailableSources(tokenId1, tokenId2).let {
            marketMapper.mapMarket(it)
        }

    override fun observePoolXYKReserves(tokenId: String): Flow<String> {
        val key = runtimeManager.getRuntimeSnapshot().reservesKey(runtime = runtimeManager, tokenId = tokenId.fromHex())
        return substrateCalls.observeStorage(key)
    }

    override fun observePoolTBCReserves(tokenId: String): Flow<String> {
        val key = runtimeManager.getRuntimeSnapshot().metadata
            .module(Pallete.POOL_TBC.palletName)
            .storage(Storage.RESERVES_COLLATERAL.storageName)
            .storageKey(
                runtimeManager.getRuntimeSnapshot(),
                tokenId.fromHex()
            )
        return substrateCalls.observeStorage(key)
    }

    override suspend fun calcSwapNetworkFee(
        tokenId1: Token,
        address: String,
    ): BigDecimal {
        val amount2 = mapBalance(
            BigDecimal.ONE,
            tokenId1.precision
        )
        val markets = emptyList<Market>()
        val fee = extrinsicManager.calcFee(
            from = address,
        ) {
            swap(
                runtime = runtimeManager,
                inputAssetId = tokenId1.id,
                outputAssetId = tokenId1.id,
                amount = amount2,
                limit = amount2,
                filter = marketMapper.mapMarketsToFilter(markets),
                markets = marketMapper.mapMarketsToStrings(markets),
                desired = WithDesired.INPUT,
            )
        }
        return mapBalance(fee, tokenId1.precision)
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token,
        address: String,
    ): BigDecimal {
        val fee = extrinsicManager.calcFee(
            from = address
        ) {
            removeLiquidity(
                runtime = runtimeManager,
                outputAssetIdA = tokenId1.id,
                outputAssetIdB = tokenId2.id,
                markerAssetDesired = mapBalance(BigDecimal.ONE, tokenId1.precision),
                outputAMin = mapBalance(BigDecimal.ONE, tokenId1.precision),
                outputBMin = mapBalance(BigDecimal.ONE, tokenId1.precision)
            )
        }
        return mapBalance(fee, tokenId1.precision)
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
    ): BigDecimal {
        val amountFromMin = calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = calculateMinAmount(tokenToAmount, slippageTolerance)
        val fee = extrinsicManager.calcFee(
            from = address,
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        runtime = runtimeManager,
                        tokenFrom.id, tokenTo.id
                    )
                }
                initializePool(
                    runtime = runtimeManager,
                    tokenFrom.id, tokenTo.id
                )
            }

            depositLiquidity(
                runtime = runtimeManager,
                tokenFrom.id,
                tokenTo.id,
                mapBalance(tokenFromAmount, tokenFrom.precision),
                mapBalance(tokenToAmount, tokenTo.precision),
                mapBalance(amountFromMin, tokenFrom.precision),
                mapBalance(amountToMin, tokenTo.precision)
            )
        }
        return mapBalance(fee, tokenFrom.precision)
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
        return extrinsicManager.submitAndWatchExtrinsic(
            from = address,
            keypair = keypair,
            useBatchAll = !pairPresented,
        ) {
            if (!pairPresented) {
                if (!pairEnabled) {
                    register(
                        runtime = runtimeManager,
                        tokenFrom.id, tokenTo.id
                    )
                }
                initializePool(
                    runtime = runtimeManager,
                    tokenFrom.id, tokenTo.id
                )
            }

            depositLiquidity(
                runtime = runtimeManager,
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
                runtime = runtimeManager,
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
    ): SwapQuote? {
        val tokenId = if (swapVariant == WithDesired.INPUT) tokenId1 else tokenId2
        val precision = db.assetDao().getPrecisionOfToken(tokenId)
            ?: throw IllegalArgumentException("Token ($tokenId) not found in db")
        val precisionFee = db.assetDao().getPrecisionOfToken(SubstrateOptionsProvider.feeAssetId)
            ?: throw IllegalArgumentException("Token ($tokenId) not found in db")
        return wsConnection.getSwapFees(
            tokenId1,
            tokenId2,
            mapBalance(amount, precision),
            swapVariant.backString,
            marketMapper.mapMarketsToStrings(markets),
            marketMapper.mapMarketsToFilter(markets)
        )?.let {
            SwapQuote(mapBalance(it.amount, 18), mapBalance(it.fee, precisionFee))
        }
    }

    override fun isPairEnabled(
        inputAssetId: String,
        outputAssetId: String,
        accountAddress: String
    ): Flow<Boolean> {
        return db.poolDao().getPool(outputAssetId, accountAddress).map { pool ->
            if (pool != null) {
                true
            } else {
                wsConnection.isPairEnabled(inputAssetId, outputAssetId)
            }
        }
    }

    override fun isPairPresentedInNetwork(
        tokenId: String,
        accountAddress: String
    ): Flow<Boolean> {
        return db.poolDao().getPool(tokenId, accountAddress).map { pool ->
            if (pool != null) {
                true
            } else {
                runCatching {
                    val reserveAccount = wsConnection.getPoolReserveAccount(
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
                tokenTo.id
            ) ?: (BigInteger.ZERO to BigInteger.ZERO)

            val sbApy = getPoolStrategicBonusAPY(tokenTo.id)?.times(100)
            LiquidityData(
                firstReserves = mapBalance(reservesFirst, tokenFrom.precision),
                secondReserves = mapBalance(reservesSecond, tokenTo.precision),
                firstPooled = mapBalance(BigInteger.ZERO, tokenFrom.precision),
                secondPooled = mapBalance(BigInteger.ZERO, tokenFrom.precision),
                sbApy = sbApy
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
            runtime = runtimeManager,
            outputAssetIdA = token1.id,
            outputAssetIdB = token2.id,
            markerAssetDesired = mapBalance(markerAssetDesired, token1.precision),
            outputAMin = mapBalance(firstAmountMin, token1.precision),
            outputBMin = mapBalance(secondAmountMin, token2.precision)
        )
    }
}
