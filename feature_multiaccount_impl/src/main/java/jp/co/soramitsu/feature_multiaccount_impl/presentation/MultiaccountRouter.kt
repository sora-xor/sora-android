/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import androidx.navigation.NavController

interface MultiaccountRouter {

    fun attachNavController(navController: NavController)

    fun detachNavController(navController: NavController)

    fun showMnemonic()

    fun onBackButtonPressed()

    fun showTermsScreen()

    fun showPrivacyScreen()

    fun showMnemonicConfirmation()
}
