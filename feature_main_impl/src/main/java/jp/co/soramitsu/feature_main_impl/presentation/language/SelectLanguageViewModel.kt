/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.feature_main_impl.presentation.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
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
