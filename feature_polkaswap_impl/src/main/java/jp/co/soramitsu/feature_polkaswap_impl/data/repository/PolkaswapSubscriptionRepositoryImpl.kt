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
import jp.co.soramitsu.common.data.network.dto.SwapFeeDto
import jp.co.soramitsu.common.domain.Market
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.ext.safeCast
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.WithDesired
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.BasicPoolLocal
import jp.co.soramitsu.core_db.model.PoolBaseTokenLocal
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocal
import jp.co.soramitsu.core_db.model.UserPoolLocal
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapSubscriptionRepository
import jp.co.soramitsu.feature_polkaswap_api.domain.model.SwapQuote
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.SwapMarketMapper
import jp.co.soramitsu.sora.substrate.request.StateKeys
import jp.co.soramitsu.sora.substrate.runtime.Pallete
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.runtime.Storage
import jp.co.soramitsu.sora.substrate.runtime.assetIdFromKey
import jp.co.soramitsu.sora.substrate.runtime.mapToToken
import jp.co.soramitsu.sora.substrate.runtime.poolTBCReserves
import jp.co.soramitsu.sora.substrate.runtime.reservesKey
import jp.co.soramitsu.sora.substrate.runtime.reservesKeyToken
import jp.co.soramitsu.sora.substrate.runtime.takeInt32
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import jp.co.soramitsu.xcrypto.util.fromHex
import jp.co.soramitsu.xcrypto.util.toHexString
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.xsubstrate.runtime.definitions.types.fromHex
import jp.co.soramitsu.xsubstrate.runtime.metadata.module
import jp.co.soramitsu.xsubstrate.runtime.metadata.storage
import jp.co.soramitsu.xsubstrate.runtime.metadata.storageKey
import jp.co.soramitsu.xsubstrate.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.xsubstrate.wsrpc.SocketService
import jp.co.soramitsu.xsubstrate.wsrpc.executeAsync
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.nonNull
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojo
import jp.co.soramitsu.xsubstrate.wsrpc.mappers.pojoList
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.xsubstrate.wsrpc.request.runtime.storage.GetStorageRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class PolkaswapSubscriptionRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wsConnection: SubstrateApi,
    private val marketMapper: SwapMarketMapper,
    private val substrateCalls: SubstrateCalls,
    private val blockExplorerManager: BlockExplorerManager,
    private val runtimeManager: RuntimeManager,
    private val socketService: SocketService,
) : PolkaswapSubscriptionRepository,
    PolkaswapBasicRepositoryImpl(db, blockExplorerManager) {

    private suspend fun getPoolBaseTokens(): List<Pair<Int, String>> {
        val metadataStorage =
            runtimeManager.getRuntimeSnapshot().metadata
                .module(Pallete.DEX_MANAGER.palletName)
                .storage(Storage.DEX_INFOS.storageName)
        val partialKey = metadataStorage.storageKey()
        return runCatching {
            socketService.executeAsync(
                request = StateKeys(listOf(partialKey)),
                mapper = pojoList<String>().nonNull()
            ).let { storageKeys ->
                storageKeys.mapNotNull { storageKey ->
                    socketService.executeAsync(
                        request = GetStorageRequest(listOf(storageKey)),
                        mapper = pojo<String>().nonNull(),
                    )
                        .let { storage ->
                            val storageType = metadataStorage.type.value!!
                            val storageRawData =
                                storageType.fromHex(runtimeManager.getRuntimeSnapshot(), storage)
                            (storageRawData as? Struct.Instance)?.let { instance ->
                                instance.mapToToken("baseAssetId")?.let { token ->
                                    storageKey.takeInt32() to token
                                }
                            }
                        }
                }
            }
        }
            .onFailure(FirebaseWrapper::recordException)
            .getOrThrow()
    }

    override suspend fun updateAccountPools(address: String) {
        blockExplorerManager.updatePoolsSbApy()
        val baseTokens = getPoolBaseTokens()
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

            val poolLocal = db.poolDao().getBasicPool(tokenFrom.id, tokenTo.id)

            LiquidityData(
                firstReserves = mapBalance(reservesFirst, tokenFrom.precision),
                secondReserves = mapBalance(reservesSecond, tokenTo.precision),
                firstPooled = mapBalance(BigInteger.ZERO, tokenFrom.precision),
                secondPooled = mapBalance(BigInteger.ZERO, tokenTo.precision),
                sbApy = poolLocal?.reservesAccount?.let {
                    getPoolStrategicBonusAPY(it)?.times(100)
                },
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
        val response = socketService.executeAsync(
            request = RuntimeRequest(
                "liquidityProxy_quote",
                listOf(
                    dexId,
                    tokenId1,
                    tokenId2,
                    mapBalance(amount, feeToken.precision).toString(),
                    swapVariant.backString,
                    marketMapper.mapMarketsToStrings(markets),
                    marketMapper.mapMarketsToFilter(markets),
                )
            ),
            mapper = pojo<SwapFeeDto>(),
        ).result
        return response?.let {
            SwapQuote(
                mapBalance(it.amount, feeToken.precision),
                it.route,
            )
        }
    }

    override suspend fun updateBasicPools() {
        val storage = runtimeManager.getRuntimeSnapshot().metadata
            .module(Pallete.POOL_XYK.palletName)
            .storage(Storage.RESERVES.storageName)
        val list = mutableListOf<BasicPoolLocal>()
        db.poolDao().getPoolBaseTokens().forEach { token ->
            val key = runtimeManager.getRuntimeSnapshot().reservesKeyToken(token.base.tokenId)
            substrateCalls.getStateKeys(key).forEach { storageKey ->
                val targetToken = storageKey.assetIdFromKey()
                val whitelisted =
                    db.assetDao().getWhitelistOfToken(targetToken).isNullOrEmpty().not()
                if (whitelisted) {
                    substrateCalls.getStorageHex(storageKey)?.let { storageHex ->
                        storage.type.value
                            ?.fromHex(runtimeManager.getRuntimeSnapshot(), storageHex)
                            ?.safeCast<List<BigInteger>>()?.let { reserves ->
                                val reserveAccount = wsConnection.getPoolReserveAccount(
                                    token.base.tokenId,
                                    targetToken.fromHex()
                                )
                                val total = reserveAccount?.let { wsConnection.getPoolTotalIssuances(it) }?.let {
                                    mapBalance(it, token.token.precision)
                                }
                                list.add(
                                    BasicPoolLocal(
                                        token.base.tokenId,
                                        targetToken,
                                        mapBalance(reserves[0], token.token.precision),
                                        mapBalance(reserves[1], token.token.precision),
                                        total ?: BigDecimal.ZERO,
                                        reserveAccount?.let { runtimeManager.toSoraAddress(it) } ?: "",
                                    )
                                )
                            }
                    }
                }
            }
        }
        val minus = db.poolDao().getBasicPools().filter { db ->
            list.find { it.tokenIdBase == db.tokenIdBase && it.tokenIdTarget == db.tokenIdTarget } == null
        }
        db.poolDao().deleteBasicPools(minus)
        db.poolDao().insertBasicPools(list)
    }

    override fun subscribeToBasicPools(): Flow<String> = flow {
        val flows = db.poolDao().getPoolBaseTokens().map { token ->
            subscribeBasicPoolsOfToken(token.base.tokenId).flatMapLatest {
                val poolsFlow = it.map { triple ->
                    subscribeToPoolData(triple.first, triple.second.fromHex(), triple.third)
                }
                combine(poolsFlow) { "combined" }
            }
        }
        val combined = if (flows.isEmpty())
            flowOf("")
        else
            combine(flows) {
                it.getOrElse(0) { "" }
            }
        emitAll(combined)
    }

    private fun subscribeBasicPoolsOfToken(tokenId: String): Flow<List<Triple<String, String, ByteArray>>> = flow {
        val key = runtimeManager.getRuntimeSnapshot().reservesKeyToken(tokenId)
        val flows = substrateCalls.getStateKeys(key).mapNotNull { storageKey ->
            val targetToken = storageKey.assetIdFromKey()
            val whitelisted =
                db.assetDao().getWhitelistOfToken(targetToken).isNullOrEmpty().not()
            val reserve = getReserveAccountOfPool(tokenId, targetToken)
            if (whitelisted && reserve != null) {
                substrateCalls.observeStorage(storageKey).map {
                    Triple(tokenId, targetToken, reserve)
                }
            } else {
                null
            }
        }
        val combined = combine(flows) { it.toList() }
        emitAll(combined)
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
        val poolsFlow = if (tokensPair.isEmpty()) {
            flowOf("")
        } else {
            val flows = tokensPair.map { tokenPair ->
                tokenPair.second.mapNotNull { tToken ->
                    getReserveAccountOfPool(
                        tokenPair.first,
                        tToken.toHexString(),
                    )?.let { resAccount ->
                        subscribeToPoolData(tokenPair.first, tToken, resAccount)
                            .combine(subscribeAccountPoolProviders(address, resAccount)) { a, b ->
                                (a + b).take(5)
                            }
                    }
                }
            }.flatten()
            if (flows.isEmpty())
                flowOf("")
            else
                combine(flows) {
                    it.getOrElse(0) { "" }
                }
        }
        emitAll(poolsFlow)
    }

    private fun subscribeAccountPoolProviders(
        address: String,
        reservesAccount: ByteArray,
    ): Flow<String> = flow {
        val poolProvidersKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.POOL_PROVIDERS.storageName)
                .storageKey(
                    runtimeManager.getRuntimeSnapshot(),
                    reservesAccount,
                    address.toAccountId()
                )
        val poolProvidersFlow = substrateCalls.observeStorage(poolProvidersKey)
        emitAll(poolProvidersFlow)
    }

    private fun subscribeToPoolData(
        baseTokenId: String,
        tokenId: ByteArray,
        reservesAccount: ByteArray,
    ): Flow<String> = flow {
        val reservesKey =
            runtimeManager.getRuntimeSnapshot().reservesKey(baseTokenId, tokenId)
        val reservesFlow = substrateCalls.observeStorage(reservesKey)

        val totalIssuanceKey =
            runtimeManager.getRuntimeSnapshot().metadata.module(Pallete.POOL_XYK.palletName)
                .storage(Storage.TOTAL_ISSUANCES.storageName)
                .storageKey(runtimeManager.getRuntimeSnapshot(), reservesAccount)
        val totalIssuanceFlow = substrateCalls.observeStorage(totalIssuanceKey)

        val resultFlow = reservesFlow
            .combine(totalIssuanceFlow) { reservesString, totalIssuanceString ->
                (reservesString + totalIssuanceString).take(5)
            }

        emitAll(resultFlow)
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
        return getUserPool(inputAssetId, outputAssetId, accountAddress).map { pool ->
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
        return getUserPool(baseTokenId, tokenId, accountAddress).map { pool ->
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

    override fun subscribeEachBlock(): Flow<String> = flow {
        val key = runtimeManager.getRuntimeSnapshot().metadata
            .module(Pallete.SYSTEM.palletName)
            .storage(Storage.PARENT_HASH.storageName)
            .storageKey()
        emitAll(substrateCalls.observeStorage(key).debounce(5000))
    }

    private suspend fun getReserveAccountOfPool(
        baseToken: String,
        targetToken: String,
    ): ByteArray? {
        val local = db.poolDao().getBasicPool(baseToken, targetToken)
        return local?.reservesAccount?.toAccountId()
            ?: wsConnection.getPoolReserveAccount(baseToken, targetToken.fromHex())
    }
}
