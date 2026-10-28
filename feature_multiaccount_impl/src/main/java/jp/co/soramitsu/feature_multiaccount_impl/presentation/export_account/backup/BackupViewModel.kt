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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.androidfoundation.format.addHexPrefix
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.common.util.SensitiveClipboard
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import kotlinx.coroutines.launch

class BackupViewModel @AssistedInject constructor(
    private val multiAccountInteractor: MultiaccountInteractor,
    private val resourceManager: ResourceManager,
    private val mainRouter: MainRouter,
    private val clipboardManager: SensitiveClipboard,
    @Assisted("type") private val type: ExportProtectionViewModel.Type,
    @Assisted("address") private val address: String
) : BaseViewModel() {

    @AssistedFactory
    interface BackupViewModelFactory {
        fun create(
            @Assisted("type") type: ExportProtectionViewModel.Type,
            @Assisted("address") address: String
        ): BackupViewModel
    }

    private val _backupScreenState = MutableLiveData<BackupScreenState>()
    val backupScreenState: LiveData<BackupScreenState> = _backupScreenState

    init {
        _toolbarState.value = initMediumTitle2("")
        viewModelScope.launch {
            // Only a stored phrase or seed that re-derives this account's address is shown.
            // The stored secrets are judged once for this screen, off the main thread; the
            // answer carries what is shown.
            val material = try {
                when (type) {
                    // Also caches the seed where none is stored (as before).
                    ExportProtectionViewModel.Type.SEED ->
                        multiAccountInteractor.getSeedBackupMaterial(address)
                    else -> multiAccountInteractor.getBackupMaterial(address)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                onError(error)
                return@launch
            }
            when (type) {
                ExportProtectionViewModel.Type.SEED -> {
                    _toolbarState.value = initMediumTitle2(R.string.common_raw_seed)
                    _backupScreenState.value = if (material.hasSeed) {
                        BackupScreenState(seedString = material.seedHex.addHexPrefix())
                    } else {
                        BackupScreenState(noMatchingSecret = true, canExportJson = material.canSign)
                    }
                }

                ExportProtectionViewModel.Type.PASSPHRASE -> {
                    _toolbarState.value = initMediumTitle2(R.string.common_passphrase_title)
                    _backupScreenState.value = if (material.hasPhrase) {
                        BackupScreenState(mnemonicWords = material.phraseWords)
                    } else {
                        BackupScreenState(noMatchingSecret = true, canExportJson = material.canSign)
                    }
                }

                else -> {}
            }
        }
    }

    /** The backup that always works for an account that can sign: the encrypted JSON export. */
    fun exportJsonPressed() {
        if (_backupScreenState.value?.canExportJson != true) return
        mainRouter.showExportJSONProtection(mutableListOf(address))
    }

    fun backupPressed() {
        _backupScreenState.value?.let {
            if (it.noMatchingSecret) return
            runCatching {
                if (it.seedString.isNotEmpty()) clipboardManager.copy(it.seedString)
                else clipboardManager.copy(it.mnemonicWords.joinToString(" "))
            }.onSuccess { copiedToast.trigger() }.onFailure(::onError)
        }
    }

    override fun onNavIcon() {
        mainRouter.popBackStackToAccountDetails()
    }

    override fun onBackPressed() {
        mainRouter.popBackStackToAccountDetails()
    }
}
