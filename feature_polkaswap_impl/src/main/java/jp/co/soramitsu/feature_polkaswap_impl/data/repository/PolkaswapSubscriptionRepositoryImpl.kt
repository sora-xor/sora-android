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
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.BasicPoolLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocal
import jp.co.soramitsu.core_db.model.UserPoolLocal
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.sora.substrate.models.WithDesired
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.poolTBCReserves
import jp.co.soramitsu.sora.substrate.runtime.reservesKey
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.zip

class PolkaswapSubscriptionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
    private val substrateCalls: SubstrateCalls,
    private val blockExplorerManager: BlockExplorerManager,
    private val runtimeManager: RuntimeManager,
) : PolkaswapSubscriptionRepository,
    PolkaswapBlockchainRepositoryImpl(blockExplorerManager, runtimeManager, wsConnection, db) {

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
        val pools = mutableListOf<UserPoolJoinedLocal>()
        val tokensIds = wsConnection.getUserPoolsTokenIds(address)
        val poolsLocal = db.poolDao().getPoolsList(address)
        var count = poolsLocal.size
        val poolsPositionAndFavorite = poolsLocal.associate {
            (it.userPoolLocal.userTokenIdTarget + it.userPoolLocal.userTokenIdBase) to (it.userPoolLocal.sortOrder to it.userPoolLocal.favorite)
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
                                val basicPoolLocal =
                                    BasicPoolLocal(
                                        tokenIdBase = pair.first,
                                        tokenIdTarget = poolDataDto.assetId,
                                        reserveBase = mapBalance(
                                            poolDataDto.reservesFirst,
                                            xorPrecision
                                        ),
                                        reserveTarget = mapBalance(
                                            poolDataDto.reservesSecond,
                                            tokenPrecision
                                        ),
                                        totalIssuance = mapBalance(
                                            poolDataDto.totalIssuance,
                                            xorPrecision
                                        ),
                                        reservesAccount = poolDataDto.reservesAccount,
                                    )
                                val userPoolLocal =
                                    UserPoolLocal(
                                        accountAddress = address,
                                        userTokenIdBase = pair.first,
                                        userTokenIdTarget = poolDataDto.assetId,
                                        poolProvidersBalance = mapBalance(
                                            poolDataDto.poolProvidersBalance,
                                            xorPrecision
                                        ),
                                        favorite = poolsPositionAndFavorite[poolDataDto.assetId + pair.first]?.second
                                            ?: true,
                                        sortOrder = poolsPositionAndFavorite[poolDataDto.assetId + pair.first]?.first
                                            ?: ++count,
                                    )

                                pools.add(
                                    UserPoolJoinedLocal(
                                        userPoolLocal = userPoolLocal,
                                        basicPoolLocal = basicPoolLocal,
                                    )
                                )
                            }
                    }
                }
        }

        db.withTransaction {
            db.poolDao().clearTable(address)
            db.poolDao().insertBasicPools(
                pools.map {
                    it.basicPoolLocal
                }
            )
            db.poolDao().insertUserPools(
                pools.map {
                    it.userPoolLocal
                }
            )
        }
        blockExplorerManager.updatePoolsSbApy()
    }

    override suspend fun getRemotePoolReserves(
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
                sbApy = getPoolStrategicBonusAPY(tokenTo.id, tokenFrom.id)
                    ?.times(100)
            )
        } else {
            LiquidityData()
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
}
