/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.switchaccount

import android.graphics.drawable.Drawable

data class SwitchAccountItem(
    val icon: Drawable,
    val accountAddress: String,
    val selected: Boolean,
)
