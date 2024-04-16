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
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.domain.compareByTotal
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.ext.splitVersions
import jp.co.soramitsu.demeter.domain.DemeterFarmingInteractor
import jp.co.soramitsu.feature_assets_api.domain.AssetsInteractor
import jp.co.soramitsu.feature_blockexplorer_api.data.BlockExplorerManager
import jp.co.soramitsu.feature_polkaswap_api.domain.interfaces.PoolsInteractor
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardAvailabilityInfo
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardBasicStatus
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
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

    private val _soraCardBasicStatus = MutableStateFlow(
        SoraCardBasicStatus(
            initialized = false,
            initError = null,
            availabilityInfo = null,
            verification = SoraCardCommonVerification.NotFound,
            needInstallUpdate = false,
            applicationFee = null,
            ibanInfo = null,
            phone = null,
        )
    )

    @Suppress("UNCHECKED_CAST")
    override suspend fun initialize() {
        combine(
            flow { emit(soraCardClientProxy.init()) },
            needInstallUpdate(),
            fetchApplicationFee(),
            fetchUserIbanAccount(),
            subscribeToSoraCardAvailabilityFlow(),
            checkSoraCardPending(),
            fetchUserPhone(),
        ) { flows ->
            val init = flows[0] as Pair<Boolean, String>
            val needUpdate = flows[1] as Boolean
            val fee = flows[2] as String
            val ibanInfo = flows[3] as IbanInfo?
            val availability = flows[4] as SoraCardAvailabilityInfo
            val verification = flows[5] as SoraCardCommonVerification
            val phone = flows[6] as String
            SoraCardBasicStatus(
                initialized = init.first,
                initError = init.second,
                availabilityInfo = availability,
                verification = verification,
                needInstallUpdate = needUpdate,
                applicationFee = fee,
                ibanInfo = ibanInfo,
                phone = phone,
            )
        }
            .debounce(1000)
            .collect {
                _soraCardBasicStatus.value = it
            }
    }

    override val basicStatus: StateFlow<SoraCardBasicStatus> = _soraCardBasicStatus.asStateFlow()

    private suspend fun checkSoraCardPending() = flow {
        var isLoopInProgress = true
        while (isLoopInProgress) {
            val status =
                soraCardClientProxy.getKycStatus().getOrDefault(SoraCardCommonVerification.NotFound)
            emit(status)
            if (status != SoraCardCommonVerification.Pending) {
                isLoopInProgress = false
            } else {
                delay(POLLING_PERIOD_IN_MILLIS)
            }
        }
    }

    private fun needInstallUpdate() = flow {
        emit(needInstallUpdateInternal())
    }

    private suspend fun needInstallUpdateInternal(): Boolean {
        val remote = soraCardClientProxy.getVersion().getOrNull() ?: return false
        val currentArray = OptionsProvider.soracard.splitVersions()
        val remoteArray = remote.splitVersions()
        if (currentArray.isEmpty() || remoteArray.isEmpty()) return false
        for (i in 0..min(currentArray.lastIndex, remoteArray.lastIndex)) {
            if (remoteArray[i] > currentArray[i]) return true
        }
        return false
    }

    override suspend fun setStatus(status: SoraCardCommonVerification) {
        _soraCardBasicStatus.value = _soraCardBasicStatus.value.copy(
            verification = status,
            ibanInfo = fetchIbanItem(),
        )
    }

    override suspend fun setLogout() {
        soraCardClientProxy.logout()
        _soraCardBasicStatus.value = _soraCardBasicStatus.value.copy(
            verification = SoraCardCommonVerification.NotFound,
            ibanInfo = null,
        )
    }

    private fun subscribeToSoraCardAvailabilityFlow() =
        assetsInteractor.subscribeAssetOfCurAccount(SubstrateOptionsProvider.feeAssetId)
            .distinctUntilChanged(::compareByTotal)
            .map { asset ->
                if (asset != null) {
                    val pools = poolsInteractor.getPoolsCacheOfCurAccount()
                        .filter { poolData ->
                            poolData.basic.baseToken.id == SubstrateOptionsProvider.feeAssetId
                        }
                    val poolsSum = pools.sumOf { poolData ->
                        poolData.user.basePooled
                    }
                    val demeterStakedFarmed =
                        demeterFarmingInteractor.getStakedFarmedBalanceOfAsset(
                            SubstrateOptionsProvider.feeAssetId
                        )
                    val totalTokenBalance =
                        asset.balance.total.plus(poolsSum).plus(demeterStakedFarmed)
                    SoraCardAvailabilityInfo(
                        xorBalance = totalTokenBalance,
                        xorRatioAvailable = true,
                    )
                } else {
                    errorInfoState()
                }
            }.flowOn(coroutineManager.io)

    private fun errorInfoState(balance: BigDecimal = BigDecimal.ZERO) = SoraCardAvailabilityInfo(
        xorBalance = balance,
        xorRatioAvailable = false,
    )

    private fun fetchUserIbanAccount() = flow { emit(fetchIbanItem()) }

    private fun fetchUserPhone() = flow { emit(soraCardClientProxy.getPhone()) }

    private suspend fun fetchIbanItem(): IbanInfo? =
        soraCardClientProxy.getIBAN().getOrNull()

    private fun fetchApplicationFee() = flow { emit(soraCardClientProxy.getApplicationFee()) }

    private companion object {
        const val POLLING_PERIOD_IN_MILLIS = 90_000L
    }
}
