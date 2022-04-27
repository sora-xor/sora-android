/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

interface OnboardingRouter {

    fun showPersonalInfo()

    fun showRecovery()

    fun showBrowser(link: String)

    fun showUnsupportedScreen(appUrl: String)
}
