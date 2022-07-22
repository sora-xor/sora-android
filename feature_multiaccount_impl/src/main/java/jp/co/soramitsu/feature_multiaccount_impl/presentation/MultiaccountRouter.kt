/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation

import androidx.navigation.NavController
import jp.co.soramitsu.common.account.SoraAccount

interface MultiaccountRouter {

    fun showMnemonic(navController: NavController?, accountName: String)

    fun showTermsScreen(navController: NavController?)

    fun showPrivacyScreen(navController: NavController?)

    fun showMnemonicConfirmation(navController: NavController?, soraAccount: SoraAccount)
}
