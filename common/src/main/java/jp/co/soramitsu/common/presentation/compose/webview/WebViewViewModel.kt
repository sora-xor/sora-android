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

package jp.co.soramitsu.common.presentation.compose.webview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel

class WebViewViewModel @AssistedInject constructor(
    @Assisted("title") private val title: String,
    @Assisted("url") private val url: String
) : BaseViewModel() {

    @AssistedFactory
    interface WebViewViewModelFactory {
        fun create(
            @Assisted("title") title: String,
            @Assisted("url") url: String
        ): WebViewViewModel
    }

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun provideFactory(
            factory: WebViewViewModelFactory,
            title: String,
            url: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(title, url) as T
            }
        }
    }

    init {
        _toolbarState.value = initSmallTitle2(
            title = title,
        )
    }

    var state by mutableStateOf(WebViewState(url = url, loading = true))
        private set

    fun onPageFinished() {
        state = state.copy(loading = false)
    }
}
