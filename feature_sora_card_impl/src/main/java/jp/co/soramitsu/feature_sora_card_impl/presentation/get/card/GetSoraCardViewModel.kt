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

package jp.co.soramitsu.feature_sora_card_impl.presentation.get.card

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_assets_api.presentation.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_polkaswap_api.launcher.PolkaswapRouter
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_api.util.createSoraCardContract
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.oauth.base.sdk.contract.OutwardsScreen
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardResult
import jp.co.soramitsu.sora.substrate.runtime.SubstrateOptionsProvider
import jp.co.soramitsu.sora.substrate.substrate.ConnectionManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class GetSoraCardViewModel @AssistedInject constructor(
    private val assetsRouter: AssetsRouter,
    private val walletRouter: WalletRouter,
    private val mainRouter: MainRouter,
    private val polkaswapRouter: PolkaswapRouter,
    private val resourceManager: ResourceManager,
    connectionManager: ConnectionManager,
    private val soraCardInteractor: SoraCardInteractor,
    @Assisted("SHOULD_START_SIGN_IN") val shouldStartSignIn: Boolean,
    @Assisted("SHOULD_START_SIGN_UP") val shouldStartSignUp: Boolean
) : BaseViewModel() {

    @AssistedFactory
    interface AssistedGetSoraCardViewModelFactory {
        fun create(
            @Assisted("SHOULD_START_SIGN_IN") shouldStartSignIn: Boolean,
            @Assisted("SHOULD_START_SIGN_UP") shouldStartSignUp: Boolean,
        ): GetSoraCardViewModel
    }

    private var currentSoraCardContractData: SoraCardContractData? = null

    private val _launchSoraCardRegistration = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardRegistration: LiveData<SoraCardContractData> = _launchSoraCardRegistration

    var state = mutableStateOf(GetSoraCardState())
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.get_sora_card_title,
        )

        soraCardInteractor.subscribeToSoraCardAvailabilityFlow()
            .onEach {
                currentSoraCardContractData = createSoraCardContract(
                    userAvailableXorAmount = it.xorBalance.toDouble(),
                    isEnoughXorAvailable = it.enoughXor,
                )
                state.value = state.value.copy(
                    xorRatioAvailable = it.xorRatioAvailable,
                )
            }.launchIn(viewModelScope)

        connectionManager.connectionState
            .catch { onError(it) }
            .onEach {
                state.value = state.value.copy(connection = it)
            }
            .launchIn(viewModelScope)
    }

    fun handleSoraCardResult(soraCardResult: SoraCardResult) {
        when (soraCardResult) {
            is SoraCardResult.NavigateTo -> {
                when (soraCardResult.screen) {
                    OutwardsScreen.DEPOSIT -> walletRouter.openQrCodeFlow(isLaunchedFromSoraCard = true)
                    OutwardsScreen.SWAP -> polkaswapRouter.showSwap(tokenToId = SubstrateOptionsProvider.feeAssetId)
                    OutwardsScreen.BUY -> assetsRouter.showBuyCrypto()
                }
            }
            is SoraCardResult.Success -> {
                soraCardInteractor.setStatus(soraCardResult.status)
            }
            is SoraCardResult.Failure -> {
                soraCardInteractor.setStatus(soraCardResult.status)
            }
            is SoraCardResult.Canceled -> {}
            is SoraCardResult.Logout -> {
                soraCardInteractor.setLogout()
            }
        }
        mainRouter.popBackStack()
    }

    fun onEnableCard() {
        currentSoraCardContractData?.let {
            _launchSoraCardRegistration.value = it
        }
    }

    fun onBuyCrypto() {
        assetsRouter.showBuyCrypto()
    }

    fun onSwap() {
        polkaswapRouter.showSwap(tokenToId = SubstrateOptionsProvider.feeAssetId)
    }

    fun onSeeBlacklist() {
        mainRouter.showWebView(
            title = resourceManager.getString(R.string.sora_card_blacklisted_countires_title),
            url = OptionsProvider.soraCardBlackList,
        )
    }
}
