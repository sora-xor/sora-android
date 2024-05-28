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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.androidfoundation.phone.BasicClipboardManager
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.androidfoundation.fragment.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.util.ext.addHexPrefix
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
    private val clipboardManager: BasicClipboardManager,
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
            when (type) {
                ExportProtectionViewModel.Type.SEED -> {
                    val seed = multiAccountInteractor.getSeed(address)
                    _backupScreenState.value = BackupScreenState(seedString = seed.addHexPrefix())
                    _toolbarState.value = initMediumTitle2(R.string.common_raw_seed)
                }

                ExportProtectionViewModel.Type.PASSPHRASE -> {
                    val mnemonic = multiAccountInteractor.getMnemonic(address)
                    _backupScreenState.value =
                        BackupScreenState(mnemonicWords = mnemonic.split(" "))
                    _toolbarState.value = initMediumTitle2(R.string.common_passphrase_title)
                }

                else -> {}
            }
        }
    }

    fun backupPressed() {
        _backupScreenState.value?.let {
            if (it.seedString.isNotEmpty()) {
                clipboardManager.addToClipboard(it.seedString)
            } else {
                clipboardManager.addToClipboard(it.mnemonicWords.joinToString(" "))
            }
            copiedToast.trigger()
        }
    }

    override fun onNavIcon() {
        mainRouter.popBackStackToAccountDetails()
    }

    override fun onBackPressed() {
        mainRouter.popBackStackToAccountDetails()
    }
}
