/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import javax.inject.Inject

class MultiaccountRouterImpl @Inject constructor() : MultiaccountRouter {

    private var navController: NavController? = null

    override fun attachNavController(navController: NavController) {
        this.navController = navController
    }

    override fun detachNavController(navController: NavController) {
        if (this.navController == navController) {
            this.navController = null
        }
    }

    override fun showMnemonic() {
        navController?.navigate(R.id.mnemonicFragment)
    }

    override fun onBackButtonPressed() {
        navController?.popBackStack()
    }

    override fun showTermsScreen() {
        navController?.navigate(R.id.multiaccount_nav_graph)
    }

    override fun showPrivacyScreen() {
        navController?.navigate(R.id.multiaccount_nav_graph)
        navController?.navigate(
            R.id.privacyFragment,
            args = null,
            navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.termsFragment, true)
                .build()
        )
    }

    override fun showMnemonicConfirmation() {
        navController?.navigate(R.id.mnemonicConfirmation)
    }
}
