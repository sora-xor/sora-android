package jp.co.soramitsu.feature_main_impl.domain

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.Invitations
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class InvitationInteractorTest {

    @Rule @JvmField var schedulersRule = RxSchedulersRule()

    @Mock private lateinit var userRepository: UserRepository

    private lateinit var interactor: InvitationInteractor
    private val invitationsLeft = 1

    private val parentUser = InvitedUser("test firstName", "test lastName")

    private val invitedUsers = mutableListOf<InvitedUser>().apply {
        add(InvitedUser("firstName", "lastName"))
    }

    private val invitations = Invitations(invitedUsers, parentUser)

    private val userValues = UserValues("invitationsCode", "userId")

    private val user = User(
        "id",
        "firsrtName",
        "lastName",
        "phoneNumber",
        "status",
        "parentId",
        "ru",
        0,
        userValues
    )

    @Before fun setUp() {
        interactor = InvitationInteractor(userRepository)
    }

    @Test fun `getUserInviteInfo() calls getUser() and getInvitedUsers() from user repository`() {
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))
        given(userRepository.getInvitedUsers(anyBoolean())).willReturn(Single.just(invitations))

        interactor.getUserInviteInfo(true)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { it.first == user && it.second == invitations }

        verify(userRepository).getUser(anyBoolean())
        verify(userRepository).getInvitedUsers(anyBoolean())
        verifyNoMoreInteractions(userRepository)
    }

    @Test fun `getInviteLink() calls getInvitationLink from userRepository`() {
        val invitationLink = "test invite link"
        given(userRepository.getInvitationLink()).willReturn(Single.just(invitationLink))

        interactor.getInviteLink()
            .test()
            .assertResult(invitationLink)

        verify(userRepository).getInvitationLink()
        verifyNoMoreInteractions(userRepository)
    }

    @Test fun `enterInviteCode() calls enterInviteCode and then getInvitedUsers() from user repository`() {
        val inviteCode = "test invite code"
        given(userRepository.enterInviteCode(anyString())).willReturn(Completable.complete())
        given(userRepository.getInvitedUsers(anyBoolean())).willReturn(Single.just(invitations))

        interactor.enterInviteCode(inviteCode)
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { it == invitations }

        verify(userRepository).enterInviteCode(inviteCode)
        verify(userRepository).getInvitedUsers(anyBoolean())
        verifyNoMoreInteractions(userRepository)
    }

    @Test fun `updateInvitationInfo() calls getUser() and getInvitedUsers() from user repository and returns invitations`() {
        given(userRepository.getUser(anyBoolean())).willReturn(Single.just(user))
        given(userRepository.getInvitedUsers(anyBoolean())).willReturn(Single.just(invitations))

        interactor.updateInvitationInfo()
            .test()
            .assertNoErrors()
            .assertComplete()
            .assertValue { it == invitations }

        verify(userRepository).getUser(anyBoolean())
        verify(userRepository).getInvitedUsers(anyBoolean())
        verifyNoMoreInteractions(userRepository)
    }
}