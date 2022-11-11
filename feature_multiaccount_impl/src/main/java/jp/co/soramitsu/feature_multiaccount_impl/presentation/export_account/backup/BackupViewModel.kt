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
import jp.co.soramitsu.common.base.model.ToolbarState
import jp.co.soramitsu.common.base.model.ToolbarType
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.ext.addHexPrefix
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.BackupScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection.ExportProtectionViewModel
import jp.co.soramitsu.ui_core.component.toolbar.Action
import kotlinx.coroutines.launch

class BackupViewModel @AssistedInject constructor(
    private val multiAccountInteractor: MultiaccountInteractor,
    private val resourceManager: ResourceManager,
    private val mainRouter: MainRouter,
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

    private val _toggleShareDialog = MutableLiveData<String>()
    val toggleShareDialog: LiveData<String> = _toggleShareDialog

    init {
        viewModelScope.launch {
            when (type) {
                ExportProtectionViewModel.Type.SEED -> {
                    val seed = multiAccountInteractor.getSeed(address)
                    _backupScreenState.value = BackupScreenState(seedString = seed.addHexPrefix())
                    _toolbarState.value = ToolbarState(
                        type = ToolbarType.CENTER_ALIGNED,
                        title = resourceManager.getString(R.string.backup_account_title),
                        menuActions = listOf(Action.Info())
                    )
                }

                ExportProtectionViewModel.Type.PASSPHRASE -> {
                    val mnemonic = multiAccountInteractor.getMnemonic(address)
                    _backupScreenState.value =
                        BackupScreenState(mnemonicWords = mnemonic.split(" "))
                    _toolbarState.value = ToolbarState(
                        type = ToolbarType.CENTER_ALIGNED,
                        title = resourceManager.getString(R.string.mnemonic_title),
                        menuActions = listOf(Action.Info())
                    )
                }

                else -> {}
            }
        }
    }

    fun backupPressed() {
        _backupScreenState.value?.let {
            if (it.seedString.isNotEmpty()) {
                _toggleShareDialog.value = it.seedString
            } else {
                _toggleShareDialog.value = it.mnemonicWords.joinToString(" ")
            }
        }
    }

    fun onToolbarNavigation() {
        mainRouter.popBackStackToAccountDetails()
    }
}
