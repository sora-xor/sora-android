/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.splash.domain

import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState

interface SplashRouter {

    fun showOnBoardingScreen(onBoardingState: OnboardingState)

    fun showOnBoardingScreenViaInviteLink()

    fun showMainScreen()

    fun showMainScreenFromInviteLink()
}