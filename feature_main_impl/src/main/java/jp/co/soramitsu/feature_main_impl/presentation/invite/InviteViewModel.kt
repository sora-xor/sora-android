package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor
import kotlinx.coroutines.launch

class InviteViewModel(
    private val interactor: InvitationInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
) : BaseViewModel(), WithProgress by progress {

    private val _shareCodeLiveData = MutableLiveData<String>()
    val shareCodeLiveData: LiveData<String> = _shareCodeLiveData

    fun sendInviteClick() {
        viewModelScope.launch {
            showProgress()
            tryCatch {
                _shareCodeLiveData.value = interactor.getInviteLink()
            }
            hideProgress()
        }
    }

    fun backButtonPressed() {
        router.popBackStack()
    }
}
