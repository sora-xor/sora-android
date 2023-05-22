/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl

import android.content.Context
import javax.inject.Inject
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity

class MainStarterImpl @Inject constructor() : MainStarter {

    override fun start(context: Context) {
        MainActivity.start(context)
    }

    override fun startWithInvite(context: Context) {
        MainActivity.startWithInvite(context)
    }

    override fun restartAfterAddAccount(context: Context) {
        MainActivity.restartAfterAddAccount(context)
    }
}
