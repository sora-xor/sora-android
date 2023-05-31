/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

internal data class ProfileScreenState(
    val nodeName: String,
    val nodeConnected: Boolean,
    val isDebugMenuAvailable: Boolean,
    val soraCardEnabled: Boolean,
    val soraCardStatusStringRes: Int,
    val soraCardStatusIconDrawableRes: Int?
)
