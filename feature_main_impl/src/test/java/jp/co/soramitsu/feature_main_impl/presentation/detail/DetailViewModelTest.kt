/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.detail

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_project_api.domain.model.DiscussionLink
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItem
import jp.co.soramitsu.feature_project_api.domain.model.GalleryItemType
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.feature_project_api.domain.model.ProjectStatus
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.net.URL
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class DetailViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var preloader: WithPreloader
    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var detailViewModel: DetailViewModel

    private val numbersFormatter = NumbersFormatter()

    private val projectId = "projectId"

    private val galleryItemImage = GalleryItem(GalleryItemType.IMAGE, "http://image", "http://imagepreview", 0)
    private val galleryItemVideo = GalleryItem(GalleryItemType.VIDEO, "http://video", "http://videopreview", 10)

    private val gallery = mutableListOf(galleryItemImage, galleryItemVideo)

    private val votes = BigDecimal.TEN

    private val projectDetails = ProjectDetails(
        projectId,
        URL("http://imageLink"),
        URL("http://projectLink"),
        "name",
        "email",
        "description",
        "detailedDescription",
        Date(Date().time + 1000),
        100,
        200,
        12,
        13,
        BigDecimal.ONE,
        true,
        true,
        gallery,
        ProjectStatus.OPEN,
        Date(),
        DiscussionLink("Reddit", "https://link")
    )

    @Before
    fun setUp() {
        given(interactor.getProjectById(projectId)).willReturn(Single.just(projectDetails).toObservable())
        given(interactor.updateProject(projectId)).willReturn(Completable.complete())
        given(interactor.getVotes(true)).willReturn(Single.just(votes))

        detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)
    }

    @Test fun `correct project`() {
        detailViewModel.projectDetailsLiveData.observeForever {
            assertEquals(projectDetails, it)
        }

        assertEquals(projectDetails, detailViewModel.projectDetailsLiveData.value)
    }

    @Test fun `correct gallery`() {
        detailViewModel.galleryLiveData.observeForever {
            assertEquals(gallery, it)
        }

        assertEquals(gallery, detailViewModel.galleryLiveData.value)
    }

    @Test fun `description filled with detailed description`() {
        detailViewModel.projectDescriptionLiveData.observeForever {
            assertEquals(projectDetails.detailedDescription, it)
        }

        assertEquals(projectDetails.detailedDescription, detailViewModel.projectDescriptionLiveData.value)
    }

    @Test fun `description filled with short description if detailed is empty`() {
        val projectWithShortDesc = projectDetails.copy(detailedDescription = "")
        given(interactor.getProjectById(projectId)).willReturn(Observable.just(projectWithShortDesc))
        val detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.projectDescriptionLiveData.observeForever {
            assertEquals(projectWithShortDesc.description, it)
        }

        assertEquals(projectWithShortDesc.description, detailViewModel.projectDescriptionLiveData.value)
    }

    @Test fun `friends voted text correct`() {
        given(resourceManager.getQuantityString(R.plurals.project_friends_template, 12)).willReturn("%1\$s friends voted")

        val detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.friendsVotedLiveData.observeForever {
            assertEquals("12 friends voted", it)
        }
        detailViewModel.votesAndFavoritesVisibility.observeForever {
            assertTrue(it)
        }

        assertEquals("12 friends voted", detailViewModel.friendsVotedLiveData.value)
        assertTrue(detailViewModel.votesAndFavoritesVisibility.value!!)
    }

    @Test fun `favorites count text correct`() {
        given(resourceManager.getQuantityString(R.plurals.project_details_favorites_format, 13)).willReturn("13 favorites")

        val detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.favoritesLiveData.observeForever {
            assertEquals("13 favorites", it)
        }
        detailViewModel.votesAndFavoritesVisibility.observeForever {
            assertTrue(it)
        }

        assertEquals("13 favorites", detailViewModel.favoritesLiveData.value)
        assertTrue(detailViewModel.votesAndFavoritesVisibility.value!!)
    }

    @Test fun `friend votes and favorites view gone if no votes and favorites`() {
        val projectWithShortDesc = projectDetails.copy(votedFriendsCount = 0, favoriteCount = 0)
        given(interactor.getProjectById(projectId)).willReturn(Observable.just(projectWithShortDesc))

        val detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.votesAndFavoritesVisibility.observeForever {
            assertFalse(it)
        }
        assertFalse(detailViewModel.votesAndFavoritesVisibility.value!!)
    }

    @Test fun `votes without formatting`() {
        given(interactor.getVotes(anyBoolean())).willReturn(Single.just(BigDecimal.TEN))

        detailViewModel.getVotes(true)

        detailViewModel.votesFormattedLiveData.observeForever {
            assertEquals("10", it)
        }
        assertEquals("10", detailViewModel.votesFormattedLiveData.value)
    }

    @Test fun `votes with formatting`() {
        given(interactor.getVotes(anyBoolean())).willReturn(Single.just(BigDecimal(12345)))
        given(resourceManager.getString(R.string.project_votes_k_template)).willReturn("%1\$sK")

        detailViewModel.getVotes(true)

        detailViewModel.votesFormattedLiveData.observeForever {
            assertEquals("12K", it)
        }
        assertEquals("12K", detailViewModel.votesFormattedLiveData.value)
    }

    @Test fun `web site clicked`() {
        detailViewModel.websiteClicked()

        detailViewModel.showBrowserLiveData.observeForever {
            assertEquals(projectDetails.projectLink.toString(), it.peekContent())
        }

        assertEquals(projectDetails.projectLink.toString(), detailViewModel.showBrowserLiveData.value!!.peekContent())
    }

    @Test fun `email clicked`() {
        detailViewModel.emailClicked()

        detailViewModel.sendEmailEvent.observeForever {
            assertEquals(projectDetails.email, it.peekContent())
        }
    }

    @Test fun `update project called`() {
        detailViewModel.updateProject()

        verify(interactor).updateProject(projectId)
    }

    @Test fun `vote for project called`() {
        val votesCount = 100.toLong()
        given(interactor.voteForProject(projectId, votesCount)).willReturn(Completable.complete())

        detailViewModel.voteForProject(votesCount)

        verify(interactor).voteForProject(projectId, votesCount)
        verify(interactor).updateProject(projectId)
    }

    @Test fun `favorite clicked`() {
        given(interactor.addProjectToFavorites(projectId)).willReturn(Completable.complete())
        given(interactor.removeProjectFromFavorites(projectId)).willReturn(Completable.complete())

        detailViewModel.favoriteClicked()

        verify(interactor).removeProjectFromFavorites(projectId)

        projectDetails.isFavorite = false
        detailViewModel.projectDetailsLiveData.value = projectDetails

        detailViewModel.favoriteClicked()

        verify(interactor).addProjectToFavorites(projectId)
        verify(interactor, times(2)).updateProject(projectId)
    }

    @Test fun `gallery item video clicked`() {
        detailViewModel.galleryClicked(mock(Activity::class.java), galleryItemVideo, 0)

        detailViewModel.playVideoLiveData.observeForever {
            assertEquals(galleryItemVideo.url, it.peekContent())
        }
    }

    @Test fun `click on vote when user have more votes that project need`() {
        val project = projectDetails.copy(fundingTarget = 10, fundingCurrent = 5)

        given(interactor.getProjectById(projectId)).willReturn(Observable.just(project))
        given(interactor.getVotes(anyBoolean())).willReturn(Single.just(BigDecimal(12345)))

        val detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.getVotes(true)
        detailViewModel.voteClicked()

        detailViewModel.showVoteProjectLiveData.observeForever {
            assertEquals(5, it.peekContent())
        }

        assertEquals(5, detailViewModel.showVoteProjectLiveData.value!!.peekContent())
    }

    @Test fun `click on vote when project need more votes than user have`() {
        val project = projectDetails.copy(fundingTarget = 100, fundingCurrent = 50)

        given(interactor.getProjectById(projectId)).willReturn(Observable.just(project))
        given(interactor.getVotes(anyBoolean())).willReturn(Single.just(BigDecimal(20)))

        val detailViewModel = DetailViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.getVotes(true)
        detailViewModel.voteClicked()

        detailViewModel.showVoteUserLiveData.observeForever {
            assertEquals(20, it.peekContent())
        }

        assertEquals(20, detailViewModel.showVoteUserLiveData.value!!.peekContent())
    }

    @Test fun `back button clicked`() {
        detailViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test fun `votes clicked`() {
        detailViewModel.votesClicked()

        verify(router).showVotesHistory()
    }

    @Test fun `discuss clicked`() {
        detailViewModel.discussionLinkClicked()

        detailViewModel.showBrowserLiveData.observeForever {
            assertEquals(projectDetails.discussionLink!!.link, it.peekContent())
        }

        assertEquals(projectDetails.discussionLink!!.link, detailViewModel.showBrowserLiveData.value!!.peekContent())
    }
}