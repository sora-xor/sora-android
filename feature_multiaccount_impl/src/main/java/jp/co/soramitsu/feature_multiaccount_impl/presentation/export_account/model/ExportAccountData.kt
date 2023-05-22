/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

import android.graphics.drawable.Drawable
import jp.co.soramitsu.common.account.SoraAccount

data class ExportAccountData(
    val isSelected: Boolean,
    val isSelectedAction: Boolean,
    val icon: Drawable,
    val account: SoraAccount,
)
