/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

import androidx.annotation.StringRes

data class ExportProtectionScreenState(
    @StringRes val titleResource: Int,
    @StringRes val descriptionResource: Int,
    val selectableItemList: List<ExportProtectionSelectableModel>,
    val isButtonEnabled: Boolean = false
)

data class ExportProtectionSelectableModel(val isSelected: Boolean = false, @StringRes val textString: Int)
