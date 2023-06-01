/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.domain.SoraCardInformation
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.BuildUtils
import jp.co.soramitsu.common.util.createSoraCardContract
import jp.co.soramitsu.feature_assets_api.presentation.launcher.AssetsRouter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.oauth.base.sdk.SoraCardInfo
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardCommonVerification
import jp.co.soramitsu.oauth.base.sdk.contract.SoraCardContractData
import jp.co.soramitsu.sora.substrate.blockexplorer.SoraConfigManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val assetsRouter: AssetsRouter,
    interactor: MainInteractor,
    private val walletInteractor: WalletInteractor,
    private val router: MainRouter,
    private val walletRouter: WalletRouter,
    private val referralRouter: ReferralRouter,
    private val selectNodeRouter: SelectNodeRouter,
    private val soraConfigManager: SoraConfigManager,
    nodeManager: NodeManager,
) : BaseViewModel() {

    internal var state by mutableStateOf(
        ProfileScreenState(
            nodeName = "",
            nodeConnected = false,
            isDebugMenuAvailable = BuildUtils.isPlayMarket().not(),
            soraCardEnabled = false,
            soraCardStatusStringRes = R.string.more_menu_sora_card_subtitle,
            soraCardStatusIconDrawableRes = null
        )
    )
        private set

    private val _launchSoraCardSignIn = SingleLiveEvent<SoraCardContractData>()
    val launchSoraCardSignIn: LiveData<SoraCardContractData> = _launchSoraCardSignIn

    private var soraCardInfo: SoraCardInformation? = null
        set(value) {
            val soraCardStatusStringRes =
                when (value?.kycStatus) {
                    SoraCardCommonVerification.Rejected.toString() -> R.string.sora_card_verification_rejected
                    SoraCardCommonVerification.Pending.toString() -> R.string.sora_card_verification_in_progress
                    SoraCardCommonVerification.Successful.toString() -> R.string.more_menu_sora_card_subtitle
                    else -> R.string.sora_card_sign_in_required
                }

            val soraCardStatusIconDrawableRes =
                when (value?.kycStatus) {
                    SoraCardCommonVerification.Rejected.toString() -> R.drawable.ic_status_denied
                    SoraCardCommonVerification.Pending.toString() -> R.drawable.ic_status_pending
                    else -> null
                }

            state = state.copy(
                soraCardStatusStringRes = soraCardStatusStringRes,
                soraCardStatusIconDrawableRes = soraCardStatusIconDrawableRes
            )
            field = value
        }

    init {
        interactor.flowSelectedNode()
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { node ->
                state = state.copy(nodeName = node?.name.orEmpty())
            }
            .launchIn(viewModelScope)

        nodeManager.connectionState
            .catch { onError(it) }
            .distinctUntilChanged()
            .onEach { connected ->
                state = state.copy(nodeConnected = connected)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            state = state.copy(
                soraCardEnabled = soraConfigManager.getSoraCard()
            )
        }

        walletInteractor.subscribeSoraCardInfo()
            .onEach { soraCardInfo = it }
            .launchIn(viewModelScope)
    }

    fun showAccountList() {
        router.showAccountList()
    }

    fun showSoraCard() {
        if (soraCardInfo == null || isKycStatusAvailable()) {
            router.showGetSoraCard()
        } else {
            showCardState()
        }
    }

    private fun isKycStatusAvailable() = runCatching {
        SoraCardCommonVerification.valueOf(
            soraCardInfo?.kycStatus ?: ""
        )
    }.getOrNull() == null

    private fun showCardState() {
        _launchSoraCardSignIn.value = createSoraCardContract(
            soraCardInfo?.let {
                SoraCardInfo(
                    accessToken = it.accessToken,
                    refreshToken = it.refreshToken,
                    accessTokenExpirationTime = it.accessTokenExpirationTime
                )
            }
        )
    }

    fun updateSoraCardInfo(
        accessToken: String,
        refreshToken: String,
        accessTokenExpirationTime: Long,
        kycStatus: String
    ) {
        viewModelScope.launch {
            walletInteractor.updateSoraCardInfo(
                accessToken,
                refreshToken,
                accessTokenExpirationTime,
                kycStatus
            )
        }
    }

    fun showBuyCrypto() {
        assetsRouter.showBuyCrypto()
    }

    fun showSelectNode() {
        selectNodeRouter.showSelectNode()
    }

    fun showAppSettings() {
        router.showAppSettings()
    }

    fun showLogin() {
        router.showLoginSecurity()
    }

    fun showReferral() {
        referralRouter.showReferrals()
    }

    fun showAbout() {
        router.showInformation()
    }

    fun showDebugMenu() {
        if (BuildUtils.isPlayMarket())
            return
        router.showDebugMenu()
    }
}
