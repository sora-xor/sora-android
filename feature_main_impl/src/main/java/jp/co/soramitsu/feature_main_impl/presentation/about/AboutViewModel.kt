package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor

class AboutViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    val appVersionLiveData = MutableLiveData<String>()
    val openSendEmailEvent = MutableLiveData<Event<String>>()

    private val _showBrowserLiveData = MutableLiveData<Event<String>>()
    val showBrowserLiveData: LiveData<Event<String>> = _showBrowserLiveData

    fun backPressed() {
        router.popBackStack()
    }

    fun openSourceClicked() {
        _showBrowserLiveData.value = Event(resourceManager.getString(R.string.about_open_source_url))
    }

    fun termsClicked() {
        router.showTerms()
    }

    fun privacyClicked() {
        router.showPrivacy()
    }

    fun getAppVersion() {
        disposables.add(
            interactor.getAppVersion()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    appVersionLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    fun contactsClicked() {
        openSendEmailEvent.value = Event(resourceManager.getString(R.string.common_sora_support_email))
    }
}