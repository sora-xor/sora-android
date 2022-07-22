/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.navigation.NavController
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val invitationHandler: InvitationHandler,
    private val multiaccountStarter: MultiaccountStarter
) : BaseViewModel() {

    fun startedWithInviteAction() {
        invitationHandler.invitationApplied()
    }

    fun onSignUpClicked(navController: NavController) {
        multiaccountStarter.startCreateAccount(navController)
    }

    fun onRecoveryClicked(navController: NavController) {
        multiaccountStarter.startRecoveryAccount(navController)
    }
}
