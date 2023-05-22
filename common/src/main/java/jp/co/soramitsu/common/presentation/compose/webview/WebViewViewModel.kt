/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
