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

package jp.co.soramitsu.feature_sora_card_impl.domain

import java.math.BigDecimal
import javax.inject.Inject
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.OptionsProvider.euroSign
import jp.co.soramitsu.common.domain.compareByTotal
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.Big100
import jp.co.soramitsu.common.util.ext.divideBy
import jp.co.soramitsu.common.util.ext.greaterThan
import jp.co.soramitsu.common.util.ext.safeDivide
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_api.domain.models.SoraCardAvailabilityInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

internal class SoraCardInteractorImpl @Inject constructor(
    private val blockExplorerManager: BlockExplorerManager,
    private val formatter: NumbersFormatter,
    private val assetsInteractor: AssetsInteractor,
    private val poolsInteractor: PoolsInteractor,
    private val soraCardClientProxy: SoraCardClientProxy,
    private val demeterFarmingInteractor: DemeterFarmingInteractor,
    private val coroutineManager: CoroutineManager,
) : SoraCardInteractor {

    private var xorToEuro: Double? = null

    private val _soraCardStatus = MutableStateFlow(SoraCardCommonVerification.NotFound)

    override fun subscribeSoraCardStatus(): Flow<SoraCardCommonVerification> =
        _soraCardStatus.asStateFlow()

    override suspend fun checkSoraCardPending() {
        var isLoopInProgress = true
        while (isLoopInProgress) {
            val status = soraCardClientProxy.getKycStatus().getOrDefault(SoraCardCommonVerification.NotFound)
            _soraCardStatus.value = status
            if (status != SoraCardCommonVerification.Pending) {
                isLoopInProgress = false
            } else {
                delay(POLLING_PERIOD_IN_MILLIS)
            }
        }
    }

    override fun setStatus(status: SoraCardCommonVerification) {
        _soraCardStatus.value = status
    }

    override fun setLogout() {
        _soraCardStatus.value = SoraCardCommonVerification.NotFound
    }

    override fun subscribeToSoraCardAvailabilityFlow() =
        assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
            .distinctUntilChanged(::compareByTotal)
            .map { asset ->
                val xorEuroLocal = getXorEuro()
                if (asset != null && xorEuroLocal != null) {
                    val pools = poolsInteractor.getPoolsCacheOfCurAccount()
                        .filter { poolData ->
                            poolData.basic.baseToken.id == SubstrateOptionsProvider.feeAssetId
                        }
                    val poolsSum = pools.sumOf { poolData ->
                        poolData.user.basePooled
                    }
                    val demeterStakedFarmed = demeterFarmingInteractor.getStakedFarmedBalanceOfAsset(SubstrateOptionsProvider.feeAssetId)
                    val totalPoolBalance = asset.balance.total.plus(poolsSum).plus(demeterStakedFarmed)
                    try {
                        val xorRequiredBalanceWithBacklash =
                            KYC_REQUIRED_BALANCE_WITH_BACKLASH.divideBy(
                                BigDecimal.valueOf(
                                    xorEuroLocal
                                )
                            )
                        val xorRealRequiredBalance =
                            KYC_REAL_REQUIRED_BALANCE.divideBy(BigDecimal.valueOf(xorEuroLocal))
                        val xorBalanceInEur =
                            totalPoolBalance.multiply(BigDecimal.valueOf(xorEuroLocal))

                        val needInXor =
                            if (totalPoolBalance.greaterThan(xorRealRequiredBalance)) {
                                BigDecimal.ZERO
                            } else {
                                xorRequiredBalanceWithBacklash.minus(totalPoolBalance)
                            }

                        val needInEur =
                            if (xorBalanceInEur.greaterThan(KYC_REAL_REQUIRED_BALANCE)) {
                                BigDecimal.ZERO
                            } else {
                                KYC_REQUIRED_BALANCE_WITH_BACKLASH.minus(xorBalanceInEur)
                            }

                        SoraCardAvailabilityInfo(
                            xorBalance = totalPoolBalance,
                            enoughXor = totalPoolBalance.greaterThan(
                                xorRealRequiredBalance
                            ),
                            percent = totalPoolBalance.safeDivide(xorRealRequiredBalance),
                            needInXor = formatter.formatBigDecimal(needInXor, 5),
                            needInEur = formatter.formatBigDecimal(needInEur, 2),
                            xorRatioAvailable = true
                        )
                    } catch (t: Throwable) {
                        errorInfoState(totalPoolBalance)
                    }
                } else {
                    errorInfoState(BigDecimal.ZERO)
                }
            }.flowOn(coroutineManager.io)

    private fun errorInfoState(balance: BigDecimal) = SoraCardAvailabilityInfo(
        xorBalance = balance,
        enoughXor = false,
        xorRatioAvailable = false,
    )

    private suspend fun getXorEuro(): Double? =
        xorToEuro ?: blockExplorerManager.getXorPerEurRatio().also {
            xorToEuro = it
        }

    override suspend fun fetchUserIbanAccount(): Result<String> =
        soraCardClientProxy.getIBAN().mapCatching { wrapper ->
            val sorted = wrapper.ibans.sortedByDescending { it.createdDate }
            sorted.first().iban
        }

    override suspend fun fetchIbanBalance(): Result<String> =
        soraCardClientProxy.getIBAN().mapCatching { wrapper ->
            val sorted = wrapper.ibans.sortedByDescending { it.createdDate }
            val euroValue = (sorted.first().availableBalance / 100.0)
            "%s%.2f".format(euroSign, euroValue)
        }

    override suspend fun logOutFromSoraCard() {
        soraCardClientProxy.logout()
        setLogout()
    }

    private companion object {
        val KYC_REAL_REQUIRED_BALANCE: BigDecimal = BigDecimal.valueOf(95)
        val KYC_REQUIRED_BALANCE_WITH_BACKLASH: BigDecimal = Big100
        const val POLLING_PERIOD_IN_MILLIS = 30_000L
    }
}
