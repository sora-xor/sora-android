/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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

    /**
     * Closes the PIN screen after the PIN (or biometrics) confirmed [action], and tells the screen
     * below it under [jp.co.soramitsu.feature_main_api.domain.model.PIN_CONFIRMED_ACTION_KEY].
     */
    fun returnFromPinConfirmation(action: PinCodeAction)

    fun showFlexibleUpdateScreen()

    fun popBackStack()

    fun popBackStackToAccountList()

    /**
     * Back from the JSON export. It returns to the screen that opened it when that screen is
     * still waiting underneath (the removal screen's "Export JSON first"), and to the account
     * list otherwise, as before.
     */
    fun leaveBackupJson() = popBackStackToAccountList()

    fun popBackStackToAccountDetails()

    fun showSelectLanguage()

    fun showAppSettings()

    fun showInformation()

    fun showDebugMenu()

    fun currentDestinationIsPincode(): Boolean

    fun currentDestinationIsClaimFragment(): Boolean

    fun currentDestinationIsPinCheckNeeded(): Boolean

    /**
     * Closes a phrase, seed or JSON export screen that is on top, with the screens that led to it.
     * Opening one again takes the typed PIN, which a lock screen's fingerprint does not replace.
     */
    fun closeSecretScreensLeftOpen()

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
