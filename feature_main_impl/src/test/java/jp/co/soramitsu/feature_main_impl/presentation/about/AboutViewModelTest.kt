/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.about

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class AboutViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var aboutViewModel: AboutViewModel

    @Before fun setUp() {
        aboutViewModel = AboutViewModel(interactor, router, resourceManager)
    }

    @Test fun `init called`() {
        val version = "1.0"

        given(interactor.getAppVersion()).willReturn(Single.just(version))

        aboutViewModel.getAppVersion()

        aboutViewModel.appVersionLiveData.observeForever {
            assertEquals(version, it)
        }
    }

    @Test fun `back clicked`() {
        aboutViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test fun `opensource item clicked`() {
        val opensourceLink = "https://github.com/sora-xor/Sora-Android"

        given(resourceManager.getString(R.string.about_open_source_url)).willReturn(opensourceLink)

        aboutViewModel.openSourceClicked()

        aboutViewModel.showBrowserLiveData.observeForever {
            assertEquals(opensourceLink, it.peekContent())
        }

        assertEquals(opensourceLink, aboutViewModel.showBrowserLiveData.value!!.peekContent())
    }

    @Test fun `terms item clicked`() {
        aboutViewModel.termsClicked()

        verify(router).showTerms()
    }

    @Test fun `privacy item clicked`() {
        aboutViewModel.privacyClicked()

        verify(router).showPrivacy()
    }

    @Test fun `contacts item clicked`() {
        val email = "sora@sora.jp"

        given(resourceManager.getString(R.string.common_sora_support_email)).willReturn(email)

        aboutViewModel.contactsClicked()

        aboutViewModel.openSendEmailEvent.observeForever {
            assertEquals(email, it.peekContent())
        }
    }
}