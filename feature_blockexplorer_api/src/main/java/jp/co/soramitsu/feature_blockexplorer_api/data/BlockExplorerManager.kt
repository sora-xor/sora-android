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

package jp.co.soramitsu.feature_blockexplorer_api.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.toDoubleInfinite
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun String.toDoubleNan(): Double? = this.toDoubleOrNull()?.let {
    if (it.isNaN()) null else it
}

@Singleton
class BlockExplorerManager @Inject constructor(
    private val polkaswapIndexerClient: PolkaswapIndexerClient,
    private val db: AppDatabase,
    private val appStateProvider: AppStateProvider,
    private val soraConfigManager: SoraConfigManager,
) {

    private val tempApy = mutableListOf<IndexerPoolApy>()

    private var assetsInfo: List<Pair<String, Double>>? = null

    private val mutexFiat = Mutex()

    fun getTempApy(id: String) = tempApy.find { it.id == id }?.value?.toDoubleNan()
        ?.times(100)

    suspend fun getTokensLiquidity(tokenIds: List<String>): List<Pair<String, Double>> {
        if (assetsInfo == null) {
            mutexFiat.withLock {
                if (assetsInfo == null) {
                    assetsInfo = getAssetsInfoInternal(tokenIds)
                }
            }
        }

        return checkNotNull(assetsInfo)
    }

    private suspend fun getAssetsInfoInternal(tokenIds: List<String>): List<Pair<String, Double>> {
        return runCatching {
            val selected = soraConfigManager.getSelectedCurrency()
            val tokens = db.assetDao().getFiatTokenPriceLocal(selected.code)
            val yesterdayHour = yesterday()
            val resultList = mutableListOf<Pair<String, Double>>()
            val fiats = mutableListOf<FiatTokenPriceLocal>()

            polkaswapIndexerClient.getAssetsInfo(tokenIds).forEach { assetInfo ->
                val dbValue = tokens.find { it.tokenIdFiat == assetInfo.id }
                val priceChangeDay = assetInfo.priceChangeDay

                if (dbValue != null) {
                    fiats += dbValue.copy(
                        fiatChange = priceChangeDay?.div(100.0),
                        fiatPricePrevHTime = yesterdayHour,
                    )
                }

                val precision = db.assetDao().getPrecisionOfToken(
                    tokenId = dbValue?.tokenIdFiat ?: return@forEach
                ) ?: return@forEach

                val supply = assetInfo.liquidity?.toBigIntegerOrNull()?.let {
                    mapBalance(it, precision)
                } ?: return@forEach

                resultList += Pair(
                    first = assetInfo.id,
                    second = supply.times(BigDecimal(dbValue.fiatPrice))
                        .toDoubleInfinite()
                )
            }

            db.assetDao().insertFiatPrice(fiats)
            resultList
        }.getOrElse {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordException(it)
            emptyList()
        }
    }

    suspend fun updatePoolsSbApy() {
        updateSbApyInternal()
    }

    suspend fun updateFiat() {
        if (appStateProvider.isForeground) {
            runCatching {
                updateFiatPrices(
                    fiatData = polkaswapIndexerClient.getFiat().map {
                        FiatInfo(it.id, it.priceUSD?.toDoubleNan())
                    }
                )
            }.onFailure {
                if (it is CancellationException) throw it
            }
        }
    }

    suspend fun updateReferrerRewards(address: String) {
        runCatching {
            val rewards = polkaswapIndexerClient.getReferralRewards(address).map {
                ReferralLocal(it.referral, it.amount)
            }

            db.withTransaction {
                db.referralsDao().clearTable()
                db.referralsDao().insertReferrals(rewards)
            }
        }.onFailure {
            if (it is CancellationException) throw it
            FirebaseWrapper.recordException(it)
        }
    }

    private suspend fun updateSbApyInternal() {
        runCatching {
            val response = polkaswapIndexerClient.getPoolApys()
            tempApy.clear()
            tempApy.addAll(response)
        }.onFailure {
            if (it is CancellationException) throw it
        }
    }

    private suspend fun updateFiatPrices(fiatData: List<FiatInfo>) = mutexFiat.withLock {
        val selected = soraConfigManager.getSelectedCurrency()
        val tokens = db.assetDao().getTokensWithFiatOfCurrency(selected.code)

        val prices = fiatData.mapNotNull { apy ->
            val dbValue = tokens.find { it.token.id == apy.id }
            val fiatPrice = apy.priceUsd
            if (dbValue != null && fiatPrice != null) {
                val dbPrice = dbValue.price
                if (dbPrice != null) {
                    FiatTokenPriceLocal(
                        dbPrice.tokenIdFiat,
                        selected.code,
                        fiatPrice,
                        nowInSecond(),
                        dbPrice.fiatPricePrevH,
                        dbPrice.fiatPricePrevHTime,
                        dbPrice.fiatPricePrevD,
                        dbPrice.fiatPricePrevDTime,
                        dbPrice.fiatChange,
                    )
                } else {
                    FiatTokenPriceLocal(
                        tokenIdFiat = dbValue.token.id,
                        selected.code,
                        fiatPrice,
                        nowInSecond(),
                        0.0,
                        0,
                        0.0,
                        0,
                        null,
                    )
                }
            } else {
                null
            }
        }
        db.assetDao().insertFiatPrice(prices)
    }

    private fun nowInSecond() =
        TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS)

    private fun yesterday() =
        TimeUnit.SECONDS.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS) - 24 * 60 * 60

    private class FiatInfo(val id: String, val priceUsd: Double? = null)
}
