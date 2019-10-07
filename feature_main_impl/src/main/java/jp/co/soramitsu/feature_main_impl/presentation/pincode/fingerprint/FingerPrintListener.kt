/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.pincode.fingerprint

interface FingerPrintListener {

    fun onFingerPrintSuccess()

    fun onAuthFailed()

    fun onAuthenticationHelp(message: String)

    fun onAuthenticationError(message: String)

    fun showFingerPrintDialog()

    fun hideFingerPrintDialog()
}