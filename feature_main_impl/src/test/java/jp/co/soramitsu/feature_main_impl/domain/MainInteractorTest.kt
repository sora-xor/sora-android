/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.domain.ResponseCode
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_project_api.domain.model.Project
import jp.co.soramitsu.feature_project_api.domain.model.ProjectDetails
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class MainInteractorTest {

    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var projectRepository: ProjectRepository
    @Mock private lateinit var didRepository: DidRepository
    @Mock private lateinit var informationRepository: InformationRepository

    private lateinit var interactor: MainInteractor

    @Before fun setUp() {
        interactor = MainInteractor(userRepository, projectRepository, didRepository, informationRepository)
    }

    @Test fun `getReputationWithLastVotes() calls project repository getLastVotesFromCache() and getUserReputation()`() {
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

    @Test fun `getMnemonic() function returns not empty mnemonic`() {
        val mnemonic = "test mnemonic"
        given(didRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))

        interactor.getMnemonic()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(mnemonic)

        verify(didRepository).retrieveMnemonic()
    }

    @Test fun `getMnemonic() function returns empty mnemonic`() {
        val mnemonic = ""
        given(didRepository.retrieveMnemonic()).willReturn(Single.just(mnemonic))

        interactor.getMnemonic()
            .test()
            .assertErrorMessage(ResponseCode.GENERAL_ERROR.toString())

        verify(didRepository).retrieveMnemonic()
    }

    @Test fun `getUserInfo() calls userRepository repository getUser() function`() {
        val user = User("id", "firstName", "lastName", "phone", "status", "parent", "RU")
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))

        interactor.getUserInfo(false)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(user)

        verify(userRepository).getUser(false)
    }

    @Test fun `getUserReputation() calls userRepository repository getUserReputation() function`() {
        val reputation = Reputation(0, 0f, 0)
        given(userRepository.getUserReputation(anyBoolean())).willReturn(Single.just(reputation))

        interactor.getUserReputation(false)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(reputation)

        verify(userRepository).getUserReputation(false)
    }

    @Test fun `updateProject() calls projectRepository repository updateProject() function`() {
        val projectId = "test project id"
        given(projectRepository.updateProject(anyString())).willReturn(Completable.complete())

        interactor.updateProject(projectId)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).updateProject(projectId)
    }

    @Test fun `getProjectById() calls projectRepository repository getProjectById() function`() {
        val projectId = "test project id"
        val projectDetails = mock(ProjectDetails::class.java)
        given(projectRepository.getProjectById(anyString())).willReturn(Observable.just(projectDetails))

        interactor.getProjectById(projectId)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projectDetails)

        verify(projectRepository).getProjectById(projectId)
    }

    @Test fun `getAllProjects() calls projectRepository repository getAllProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.getAllProjects()).willReturn(Observable.just(projects))

        interactor.getAllProjects()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).getAllProjects()
    }

    @Test fun `updateAllProjects() calls projectRepository repository updateAllProjects() function`() {
        given(projectRepository.updateAllProjects()).willReturn(Completable.complete())

        interactor.updateAllProjects()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).updateAllProjects()
    }

    @Test fun `getVotedProjects() calls projectRepository repository getVotedProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.getVotedProjects()).willReturn(Observable.just(projects))

        interactor.getVotedProjects()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).getVotedProjects()
    }

    @Test fun `updateVotedProjects() calls projectRepository repository updateVotedProjects() function`() {
        given(projectRepository.updateVotedProjects()).willReturn(Completable.complete())

        interactor.updateVotedProjects()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).updateVotedProjects()
    }

    @Test fun `getFavoriteProjects() calls projectRepository repository getFavoriteProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.getFavoriteProjects()).willReturn(Observable.just(projects))

        interactor.getFavoriteProjects()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).getFavoriteProjects()
    }

    @Test fun `updateFavoriteProjects() calls projectRepository repository updateFavoriteProjects() function`() {
        given(projectRepository.updateFavoriteProjects()).willReturn(Completable.complete())

        interactor.updateFavoriteProjects()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).updateFavoriteProjects()
    }

    @Test fun `getCompletedProjects() calls projectRepository repository getFinishedProjects() function`() {
        val project = mock(Project::class.java)
        val projects = mutableListOf<Project>().apply { add(project) }
        given(projectRepository.getFinishedProjects()).willReturn(Observable.just(projects))

        interactor.getCompletedProjects()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue(projects)

        verify(projectRepository).getFinishedProjects()
    }

    @Test fun `updateCompletedProjects() calls projectRepository repository updateFinishedProjects() function`() {
        given(projectRepository.updateFinishedProjects()).willReturn(Completable.complete())

        interactor.updateCompletedProjects()
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(projectRepository).updateFinishedProjects()
    }
}