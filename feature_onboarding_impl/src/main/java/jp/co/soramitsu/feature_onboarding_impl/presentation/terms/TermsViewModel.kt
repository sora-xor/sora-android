package jp.co.soramitsu.feature_onboarding_impl.presentation.terms

import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class TermsViewModel(
    private val router: OnboardingRouter
) : BaseViewModel() {

    fun onBackPressed() {
        router.onBackButtonPressed()
    }
}
