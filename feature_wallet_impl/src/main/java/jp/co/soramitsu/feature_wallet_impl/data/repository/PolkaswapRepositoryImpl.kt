/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.data.repository

import androidx.room.withTransaction
import jp.co.soramitsu.common.data.network.substrate.OptionsProvider
import jp.co.soramitsu.common.data.network.substrate.Pallete
import jp.co.soramitsu.common.data.network.substrate.Storage
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeHolder
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.ExtrinsicLocal
import jp.co.soramitsu.core_db.model.ExtrinsicParam
import jp.co.soramitsu.core_db.model.ExtrinsicParamLocal
import jp.co.soramitsu.core_db.model.ExtrinsicStatus
import jp.co.soramitsu.core_db.model.ExtrinsicType
import jp.co.soramitsu.fearless_utils.encrypt.model.Keypair
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.metadata.module
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_wallet_api.domain.model.ExtrinsicStatusResponse
import jp.co.soramitsu.feature_wallet_api.domain.model.Market
import jp.co.soramitsu.feature_wallet_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_wallet_api.domain.model.WithDesired
import jp.co.soramitsu.feature_wallet_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapBalance
import jp.co.soramitsu.feature_wallet_impl.data.network.substrate.SubstrateApi
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.Date
import javax.inject.Inject

class PolkaswapRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
) : PolkaswapRepository {

    override suspend fun isSwapAvailable(tokenId1: String, tokenId2: String): Boolean {
        return wsConnection.isSwapAvailable(tokenId1, tokenId2)
    }

    override suspend fun getAvailableSources(tokenId1: String, tokenId2: String): List<Market> =
        wsConnection.fetchAvailableSources(tokenId1, tokenId2).let {
            marketMapper.mapMarket(it)
        }

    override fun observePoolXYKReserves(tokenId: String): Flow<String> {
        val key = RuntimeHolder.getRuntime().metadata
            .module(Pallete.POOL_XYK.palleteName)
            .storage(Storage.RESERVES.storageName)
            .storageKey(
                RuntimeHolder.getRuntime(),
                OptionsProvider.feeAssetId.fromHex(),
                tokenId.fromHex()
            )
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
        keypair: Keypair,
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
