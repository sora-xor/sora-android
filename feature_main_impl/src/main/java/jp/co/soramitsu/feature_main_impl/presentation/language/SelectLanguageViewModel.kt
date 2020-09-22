package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.language.model.LanguageItem

class SelectLanguageViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _languagesLiveData = MutableLiveData<List<LanguageItem>>()
    val languagesLiveData: LiveData<List<LanguageItem>> = _languagesLiveData

    private val _languageChangedLiveData = MutableLiveData<Event<String>>()
    val languageChangedLiveData: LiveData<Event<String>> = _languageChangedLiveData

    init {
        disposables.add(
            interactor.getAvailableLanguagesWithSelected()
                .map {
                    val availableLanguages = it.first
                    val selectedLanguage = it.second

                    availableLanguages.map {
                        LanguageItem(
                            it.iso,
                            resourceManager.getString(it.displayNameResource),
                            resourceManager.getString(it.nativeDisplayNameResource),
                            it.iso == selectedLanguage
                        )
                    }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _languagesLiveData.value = it
                }, {
                    logException(it)
                })
        )
    }

    fun onBackPressed() {
        router.popBackStack()
    }

    fun languageSelected(language: LanguageItem) {
        disposables.add(
            interactor.changeLanguage(language.iso)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _languageChangedLiveData.value = Event(it)
                }, {
                    logException(it)
                })
        )
    }
}