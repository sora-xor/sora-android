package jp.co.soramitsu.feature_main_impl.presentation.privacy

import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter

class PrivacyViewModel(
    private val router: MainRouter
) : BaseViewModel() {

    fun onBackPressed() {
        router.popBackStack()
    }
}
