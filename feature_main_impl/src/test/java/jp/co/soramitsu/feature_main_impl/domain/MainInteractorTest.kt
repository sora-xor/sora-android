package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeed
import jp.co.soramitsu.feature_account_api.domain.model.ActivityFeedAnnouncement
import jp.co.soramitsu.feature_account_api.domain.model.AddInvitationCase
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ReferendumRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectDetails
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class MainInteractorTest {

    @Rule
    @JvmField
    var schedulersRule = RxSchedulersRule()

    @Mock
    private lateinit var userRepository: UserRepository
    @Mock
    private lateinit var notificationRepository: NotificationRepository
    @Mock
    private lateinit var projectRepository: ProjectRepository
    @Mock
    private lateinit var didRepository: DidRepository
    @Mock
    private lateinit var informationRepository: InformationRepository
    @Mock
    private lateinit var referendumRepository: ReferendumRepository
    @Mock
    private lateinit var votesDataSource: VotesDataSource

    private lateinit var interactor: MainInteractor

    @Before
    fun setUp() {
        interactor = MainInteractor(userRepository, projectRepository, referendumRepository, votesDataSource, didRepository, informationRepository, notificationRepository)
    }

    @Test
    fun `getReputationWithLastVotes() calls project repository getLastVotesFromCache() and getUserReputation()`() {
        val lastVotes = BigDecimal(1000.0)
        val userReputation = Reputation(0, 0f, 0)
        given(projectRepository.getLastVotesFromCache()).willReturn(Single.just(lastVotes))
        given(userRepository.getUserReputation(anyBoolean())).willReturn(Single.just(userReputation))

        interactor.getReputationWithLastVotes(true)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { it.first == userReputation && it.second == lastVotes }

        verify(userRepository).getUserReputation(anyBoolean())
        verify(projectRepository).getLastVotesFromCache()
    }

    @Test
    fun `getMnemonic() function returns not empty mnemonic`() {
        val mnemonic = "test mnemonic"
        given(didRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))

        interactor.getMnemonic()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(mnemonic)

        verify(didRepository).retrieveMnemonic()
    }

    @Test
    fun `getMnemonic() function returns empty mnemonic`() {
        val mnemonic = ""
        given(didRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))

        interactor.getMnemonic()
            .test()
            .assertErrorMessage(ResponseCode.GENERAL_ERROR.toString())

        verify(didRepository).retrieveMnemonic()
    }

    @Test
    fun `getUserInfo() calls userRepository repository getUser() function`() {
        val userValues = UserValues("invitationLink", "id")
        val user = User("id", "firstName", "lastName", "phone", "status", "parent", "RU", 0, userValues)
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))

        interactor.getUserInfo(false)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(user)

        verify(userRepository).getUser(false)
    }

    @Test
    fun `getUserReputation() calls userRepository repository getUserReputation() function`() {
        val reputation = Reputation(0, 0f, 0)
        given(userRepository.getUserReputation(anyBoolean())).willReturn(Single.just(reputation))

        interactor.getUserReputation(false)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(reputation)

        verify(userRepository).getUserReputation(false)
    }

    @Test
    fun `updateProject() calls projectRepository repository updateProject() function`() {
        val projectId = "test project id"
        given(projectRepository.updateProject(anyString())).willReturn(Completable.complete())

        interactor.syncProject(projectId)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).updateProject(projectId)
    }

    @Test
    fun `getProjectById() calls projectRepository repository getProjectById() function`() {
        val projectId = "test project id"
        val projectDetails = mock(ProjectDetails::class.java)
        given(projectRepository.getProjectById(anyString())).willReturn(Observable.just(projectDetails))

        interactor.observeProject(projectId)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projectDetails)

        verify(projectRepository).getProjectById(projectId)
    }

    @Test
    fun `getAllProjects() calls projectRepository repository getAllProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.observeOpenedProjects()).willReturn(Observable.just(projects))
        given(referendumRepository.observeOpenedReferendums()).willReturn(Observable.just(emptyList()))

        interactor.observeOpenVotables()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).observeOpenedProjects()
    }

    @Test
    fun `updateAllProjects() calls projectRepository repository updateAllProjects() function`() {
        given(projectRepository.syncOpenedProjects(true)).willReturn(Single.just(3))
        given(referendumRepository.syncOpenedReferendums()).willReturn(Completable.complete())

        interactor.syncOpenVotables()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).syncOpenedProjects(true)
    }
    
    @Test
    fun `getVotedProjects() calls projectRepository repository getVotedProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.observeVotedProjects()).willReturn(Observable.just(projects))
        given(referendumRepository.observeVotedReferendums()).willReturn(Observable.just(emptyList()))

        interactor.observeVotedVotables()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).observeVotedProjects()
    }

    @Test
    fun `updateVotedProjects() calls projectRepository repository updateVotedProjects() function`() {
        given(projectRepository.syncVotedProjects(true)).willReturn(Single.just(3))
        given(referendumRepository.syncVotedReferendums()).willReturn(Completable.complete())

        interactor.syncVotedVotables()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).syncVotedProjects(true)
    }

    @Test
    fun `getFavoriteProjects() calls projectRepository repository getFavoriteProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.observeFavouriteProjects()).willReturn(Observable.just(projects))

        interactor.observeFavoriteVotables()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).observeFavouriteProjects()
    }

    @Test
    fun `updateFavoriteProjects() calls projectRepository repository updateFavoriteProjects() function`() {
        given(projectRepository.syncFavoriteProjects(true)).willReturn(Single.just(3))

        interactor.syncFavoriteVotables()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).syncFavoriteProjects(true)
    }

    @Test
    fun `getCompletedProjects() calls projectRepository repository getFinishedProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.observeFinishedProjects()).willReturn(Observable.just(projects))
        given(referendumRepository.observeFinishedReferendums()).willReturn(Observable.just(emptyList()))

        interactor.observeFinishedVotables()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).observeFinishedProjects()
    }

    @Test
    fun `updateCompletedProjects() calls projectRepository repository updateFinishedProjects() function`() {
        given(projectRepository.syncFinishedProjects(true)).willReturn(Single.just(3))
        given(referendumRepository.syncFinishedReferendums()).willReturn(Completable.complete())

        interactor.syncCompletedVotables()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).syncFinishedProjects(true)
    }

    @Test
    fun `updatePushTokenIfNeeded() calls userRepository updatePushTokenIfNeeded`() {
        given(notificationRepository.updatePushTokenIfNeeded()).willReturn(Completable.complete())

        interactor.updatePushTokenIfNeeded()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(notificationRepository).updatePushTokenIfNeeded()
    }

    @Test
    fun `addProjectToFavorites() calls projectRepository addProjectToFavorites`() {
        val projectId = "test project id"
        given(projectRepository.addProjectToFavorites(projectId)).willReturn(Completable.complete())

        interactor.addProjectToFavorites(projectId)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).addProjectToFavorites(projectId)
    }

    @Test
    fun `removeProjectFromFavorites() calls projectRepository removeProjectFromFavorites`() {
        val projectId = "test project id"
        given(projectRepository.removeProjectFromFavorites(projectId)).willReturn(Completable.complete())

        interactor.removeProjectFromFavorites(projectId)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).removeProjectFromFavorites(projectId)
    }

    @Test
    fun `saveUserInfo() calls userRepository saveUserInfo`() {
        val firstName = "FirstName"
        val lastName = "LastName"
        given(userRepository.saveUserInfo(firstName, lastName)).willReturn(Completable.complete())

        interactor.saveUserInfo(firstName, lastName)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(userRepository).saveUserInfo(firstName, lastName)
    }

    @Test
    fun `getReputationContent() calls informationRepository getReputationContent`() {
        val expectedResult = mutableListOf(InformationContainer("title", "description"))
        given(informationRepository.getReputationContent(anyBoolean())).willReturn(Single.just(expectedResult))

        interactor.getReputationContent(true)
            .test()
            .assertResult(expectedResult)

        verify(informationRepository).getReputationContent(true)
    }

    @Test
    fun `getInviteCode() calls userRepository getParentInviteCode()`() {
        val expectedResult = "parentInviteCode"
        given(userRepository.getParentInviteCode()).willReturn(Single.just(expectedResult))

        interactor.getInviteCode()
            .test()
            .assertResult(expectedResult)

        verify(userRepository).getParentInviteCode()
    }

    @Test
    fun `applyInvitationCode() calls userRepository applyInvitationCode()`() {
        given(userRepository.applyInvitationCode()).willReturn(Completable.complete())

        interactor.applyInvitationCode()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(userRepository).applyInvitationCode()
    }

    @Test
    fun `checkAddInviteCodeIsPossible() calls userRepository getUser() and map to AddInvitationCase ALREADY_APPLIED`() {
        val user = User(
            "id",
            "firstName",
            "lastName",
            "phone",
            "status",
            "parentId",
            "country",
            100,
            UserValues(
                "link",
                "id"
            )
        )
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))

        interactor.checkAddInviteCodeIsPossible(0)
            .test()
            .assertResult(AddInvitationCase.ALREADY_APPLIED)

        verify(userRepository).getUser(true)
    }

    @Test
    fun `checkAddInviteCodeIsPossible() calls userRepository getUser() and map to AddInvitationCase TIME_IS_UP`() {
        val user = User(
            "id",
            "firstName",
            "lastName",
            "phone",
            "status",
            "",
            "country",
            100,
            UserValues(
                "link",
                "id"
            )
        )
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))

        interactor.checkAddInviteCodeIsPossible(200)
            .test()
            .assertResult(AddInvitationCase.TIME_IS_UP)

        verify(userRepository).getUser(true)
    }

    @Test
    fun `checkAddInviteCodeIsPossible() calls userRepository getUser() and map to AddInvitationCase AVAILABLE`() {
        val user = User(
            "id",
            "firstName",
            "lastName",
            "phone",
            "status",
            "",
            "country",
            200,
            UserValues(
                "link",
                "id"
            )
        )
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))

        interactor.checkAddInviteCodeIsPossible(100)
            .test()
            .assertResult(AddInvitationCase.AVAILABLE)

        verify(userRepository).getUser(true)
    }

    @Test
    fun `getAppVersion() calls userRepository getAppVersion()`() {
        val expectedResult = "version"
        given(userRepository.getAppVersion()).willReturn(Single.just(expectedResult))

        interactor.getAppVersion()
            .test()
            .assertResult(expectedResult)

        verify(userRepository).getAppVersion()
    }

    @Test
    fun `getActivityFeedWithAnnouncement() calls userRepository getActivityFeed() and getAnnouncements()`() {
        val listActivityFeed = mutableListOf(ActivityFeed(
            "type",
            "title",
            "description",
            "voteString",
            Date(1000),
            R.drawable.icon_activity_heart_shape,
            R.drawable.heart_shape
        ))
        val listActivityFeedAnnouncement = mutableListOf(ActivityFeedAnnouncement(
            "message",
            "publicationDate"
        ))
        given(userRepository.getActivityFeed(anyInt(), anyInt(), anyBoolean())).willReturn(Single.just(listActivityFeed))
        given(userRepository.getAnnouncements(anyBoolean())).willReturn(Single.just(listActivityFeedAnnouncement))

        val expectedResult = mutableListOf<Any>()
        expectedResult.addAll(listActivityFeedAnnouncement)
        expectedResult.addAll(listActivityFeed)

        interactor.getActivityFeedWithAnnouncement(true, 3, 3)
            .test()
            .assertResult(expectedResult)

        verify(userRepository).getActivityFeed(3, 3, true)
        verify(userRepository).getAnnouncements(true)
    }

    @Test
    fun `getActivityFeed() calls userRepository getActivityFeed()`() {
        val expectedResult = mutableListOf(ActivityFeed(
            "type",
            "title",
            "description",
            "voteString",
            Date(1000),
            R.drawable.icon_activity_heart_shape,
            R.drawable.heart_shape
        ))
        given(userRepository.getActivityFeed(anyInt(), anyInt(), anyBoolean())).willReturn(Single.just(expectedResult))

        interactor.getActivityFeed(true, 3, 3)
            .test()
            .assertResult(expectedResult)

        verify(userRepository).getActivityFeed(3, 3, true)
    }

    @Test
    fun `getVotesHistory() calls projectRepository getVotesHistory()`() {
        val expectedResult = mutableListOf(VotesHistory(
            "message",
            "1234",
            BigDecimal.ZERO
        ))
        given(projectRepository.getVotesHistory(anyInt(), anyInt(), anyBoolean())).willReturn(Single.just(expectedResult))

        interactor.getVotesHistory(true, 3, 3)
            .test()
            .assertResult(expectedResult)

        verify(projectRepository).getVotesHistory(3, 3, true)
    }

    @Test
    fun `getVotes() calls votesDataSource getVotes()`() {
        given(votesDataSource.syncVotes()).willReturn(Completable.complete())

        interactor.syncVotes()
            .test()
            .assertComplete()

        verify(votesDataSource).syncVotes()
    }

    @Test
    fun `voteForProject() calls projectRepository voteForProject()`() {
        val projectId = "projectId"
        val votesCount = 100.toLong()
        given(projectRepository.voteForProject(projectId, votesCount)).willReturn(Completable.complete())

        interactor.voteForProject(projectId, votesCount)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).voteForProject(projectId, votesCount)
    }
}