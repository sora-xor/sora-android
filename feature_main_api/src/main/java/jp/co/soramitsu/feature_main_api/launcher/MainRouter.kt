/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_api.launcher

import androidx.navigation.NavController
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction

interface MainRouter {

    fun attachNavController(navController: NavController)

    fun detachNavController(navController: NavController)

    fun showPin(action: PinCodeAction)

    fun showPinForLogout(address: String)

    fun showPinForBackup(action: PinCodeAction, addresses: List<String>)

    fun showPinLengthInfo()

    fun showFlexibleUpdateScreen()

    fun showPersonalDataEdition()

    fun showSwitchAccount()

    fun popBackStack()

    fun popBackStackToAccountList()

    fun popBackStackToAccountDetails()

    fun showTerms()

    fun showSelectLanguage()

    fun showFaq()

    fun showVotesHistory()

    fun showUnsupportedScreen(appUrl: String)

    fun showAbout()

    fun showPolkaswapDisclaimerFromSettings()

    fun showPrivacy()

    fun showVerification()

    fun currentDestinationIsPincode(): Boolean

    fun currentDestinationIsUserVerification(): Boolean

    fun currentDestinationIsClaimFragment(): Boolean

    fun currentDestinationIsPinCheckNeeded(): Boolean

    fun showProfile()

    fun showClaim()

    fun showAccountList()

    fun showExportPassphraseProtection(address: String)

    fun showExportSeedProtection(address: String)

    fun showExportJSONProtection(addresses: List<String>)

    fun showBackupPassphrase(address: String)

    fun showBackupSeed(address: String)

    fun showBackupJson(addresses: List<String>)

    fun showAccountDetails(address: String)

    fun showWebView(title: String, url: String)
}
