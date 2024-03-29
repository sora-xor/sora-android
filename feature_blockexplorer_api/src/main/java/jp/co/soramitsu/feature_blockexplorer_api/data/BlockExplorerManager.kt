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
import jp.co.soramitsu.common.config.BuildConfigWrapper
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.RetryStrategyBuilder
import jp.co.soramitsu.common.domain.fiatChange
import jp.co.soramitsu.common.logger.FirebaseWrapper
import jp.co.soramitsu.common.util.ext.toDoubleInfinite
import jp.co.soramitsu.common.util.mapBalance
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.xnetworking.basic.common.Utils.toDoubleNan
import jp.co.soramitsu.xnetworking.basic.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.basic.networkclient.SoramitsuNetworkException
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.sbapy.SbApyInfo
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

@Singleton
class BlockExplorerManager @Inject constructor(
    private val info: SoraWalletBlockExplorerInfo,
    private val db: AppDatabase,
    private val appStateProvider: AppStateProvider,
    private val networkClient: SoramitsuNetworkClient,
    private val soraConfigManager: SoraConfigManager,
) {

    private val tempApy = mutableListOf<SbApyInfo>()

    private var assetsInfo: List<Pair<String, Double>>? = null

    private val mutexFiat = Mutex()

    fun getTempApy(id: String) = tempApy.find {
        it.id == id
    }?.sbApy?.times(100)

    suspend fun getTokensLiquidity(tokenIds: List<String>): List<Pair<String, Double>> =
        assetsInfo ?: mutexFiat.withLock {
            assetsInfo ?: getAssetsInfoInternal(tokenIds).also {
                assetsInfo = it
            }
        }

    private suspend fun getAssetsInfoInternal(tokenIds: List<String>): List<Pair<String, Double>> =
        runCatching {
            val selected = soraConfigManager.getSelectedCurrency()
            val tokens = db.assetDao().getFiatTokenPriceLocal(selected.code)
            val yesterdayHour = yesterday()
            val resultList = mutableListOf<Pair<String, Double>>()
            val fiats = mutableListOf<FiatTokenPriceLocal>()
            RetryStrategyBuilder.build().retryIf(
                retries = 3,
                predicate = { t -> t is SoramitsuNetworkException },
                block = { info.getAssetsInfo(tokenIds, yesterdayHour) },
            ).forEach { assetInfo ->
                val dbValue = tokens.find { it.tokenIdFiat == assetInfo.tokenId }
                val delta = assetInfo.hourDelta
                if (dbValue != null && delta != null) {
                    fiats.add(
                        dbValue.copy(
                            fiatChange = delta / 100.0,
                            fiatPricePrevHTime = yesterdayHour,
                        )
                    )
                }
                dbValue?.tokenIdFiat?.let { tokenId ->
                    db.assetDao().getPrecisionOfToken(tokenId)?.let { precision ->
                        assetInfo.liquidity.toBigIntegerOrNull()?.let { mapBalance(it, precision) }?.let { supply ->
                            val tvl = supply.times(BigDecimal(dbValue.fiatPrice))
                            resultList.add(assetInfo.tokenId to tvl.toDoubleInfinite())
                        }
                    }
                }
            }
            db.assetDao().insertFiatPrice(fiats)
            resultList
        }.getOrElse {
            FirebaseWrapper.recordException(it)
            emptyList()
        }

    suspend fun updatePoolsSbApy() {
        updateSbApyInternal()
    }

    suspend fun updateFiat() {
        if (appStateProvider.isForeground) {
            runCatching {
                val response = info.getFiat()
                updateFiatPrices(response.map { FiatInfo(it.id, it.priceUsd) })
            }
        }
    }

    suspend fun updateReferrerRewards(
        address: String,
    ) {
        runCatching {
            val response = info.getReferrerRewards(address)
            response.rewards.map {
                ReferralLocal(it.referral, it.amount)
            }
        }
            .onSuccess {
                db.withTransaction {
                    db.referralsDao().clearTable()
                    db.referralsDao().insertReferrals(it)
                }
            }
            .onFailure {
                FirebaseWrapper.recordException(it)
            }
    }

    suspend fun getXorPerEurRatio(): Double? = runCatching {
        val json = networkClient.get(BuildConfigWrapper.soraCardEuroRateUrl)
        val soraCoin = Json.decodeFromString<SoraCoin>(serializer(), json)
        soraCoin.price.toDoubleNan()
    }.getOrNull()

    private suspend fun updateSbApyInternal() {
        runCatching {
            val response = info.getSpApy()
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
