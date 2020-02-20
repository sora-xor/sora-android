/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.activity

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.ActivityFeedTypes
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class ActivityFeedViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val schedulersRule = RxSchedulersRule()

    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var preloader: WithPreloader
    @Mock private lateinit var resourceManager: ResourceManager
    @Mock private lateinit var dateTimeFormatter: DateTimeFormatter

    private lateinit var activityFeedViewModel: ActivityFeedViewModel

    private val activityDate = ActivityDate("Today")
    private val activityHeader = ActivityHeader("Header")
    private val todayDate = Date()
    private val todayTime = "00:00"
    private val activityFeedElement = ActivityFeed(ActivityFeedTypes.FRIEND_REGISTERED.typeCode, "Title", "Description", "1000", todayDate, R.drawable.circle_pincode_drawable_default)
    private val activityFeedItem = ActivityFeedItem(ActivityFeedTypes.FRIEND_REGISTERED.typeCode, "Title", "Description", "1000", todayTime, R.drawable.circle_pincode_drawable_default, ActivityFeedItem.Type.THE_ONLY_EVENT_OF_THE_DAY, -1)

    @Before fun setUp() {
        given(resourceManager.getString(R.string.activity)).willReturn("Header")

        given(resourceManager.getString(R.string.common_today)).willReturn("Today")

        given(resourceManager.getString(R.string.common_yesterday)).willReturn("Yesterday")

        given(dateTimeFormatter.date2Day(anyNonNull(), anyString(), anyString())).willReturn("Today")
        given(dateTimeFormatter.formatTime(anyNonNull())).willReturn(todayTime)

        activityFeedViewModel = ActivityFeedViewModel(interactor, router, preloader, resourceManager, dateTimeFormatter)
    }

    @Test
    fun `help card clicked`() {
        activityFeedViewModel.btnHelpClicked()

        verify(router).showFaq()
    }

    @Test
    fun `on scrolled down`() {
        activityFeedViewModel.showToolbarLiveData.value = null
        activityFeedViewModel.onScrolled(5)

        activityFeedViewModel.showToolbarLiveData.observeForever {
            assertEquals(true, it)
        }
    }

    @Test
    fun `on scrolled up`() {
        activityFeedViewModel.showToolbarLiveData.value = null
        activityFeedViewModel.onScrolled(-5)

        activityFeedViewModel.showToolbarLiveData.observeForever {
            assertEquals(false, it)
        }
    }

    @Test
    fun `refresh data`() {
        given(interactor.getActivityFeedWithAnnouncement(true, 50, 0))
            .willReturn(Single.just(mutableListOf(activityFeedElement)))

        activityFeedViewModel.refreshData(true, true)

        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()

        activityFeedViewModel.activityFeedLiveData.observeForever {
            assertEquals(mutableListOf(activityHeader, activityDate, activityFeedItem), it)
        }
    }

    @Test
    fun `refresh data with empty data`() {
        given(interactor.getActivityFeedWithAnnouncement(true, 50, 0))
            .willReturn(Single.just(mutableListOf<ActivityFeed>()))

        activityFeedViewModel.refreshData(true, true)

        verify(preloader).showPreloader()
        verify(preloader).hidePreloader()

        activityFeedViewModel.activityFeedLiveData.observeForever {
            assertEquals(mutableListOf(activityHeader), it)
        }

        activityFeedViewModel.showEmptyLiveData.observeForever {
            assertEquals(true, it)
        }
    }

    @Test
    fun `load more activity`() {
        given(interactor.getActivityFeed(true, 50, 0))
            .willReturn(Single.just(mutableListOf(activityFeedElement)))

        activityFeedViewModel.loadMoreActivity()

        activityFeedViewModel.activityFeedLiveData.observeForever {
            assertEquals(mutableListOf(activityDate, activityFeedItem), it)
        }

        activityFeedViewModel.showEmptyLiveData.observeForever {
            assertEquals(true, it)
        }
    }
}