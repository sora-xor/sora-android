/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation

interface OnboardingRouter {

    fun showPersonalInfo(countryIso: String)

    fun showMnemonic()

    fun showVerification(countryIso: String, blockingTime: Int)

    fun showMainScreen()

    fun showRecovery()

    fun showBrowser(link: String)

    fun onBackButtonPressed()

    fun showTermsScreen()

    fun showCountries()

    fun showPhoneNumber(countryIso: String, phoneCode: String)

    fun showUnsupportedScreen(appUrl: String)

    fun showPrivacyScreen()
}