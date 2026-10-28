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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.androidfoundation.resource.ResourceManager
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.domain.MultiaccountInteractor
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

class ExportProtectionViewModel @AssistedInject constructor(
    private val router: MainRouter,
    private val interactor: MultiaccountInteractor,
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
        SEED,
        PASSPHRASE,
        JSON
    }

    private val _exportProtectionScreenState = MutableLiveData<ExportProtectionScreenState>()
    val exportProtectionScreenState: LiveData<ExportProtectionScreenState> =
        _exportProtectionScreenState

    init {
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

        _exportProtectionScreenState.value?.let {
            _toolbarState.value = initMediumTitle2(
                title = it.titleResource,
            )
        }
        if (type == Type.PASSPHRASE) describePassphrase()
    }

    /**
     * The warning says how many words this account's recovery phrase really has (12, 15, 18, 21
     * or 24), not a fixed "24". The count comes from the stored phrase that re-derives this
     * account; when none does, the text names no number and says that the next screen shows a
     * phrase only if one matches. Until it is known, and if it cannot be read, the neutral text
     * stays. Only the number of words is used here: the words are shown after the PIN.
     */
    private fun describePassphrase() {
        _exportProtectionScreenState.value = _exportProtectionScreenState.value?.copy(
            descriptionText = resourceManager.getString(
                jp.co.soramitsu.common.R.string.export_protection_passphrase_description_neutral
            ),
        )
        viewModelScope.launch {
            val words = try {
                interactor.getBackupMaterial(address).phraseWords.size
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Throwable) {
                jp.co.soramitsu.common.logger.FirebaseWrapper.recordException(error)
                return@launch
            }
            if (words > 0) {
                _exportProtectionScreenState.value = _exportProtectionScreenState.value?.copy(
                    descriptionText = resourceManager.getString(
                        jp.co.soramitsu.common.R.string.export_protection_passphrase_description_words
                    ).format(words.toString()),
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
            Type.SEED -> {
                router.showPinForBackup(action = PinCodeAction.OPEN_SEED, listOf(address))
            }
            Type.PASSPHRASE -> {
                router.showPinForBackup(action = PinCodeAction.OPEN_PASSPHRASE, listOf(address))
            }
            Type.JSON -> {
                router.showPinForBackup(action = PinCodeAction.OPEN_JSON, addresses)
            }
        }
    }

    fun backButtonPressed() {
        router.popBackStack()
    }
}
