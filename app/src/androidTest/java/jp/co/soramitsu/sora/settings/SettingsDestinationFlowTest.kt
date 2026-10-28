@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package jp.co.soramitsu.sora.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.fragment.app.Fragment
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.*
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.*
import jp.co.soramitsu.common.domain.OptionsProvider
import jp.co.soramitsu.common.presentation.compose.theme.SoraAppTheme
import jp.co.soramitsu.common.util.ShareUtil
import jp.co.soramitsu.feature_main_impl.presentation.language.*
import jp.co.soramitsu.feature_main_impl.presentation.profile.information.*
import jp.co.soramitsu.feature_main_impl.presentation.profile.appsettings.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real settings graphs; external links and all preference writes are intercepted. */
@RunWith(AndroidJUnit4::class)
class SettingsDestinationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val infoState = mutableStateOf(InformationScreenState("Fixture version 1.2.3"))
    private val infoVm = mockk<InformationViewModel>(relaxed = true) { every { state } answers { infoState.value } }
    private val languages = mutableStateOf(LanguageScreenState(listOf(
        LanguageItem("en", "English", "English", true), LanguageItem("ja", "Japanese", "日本語", false))))
    private val languageVm = mockk<SelectLanguageViewModel>(relaxed = true) { every { state } answers { languages.value } }
    private val settingsState = mutableStateOf(AppSettingsState(false, true, true, false))
    private val settingsVm = mockk<AppSettingsViewModel>(relaxed = true) { every { state } answers { settingsState.value } }
    private lateinit var nav: NavHostController
    private var activeFragment: Fragment? = null
    @Before fun setup() { mockkObject(ShareUtil); every { ShareUtil.shareInBrowser(any<Fragment>(), any()) } just Runs }
    @After fun cleanup() = unmockkObject(ShareUtil)
    private fun show(fragment: SoraBaseFragment<*>, vm: Any, route: String = theOnlyRoute) {
        activeFragment = fragment
        // The disclaimer embeds XML views that resolve the application's theme attributes.
        compose.runOnUiThread { compose.activity.setTheme(jp.co.soramitsu.sora.R.style.AppTheme) }
        fragment.javaClass.getDeclaredField("viewModel\$delegate").apply { isAccessible = true }.set(fragment, lazyOf(vm))
        compose.setContent { SoraAppTheme {
            nav = rememberNavController(); val scroll = rememberScrollState()
            NavHost(nav, startDestination = route) { with(fragment) { content(scroll, nav) } }
        } }
    }
    private fun showInformation() = show(InformationFragment(), infoVm, "Information.Start")
    private fun click(id: Int) = compose.onNodeWithText(compose.activity.getString(id)).performScrollTo().performClick()
    private fun link(id: Int, expected: String) {
        showInformation(); click(id); compose.runOnIdle { verify(exactly = 1) { ShareUtil.shareInBrowser(activeFragment!!, expected) } }
    }
    @Test fun supportOpensDedicatedSupportEndpoint() = link(R.string.about_ask_support, OptionsProvider.telegramHappinessLink)
    @Test fun announcementOpensAnnouncementChannel() = link(R.string.about_announcements, OptionsProvider.telegramAnnouncementsLink)
    @Test fun telegramOpensCommunityEndpoint() = link(R.string.about_telegram, OptionsProvider.telegramLink)
    @Test fun wikiOpensDocumentationEndpoint() = link(R.string.about_wiki, OptionsProvider.wikiLink)
    @Test fun mediumOpensPublicationEndpoint() = link(R.string.about_medium, OptionsProvider.mediumLink)
    @Test fun instagramOpensSocialEndpoint() = link(R.string.about_instagram, OptionsProvider.instagramLink)
    @Test fun youtubeOpensVideoEndpoint() = link(R.string.about_youtube, OptionsProvider.youtubeLink)
    @Test fun twitterOpensSocialEndpoint() = link(R.string.about_twitter, OptionsProvider.twitterLink)
    @Test fun sourceOpensRepositoryEndpoint() = link(R.string.about_source_code, OptionsProvider.sourceLink)
    @Test fun websiteOpensOfficialEndpoint() = link(R.string.about_website, OptionsProvider.website)
    @Test fun faqUsesInternalWebRoute() { showInformation(); click(R.string.faq_title); compose.runOnIdle { verify { infoVm.faq() } } }
    @Test fun termsUseInternalWebRoute() { showInformation(); click(R.string.common_terms_title); compose.runOnIdle { verify { infoVm.terms() } } }
    @Test fun privacyUsesInternalWebRoute() { showInformation(); click(R.string.about_privacy); compose.runOnIdle { verify { infoVm.privacy() } } }
    @Test fun disclaimerCanBeOpenedAndClosedWithoutLeavingInformation() {
        showInformation(); click(R.string.polkaswap_info_title_2)
        compose.runOnIdle { assertEquals("Information.polkaswapDisclaimer", nav.currentDestination?.route) }
        click(R.string.common_close); compose.runOnIdle { assertEquals("Information.Start", nav.currentDestination?.route) }
    }
    @Test fun versionDescriptionTracksCurrentState() {
        showInformation(); compose.runOnIdle { infoState.value = InformationScreenState("Fixture version 2.0.0") }
        compose.onNodeWithText("Fixture version 2.0.0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fixture version 1.2.3").assertDoesNotExist()
    }
    @Test fun languageChoiceUsesExactIsoAndNativeLabel() {
        show(SelectLanguageFragment(), languageVm); compose.onNodeWithText("日本語").assertExists()
        compose.onNodeWithText("Japanese").performClick(); compose.runOnIdle { verify(exactly = 1) { languageVm.languageSelected(languages.value.items[1]) } }
    }
    @Test fun changingLanguageListRemovesStaleChoice() {
        show(SelectLanguageFragment(), languageVm)
        compose.runOnIdle { languages.value = LanguageScreenState(listOf(LanguageItem("fr", "French", "Français", true))) }
        compose.onNodeWithText("Japanese").assertDoesNotExist(); compose.onNodeWithText("French").performClick()
        compose.runOnIdle { verify(exactly = 1) { languageVm.languageSelected(languages.value.items.single()) } }
    }
    @Test fun selectedLanguageRemainsSelectableWithoutRoutingElsewhere() {
        show(SelectLanguageFragment(), languageVm); compose.onAllNodesWithText("English")[0].performClick()
        compose.runOnIdle { verify(exactly = 1) { languageVm.languageSelected(languages.value.items.first()) }; assertEquals(theOnlyRoute, nav.currentDestination?.route) }
    }
    @Test fun emptyLanguageListHasNoClickableChoices() {
        languages.value = LanguageScreenState(emptyList()); show(SelectLanguageFragment(), languageVm)
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }
    @Test fun appLanguageActionUsesPickerHandler() {
        show(AppSettingsFragment(), settingsVm); click(R.string.change_language); compose.runOnIdle { verify { settingsVm.onLanguageClick() } }
    }
    @Test fun reducedEffectsToggleUsesPreferenceHandler() {
        show(AppSettingsFragment(), settingsVm); compose.onNodeWithContentDescription(compose.activity.getString(R.string.appearance_reduce_effects)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { settingsVm.toggleReduceEffects(true) } }
    }
    @Test fun testNetworkToggleRequiresAvailableCapability() {
        show(AppSettingsFragment(), settingsVm); compose.onNodeWithContentDescription(compose.activity.getString(R.string.test_networks)).performScrollTo().performClick()
        compose.runOnIdle { verify(exactly = 1) { settingsVm.toggleTestNetworks(true) } }
    }
    @Test fun capabilityRevocationRemovesTestNetworkToggle() {
        show(AppSettingsFragment(), settingsVm); compose.runOnIdle { settingsState.value = settingsState.value.copy(testNetworksAvailable = false) }
        compose.onNodeWithText(compose.activity.getString(R.string.test_networks)).assertDoesNotExist()
    }
}
