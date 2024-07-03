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

package jp.co.soramitsu.feature_blockexplorer_api.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.androidfoundation.format.toDoubleInfinite
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.RetryStrategyBuilder
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.BlockExplorerRepository
import jp.co.soramitsu.xnetworking.lib.datasources.blockexplorer.api.models.Apy
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.RestClient
import jp.co.soramitsu.xnetworking.lib.engines.rest.api.models.RestClientException
import jp.co.soramitsu.xnetworking.lib.engines.utils.JsonGetRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun String.toDoubleNan(): Double? = this.toDoubleOrNull()?.let {
    if (it.isNaN()) null else it
}

@Singleton
class BlockExplorerManager @Inject constructor(
    private val restClient: RestClient,
    private val info: BlockExplorerRepository,
    private val db: AppDatabase,
    private val appStateProvider: AppStateProvider,
    private val soraConfigManager: SoraConfigManager,
) {

    private val tempApy = mutableListOf<Apy>()

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

            RetryStrategyBuilder.build().retryIf(
                retries = 3,
                predicate = { t -> t is RestClientException },
                block = { info.getAssetsInfo(soraConfigManager.getGenesis(), tokenIds, yesterdayHour.toInt()) },
            ).forEach { assetInfo ->
                val dbValue = tokens.find { it.tokenIdFiat == assetInfo.id }
                val delta = assetInfo.previousPrice

                if (dbValue != null && delta != null) {
                    fiats += dbValue.copy(
                        fiatChange = delta / 100.0,
                        fiatPricePrevHTime = yesterdayHour,
                    )
                }

                val precision = db.assetDao().getPrecisionOfToken(
                    tokenId = dbValue?.tokenIdFiat ?: return@forEach
                ) ?: return@forEach

                val supply = assetInfo.liquidity.toBigIntegerOrNull()?.let {
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
                    fiatData = info.getFiat(soraConfigManager.getGenesis()).map {
                        FiatInfo(it.id, it.priceUSD.toDoubleNan())
                    }
                )
            }
        }
    }

    suspend fun updateReferrerRewards(address: String) {
        runCatching {
            val rewards = info.getReferralReward(soraConfigManager.getGenesis(), address).map {
                ReferralLocal(it.referral, it.amount)
            }

            db.withTransaction {
                db.referralsDao().clearTable()
                db.referralsDao().insertReferrals(rewards)
            }
        }.onFailure {
            FirebaseWrapper.recordException(it)
        }
    }

    suspend fun getXorPerEurRatio(): Double? = runCatching {
        restClient.get(
            request = JsonGetRequest(
                url = BuildConfigWrapper.soraCardEuroRateUrl,
                responseDeserializer = SoraCoin.serializer()
            )
        ).price.toDoubleNan()
    }.getOrNull()

    private suspend fun updateSbApyInternal() {
        runCatching {
            val response = info.getApy(soraConfigManager.getGenesis())
            tempApy.clear()
            tempApy.addAll(response)
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
