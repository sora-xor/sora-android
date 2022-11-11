/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_api

import android.content.Context

interface OnboardingStarter {

    fun start(context: Context, isClearTask: Boolean = true)

    fun startWithInviteLink(context: Context)
}
