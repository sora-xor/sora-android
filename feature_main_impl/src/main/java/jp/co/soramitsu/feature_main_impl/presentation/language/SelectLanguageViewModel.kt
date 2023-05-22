/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch

@HiltViewModel
class SelectLanguageViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    private val _languageChangedLiveData = SingleLiveEvent<String>()
    val languageChangedLiveData: LiveData<String> = _languageChangedLiveData

    internal var state by mutableStateOf(LanguageScreenState(emptyList()))
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.change_language,
        )

        viewModelScope.launch {
            val result = interactor.getAvailableLanguagesWithSelected()
            val availableLanguages = mutableListOf<LanguageItem>()

            result.first.forEachIndexed { ind, lan ->
                availableLanguages.add(
                    LanguageItem(
                        lan.iso,
                        resourceManager.getString(lan.displayNameResource),
                        resourceManager.getString(lan.nativeDisplayNameResource),
                        ind == result.second,
                    )
                )
            }
            state = LanguageScreenState(availableLanguages)
        }
    }

    internal fun languageSelected(language: LanguageItem) {
        viewModelScope.launch {
            _languageChangedLiveData.value = interactor.changeLanguage(language.iso)
        }
    }
}
