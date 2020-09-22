package jp.co.soramitsu.feature_main_impl.presentation.userverification

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.SoraException
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor

class UserVerificationViewModel(
    private val router: MainRouter,
    private val interactor: PinCodeInteractor
) : BaseViewModel() {

    private val _checkInviteLiveData = MutableLiveData<Event<Unit>>()
    val checkInviteLiveData: LiveData<Event<Unit>> = _checkInviteLiveData

    private val _restartApplicationLiveData = MutableLiveData<Event<Unit>>()
    val restartApplicationLiveData: LiveData<Event<Unit>> = _restartApplicationLiveData

    fun checkUser() {
        disposables.add(
            interactor.runCheckUserFlow()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.supported) {
                        _checkInviteLiveData.value = Event(Unit)
                        router.popBackStack()
                    } else {
                        router.showUnsupportedScreen(it.downloadUrl)
                    }
                }, {
                    if (it is SoraException && it.kind == SoraException.Kind.BUSINESS && ResponseCode.DID_NOT_FOUND == it.errorResponseCode) {
                        resetUser()
                    } else {
                        onError(it)
                    }
                })
        )
    }

    private fun resetUser() {
        disposables.add(
            interactor.resetUser()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _restartApplicationLiveData.value = Event(Unit)
                }, {
                    onError(it)
                })
        )
    }
}