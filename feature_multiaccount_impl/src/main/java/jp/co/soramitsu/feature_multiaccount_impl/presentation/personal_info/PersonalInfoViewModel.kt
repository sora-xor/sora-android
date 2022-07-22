/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.personal_info

import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalInfoViewModel @Inject constructor(
    private val router: MultiaccountRouter
) : BaseViewModel() {

    private val _screenshotAlertDialogEvent = SingleLiveEvent<Unit>()
    val screenshotAlertDialogEvent: LiveData<Unit> = _screenshotAlertDialogEvent

    private var accountName: String = ""

    fun register(name: String) {
        viewModelScope.launch {
            accountName = name
            _screenshotAlertDialogEvent.trigger()
        }
    }

    fun screenshotAlertOkClicked(navController: NavController) {
        router.showMnemonic(navController, accountName)
    }

    fun showTermsScreen(navController: NavController) {
        router.showTermsScreen(navController)
    }

    fun showPrivacyScreen(navController: NavController?) {
        router.showPrivacyScreen(navController)
    }
}
