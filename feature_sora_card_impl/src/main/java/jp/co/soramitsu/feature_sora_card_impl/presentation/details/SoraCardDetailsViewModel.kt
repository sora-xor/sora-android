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

package jp.co.soramitsu.feature_sora_card_impl.presentation.details

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.androidfoundation.intent.isAppAvailableCompat
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.feature_sora_card_api.domain.SoraCardInteractor
import jp.co.soramitsu.feature_sora_card_api.util.createSoraCardGateHubContract
import jp.co.soramitsu.feature_sora_card_api.util.readyToStartGatehubOnboarding
import jp.co.soramitsu.oauth.base.sdk.contract.IbanInfo
import jp.co.soramitsu.oauth.base.sdk.contract.IbanStatus
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SoraCardDetailsViewModel @Inject constructor(
    private val soraCardInteractor: SoraCardInteractor,
    private val clipboardManager: BasicClipboardManager,
) : BaseViewModel() {

    private val _shareLinkEvent = SingleLiveEvent<String>()
    val shareLinkEvent: LiveData<String> = _shareLinkEvent

    val telegramChat = SingleLiveEvent<Unit>()
    val fiatWallet = SingleLiveEvent<String>()
    val fiatWalletMarket = SingleLiveEvent<String>()

    private var ibanCache: IbanInfo? = null

    private val _launchSoraCard = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCard: LiveData<SoraCardContractData> = _launchSoraCard

    private val _soraCardDetailsScreenState = MutableStateFlow(
        SoraCardDetailsScreenState(
            soraCardMainSoraContentCardState = SoraCardMainSoraContentCardState(
                balance = null,
                phone = null,
                soraCardMenuActions = SoraCardMenuAction.entries,
                canStartGatehubFlow = false,
            ),
            soraCardSettingsCard = SoraCardSettingsCardState(
                soraCardSettingsOptions = SoraCardSettingsOption.entries,
                phone = "",
            ),
            soraCardIBANCardState = null,
            logoutDialog = false,
            fiatWalletDialog = false,
        )
    )
    val soraCardDetailsScreenState = _soraCardDetailsScreenState.asStateFlow()

    init {
        _toolbarState.value = SoramitsuToolbarState(
            type = SoramitsuToolbarType.SmallCentered(),
            basic = BasicToolbarState(
                title = R.string.sora_card_details_title,
                navIcon = jp.co.soramitsu.ui_core.R.drawable.ic_cross,
            )
        )

        viewModelScope.launch {
            tryCatch {
                soraCardInteractor.basicStatus.value.let { basicStatus ->
                    val local = _soraCardDetailsScreenState.value
                    ibanCache = basicStatus.ibanInfo
                    val phoneFormatted = basicStatus.phone?.let { "+$it" }
                    _soraCardDetailsScreenState.value = local.copy(
                        soraCardIBANCardState = SoraCardIBANCardState(
                            iban = basicStatus.ibanInfo?.iban.orEmpty(),
                            closed = basicStatus.ibanInfo?.ibanStatus == IbanStatus.CLOSED,
                        ),
                        soraCardMainSoraContentCardState = local.soraCardMainSoraContentCardState.copy(
                            balance = basicStatus.ibanInfo?.balance,
                            phone = phoneFormatted,
                            canStartGatehubFlow = basicStatus.ibanInfo?.ibanStatus.readyToStartGatehubOnboarding(),
                        ),
                        soraCardSettingsCard = local.soraCardSettingsCard?.copy(
                            phone = phoneFormatted.orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    fun onShowSoraCardDetailsClick() {
        /* Functionality will be added in further releases */
    }

    fun onSoraCardMenuActionClick(position: Int) {
        /* Functionality will be added in further releases */
    }

    fun onReferralBannerClick() {
        /* Functionality will be added in further releases */
    }

    fun onCloseReferralBannerClick() {
        /* Functionality will be added in further releases */
    }

    fun onRecentActivityClick(position: Int) {
        /* Functionality will be added in further releases */
    }

    fun onShowMoreRecentActivitiesClick() {
        /* Functionality will be added in further releases */
    }

    fun onIbanCardShareClick() {
        ibanCache?.let {
            if (it.ibanStatus != IbanStatus.CLOSED && it.iban.isNotEmpty()) _shareLinkEvent.value = it.iban
        }
    }

    fun onIbanCardClick() {
        ibanCache?.let {
            if (it.ibanStatus != IbanStatus.CLOSED && it.iban.isNotEmpty()) {
                clipboardManager.addToClipboard(it.iban)
                copiedToast.trigger()
            }
        }
    }

    /**
     * only clickable if IBAN is issued
     */
    fun onExchangeXorClick() {
        _launchSoraCard.value = createSoraCardGateHubContract()
    }

    fun onSettingsOptionClick(position: Int, context: Context?) {
        val settings = soraCardDetailsScreenState.value.soraCardSettingsCard
            ?.soraCardSettingsOptions ?: return

        when (settings[position]) {
            SoraCardSettingsOption.LOG_OUT ->
                _soraCardDetailsScreenState.value =
                    _soraCardDetailsScreenState.value.copy(logoutDialog = true)

            SoraCardSettingsOption.SUPPORT_CHAT ->
                telegramChat.trigger()

            SoraCardSettingsOption.MANAGE_SORA_CARD -> {
                checkNotNull(context)
                val fiat = BuildUtils.fiatPackageName()
                if (context.isAppAvailableCompat(fiat)) {
                    fiatWallet.value = fiat
                } else {
                    _soraCardDetailsScreenState.value =
                        _soraCardDetailsScreenState.value.copy(fiatWalletDialog = true)
                }
            }
        }
    }

    fun onLogoutDismiss() {
        _soraCardDetailsScreenState.value =
            _soraCardDetailsScreenState.value.copy(logoutDialog = false)
    }

    fun onFiatWalletDismiss() {
        _soraCardDetailsScreenState.value =
            _soraCardDetailsScreenState.value.copy(fiatWalletDialog = false)
    }

    fun onOpenFiatWalletMarket() {
        fiatWalletMarket.value = BuildUtils.fiatPackageName()
    }

    fun onSoraCardLogOutClick() {
        viewModelScope.launch {
            tryCatch {
                soraCardInteractor.setLogout()
            }
        }.invokeOnCompletion {
            if (it == null)
                onNavIcon()
        }
    }
}
