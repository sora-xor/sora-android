/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.data.network.dto.StatusDto
import jp.co.soramitsu.common.data.network.response.BaseResponse
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_db.dao.ProjectDao
import jp.co.soramitsu.core_db.dao.ProjectDetailsDao
import jp.co.soramitsu.core_db.dao.VotesHistoryDao
import jp.co.soramitsu.core_db.model.DiscussionLinkLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsLocal
import jp.co.soramitsu.core_db.model.ProjectDetailsWithGalleryLocal
import jp.co.soramitsu.core_db.model.ProjectLocal
import jp.co.soramitsu.core_db.model.ProjectStatusLocal
import jp.co.soramitsu.core_db.model.VotesHistoryLocal
import jp.co.soramitsu.feature_votable_api.domain.model.project.DiscussionLink
import jp.co.soramitsu.feature_votable_api.domain.model.project.Project
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectDetails
import jp.co.soramitsu.feature_votable_api.domain.model.project.ProjectStatus
import jp.co.soramitsu.feature_votable_api.domain.model.VotesHistory
import jp.co.soramitsu.feature_votable_impl.data.network.ProjectNetworkApi
import jp.co.soramitsu.feature_votable_impl.data.network.model.DiscussionLinkRemote
import jp.co.soramitsu.feature_votable_impl.data.network.model.ProjectDetailsRemote
import jp.co.soramitsu.feature_votable_impl.data.network.model.ProjectRemote
import jp.co.soramitsu.feature_votable_impl.data.network.model.VotesHistoryRemote
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectDetailsResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetProjectVotesResponse
import jp.co.soramitsu.feature_votable_impl.data.network.response.GetVotesHistoryResponse
import jp.co.soramitsu.feature_votable_impl.data.local.PrefsVotesDataSource
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal
import java.net.URL
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class ProjectRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var projectNetworkApi: ProjectNetworkApi
    @Mock private lateinit var dataSource: PrefsVotesDataSource
    @Mock private lateinit var db: AppDatabase
    @Mock private lateinit var projectDao: ProjectDao
    @Mock private lateinit var projectDetailsDao: ProjectDetailsDao
    @Mock private lateinit var votesHistoryDao: VotesHistoryDao

    private lateinit var projectRepository: ProjectRepositoryImpl

    private val projectLocalList = mutableListOf(
        ProjectLocal(
            "id",
            URL("http://sora.ru/image"),
            URL("http://sora.ru/image"),
            "name",
            "email",
            "description",
            "detailedDesc",
            100,
            300.0,
            200,
            4,
            4,
            BigDecimal.TEN,
            true,
            true,
            ProjectStatusLocal.OPEN,
            200
        ),
        ProjectLocal(
            "id2",
            URL("http://sora.ru/image"),
            URL("http://sora.ru/image"),
            "name",
            "email",
            "description",
            "detailedDesc",
            100,
            200.0,
            300,
            4,
            4,
            BigDecimal.TEN,
            true,
            true,
            ProjectStatusLocal.OPEN,
            100
        )
    )

    private val projects = mutableListOf(
        Project(
            "id",
            URL("http://sora.ru/image"),
            URL("http://sora.ru/image"),
            "name",
            "email",
            "description",
            "detailedDesc",
            Date(100),
            300.0,
            200,
            4,
            4,
            BigDecimal.TEN,
            true,
            true,
            ProjectStatus.OPEN,
            Date(200)
        ),
        Project(
            "id2",
            URL("http://sora.ru/image"),
            URL("http://sora.ru/image"),
            "name",
            "email",
            "description",
            "detailedDesc",
            Date(100),
            200.0,
            300,
            4,
            4,
            BigDecimal.TEN,
            true,
            true,
            ProjectStatus.OPEN,
            Date(100)
        )
    )

    private val projectsRemotesList = mutableListOf(
        ProjectRemote(
            "id",
            "name",
            "email",
            "description",
            "detailedDesc",
            100.0,
            300,
            200,
            URL("http://sora.ru/image"),
            ProjectStatus.OPEN.toString(),
            URL("http://sora.ru/image"),
            true,
            true,
            4,
            4,
            BigDecimal.TEN,
            200
        ),
        ProjectRemote(
            "id2",
            "name",
            "email",
            "description",
            "detailedDesc",
            100.0,
            200,
            300,
            URL("http://sora.ru/image"),
            ProjectStatus.OPEN.toString(),
            URL("http://sora.ru/image"),
            true,
            true,
            4,
            4,
            BigDecimal.TEN,
            100
        )
    )
    private val projectDetailsLocal = ProjectDetailsLocal(
        "id2",
        URL("http://sora.ru/image"),
        URL("http://sora.ru/image"),
        "name",
        "email",
        "description",
        "detailedDesc",
        100,
        200.0,
        300,
        4,
        4,
        BigDecimal.TEN,
        true,
        true,
        ProjectStatusLocal.OPEN,
        100,
        DiscussionLinkLocal("title", "link")
    )
    private val projectDetails = ProjectDetails(
        "id2",
        URL("http://sora.ru/image"),
        URL("http://sora.ru/image"),
        "name",
        "email",
        "description",
        "detailedDesc",
        Date(100),
        200.0,
        300,
        4,
        4,
        BigDecimal.TEN,
        true,
        true,
        mutableListOf(),
        ProjectStatus.OPEN,
        Date(100),
        DiscussionLink("title", "link")
    )
    private val projectDetailsRemote = ProjectDetailsRemote(
        "id2",
        "name",
        "email",
        "description",
        "detailedDesc",
        100.0,
        200,
        300,
        URL("http://sora.ru/image"),
        ProjectStatus.OPEN.toString(),
        URL("http://sora.ru/image"),
        true,
        true,
        4,
        4,
        BigDecimal.TEN,
        mutableListOf(),
        100,
        DiscussionLinkRemote("title", "link")
    )
    private val votesHistoryLocal = mutableListOf(
        VotesHistoryLocal(
            0,
            "message",
            "100",
            BigDecimal.TEN
        )
    )
    private val votesHistory = mutableListOf(
        VotesHistory(
            "message",
            "100",
            BigDecimal.TEN
        )
    )
    private val votesHistoryRemote = mutableListOf(
        VotesHistoryRemote(
            "message",
            "100",
            BigDecimal.TEN
        )
    )

    @Before fun setUp() {
        given(db.votesHistoryDao()).willReturn(votesHistoryDao)
        given(db.projectDao()).willReturn(projectDao)
        given(db.projectDetailsDao()).willReturn(projectDetailsDao)

        projectRepository = ProjectRepositoryImpl(projectNetworkApi, dataSource, db)
    }

    @Test fun `get votes history called`() {
        given(votesHistoryDao.getVotesHistory()).willReturn(Single.just(votesHistoryLocal))

        projectRepository.getVotesHistory(3, 3, false)
            .test()
            .assertResult(votesHistory)
    }

    @Test fun `get votes history called with update cached`() {
        given(projectNetworkApi.getVotesHistory(3, 0)).willReturn(Single.just(GetVotesHistoryResponse(votesHistoryRemote, StatusDto("Ok", ""))))

        projectRepository.getVotesHistory(3, 0, true)
            .test()
            .assertResult(votesHistory)

        verify(votesHistoryDao).clearTable()
        verify(votesHistoryDao).insert(votesHistoryLocal)
    }

    @Test fun `get all projects called`() {
        given(projectDao.getProjectsByStatus(ProjectStatusLocal.OPEN)).willReturn(Observable.just(projectLocalList))

        projectRepository.observeOpenedProjects()
            .test()
            .assertResult(projects)
    }

    @Test fun `fetch remote all projects called`() {
        given(projectNetworkApi.getAllProjects()).willReturn(Single.just(GetProjectResponse(projectsRemotesList, StatusDto("Ok", ""))))

        projectRepository.syncOpenedProjects(true)
            .test()
            .assertResult(projects.size)

        verify(db).runInTransaction(anyNonNull())
    }

    @Test fun `get voted projects called`() {
        given(projectDao.getVotedProjects()).willReturn(Observable.just(projectLocalList))

        projectRepository.observeVotedProjects()
            .test()
            .assertResult(projects)
    }


    @Test fun `fetch remote voted projects called`() {
        given(projectNetworkApi.getVotedProjects()).willReturn(Single.just(GetProjectResponse(projectsRemotesList, StatusDto("Ok", ""))))

        projectRepository.syncVotedProjects(true)
            .test()
            .assertResult(projects.size)

        verify(db).runInTransaction(anyNonNull())
    }

    @Test fun `get favorite projects called`() {
        given(projectDao.getFavoriteProjects()).willReturn(Observable.just(projectLocalList))

        projectRepository.observeFavouriteProjects()
            .test()
            .assertResult(projects)
    }


    @Test fun `fetch remote favorite projects called`() {
        given(projectNetworkApi.getFavoriteProjects()).willReturn(Single.just(GetProjectResponse(projectsRemotesList, StatusDto("Ok", ""))))

        projectRepository.syncFavoriteProjects(true)
            .test()
            .assertResult(projects.size)

        verify(db).runInTransaction(anyNonNull())
    }

    @Test fun `get finished projects called`() {
        given(projectDao.getProjectsByStatuses(ProjectStatusLocal.COMPLETED, ProjectStatusLocal.FAILED)).willReturn(Observable.just(projectLocalList))

        projectRepository.observeFinishedProjects()
            .test()
            .assertResult(projects)
    }


    @Test fun `fetch remote finished projects called`() {
        given(projectNetworkApi.getFinishedProjects()).willReturn(Single.just(GetProjectResponse(projectsRemotesList, StatusDto("Ok", ""))))

        projectRepository.syncFinishedProjects(true)
            .test()
            .assertResult(projects.size)

        verify(db).runInTransaction(anyNonNull())
    }

    @Test fun `get project by id called`() {
        given(projectDetailsDao.getProjectById(projectDetails.id)).willReturn(Observable.just(ProjectDetailsWithGalleryLocal(projectDetailsLocal)))

        projectRepository.getProjectById(projectDetails.id)
            .test()
            .assertResult(projectDetails)
    }

    @Test fun `update project called`() {
        given(projectNetworkApi.getProjectDetails(projectDetails.id)).willReturn(Single.just(GetProjectDetailsResponse(projectDetailsRemote, StatusDto("Ok", ""))))

        projectRepository.updateProject(projectDetails.id)
            .test()
            .assertNoErrors()
            .assertComplete()

        db.runInTransaction(anyNonNull())
    }

    @Test fun `get last votes called`() {
        val votes = "100"
        val bigDecimalVotes = BigDecimal(votes)
        given(dataSource.retrieveLastReceivedVotes()).willReturn(votes)

        projectRepository.getLastVotesFromCache()
            .test()
            .assertResult(bigDecimalVotes)
    }

    @Test fun `get last votes called with no cache`() {
        val bigDecimalVotes = BigDecimal("-1")
        given(dataSource.retrieveLastReceivedVotes()).willReturn("")

        projectRepository.getLastVotesFromCache()
            .test()
            .assertResult(bigDecimalVotes)
    }

    @Test fun `vote for project called`() {
        val votes = 100.toLong()
        val currentVotes = 200.toLong()
        given(projectNetworkApi.voteForProject(projectLocalList.first().id, votes)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))
        given(dataSource.retrieveVotes()).willReturn(currentVotes.toString())
        given(projectDao.getProjectById(projectLocalList.first().id)).willReturn(projectLocalList.first())

        projectRepository.voteForProject(projectLocalList.first().id, votes)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(dataSource).saveVotes(votes.toString())
        verify(projectDetailsDao).addVotesToProject(projectLocalList.first().id, votes)
        verify(projectDao).addVotesToProject(projectLocalList.first().id, votes)
        verify(projectDao).updateProjectStatus(projectLocalList.first().id, ProjectStatusLocal.COMPLETED)
    }

    @Test fun `add project to favorites called`() {
        given(projectNetworkApi.toggleFavoriteProject(projectLocalList.first().id)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        projectRepository.addProjectToFavorites(projectLocalList.first().id)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(db).runInTransaction(anyNonNull())
    }

    @Test fun `remove project from favorites called`() {
        given(projectNetworkApi.toggleFavoriteProject(projectLocalList.first().id)).willReturn(Single.just(BaseResponse(StatusDto("Ok", ""))))

        projectRepository.removeProjectFromFavorites(projectLocalList.first().id)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(db).runInTransaction(anyNonNull())
    }
}
