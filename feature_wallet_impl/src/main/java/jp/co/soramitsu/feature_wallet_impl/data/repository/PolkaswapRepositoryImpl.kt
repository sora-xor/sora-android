/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.Storage
import jp.co.soramitsu.common.data.network.substrate.accountPoolsKey
import jp.co.soramitsu.common.data.network.substrate.reservesKey
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.core_db.model.PoolLocal
import jp.co.soramitsu.fearless_utils.encrypt.keypair.substrate.Sr25519Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.PoolData
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.data.mappers.AssetLocalToAssetMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapBalance
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.zip
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

class PolkaswapRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
    private val resourceManager: ResourceManager,
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
        wsConnection.getUserPoolsData(RuntimeHolder.getRuntime(), address, tokensIds)
            .forEach { poolDataDto ->
                if (db.assetDao().getWhitelistOfToken(poolDataDto.assetId).isNullOrEmpty().not()) {
                    db.assetDao().getPrecisionOfToken(poolDataDto.assetId)?.let { tokenPrecision ->
                        pools.add(
                            PoolLocal(
                                poolDataDto.assetId,
                                mapBalance(poolDataDto.reservesFirst, xorPrecision),
                                mapBalance(poolDataDto.reservesSecond, tokenPrecision),
                                mapBalance(poolDataDto.totalIssuance, xorPrecision),
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

    override fun subscribeToPoolsAssets(address: String): Flow<String> {
        val poolsStorageKey = RuntimeHolder.getRuntime().accountPoolsKey(address)
        return wsConnection.observeStorage(poolsStorageKey)
    }

    override suspend fun subscribeToPoolsData(address: String): Flow<String> {
        return wsConnection.getUserPoolsTokenIds(RuntimeHolder.getRuntime(), address)
            .map {
                val reservesAccount =
                    wsConnection.getPoolReserveAccount(RuntimeHolder.getRuntime(), it)
                val reservesKey =
                    RuntimeHolder.getRuntime().reservesKey(it)
                val reservesFlow = wsConnection.observeStorage(reservesKey)

                val totalIssuanceKey =
                    RuntimeHolder.getRuntime().metadata.module(Pallete.POOL_XYK.palleteName)
                        .storage(Storage.TOTAL_ISSUANCES.storageName)
                        .storageKey(RuntimeHolder.getRuntime(), reservesAccount)
                val totalIssuanceFlow = wsConnection.observeStorage(totalIssuanceKey)

                val poolProvidersKey =
                    RuntimeHolder.getRuntime().metadata.module(Pallete.POOL_XYK.palleteName)
                        .storage(Storage.POOL_PROVIDERS.storageName).storageKey(
                            RuntimeHolder.getRuntime(),
                            reservesAccount,
                            address.toAccountId()
                        )
                val poolProvidersFlow = wsConnection.observeStorage(poolProvidersKey)

                val resultFlow =
                    reservesFlow.zip(totalIssuanceFlow) { reservesString, totalIssuanceString ->
                        Pair(reservesString, totalIssuanceString)
                    }.zip(poolProvidersFlow) { pair, poolProvidersString -> poolProvidersString }

                resultFlow
            }.merge()
    }

    override fun subscribePoolFlow(address: String): Flow<List<PoolData>> {
        return db.poolDao().getPools().map { pools ->
            pools.map { poolData ->
                val xorPooled =
                    poolData.reservesFirst * poolData.poolProvidersBalance / poolData.totalIssuance
                val secondPooled =
                    poolData.reservesSecond * poolData.poolProvidersBalance / poolData.totalIssuance
                val share = poolData.poolProvidersBalance / poolData.totalIssuance * BigDecimal(100)

                val token = assetLocalToAssetMapper.map(
                    db.assetDao().getAssetWithToken(address, poolData.assetId)!!.tokenLocal,
                    resourceManager
                )

                PoolData(token, xorPooled, secondPooled, share.toDouble())
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
            .module(Pallete.POOL_TBC.palleteName)
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
    ) {
        db.withTransaction {
            db.transactionDao().insert(
                ExtrinsicLocal(
                    txHash,
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
}
