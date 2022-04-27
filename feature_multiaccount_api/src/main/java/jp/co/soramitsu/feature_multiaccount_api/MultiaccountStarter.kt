/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_api

import androidx.navigation.NavController

interface MultiaccountStarter {

    fun startCreateAccount(navController: NavController)

    fun startRecoveryAccount(navController: NavController)
}
