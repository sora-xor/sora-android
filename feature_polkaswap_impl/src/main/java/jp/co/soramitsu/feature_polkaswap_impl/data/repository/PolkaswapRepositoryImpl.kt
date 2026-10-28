/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_polkaswap_impl.data.repository

import androidx.room.withTransaction
import javax.inject.Inject
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.domain.PoolDex
import jp.co.soramitsu.common.domain.Token
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.domain.model.BasicPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonPoolData
import jp.co.soramitsu.common_wallet.domain.model.CommonUserPoolData
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculatePooledValue
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocal
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocalNullable
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_blockexplorer_api.data.SoraConfigManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.PoolLocalMapper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.sora.substrate.substrate.SubstrateCalls
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@ExperimentalCoroutinesApi
class PolkaswapRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    blockExplorerManager: BlockExplorerManager,
    private val soraConfigManager: SoraConfigManager,
    private val substrateCalls: SubstrateCalls,
) : PolkaswapRepository, PolkaswapBasicRepositoryImpl(db, blockExplorerManager) {

    override fun subscribeBasicPools(): Flow<List<BasicPoolData>> {
        return db.poolDao().subscribeBasicPoolsWithToken().map { list ->
            list.map { local ->
                PoolLocalMapper.mapBasicPoolTokenFiatLocal(
                    local,
                    assetLocalToAssetMapper,
                ) { getPoolStrategicBonusAPY(it) }
            }
        }.distinctUntilChanged()
    }

    override suspend fun getBasicPool(b: String, t: String): BasicPoolData? = db.poolDao().getBasicPool(b, t)?.let {
        PoolLocalMapper.mapBasicToPoolData(
            basicPoolLocal = it,
            baseToken = getToken(it.tokenIdBase),
            token = getToken(it.tokenIdTarget),
            apy = getPoolStrategicBonusAPY(it.reservesAccount),
        )
    }

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
                    it.key.first, it.key.second, it.value, account.substrateAddress
                )
            }
        }
    }

    override suspend fun getBasicPools(): List<BasicPoolData> {
        return db.poolDao().getBasicPools().map {
            PoolLocalMapper.mapBasicToPoolData(
                basicPoolLocal = it,
                baseToken = getToken(it.tokenIdBase),
                token = getToken(it.tokenIdTarget),
                apy = getPoolStrategicBonusAPY(it.reservesAccount)
            )
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

    override fun subscribePoolOfAccount(
        address: String, baseTokenId: String, targetTokenId: String
    ): Flow<CommonPoolData?> {
        return getUserPool(baseTokenId, targetTokenId, address).map {
            mapPoolLocalToData(it)
        }
    }

    override suspend fun getPoolsCacheOfAccount(address: String): List<CommonUserPoolData> {
        val poolsLocal = db.poolDao().getPoolsList(address)
        return poolsLocal.map { poolLocal ->
            mapPoolLocalToUserData(poolLocal)
        }
    }

    @OptIn(FlowPreview::class)
    override fun subscribePools(address: String): Flow<List<CommonUserPoolData>> {
        return db.poolDao().subscribePoolsList(address).map { pools ->
            pools.map { poolLocal ->
                mapPoolLocalToUserData(poolLocal)
            }
        }.debounce(500)
    }

    private suspend fun mapPoolLocalToData(
        poolLocal: UserPoolJoinedLocalNullable?,
    ): CommonPoolData? {
        if (poolLocal == null) return null
        val userLocal = poolLocal.userPoolLocal
        val basic = PoolLocalMapper.mapBasicToPoolData(
            poolLocal.basicPoolLocal,
            getToken(poolLocal.basicPoolLocal.tokenIdBase),
            getToken(poolLocal.basicPoolLocal.tokenIdTarget),
            getPoolStrategicBonusAPY(poolLocal.basicPoolLocal.reservesAccount),
        )
        return if (userLocal == null) {
            CommonPoolData(
                basic, null,
            )
        } else {
            val userPoolData = mapPoolLocalToUserData(
                UserPoolJoinedLocal(userLocal, poolLocal.basicPoolLocal),
            )
            CommonPoolData(
                basic,
                userPoolData.user,
            )
        }
    }

    private suspend fun mapPoolLocalToUserData(
        poolLocal: UserPoolJoinedLocal,
    ): CommonUserPoolData {
        val token = getToken(poolLocal.userPoolLocal.userTokenIdTarget)
        val baseToken = getToken(poolLocal.userPoolLocal.userTokenIdBase)
        return PoolLocalMapper.mapLocal(
            poolLocal = poolLocal,
            baseToken = baseToken,
            token = token,
            apy = getPoolStrategicBonusAPY(poolLocal.basicPoolLocal.reservesAccount),
        )
    }

    private suspend fun getToken(tokenId: String): Token {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return assetLocalToAssetMapper.map(
            db.assetDao().getToken(tokenId, selectedCurrency.code),
        )
    }

    override fun subscribeLocalPoolReserves(
        address: String, baseTokenId: String, assetId: String
    ): Flow<LiquidityData?> {
        return getUserPool(baseTokenId, assetId, address).map { pool ->
            val userLocal = pool?.userPoolLocal
            if (userLocal == null) {
                null
            } else {
                val firstPooled = calculatePooledValue(
                    pool.basicPoolLocal.reserveBase,
                    userLocal.poolProvidersBalance,
                    pool.basicPoolLocal.totalIssuance,
                )
                val secondPooled = calculatePooledValue(
                    pool.basicPoolLocal.reserveTarget,
                    userLocal.poolProvidersBalance,
                    pool.basicPoolLocal.totalIssuance,
                )

                LiquidityData(
                    pool.basicPoolLocal.reserveBase,
                    pool.basicPoolLocal.reserveTarget,
                    firstPooled,
                    secondPooled,
                    getPoolStrategicBonusAPY(pool.basicPoolLocal.reservesAccount),
                )
            }
        }
    }
}
