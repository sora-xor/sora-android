/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl

import android.content.Context
import jp.co.soramitsu.feature_onboarding_api.OnboardingStarter
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingActivity

class OnboardingStarterImpl : OnboardingStarter {

    override fun startWithInviteLink(context: Context) {
        OnboardingActivity.startWithInviteLink(context)
    }

    override fun start(context: Context, isClearTask: Boolean) {
        OnboardingActivity.start(context, isClearTask)
    }
}
