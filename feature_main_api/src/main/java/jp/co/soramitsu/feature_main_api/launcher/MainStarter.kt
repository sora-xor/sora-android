/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_api.launcher

import android.content.Context

interface MainStarter {

    fun start(context: Context)

    fun startWithInvite(context: Context)
}