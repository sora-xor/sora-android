package jp.co.soramitsu.sora.ux

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.account.AccountOptionsRouter
import jp.co.soramitsu.common.account.SelectedAccountSigning
import jp.co.soramitsu.common.account.SelectedAccountSigningSource
import jp.co.soramitsu.common.account.WalletRecoveryCapabilityGate
import jp.co.soramitsu.common.base.SoraBaseFragment
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.DarkThemeManager
import jp.co.soramitsu.common.presentation.compose.components.animatedComposable
import jp.co.soramitsu.common.presentation.compose.neon.safemode.AccountReadOnlyTags
import jp.co.soramitsu.common.presentation.compose.neon.safemode.LocalSelectedAccountReadOnly
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DebounceClickHandler
import jp.co.soramitsu.feature_multiaccount_impl.presentation.OnboardingActivity
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real `SoraBaseFragment`, the host of every screen, on a device: its own `onCreateView`
 * with a stand-in for what Hilt injects. For a read-only account it must put the notice above a
 * signing screen, hand the screen the state its button reads, open Account options of the
 * selected account from the repair action, and say nothing about the account while the whole
 * app is in read-only mode.
 *
 * (Which fragments are signing screens is checked in `ReadOnlyAccountScreensTest`.)
 */
@RunWith(AndroidJUnit4::class)
class ReadOnlyHostFragmentTest {

    class ProbeViewModel : BaseViewModel()

    /** A screen where the person signs, reduced to the one thing a form reads from its host. */
    class SigningProbeFragment : SoraBaseFragment<ProbeViewModel>() {
        override val viewModel = ProbeViewModel()
        override val explainsReadOnlyAccount: Boolean = true

        @OptIn(ExperimentalAnimationApi::class)
        override fun NavGraphBuilder.content(scrollState: ScrollState, navController: NavHostController) {
            animatedComposable(route = theOnlyRoute) { ProbeAction() }
        }
    }

    /** Any other screen: the flag is left as the base class has it. */
    class PlainProbeFragment : SoraBaseFragment<ProbeViewModel>() {
        override val viewModel = ProbeViewModel()

        @OptIn(ExperimentalAnimationApi::class)
        override fun NavGraphBuilder.content(scrollState: ScrollState, navController: NavHostController) {
            animatedComposable(route = theOnlyRoute) { ProbeAction() }
        }
    }

    @get:Rule
    val rule = createEmptyComposeRule()

    private val signing = MutableStateFlow(SelectedAccountSigning(SELECTED, readOnly = true))
    private val repairs = mutableListOf<String>()

    @After
    fun tearDown() {
        WalletRecoveryCapabilityGate.enterNormal()
    }

    private fun show(fragment: SoraBaseFragment<ProbeViewModel>): ActivityScenario<OnboardingActivity> {
        val scenario = ActivityScenario.launch(OnboardingActivity::class.java)
        scenario.onActivity { activity ->
            // What Hilt injects into every screen.
            fragment.debounceClickHandler = DebounceClickHandler()
            fragment.darkThemeManager = mockk<DarkThemeManager> {
                every { darkModeStatusFlow } returns MutableStateFlow(true)
            }
            fragment.selectedAccountSigningSource = SelectedAccountSigningSource { signing }
            fragment.accountOptionsRouter = object : AccountOptionsRouter {
                override fun showAccountDetails(address: String) {
                    repairs += address
                }
            }
            activity.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, "read-only-host-probe")
                .commitNow()
        }
        return scenario
    }

    @Test
    fun aSigningScreenGetsTheNoticeTheRepairAndTheStateItsButtonReads() {
        show(SigningProbeFragment()).use {
            rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertIsDisplayed()
            rule.onNodeWithTag(PROBE).assertTextEquals(OFF)

            rule.onNodeWithTag(AccountReadOnlyTags.REPAIR).performClick()
            assertEquals(listOf(SELECTED), repairs)

            // Its keys are restored: the notice goes and the action is back, on the same screen.
            signing.value = SelectedAccountSigning(SELECTED, readOnly = false)
            rule.waitForIdle()
            rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
            rule.onNodeWithTag(PROBE).assertTextEquals(ON)
        }
    }

    @Test
    fun inReadOnlyModeOfTheWholeAppNothingIsSaidAboutTheAccount() {
        WalletRecoveryCapabilityGate.enterInspectedRecovery()
        show(SigningProbeFragment()).use {
            rule.onNodeWithTag(PROBE).assertIsDisplayed()
            rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
            // The account's state is not handed down either: forms show the app-wide reason.
            rule.onNodeWithTag(PROBE).assertTextEquals(ON)

            WalletRecoveryCapabilityGate.enterNormal()
            rule.waitForIdle()
            rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertIsDisplayed()
            rule.onNodeWithTag(PROBE).assertTextEquals(OFF)
        }
    }

    @Test
    fun aScreenThatDoesNotSignShowsNoNotice() {
        show(PlainProbeFragment()).use {
            rule.onNodeWithTag(PROBE).assertTextEquals(OFF)
            rule.onNodeWithTag(AccountReadOnlyTags.NOTICE).assertDoesNotExist()
        }
    }

    private companion object {
        const val SELECTED = "cnVkoGs3rEMqLqY27c2nfVXJRGdzNJk2ns78DcqtppaSRe8qm"
        const val PROBE = "readOnlyHostProbe"
        const val ON = "action on"
        const val OFF = "action off"

        @androidx.compose.runtime.Composable
        fun ProbeAction() {
            val readOnly = LocalSelectedAccountReadOnly.current
            Text(modifier = Modifier.testTag(PROBE), text = if (readOnly) OFF else ON)
        }
    }
}
