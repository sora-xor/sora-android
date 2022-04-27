/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.Storage
import jp.co.soramitsu.common.data.network.substrate.accountPoolsKey
import jp.co.soramitsu.common.data.network.substrate.reservesKey
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ExtrinsicLiquidityType
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.LiquidityData
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapBalance
import jp.co.soramitsu.feature_wallet_impl.data.network.request.SubqueryRequest
import jp.co.soramitsu.feature_wallet_impl.data.network.sorascan.SoraScanApi
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateMinAmount
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculatePooledValue
import jp.co.soramitsu.feature_wallet_impl.util.PolkaswapFormulas.calculateShareOfPool
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.zip
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Date
import javax.inject.Inject

@ExperimentalCoroutinesApi
class PolkaswapRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
    private val resourceManager: ResourceManager,
    private val soraScanApi: SoraScanApi,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
) : PolkaswapRepository {

    override fun getPolkaswapDisclaimerVisibility(): Flow<Boolean> {
        return datasource.getDisclaimerVisibility()
    }

    override suspend fun setPolkaswapDisclaimerVisibility(v: Boolean) {
        datasource.saveDisclaimerVisibility(v)
    }

    override suspend fun updateAccountPools(address: String) {
        val xorPrecision =
            requireNotNull(db.assetDao().getPrecisionOfToken(OptionsProvider.feeAssetId))
        val pools = mutableListOf<PoolLocal>()
        val tokensIds = wsConnection.getUserPoolsTokenIds(RuntimeHolder.getRuntime(), address)
        val poolInfo = getPoolInfo()

        wsConnection.getUserPoolsData(RuntimeHolder.getRuntime(), address, tokensIds)
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
                                poolInfo.firstOrNull { it.node.targetAssetId == poolDataDto.assetId }?.node?.strategicBonusApy,
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
        wsConnection.getUserPoolsTokenIds(RuntimeHolder.getRuntime(), address).firstOrNull {
            it.contentEquals(tokenId.fromHex())
        } ?: return

        val xorPrecision =
            requireNotNull(db.assetDao().getPrecisionOfToken(OptionsProvider.feeAssetId))
        val poolDataDto =
            wsConnection.getUserPoolData(RuntimeHolder.getRuntime(), address, tokenId.fromHex())
        val poolInfo = getPoolInfo()

        if (db.assetDao().getWhitelistOfToken(tokenId).isNullOrEmpty().not() && poolDataDto != null) {
            db.assetDao().getPrecisionOfToken(tokenId)?.let { tokenPrecision ->
                db.poolDao().updatePool(
                    poolDataDto.assetId,
                    mapBalance(poolDataDto.reservesFirst, xorPrecision),
                    mapBalance(poolDataDto.reservesSecond, tokenPrecision),
                    mapBalance(poolDataDto.totalIssuance, xorPrecision),
                    poolInfo.firstOrNull { it.node.targetAssetId == poolDataDto.assetId }?.node?.strategicBonusApy,
                    mapBalance(poolDataDto.poolProvidersBalance, xorPrecision)
                )
            }
        }
    }

    private suspend fun getPoolInfo() =
        try {
            soraScanApi.getStrategicBonusAPY(
                SubqueryRequest(
                    """ query {
                poolXYKEntities (
                    first: 1
                    orderBy: UPDATED_DESC
                  )
                  {
                    nodes {
                      pools {
                        edges {
                          node {
                            targetAssetId,
                            priceUSD,
                            strategicBonusApy
                          }
                        }
                      }
                    }
                  }
                }
                    """.trimIndent()
                )
            ).data.poolXYKEntities.nodes.first().pools.edges
        } catch (e: Throwable) {
            emptyList()
        }

    override suspend fun getPoolStrategicBonusAPY(tokenId: String): BigDecimal? =
        getPoolInfo()
            .firstOrNull { it.node.targetAssetId == tokenId }
            ?.node
            ?.strategicBonusApy

    override fun subscribeToPoolsAssets(address: String): Flow<String> {
        val poolsStorageKey = RuntimeHolder.getRuntime().accountPoolsKey(address)
        return wsConnection.observeStorage(poolsStorageKey)
    }

    override suspend fun subscribeToPoolsData(address: String): Flow<String> {
        val tokens = wsConnection.getUserPoolsTokenIds(RuntimeHolder.getRuntime(), address)
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
            RuntimeHolder.getRuntime(),
            tokenId
        )
        val reservesKey = RuntimeHolder.getRuntime().reservesKey(tokenId)
        val reservesFlow = wsConnection.observeStorage(reservesKey)

        val totalIssuanceKey =
            RuntimeHolder.getRuntime().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.TOTAL_ISSUANCES.storageName)
                .storageKey(RuntimeHolder.getRuntime(), reservesAccount)
        val totalIssuanceFlow = wsConnection.observeStorage(totalIssuanceKey)

        val poolProvidersKey =
            RuntimeHolder.getRuntime().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.POOL_PROVIDERS.storageName)
                .storageKey(RuntimeHolder.getRuntime(), reservesAccount, address.toAccountId())
        val poolProvidersFlow = wsConnection.observeStorage(poolProvidersKey)

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
        return db.poolDao().getPool(tokenId).map {
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
        return db.poolDao().getPools().map { pools ->
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
        return db.poolDao().getPool(assetId).map { pool ->
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
                    pool.strategicBonusApy?.multiply(BigDecimal(100))
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
        val key = RuntimeHolder.getRuntime().reservesKey(tokenId = tokenId.fromHex())
        return wsConnection.observeStorage(key)
    }

    override fun observePoolTBCReserves(tokenId: String): Flow<String> {
        val key = RuntimeHolder.getRuntime().metadata
            .module(Pallete.POOL_TBC.palletName)
            .storage(Storage.RESERVES_COLLATERAL.storageName)
            .storageKey(
                RuntimeHolder.getRuntime(),
                tokenId.fromHex()
            )
        return wsConnection.observeStorage(key)
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
        val result = wsConnection.calcSwapNetworkFee(
            address,
            RuntimeHolder.getRuntime(),
            tokenId1.id,
            tokenId1.id,
            marketMapper.mapMarketsToFilter(markets),
            marketMapper.mapMarketsToStrings(markets),
            WithDesired.INPUT,
            amount2,
            amount2,
        )
        return mapBalance(result, tokenId1.precision)
    }

    override suspend fun calcRemoveLiquidityNetworkFee(
        tokenId1: Token,
        tokenId2: Token,
        address: String,
    ): BigDecimal {
        val result = wsConnection.calcRemoveLiquidityNetworkFee(
            address,
            RuntimeHolder.getRuntime(),
            tokenId1.id,
            tokenId2.id,
            mapBalance(BigDecimal.ONE, tokenId1.precision),
            mapBalance(BigDecimal.ONE, tokenId1.precision),
            mapBalance(BigDecimal.ONE, tokenId2.precision)
        )

        return mapBalance(result, tokenId1.precision)
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

        val result = wsConnection.calcAddLiquidityNetworkFee(
            address,
            RuntimeHolder.getRuntime(),
            tokenFrom.id,
            tokenTo.id,
            mapBalance(tokenFromAmount, tokenFrom.precision),
            mapBalance(tokenToAmount, tokenTo.precision),
            mapBalance(amountFromMin, tokenFrom.precision),
            mapBalance(amountToMin, tokenTo.precision),
            pairEnabled,
            pairPresented
        )

        return mapBalance(result, tokenFrom.precision)
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
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        val amountFromMin = calculateMinAmount(tokenFromAmount, slippageTolerance)
        val amountToMin = calculateMinAmount(tokenToAmount, slippageTolerance)

        return wsConnection.observeAddLiquidity(
            address,
            keypair,
            RuntimeHolder.getRuntime(),
            tokenFrom.id,
            tokenTo.id,
            mapBalance(tokenFromAmount, tokenFrom.precision),
            mapBalance(tokenToAmount, tokenTo.precision),
            mapBalance(amountFromMin, tokenFrom.precision),
            mapBalance(amountToMin, tokenTo.precision),
            pairEnabled,
            pairPresented
        )
    }

    override fun observeSwap(
        tokenId1: Token,
        tokenId2: Token,
        keypair: Sr25519Keypair,
        address: String,
        markets: List<Market>,
        swapVariant: WithDesired,
        amount: BigDecimal,
        limit: BigDecimal,
    ): Flow<Pair<String, ExtrinsicStatusResponse>> {
        val (amount2, limit2) = if (swapVariant == WithDesired.INPUT) mapBalance(
            amount,
            tokenId1.precision
        ) to mapBalance(limit, tokenId2.precision) else mapBalance(
            amount,
            tokenId2.precision
        ) to mapBalance(limit, tokenId1.precision)
        return wsConnection.observeSwap(
            keypair,
            address,
            RuntimeHolder.getRuntime(),
            tokenId1.id,
            tokenId2.id,
            marketMapper.mapMarketsToFilter(markets),
            marketMapper.mapMarketsToStrings(markets),
            swapVariant,
            amount2,
            limit2
        )
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
        val precisionFee = db.assetDao().getPrecisionOfToken(OptionsProvider.feeAssetId)
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

    override suspend fun saveSwap(
        txHash: String,
        status: ExtrinsicStatusResponse,
        fee: BigDecimal,
        eventSuccess: Boolean?,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        market: Market,
        liquidityFee: BigDecimal,
        soraAccount: SoraAccount,
    ) {
        db.withTransaction {
            db.transactionDao().insert(
                ExtrinsicLocal(
                    txHash,
                    soraAccount.substrateAddress,
                    (status as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    fee,
                    when (status) {
                        is ExtrinsicStatusResponse.ExtrinsicStatusFinalized -> ExtrinsicStatus.COMMITTED
                        is ExtrinsicStatusResponse.ExtrinsicStatusPending -> ExtrinsicStatus.PENDING
                    },
                    Date().time,
                    ExtrinsicType.SWAP,
                    eventSuccess,
                    true,
                )
            )
            db.transactionDao().insertParams(
                listOf(
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.TOKEN.paramName,
                        tokenIdFrom
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.TOKEN2.paramName,
                        tokenIdTo
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT.paramName,
                        amountFrom.toString()
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT2.paramName,
                        amountTo.toString()
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.SWAP_MARKET.paramName,
                        market.backString
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT3.paramName,
                        liquidityFee.toString()
                    ),
                )
            )
        }
    }

    override suspend fun saveAddLiquidity(
        txHash: String,
        status: ExtrinsicStatusResponse,
        eventSuccess: Boolean?,
        networkFee: BigDecimal,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        soraAccount: SoraAccount
    ) {
        db.withTransaction {
            db.transactionDao().insert(
                ExtrinsicLocal(
                    txHash,
                    soraAccount.substrateAddress,
                    (status as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    networkFee,
                    when (status) {
                        is ExtrinsicStatusResponse.ExtrinsicStatusFinalized -> ExtrinsicStatus.COMMITTED
                        is ExtrinsicStatusResponse.ExtrinsicStatusPending -> ExtrinsicStatus.PENDING
                    },
                    Date().time,
                    ExtrinsicType.ADD_REMOVE_LIQUIDITY,
                    eventSuccess,
                    true,
                )
            )
            db.transactionDao().insertParams(
                listOf(
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.TOKEN.paramName,
                        tokenIdFrom
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.TOKEN2.paramName,
                        tokenIdTo
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT.paramName,
                        amountFrom.toString()
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT2.paramName,
                        amountTo.toString()
                    )
                )
            )
        }
    }

    override suspend fun isPairEnabled(inputAssetId: String, outputAssetId: String): Flow<Boolean> {
        return db.poolDao().getPool(outputAssetId).map { pool ->
            if (pool != null) {
                true
            } else {
                wsConnection.isPairEnabled(inputAssetId, outputAssetId)
            }
        }
    }

    override suspend fun isPairPresentedInNetwork(tokenId: String): Flow<Boolean> {
        return db.poolDao().getPool(tokenId).map { pool ->
            if (pool != null) {
                true
            } else {
                runCatching {
                    val reserveAccount = wsConnection.getPoolReserveAccount(
                        RuntimeHolder.getRuntime(),
                        tokenId.fromHex()
                    )
                    reserveAccount != null
                }
                    .getOrElse {
                        false
                    }
//                try {
//
//                    true
//                } catch (e: Throwable) {
//                    false
//                }
            }
        }
    }

    override suspend fun getRemotePoolReserves(
        address: String,
        runtime: RuntimeSnapshot,
        tokenFrom: Token,
        tokenTo: Token,
        enabled: Boolean,
        presented: Boolean
    ): LiquidityData {
        return if (presented || enabled) {
            val (reservesFirst, reservesSecond) = wsConnection.getPoolReserves(
                RuntimeHolder.getRuntime(),
                tokenTo.id
            ) ?: BigInteger.ZERO to BigInteger.ZERO

            val sbApy = getPoolStrategicBonusAPY(tokenTo.id)?.multiply(BigDecimal(100))
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

    override fun observeRemoveLiquidity(
        address: String,
        keypair: Sr25519Keypair,
        token1: Token,
        token2: Token,
        markerAssetDesired: BigDecimal,
        firstAmountMin: BigDecimal,
        secondAmountMin: BigDecimal
    ): Flow<Pair<String, ExtrinsicStatusResponse>> = wsConnection.observeRemoveLiquidity(
        keypair,
        address,
        RuntimeHolder.getRuntime(),
        token1.id,
        token2.id,
        mapBalance(markerAssetDesired, token1.precision),
        mapBalance(firstAmountMin, token1.precision),
        mapBalance(secondAmountMin, token2.precision)
    )

    override suspend fun saveRemoveLiquidity(
        txHash: String,
        status: ExtrinsicStatusResponse,
        fee: BigDecimal,
        eventSuccess: Nothing?,
        tokenIdFrom: String,
        tokenIdTo: String,
        amountFrom: BigDecimal,
        amountTo: BigDecimal,
        soraAccount: SoraAccount
    ) {
        db.withTransaction {
            db.transactionDao().insert(
                ExtrinsicLocal(
                    txHash,
                    soraAccount.substrateAddress,
                    (status as? ExtrinsicStatusResponse.ExtrinsicStatusFinalized)?.inBlock,
                    fee,
                    when (status) {
                        is ExtrinsicStatusResponse.ExtrinsicStatusFinalized -> ExtrinsicStatus.COMMITTED
                        is ExtrinsicStatusResponse.ExtrinsicStatusPending -> ExtrinsicStatus.PENDING
                    },
                    Date().time,
                    ExtrinsicType.ADD_REMOVE_LIQUIDITY,
                    eventSuccess,
                    true,
                )
            )

            db.transactionDao().insertParams(
                listOf(
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.EXTRINSIC_TYPE.paramName,
                        ExtrinsicLiquidityType.WITHDRAW.name,
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.TOKEN.paramName,
                        tokenIdFrom
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.TOKEN2.paramName,
                        tokenIdTo
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT.paramName,
                        amountFrom.toString()
                    ),
                    ExtrinsicParamLocal(
                        txHash,
                        ExtrinsicParam.AMOUNT2.paramName,
                        amountTo.toString()
                    ),
                )
            )
        }
    }
}
