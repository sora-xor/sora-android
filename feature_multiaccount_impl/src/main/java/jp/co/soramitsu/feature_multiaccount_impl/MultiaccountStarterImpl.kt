/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl

import android.content.Context
import jp.co.soramitsu.feature_multiaccount_api.MultiaccountStarter
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity

class MultiaccountStarterImpl : MultiaccountStarter {

    override fun startOnboardingFlow(context: Context, isClearTask: Boolean) {
        OnboardingActivity.start(context, isClearTask)
    }

    override fun startOnboardingFlowWithInviteLink(context: Context) {
        OnboardingActivity.startWithInviteLink(context)
    }
}
