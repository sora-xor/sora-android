/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu

import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import kotlinx.coroutines.launch

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val runtimeManager: RuntimeManager,
) : BaseViewModel() {

    internal var state by mutableStateOf(DebugMenuScreenState(emptyList()))
        private set

    init {
        _toolbarState.value = initSmallTitle2(
            title = "Debug Menu",
        )

        state = state.copy(
            settings = mutableListOf<SettingOption>().apply {
                add(SettingOption(name = "App ID:", value = OptionsProvider.APPLICATION_ID))
                add(SettingOption(name = "Version Name:", value = OptionsProvider.CURRENT_VERSION_NAME))
                add(SettingOption(name = "Version Code:", value = OptionsProvider.CURRENT_VERSION_CODE.toString()))
                add(SettingOption(name = "Manufacturer:", value = Build.MANUFACTURER))
                add(SettingOption(name = "Model:", value = Build.MODEL))
                add(SettingOption(name = "Version:", value = Build.VERSION.RELEASE))
                add(SettingOption(name = "API level:", value = Build.VERSION.SDK_INT.toString()))
                add(SettingOption(name = "Arch:", value = Build.SUPPORTED_ABIS.joinToString(",")))
                add(SettingOption(name = "Java:", value = System.getProperty("java.specification.version").orEmpty()))
                add(SettingOption(name = "Java:", value = System.getProperty("java.vm.name").orEmpty()))

                add(SettingOption(name = "Build Type:", value = BuildConfig.BUILD_TYPE))
                add(SettingOption(name = "Build Flavor:", value = BuildConfig.FLAVOR))

                add(SettingOption(name = "X1w:", value = BuildConfig.X1_WIDGET_ID))
                add(SettingOption(name = "X1e:", value = BuildConfig.X1_ENDPOINT_URL))
                add(SettingOption(name = "su:", value = BuildConfig.SORACARD_BACKEND_URL))
            }
        )
    }

    fun onResetRuntimeClick() {
        viewModelScope.launch {
            runtimeManager.resetRuntimeVersion()
        }
    }
}
