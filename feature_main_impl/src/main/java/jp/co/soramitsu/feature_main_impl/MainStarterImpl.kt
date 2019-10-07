/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl

import android.content.Context
import jp.co.soramitsu.feature_main_api.MainStarter
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import javax.inject.Inject

class MainStarterImpl @Inject constructor() : MainStarter {

    override fun start(context: Context) {
        MainActivity.start(context)
    }

    override fun startWithInvite(context: Context) {
        MainActivity.startWithInvite(context)
    }
}