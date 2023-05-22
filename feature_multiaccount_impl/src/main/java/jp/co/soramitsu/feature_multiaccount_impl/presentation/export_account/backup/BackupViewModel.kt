/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.backup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.presentation.SingleLiveEvent
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.common.presentation.trigger
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
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
    private val clipboardManager: ClipboardManager,
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

    private val _copyEvent = SingleLiveEvent<Unit>()
    val copyEvent: LiveData<Unit> = _copyEvent

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
                clipboardManager.addToClipboard("Seed", it.seedString)
            } else {
                clipboardManager.addToClipboard("Mnemonic", it.mnemonicWords.joinToString(" "))
            }
            _copyEvent.trigger()
        }
    }

    override fun onNavIcon() {
        mainRouter.popBackStackToAccountDetails()
    }

    override fun onBackPressed() {
        mainRouter.popBackStackToAccountDetails()
    }
}
