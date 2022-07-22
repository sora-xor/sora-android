/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl

import androidx.navigation.NavController
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter

class MultiaccountStarterImpl : MultiaccountStarter {

    override fun startCreateAccount(navController: NavController) {
        navController.navigate(R.id.create_account_nav_graph)
    }

    override fun startRecoveryAccount(navController: NavController) {
        navController.navigate(R.id.recovery_account_nav_graph)
    }
}
