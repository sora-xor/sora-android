/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.MainCoroutineRule
import jp.co.soramitsu.test_shared.getOrAwaitValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyInt
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class AboutViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: MainInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var resourceManager: ResourceManager

    private lateinit var aboutViewModel: AboutViewModel

    @Before
    fun setUp() {
        aboutViewModel = AboutViewModel(interactor, router, resourceManager)
    }

    @Test
    fun `init called`() = runTest {
        val version = "1.0"
        val title = "source"
        given(interactor.getAppVersion()).willReturn("1.0")
        given(resourceManager.getString(anyInt())).willReturn(title)

        given(interactor.getAppVersion()).willReturn("1.0")
        given(resourceManager.getString(anyInt())).willReturn("source")
        aboutViewModel.getAppVersion()
        advanceUntilIdle()
        val res = aboutViewModel.sourceTitleLiveData.getOrAwaitValue()
        assertEquals("$title $version", res)
    }

    @Test
    fun `back clicked`() {
        aboutViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test
    fun `opensource item clicked`() {
        val opensourceLink = "https://github.com/sora-xor/Sora-Android"

        aboutViewModel.openSourceClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(opensourceLink, res)
    }


    @Test
    fun `website clicked`() {
        val websiteLink = "https://sora.org"

        aboutViewModel.websiteClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(websiteLink, res)
    }

    @Test
    fun `telegram clicked`() {
        val telegramLink = "https://t.me/sora_xor"

        aboutViewModel.telegramClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(telegramLink, res)
    }

    @Test
    fun `telegram announcements clicked`() {
        val telegramAnnouncementLink = "https://t.me/sora_announcements"

        aboutViewModel.telegramAnnouncementsClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(telegramAnnouncementLink, res)
    }

    @Test
    fun `telegram happines clicked`() {
        val telegramHappinesLink = "https://t.me/sorahappiness"

        aboutViewModel.telegramAskSupportClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(telegramHappinesLink, res)
    }

    @Test
    fun `twitter clicked`() {
        val twitterLink = "https://twitter.com/sora_xor"

        aboutViewModel.twitterClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(twitterLink, res)
    }

    @Test
    fun `youtube clicked`() {
        val youtubeLink = "https://youtube.com/sora_xor"

        aboutViewModel.youtubeClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(youtubeLink, res)
    }

    @Test
    fun `instagram clicked`() {
        val instagramLink = "https://instagram.com/sora_xor"

        aboutViewModel.instagramClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(instagramLink, res)
    }

    @Test
    fun `medium clicked`() {
        val mediumLink = "https://medium.com/sora_xor"

        aboutViewModel.mediumClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(mediumLink, res)
    }

    @Test
    fun `wiki clicked`() {
        val wikiLink = "https://wiki.sora.org"

        aboutViewModel.wikiClicked()

        val res = aboutViewModel.showBrowserLiveData.getOrAwaitValue()
        assertEquals(wikiLink, res)
    }

    @Test
    fun `terms item clicked`() {
        aboutViewModel.termsClicked()

        verify(router).showTerms()
    }

    @Test
    fun `privacy item clicked`() {
        aboutViewModel.privacyClicked()

        verify(router).showPrivacy()
    }

    @Test
    fun `contacts item clicked`() {
        val email = "support@sora.org"

        aboutViewModel.contactsClicked()

        val res = aboutViewModel.openSendEmailEvent.getOrAwaitValue()
        assertEquals(email, res)
    }
}