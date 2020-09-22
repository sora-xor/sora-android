package jp.co.soramitsu.feature_main_api.launcher

import androidx.navigation.NavController
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import java.math.BigDecimal

interface MainRouter {

    fun attachNavController(navController: NavController)

    fun detachNavController(navController: NavController)

    fun showPin(action: PinCodeAction)

    fun showPersonalDataEdition()

    fun popBackStack()

    fun showTerms()

    fun showProjectDetails(projectId: String)

    fun showReferendumDetails(referendumId: String)

    fun showReputation()

    fun showPassphrase()

    fun showSelectLanguage()

    fun showFaq()

    fun showVotesHistory()

    fun showXorTransferAmount(recipientId: String, fullName: String, amount: BigDecimal)

    fun showUnsupportedScreen(appUrl: String)

    fun showAbout()

    fun showPrivacy()

    fun showVerification()

    fun currentDestinationIsPincode(): Boolean

    fun currentDestinationIsUserVerification(): Boolean

    fun showProfile()
}