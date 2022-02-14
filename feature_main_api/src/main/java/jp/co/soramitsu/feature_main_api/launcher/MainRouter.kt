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

    fun showFlexibleUpdateScreen()

    fun showPersonalDataEdition()

    fun popBackStack()

    fun showTerms()

    fun showReferendumDetails(referendumId: String)

    fun showPassphrase()

    fun showSelectLanguage()

    fun showFaq()

    fun showVotesHistory()

    fun showReferenda()

    fun showUnsupportedScreen(appUrl: String)

    fun showAbout()

    fun showPolkaswapDisclaimerFromSettings()

    fun showPrivacy()

    fun showVerification()

    fun currentDestinationIsPincode(): Boolean

    fun currentDestinationIsUserVerification(): Boolean

    fun currentDestinationIsClaimFragment(): Boolean

    fun showProfile()

    fun showFriends()

    fun showClaim()
}
