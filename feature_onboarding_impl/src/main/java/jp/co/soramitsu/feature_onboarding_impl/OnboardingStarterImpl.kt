/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl

import android.content.Context
import jp.co.soramitsu.common.util.OnboardingState
import jp.co.soramitsu.feature_onboarding_api.OnboardingStarter
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingActivity
import javax.inject.Inject

class OnboardingStarterImpl @Inject constructor() : OnboardingStarter {

    override fun start(context: Context, onboardingState: OnboardingState) {
        OnboardingActivity.start(context, onboardingState)
    }
}