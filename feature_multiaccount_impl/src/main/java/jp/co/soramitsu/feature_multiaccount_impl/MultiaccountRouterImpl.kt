/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import jp.co.soramitsu.common.account.SoraAccount
import jp.co.soramitsu.common.presentation.args.accountName
import jp.co.soramitsu.common.presentation.args.soraAccount
import jp.co.soramitsu.common.presentation.args.withArgs
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter

class MultiaccountRouterImpl : MultiaccountRouter {

    override fun showMnemonic(navController: NavController?, accountName: String) {
        navController?.navigate(
            R.id.mnemonicFragment,
            withArgs {
                this.accountName = accountName
            }
        )
    }

    override fun showTermsScreen(navController: NavController?) {
        navController?.navigate(R.id.multiaccount_nav_graph)
    }

    override fun showPrivacyScreen(navController: NavController?) {
        navController?.navigate(R.id.multiaccount_nav_graph)
        navController?.navigate(
            R.id.privacyFragment,
            args = null,
            navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.termsFragment, true)
                .build()
        )
    }

    override fun showMnemonicConfirmation(navController: NavController?, soraAccount: SoraAccount) {
        navController?.navigate(
            R.id.mnemonicConfirmation,
            withArgs {
                this.soraAccount = soraAccount
            }
        )
    }
}
