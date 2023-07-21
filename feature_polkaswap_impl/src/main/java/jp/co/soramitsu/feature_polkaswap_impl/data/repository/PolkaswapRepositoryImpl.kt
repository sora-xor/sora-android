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
import jp.co.soramitsu.common.util.StringPair
import jp.co.soramitsu.common_wallet.data.AssetLocalToAssetMapper
import jp.co.soramitsu.common_wallet.domain.model.LiquidityData
import jp.co.soramitsu.common_wallet.domain.model.UserPoolData
import jp.co.soramitsu.common_wallet.presentation.compose.util.PolkaswapFormulas.calculatePooledValue
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.UserPoolJoinedLocal
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PolkaswapRepository
import jp.co.soramitsu.feature_polkaswap_impl.data.mappers.PoolLocalMapper
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletDatasource
import jp.co.soramitsu.sora.substrate.blockexplorer.BlockExplorerManager
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.sora.substrate.substrate.SubstrateApi
import jp.co.soramitsu.xnetworking.sorawallet.mainconfig.SoraCurrency
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map

@ExperimentalCoroutinesApi
class PolkaswapRepositoryImpl @Inject constructor(
    private val datasource: WalletDatasource,
    private val db: AppDatabase,
    private val assetLocalToAssetMapper: AssetLocalToAssetMapper,
    private val blockExplorerManager: BlockExplorerManager,
    private val soraConfigManager: SoraConfigManager,
    runtimeManager: RuntimeManager,
    wsConnection: SubstrateApi,
) : PolkaswapRepository,
    PolkaswapBlockchainRepositoryImpl(blockExplorerManager, runtimeManager, wsConnection, db) {

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

    override fun getPoolData(
        address: String,
        baseTokenId: String,
        tokenId: String
    ): Flow<UserPoolData?> {
        return db.poolDao().getPool(tokenId, baseTokenId, address).map {
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
            it?.let { poolLocal ->
                mapPoolLocalToData(selectedCurrency, poolLocal)
            }
        }
    }

    override suspend fun getPoolsCache(address: String): List<UserPoolData> {
        val poolsLocal = db.poolDao().getPoolsList(address)
        val selectedCurrency = soraConfigManager.getSelectedCurrency()
        return poolsLocal.map { poolLocal ->
            mapPoolLocalToData(selectedCurrency, poolLocal)
        }
    }

    override fun subscribePoolFlow(address: String): Flow<List<UserPoolData>> {
        return db.poolDao().getPools(address).map { pools ->
            val selectedCurrency = soraConfigManager.getSelectedCurrency()
            pools.map { poolLocal ->
                mapPoolLocalToData(selectedCurrency, poolLocal)
            }
        }.debounce(500)
    }

    private suspend fun mapPoolLocalToData(
        fiatCurrencyLocal: SoraCurrency,
        poolLocal: UserPoolJoinedLocal,
    ): UserPoolData {
        val token = assetLocalToAssetMapper.map(
            db.assetDao()
                .getToken(poolLocal.userPoolLocal.userTokenIdTarget, fiatCurrencyLocal.code),
        )
        val baseToken = assetLocalToAssetMapper.map(
            db.assetDao().getToken(poolLocal.userPoolLocal.userTokenIdBase, fiatCurrencyLocal.code),
        )
        return PoolLocalMapper.mapLocal(
            poolLocal,
            baseToken,
            token,
            getPoolStrategicBonusAPY(token.id, baseToken.id),
        )
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
                    pool.basicPoolLocal.reserveBase,
                    pool.userPoolLocal.poolProvidersBalance,
                    pool.basicPoolLocal.totalIssuance,
                )
                val secondPooled = calculatePooledValue(
                    pool.basicPoolLocal.reserveTarget,
                    pool.userPoolLocal.poolProvidersBalance,
                    pool.basicPoolLocal.totalIssuance,
                )

                LiquidityData(
                    pool.basicPoolLocal.reserveBase,
                    pool.basicPoolLocal.reserveTarget,
                    firstPooled,
                    secondPooled,
                    blockExplorerManager.getTempApy(pool.basicPoolLocal.reservesAccount)?.sbApy?.times(
                        100
                    ),
                )
            }
        }
    }
}
