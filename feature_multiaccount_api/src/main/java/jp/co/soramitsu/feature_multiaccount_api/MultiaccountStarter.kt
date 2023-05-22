/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_api

import android.content.Context

interface MultiaccountStarter {

    fun startOnboardingFlow(context: Context, isClearTask: Boolean = true)

    fun startOnboardingFlowWithInviteLink(context: Context)
}
