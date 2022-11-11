/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import android.os.Parcelable
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
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class ExportProtectionViewModel @AssistedInject constructor(
    private val router: MainRouter,
    private val resourceManager: ResourceManager,
    @Assisted("type") private val type: Type,
    @Assisted("address") private val address: String,
    @Assisted("addresses") private val addresses: List<String>
) : BaseViewModel() {

    @AssistedFactory
    interface ExportProtectionViewModelFactory {
        fun create(
            @Assisted("type") type: Type,
            @Assisted("address") address: String,
            @Assisted("addresses") addresses: List<String>
        ): ExportProtectionViewModel
    }

    @Parcelize
    enum class Type : Parcelable {
        SEED, PASSPHRASE, JSON
    }

    private val _exportProtectionScreenState = MutableLiveData<ExportProtectionScreenState>()
    val exportProtectionScreenState: LiveData<ExportProtectionScreenState> =
        _exportProtectionScreenState

    init {
        viewModelScope.launch {
            delay(50L)
            type.let {
                _exportProtectionScreenState.value = when (type) {
                    Type.SEED -> {
                        ExportProtectionScreenState(
                            titleResource = R.string.common_raw_seed,
                            descriptionResource = R.string.export_protection_seed_description,
                            listOf(
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_seed_1
                                ),
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_seed_2
                                ),
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_seed_3
                                )
                            )
                        )
                    }
                    Type.JSON -> {
                        ExportProtectionScreenState(
                            titleResource = R.string.export_protection_json_title,
                            descriptionResource = R.string.export_protection_json_description,
                            listOf(
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_json_1
                                ),
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_json_2
                                ),
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_json_3
                                )
                            )
                        )
                    }
                    Type.PASSPHRASE -> {
                        ExportProtectionScreenState(
                            titleResource = R.string.common_passphrase_title,
                            descriptionResource = R.string.export_protection_passphrase_description,
                            listOf(
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_passphrase_1
                                ),
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_passphrase_2
                                ),
                                ExportProtectionSelectableModel(
                                    textString = R.string.export_protection_passphrase_3
                                )
                            )
                        )
                    }
                }
            }

            _exportProtectionScreenState.value?.let {
                _toolbarState.value = ToolbarState(
                    ToolbarType.MEDIUM,
                    resourceManager.getString(it.titleResource)
                )
            }
        }
    }

    fun onItemClicked(index: Int) {
        _exportProtectionScreenState.value?.let {
            val newList = it.selectableItemList
                .mapIndexed { i, exportProtectionSelectableModel ->
                    if (i == index) {
                        ExportProtectionSelectableModel(
                            !exportProtectionSelectableModel.isSelected,
                            exportProtectionSelectableModel.textString
                        )
                    } else {
                        exportProtectionSelectableModel
                    }
                }

            val isButtonEnabled = newList.count { it.isSelected } == newList.size

            _exportProtectionScreenState.value =
                it.copy(selectableItemList = newList, isButtonEnabled = isButtonEnabled)
        }
    }

    fun continueClicked() {
        when (type) {
            Type.SEED -> { router.showPinForBackup(action = PinCodeAction.OPEN_SEED, listOf(address)) }
            Type.PASSPHRASE -> { router.showPinForBackup(action = PinCodeAction.OPEN_PASSPHRASE, listOf(address)) }
            Type.JSON -> { router.showPinForBackup(action = PinCodeAction.OPEN_JSON, addresses) }
        }
    }

    fun backButtonPressed() {
        router.popBackStack()
    }
}
