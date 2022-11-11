/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_referral_api.ReferralRouter
import jp.co.soramitsu.feature_select_node_api.NodeManager
import jp.co.soramitsu.feature_select_node_api.SelectNodeRouter
import jp.co.soramitsu.feature_select_node_api.domain.model.Node
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val referralRouter: ReferralRouter,
    private val selectNodeRouter: SelectNodeRouter,
    nodeManager: NodeManager
) : BaseViewModel() {

    private val _biometryEnabledLiveData = MutableLiveData<Boolean>()
    val biometryEnabledLiveData: LiveData<Boolean> = _biometryEnabledLiveData

    private val _biometryAvailableLiveData = MutableLiveData<Boolean>()
    val biometryAvailableLiveData: LiveData<Boolean> = _biometryAvailableLiveData

    private val _ldAccountAddress = MutableLiveData<String>()
    val accountAddress: LiveData<String> = _ldAccountAddress

    private val _selectedNode = MutableLiveData<Node?>()
    val selectedNode: LiveData<Node?> = _selectedNode

    private val _nodeConnected = MutableLiveData<Boolean>()
    val nodeConnected: LiveData<Boolean> = _nodeConnected

    init {
        viewModelScope.launch {
            tryCatch {
                _biometryAvailableLiveData.value = interactor.isBiometryAvailable()
                _biometryEnabledLiveData.value = interactor.isBiometryEnabled()
            }
        }
        interactor.flowCurSoraAccount()
            .catch { onError(it) }
            .onEach {
                _ldAccountAddress.value = it.substrateAddress
            }
            .launchIn(viewModelScope)

        interactor.flowSelectedNode()
            .catch { onError(it) }
            .onEach {
                _selectedNode.value = it
            }
            .launchIn(viewModelScope)

        nodeManager.connectionState()
            .catch { onError(it) }
            .onEach { connected ->
                _nodeConnected.value = connected
            }
            .launchIn(viewModelScope)
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun profileAboutClicked() {
        router.showAbout()
    }

    fun disclaimerInSettingsClicked() {
        router.showPolkaswapDisclaimerFromSettings()
    }

    fun profileLanguageClicked() {
        router.showSelectLanguage()
    }

    fun profileChangePin() {
        router.showPin(PinCodeAction.CHANGE_PIN_CODE)
    }

    fun biometryIsChecked(isChecked: Boolean) {
        viewModelScope.launch {
            tryCatch {
                interactor.setBiometryEnabled(isChecked)
                _biometryEnabledLiveData.value = interactor.isBiometryEnabled()
            }
        }
    }

    fun profileFriendsClicked() {
        referralRouter.showReferrals()
    }

    fun onSwitchAccountClicked() {
        router.showAccountList()
    }

    fun selectNodeClicked() {
        selectNodeRouter.showSelectNode()
    }
}
