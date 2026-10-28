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

package jp.co.soramitsu.feature_main_impl.presentation.profile.information

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Const
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor

@HiltViewModel
class InformationViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val mainRouter: MainRouter,
    resourceManager: ResourceManager,
) : BaseViewModel() {

    internal var state by mutableStateOf(InformationScreenState(""))
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = R.string.settings_information_title,
        )
        state =
            InformationScreenState(
                appVersion = "${resourceManager.getString(R.string.common_app_version)} ${interactor.getAppVersion()}"
            )
    }

    override fun startScreen(): String = Routes.start

    fun faq() {
        mainRouter.showWebView("", Const.SORA_FAQ_PAGE)
    }

    fun terms() {
        mainRouter.showWebView("", Const.SORA_TERMS_PAGE)
    }

    fun privacy() {
        mainRouter.showWebView("", Const.SORA_PRIVACY_PAGE)
    }
}
