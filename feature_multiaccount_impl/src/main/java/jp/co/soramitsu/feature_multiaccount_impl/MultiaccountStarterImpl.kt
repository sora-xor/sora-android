/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl

import androidx.navigation.NavController
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter
import javax.inject.Inject

class MultiaccountStarterImpl @Inject constructor(
    private val router: MultiaccountRouter
) : MultiaccountStarter {

    override fun startCreateAccount(navController: NavController) {
        router.attachNavController(navController)
        navController.navigate(R.id.create_account_nav_graph)
    }

    override fun startRecoveryAccount(navController: NavController) {
        router.attachNavController(navController)
        navController.navigate(R.id.recovery_account_nav_graph)
    }
}
