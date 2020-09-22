package jp.co.soramitsu.feature_main_impl.presentation.detail

import android.app.Activity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.detail.project.DetailProjectViewModel
import jp.co.soramitsu.feature_votable_api.domain.model.project.DiscussionLink
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItem
import jp.co.soramitsu.feature_votable_api.domain.model.project.GalleryItemType
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectDetails
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectStatus
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.BDDMockito.anyInt
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
class DetailProjectViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()
    @Rule
    @JvmField
    val rxSchedulerRule = RxSchedulersRule()

    @Mock
    private lateinit var interactor: MainInteractor
    @Mock
    private lateinit var router: MainRouter
    @Mock
    private lateinit var preloader: WithPreloader
    @Mock
    private lateinit var resourceManager: ResourceManager

    @Mock
    private lateinit var observer: Observer<Any>

    private lateinit var detailProjectViewModel: DetailProjectViewModel

    private val numbersFormatter = NumbersFormatter()

    private val projectId = "projectId"

    private val galleryItemImage = GalleryItem(GalleryItemType.IMAGE, "http://image", "http://imagepreview", 0)
    private val galleryItemVideo = GalleryItem(GalleryItemType.VIDEO, "http://video", "http://videopreview", 10)

    private val gallery = mutableListOf(galleryItemImage, galleryItemVideo)

    private val projectDetails = ProjectDetails(
        projectId,
        URL("http://imageLink"),
        URL("http://projectLink"),
        "name",
        "email",
        "description",
        "detailedDescription",
        Date(Date().time + 1000),
        100.0,
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

    private val votesSubject = BehaviorSubject.createDefault(BigDecimal.TEN)
    private val projectsSubject = BehaviorSubject.createDefault(projectDetails)

    @Before
    fun setUp() {
        given(interactor.observeProject(projectId)).willReturn(projectsSubject)
        given(interactor.syncProject(projectId)).willReturn(Completable.complete())
        given(interactor.observeVotes()).willReturn(votesSubject)
        given(interactor.syncVotes()).willReturn(Completable.complete())


        detailProjectViewModel = DetailProjectViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailProjectViewModel.votesFormattedLiveData.observeForever(observer)
    }

    @After
    fun tearDown() {
        detailProjectViewModel.votesFormattedLiveData.removeObserver(observer)
    }

    @Test
    fun `correct project`() {
        detailProjectViewModel.projectDetailsLiveData.observeForever {
            assertEquals(projectDetails, it)
        }

        assertEquals(projectDetails, detailProjectViewModel.projectDetailsLiveData.value)
    }

    @Test
    fun `correct gallery`() {
        detailProjectViewModel.galleryLiveData.observeForever {
            assertEquals(gallery, it)
        }

        assertEquals(gallery, detailProjectViewModel.galleryLiveData.value)
    }

    @Test
    fun `description filled with detailed description`() {
        detailProjectViewModel.projectDescriptionLiveData.observeForever {
            assertEquals(projectDetails.detailedDescription, it)
        }

        assertEquals(projectDetails.detailedDescription, detailProjectViewModel.projectDescriptionLiveData.value)
    }

    @Test
    fun `description filled with short description if detailed is empty`() {
        val projectWithShortDesc = projectDetails.copy(detailedDescription = "")
        given(interactor.observeProject(projectId)).willReturn(Observable.just(projectWithShortDesc))
        val detailViewModel = DetailProjectViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.projectDescriptionLiveData.observeForever {
            assertEquals(projectWithShortDesc.description, it)
        }

        assertEquals(projectWithShortDesc.description, detailViewModel.projectDescriptionLiveData.value)
    }

    @Test
    fun `friends voted text correct`() {
        given(resourceManager.getQuantityString(R.plurals.project_friends_template, 12)).willReturn("%1\$s friends voted")

        val detailViewModel = DetailProjectViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.friendsVotedLiveData.observeForever {
            assertEquals("12 friends voted", it)
        }
        detailViewModel.votesAndFavoritesVisibility.observeForever {
            assertTrue(it)
        }

        assertEquals("12 friends voted", detailViewModel.friendsVotedLiveData.value)
        assertTrue(detailViewModel.votesAndFavoritesVisibility.value!!)
    }

    @Test
    fun `favorites count text correct`() {
        given(resourceManager.getQuantityString(R.plurals.project_details_favorites_format, 13)).willReturn("13 favorites")

        val detailViewModel = DetailProjectViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.favoritesLiveData.observeForever {
            assertEquals("13 favorites", it)
        }
        detailViewModel.votesAndFavoritesVisibility.observeForever {
            assertTrue(it)
        }

        assertEquals("13 favorites", detailViewModel.favoritesLiveData.value)
        assertTrue(detailViewModel.votesAndFavoritesVisibility.value!!)
    }

    @Test
    fun `friend votes and favorites view gone if no votes and favorites`() {
        val projectWithShortDesc = projectDetails.copy(votedFriendsCount = 0, favoriteCount = 0)
        given(interactor.observeProject(projectId)).willReturn(Observable.just(projectWithShortDesc))

        val detailViewModel = DetailProjectViewModel(interactor, preloader, router, projectId, numbersFormatter, resourceManager)

        detailViewModel.votesAndFavoritesVisibility.observeForever {
            assertFalse(it)
        }
        assertFalse(detailViewModel.votesAndFavoritesVisibility.value!!)
    }

    @Test
    fun `votes without formatting`() {
        votesSubject.onNext(BigDecimal.TEN)

        detailProjectViewModel.votesFormattedLiveData.observeForever {
            assertEquals("10", it)
        }
        assertEquals("10", detailProjectViewModel.votesFormattedLiveData.value)
    }

    @Test
    fun `votes with formatting`() {
        given(resourceManager.getString(R.string.project_votes_k_template)).willReturn("%1\$sK")

        votesSubject.onNext(BigDecimal(12345))

        detailProjectViewModel.votesFormattedLiveData.observeForever {
            assertEquals("12K", it)
        }

        assertEquals("12K", detailProjectViewModel.votesFormattedLiveData.value)
    }

    @Test
    fun `web site clicked`() {
        detailProjectViewModel.websiteClicked()

        detailProjectViewModel.showBrowserLiveData.observeForever {
            assertEquals(projectDetails.projectLink.toString(), it.peekContent())
        }

        assertEquals(projectDetails.projectLink.toString(), detailProjectViewModel.showBrowserLiveData.value!!.peekContent())
    }

    @Test
    fun `email clicked`() {
        detailProjectViewModel.emailClicked()

        detailProjectViewModel.sendEmailEvent.observeForever {
            assertEquals(projectDetails.email, it.peekContent())
        }
    }

    @Test
    fun `update project called`() {
        detailProjectViewModel.updateProject()

        verify(interactor).syncProject(projectId)
    }

    @Test
    fun `vote for project called`() {
        val votesCount = 100.toLong()
        given(interactor.voteForProject(projectId, votesCount)).willReturn(Completable.complete())

        detailProjectViewModel.voteForProject(votesCount)

        verify(interactor).voteForProject(projectId, votesCount)
        verify(interactor).syncProject(projectId)
    }

    @Test
    fun `favorite clicked`() {
        given(interactor.addProjectToFavorites(projectId)).willReturn(Completable.complete())
        given(interactor.removeProjectFromFavorites(projectId)).willReturn(Completable.complete())

        detailProjectViewModel.favoriteClicked()

        verify(interactor).removeProjectFromFavorites(projectId)

        projectDetails.isFavorite = false
        detailProjectViewModel.projectDetailsLiveData.value = projectDetails

        detailProjectViewModel.favoriteClicked()

        verify(interactor).addProjectToFavorites(projectId)
        verify(interactor, times(2)).syncProject(projectId)
    }

    @Test
    fun `gallery item video clicked`() {
        detailProjectViewModel.galleryClicked(mock(Activity::class.java), galleryItemVideo, 0)

        detailProjectViewModel.playVideoLiveData.observeForever {
            assertEquals(galleryItemVideo.url, it.peekContent())
        }
    }

    @Test
    fun `click on vote when user have more votes that project need`() {
        given(resourceManager.getString(anyInt())).willReturn("%sK")

        val project = projectDetails.copy(fundingTarget = 10, fundingCurrent = 5.0)

        projectsSubject.onNext(project)
        votesSubject.onNext(BigDecimal(12345))

        detailProjectViewModel.voteClicked()

        detailProjectViewModel.showVoteProjectLiveData.observeForever {
            assertEquals(5, it.peekContent())
        }

        assertEquals(5, detailProjectViewModel.showVoteProjectLiveData.value!!.peekContent())
    }

    @Test
    fun `click on vote when project need more votes than user have`() {
        val project = projectDetails.copy(fundingTarget = 100, fundingCurrent = 50.0)

        projectsSubject.onNext(project)
        votesSubject.onNext(BigDecimal(20))

        detailProjectViewModel.voteClicked()

        detailProjectViewModel.showVoteUserLiveData.observeForever {
            assertEquals(20, it.peekContent())
        }

        assertEquals(20, detailProjectViewModel.showVoteUserLiveData.value!!.peekContent())
    }

    @Test
    fun `back button clicked`() {
        detailProjectViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test
    fun `votes clicked`() {
        detailProjectViewModel.votesClicked()

        verify(router).showVotesHistory()
    }

    @Test
    fun `discuss clicked`() {
        detailProjectViewModel.discussionLinkClicked()

        detailProjectViewModel.showBrowserLiveData.observeForever {
            assertEquals(projectDetails.discussionLink!!.link, it.peekContent())
        }

        assertEquals(projectDetails.discussionLink!!.link, detailProjectViewModel.showBrowserLiveData.value!!.peekContent())
    }
}