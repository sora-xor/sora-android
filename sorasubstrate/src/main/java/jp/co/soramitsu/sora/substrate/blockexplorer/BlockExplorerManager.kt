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

package jp.co.soramitsu.sora.substrate.blockexplorer

import androidx.room.withTransaction
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.FlavorOptionsProvider
import jp.co.soramitsu.common.domain.fiatChange
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.model.FiatTokenPriceLocal
import jp.co.soramitsu.core_db.model.ReferralLocal
import jp.co.soramitsu.sora.substrate.models.SoraCoin
import jp.co.soramitsu.xnetworking.common.Utils.toDoubleNan
import jp.co.soramitsu.xnetworking.networkclient.SoramitsuNetworkClient
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.SoraWalletBlockExplorerInfo
import jp.co.soramitsu.xnetworking.sorawallet.blockexplorerinfo.sbapy.SbApyInfo
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

    companion object {
        private val fiatChangeUpdatePeriod = TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS)
    }

    private val tempApy = mutableListOf<SbApyInfo>()

    fun getTempApy(id: String) = tempApy.find {
        it.id == id
    }

    suspend fun updatePoolsSbApy(address: String) {
        updateSbApyInternal(address)
    }

    suspend fun updateFiat() {
        updateFiatInternal()
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
    }

    suspend fun getXorPerEurRatio(): Double? = runCatching {
        val json = networkClient.get(FlavorOptionsProvider.xorEuroUrl)
        val soraCoin = Json.decodeFromString<SoraCoin>(serializer(), json)
        soraCoin.price.toDoubleNan()
    }.getOrNull()

    private suspend fun updateSbApyInternal(
        address: String,
    ) {
        runCatching {
            val response = info.getSpApy()
            tempApy.clear()
            tempApy.addAll(response)
            db.withTransaction {
                response.forEach { info ->
                    db.poolDao()
                        .updateSbApyByReservesAccount(info.sbApy?.toBigDecimal(), info.id, address)
                }
            }
        }
    }

    private suspend fun updateFiatInternal() {
        if (appStateProvider.isForeground) {
            runCatching {
                val response = info.getFiat()
                updateFiatPrices(response.map { FiatInfo(it.id, it.priceUsd) })
            }
        }
    }

    private suspend fun updateFiatPrices(fiatData: List<FiatInfo>) {
        val selected = soraConfigManager.getSelectedCurrency()
        val tokens = db.assetDao().getTokensWithFiatOfCurrency(selected.code)
        val prices = fiatData.mapNotNull { apy ->
            val dbValue = tokens.find { it.token.id == apy.id }
            val apyRate = apy.priceUsd
            if (dbValue != null && apyRate != null) {
                val change = fiatChange(dbValue.price?.fiatPricePrevD, apyRate)
                val now = Date().time
                val replace = dbValue.price?.let { price ->
                    now > price.fiatPricePrevHTime + fiatChangeUpdatePeriod
                } ?: true
                FiatTokenPriceLocal(
                    dbValue.token.id,
                    selected.code,
                    apyRate,
                    now,
                    if (replace) dbValue.price?.fiatPrice
                        ?: apyRate else dbValue.price?.fiatPricePrevH ?: apyRate,
                    if (replace) dbValue.price?.fiatPriceTime
                        ?: now else dbValue.price?.fiatPricePrevHTime ?: now,
                    if (replace) dbValue.price?.fiatPricePrevH
                        ?: apyRate else dbValue.price?.fiatPricePrevD ?: apyRate,
                    if (replace) dbValue.price?.fiatPricePrevHTime
                        ?: now else dbValue.price?.fiatPricePrevDTime ?: now,
                    change,
                )
            } else {
                null
            }
        }
        db.assetDao().insertFiatPrice(prices)
    }

    private class FiatInfo(val id: String, val priceUsd: Double? = null)
}
