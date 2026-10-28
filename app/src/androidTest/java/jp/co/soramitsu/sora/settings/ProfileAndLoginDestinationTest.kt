@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.feature_main_impl.presentation.profile.*
import jp.co.soramitsu.feature_main_impl.presentation.profile.login.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAndLoginDestinationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profileState = MutableStateFlow(ProfileScreenState("Fixture node", true, true, true))
    private val profile = mockk<ProfileViewModel>(relaxed = true) { every { state } returns profileState }
    private val loginState = mutableStateOf(LoginSecurityState(true, false))
    private val login = mockk<LoginSecurityViewModel>(relaxed = true) { every { state } answers { loginState.value } }
    private fun show(fragment: SoraBaseFragment<*>, vm: Any) {
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            val nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = theOnlyRoute) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()

    @Test fun profileDestinationConnectsEveryMenuChoiceToItsOwnViewModelHandler() {
        show(ProfileFragment(), profile)
        for (id in listOf(R.string.settings_crypto_accounts, R.string.polkamarkt_title, R.string.settings_nodes,
            R.string.settings_header_app, R.string.settings_login_title, R.string.settings_invite_title, R.string.settings_information_title)) click(id)
        compose.onNodeWithText("Debug Menu").performScrollTo().performClick()
        compose.runOnIdle {
            verify(exactly = 1) { profile.showAccountList(); profile.showPolkamarkt(); profile.showSelectNode(); profile.showAppSettings(); profile.showLogin(); profile.showReferral(); profile.showAbout(); profile.showDebugMenu() }
        }
    }

    @Test fun profileDestinationUpdatesWhenCapabilitiesAndNodeStatusChange() {
        show(ProfileFragment(), profile)
        compose.runOnIdle { profileState.value = profileState.value.copy(nodeName = "Offline fixture", nodeConnected = false, isDebugMenuAvailable = false, polkamarktVisible = false) }
        compose.onNodeWithText("Fixture node").assertDoesNotExist()
        compose.onNodeWithText("Offline fixture").assertExists()
        compose.onNodeWithText("Debug Menu").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.polkamarkt_title)).assertDoesNotExist()
    }

    @Test fun loginDestinationWiresPinAndBiometricActions() {
        show(LoginSecurityFragment(), login)
        click(R.string.change_pin)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.profile_biometry_title)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { login.changePin(); login.toggleBio(true) } }
    }

    @Test fun retainedWalletActionsPreserveTheAddressAndImportConsent() {
        loginState.value = LoginSecurityState(true, false, retainedWallets = listOf("retained-wallet", "phrase-wallet"), needRecoveryPhrase = listOf("phrase-wallet"))
        show(LoginSecurityFragment(), login)
        compose.onNodeWithTag(RetainedWalletsTags.RESTORE).performScrollTo().performClick()
        compose.onNodeWithTag(RetainedWalletsTags.IMPORT_PHRASE).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { login.restoreRetainedWallet("retained-wallet"); login.importRecoveryPhrase() } }
    }
}
