package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val numbersFormatter: NumbersFormatter
) : BaseViewModel() {

    private val _biometryEnabledLiveData = MutableLiveData<Boolean>()
    val biometryEnabledLiveData: LiveData<Boolean> = _biometryEnabledLiveData

    private val _biometryAvailabledLiveData = MutableLiveData<Boolean>()
    val biometryAvailabledLiveData: LiveData<Boolean> = _biometryAvailabledLiveData

    init {
        viewModelScope.launch {
            tryCatch {
                _biometryAvailabledLiveData.value = interactor.isBiometryAvailable()
                _biometryEnabledLiveData.value = interactor.isBiometryEnabled()
            }
        }
    }

    fun onVotesClick() {
        router.showVotesHistory()
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun onPassphraseClick() {
        router.showPin(PinCodeAction.OPEN_PASSPHRASE)
    }

    fun profileAboutClicked() {
        router.showAbout()
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
        router.showFriends()
    }

    fun logoutClicked() {
        router.showPin(PinCodeAction.LOGOUT)
    }

    fun onPersonalDetailsClicked() {
        router.showPersonalDataEdition()
    }
}
