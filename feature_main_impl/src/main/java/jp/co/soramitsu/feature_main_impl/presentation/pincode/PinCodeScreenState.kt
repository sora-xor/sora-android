/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode

internal data class PinCodeScreenState(
    val checkedDotsCount: Int = 0,
    val maxDotsCount: Int = 6,
    val isBackButtonVisible: Boolean = false,
    val isBiometricButtonVisible: Boolean = false,
    val toolbarTitleString: String,
    val enableShakeAnimation: Boolean = false,
    val isLengthInfoAlertVisible: Boolean = false,
    val migrating: Boolean = false,
    val isConnected: Boolean = true,
)
