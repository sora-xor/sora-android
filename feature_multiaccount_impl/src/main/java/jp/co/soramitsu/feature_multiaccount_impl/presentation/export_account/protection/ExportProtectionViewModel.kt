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

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.protection

import android.os.Parcelable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import jp.co.soramitsu.common.presentation.compose.components.initMediumTitle2
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_multiaccount_impl.R
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionScreenState
import jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model.ExportProtectionSelectableModel
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
