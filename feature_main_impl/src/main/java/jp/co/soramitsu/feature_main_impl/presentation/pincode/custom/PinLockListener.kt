/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.custom

interface PinLockListener {

    fun onComplete(pin: String)

    fun onEmpty()

    fun onPinChange(pinLength: Int, intermediatePin: String)

    fun onFingerprintButtonClicked()
}