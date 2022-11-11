/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account.model

import android.graphics.drawable.Drawable

data class ExportAccountData(val isActivated: Boolean, val isSelected: Boolean, val icon: Drawable, val address: String, val accountName: String)
