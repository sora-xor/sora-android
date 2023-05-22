/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
