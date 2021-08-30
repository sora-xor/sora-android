/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.recovery

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import kotlinx.coroutines.launch

class RecoveryViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    companion object {
        const val MNEMONIC_INPUT_LENGTH = 150
    }

    private val _mnemonicInputLengthLiveData = MutableLiveData(MNEMONIC_INPUT_LENGTH)
    val mnemonicInputLengthLiveData: LiveData<Int> = _mnemonicInputLengthLiveData

    private val _nextButtonEnabledLiveData = MutableLiveData<Boolean>()
    val nextButtonEnabledLiveData: LiveData<Boolean> = _nextButtonEnabledLiveData

    fun btnNextClick(mnemonic: String, accountName: String) {
        viewModelScope.launch {
            progress.showProgress()
            try {
                val valid = interactor.isMnemonicValid(mnemonic)
                if (valid) {
                    interactor.runRecoverFlow(mnemonic, accountName)
                    router.showMainScreen()
                } else {
                    throw SoraException.businessError(ResponseCode.MNEMONIC_IS_NOT_VALID)
                }
            } catch (t: Throwable) {
                onError(t)
            } finally {
                progress.hideProgress()
            }
        }
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    fun onInputChanged(mnemonic: String) {
        _nextButtonEnabledLiveData.value = mnemonic.isNotEmpty()
    }
}
