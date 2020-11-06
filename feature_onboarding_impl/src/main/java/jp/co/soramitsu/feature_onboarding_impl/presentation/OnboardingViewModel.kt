package jp.co.soramitsu.feature_onboarding_impl.presentation

import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel

class OnboardingViewModel(
    private val invitationHandler: InvitationHandler
) : BaseViewModel() {

    fun startedWithInviteAction() {
        invitationHandler.invitationApplied()
    }
}
