/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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

                add(SettingOption(name = "Sora KYS Username:", value = BuildConfig.SORA_CARD_KYC_USERNAME))
                add(SettingOption(name = "X1 Endpoint:", value = BuildConfig.X1_ENDPOINT_URL))
            }
        )
    }

    fun onResetRuntimeClick() {
        viewModelScope.launch {
            runtimeManager.resetRuntimeVersion()
        }
    }
}
