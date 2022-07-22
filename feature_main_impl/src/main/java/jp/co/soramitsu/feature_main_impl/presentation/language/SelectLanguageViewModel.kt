/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.language.model.LanguageItem
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SelectLanguageViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _languagesLiveData = MutableLiveData<List<LanguageItem>>()
    val languagesLiveData: LiveData<List<LanguageItem>> = _languagesLiveData

    private val _languageChangedLiveData = SingleLiveEvent<String>()
    val languageChangedLiveData: LiveData<String> = _languageChangedLiveData

    init {
        viewModelScope.launch {
            val result = interactor.getAvailableLanguagesWithSelected()
            val availableLanguages = mutableListOf<LanguageItem>()
            val selectedLanguage = result.second

            result.first.forEach {
                availableLanguages.add(
                    LanguageItem(
                        it.iso,
                        resourceManager.getString(it.displayNameResource),
                        resourceManager.getString(it.nativeDisplayNameResource),
                        it.iso == selectedLanguage
                    )
                )
            }

            _languagesLiveData.value = availableLanguages
        }
    }

    fun onBackPressed() {
        router.popBackStack()
    }

    fun languageSelected(language: LanguageItem) {
        viewModelScope.launch {
            _languageChangedLiveData.value = interactor.changeLanguage(language.iso)
        }
    }
}
