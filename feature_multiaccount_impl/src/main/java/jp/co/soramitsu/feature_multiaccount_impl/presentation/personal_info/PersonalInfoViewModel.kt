/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.personal_info

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import kotlinx.coroutines.launch

class PersonalInfoViewModel(
    private val interactor: MultiaccountInteractor,
    private val router: MultiaccountRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    private val _screenshotAlertDialogEvent = SingleLiveEvent<Unit>()
    val screenshotAlertDialogEvent: LiveData<Unit> = _screenshotAlertDialogEvent

    fun register(accountName: String) {
        viewModelScope.launch {
            showProgress()
            interactor.createUser(accountName)
            hideProgress()
            _screenshotAlertDialogEvent.trigger()
        }
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    fun screenshotAlertOkClicked() {
        router.showMnemonic()
    }

    fun showTermsScreen() {
        router.showTermsScreen()
    }

    fun showPrivacyScreen() {
        router.showPrivacyScreen()
    }
}
