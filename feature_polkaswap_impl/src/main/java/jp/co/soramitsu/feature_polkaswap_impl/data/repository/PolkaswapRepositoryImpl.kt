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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
) : PolkaswapRepository,
    PolkaswapBasicRepositoryImpl(db, blockExplorerManager) {

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

    override fun subscribePoolOfAccount(
        address: String,
        baseTokenId: String,
        targetTokenId: String
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
            val userPoolData =
                mapPoolLocalToUserData(UserPoolJoinedLocal(userLocal, poolLocal.basicPoolLocal))
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
            poolLocal,
            baseToken,
            token,
            getPoolStrategicBonusAPY(poolLocal.basicPoolLocal.reservesAccount),
        )
    }

    private suspend fun getToken(tokenId: String): Token {
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return assetLocalToAssetMapper.map(
            db.assetDao()
                .getToken(tokenId, selectedCurrency.code),
        )
    }

    override fun subscribeLocalPoolReserves(
        address: String,
        baseTokenId: String,
        assetId: String
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
