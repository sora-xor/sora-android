/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_api

import android.content.Context
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface OnboardingStarter {

    fun start(context: Context, onboardingState: OnboardingState)

    fun startWithInviteLink(context: Context)
}