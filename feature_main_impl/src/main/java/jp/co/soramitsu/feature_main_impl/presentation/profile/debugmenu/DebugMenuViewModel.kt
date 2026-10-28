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

package jp.co.soramitsu.feature_main_impl.presentation.profile.debugmenu

import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.androidfoundation.fragment.SingleLiveEvent
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.compose.components.initSmallTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.sora.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.backup.CloudBackupProvider
import kotlinx.coroutines.launch

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val runtimeManager: RuntimeManager,
    private val cloudBackupProvider: CloudBackupProvider,
) : BaseViewModel() {

    private val backupService get() = requireNotNull(cloudBackupProvider.serviceOrNull())

    private fun requireCloudBackup(): Boolean {
        if (cloudBackupProvider.isAvailable) return true
        onError(jp.co.soramitsu.common.R.string.wallet_cloud_backup_unavailable)
        return false
    }

    internal var state by mutableStateOf(DebugMenuScreenState(emptyList()))
        private set

    private val _googleSignInEvent = SingleLiveEvent<Unit>()
    val googleSignInEvent: LiveData<Unit> = _googleSignInEvent

    init {
        _toolbarState.value = initSmallTitle2(
            title = "Debug Menu",
        )

        state = state.copy(
            settings = mutableListOf<SettingOption>().apply {
                add(SettingOption(name = "App ID:", value = OptionsProvider.APPLICATION_ID))
                add(
                    SettingOption(
                        name = "Version Name:",
                        value = OptionsProvider.CURRENT_VERSION_NAME
                    )
                )
                add(
                    SettingOption(
                        name = "Version Code:",
                        value = OptionsProvider.CURRENT_VERSION_CODE.toString()
                    )
                )
                add(SettingOption(name = "Manufacturer:", value = Build.MANUFACTURER))
                add(SettingOption(name = "Model:", value = Build.MODEL))
                add(SettingOption(name = "Version:", value = Build.VERSION.RELEASE))
                add(SettingOption(name = "API level:", value = Build.VERSION.SDK_INT.toString()))
                add(SettingOption(name = "Arch:", value = Build.SUPPORTED_ABIS.joinToString(",")))
                add(
                    SettingOption(
                        name = "Java:",
                        value = System.getProperty("java.specification.version").orEmpty()
                    )
                )
                add(
                    SettingOption(
                        name = "Java:",
                        value = System.getProperty("java.vm.name").orEmpty()
                    )
                )
                add(SettingOption(name = "Build Type:", value = BuildConfig.BUILD_TYPE))
                add(SettingOption(name = "Build Flavor:", value = BuildConfig.FLAVOR))
            }
        )
    }

    fun onResetRuntimeClick() {
        viewModelScope.launch {
            runtimeManager.resetRuntimeVersion()
        }
    }

    fun onChangeGoogleAccount(launcher: ActivityResultLauncher<Intent>) {
        if (!requireCloudBackup()) return
        viewModelScope.launch {
            try {
                backupService.logout()
                backupService.authorize(launcher)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(jp.co.soramitsu.common.R.string.wallet_cloud_backup_failed)
            }
        }
    }

    fun onSuccessfulGoogleSignIn() {
        _googleSignInEvent.trigger()
    }
}
