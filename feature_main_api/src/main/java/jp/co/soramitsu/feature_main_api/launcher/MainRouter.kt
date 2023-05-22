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

    fun showLoginSecurity()

    fun showPinForLogout(address: String)

    fun showPinForBackup(action: PinCodeAction, addresses: List<String>)

    fun showFlexibleUpdateScreen()

    fun popBackStack()

    fun popBackStackToAccountList()

    fun popBackStackToAccountDetails()

    fun showSelectLanguage()

    fun showAppSettings()

    fun showInformation()

    fun showDebugMenu()

    fun currentDestinationIsPincode(): Boolean

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

    fun showGetSoraCard()
}
