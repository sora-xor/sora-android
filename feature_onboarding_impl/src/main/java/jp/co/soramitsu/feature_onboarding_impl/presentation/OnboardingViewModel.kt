package jp.co.soramitsu.feature_onboarding_impl.presentation

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val invitationHandler: InvitationHandler,
    private val runtimeManager: RuntimeManager,
) : BaseViewModel() {

    init {
        viewModelScope.launch {
            tryCatch {
                runtimeManager.start()
            }
        }
    }

    fun startedWithInviteAction() {
        invitationHandler.invitationApplied()
    }
}
