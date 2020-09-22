package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.SingleTransformer
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_account_api.domain.model.Invitations
import jp.co.soramitsu.feature_account_api.domain.model.InvitedUser
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class InviteViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val schedulersRule = RxSchedulersRule()

    @Mock private lateinit var interactor: InvitationInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var progress: WithProgress
    @Mock private lateinit var deviceParamsProvider: DeviceParamsProvider
    @Mock private lateinit var invitationHandler: InvitationHandler
    @Mock private lateinit var timer: TimerWrapper
    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var inviteViewModel: InviteViewModel

    private val invitedUsers = mutableListOf<InvitedUser>().apply {
        add(InvitedUser("firstName", "lastName"))
    }

    @Before fun setUp() {
        given(progress.progressCompose<Any>()).willReturn(SingleTransformer { upstream -> upstream })
        given(invitationHandler.observeInvitationApplies()).willReturn(Observable.just(""))
        given(interactor.updateInvitationInfo()).willReturn(Single.just(Invitations(emptyList(), null)))

        val invitations = Invitations(invitedUsers, null)

        val userValues = UserValues("invitationsCode", "userId")

        val user = User(
            "id",
            "firsrtName",
            "lastName",
            "phoneNumber",
            "status",
            "",
            "ru",
            20,
            userValues
        )

        val userInviteInfo = Pair(user, invitations)

        given(interactor.getUserInviteInfo(anyBoolean())).willReturn(Single.just(userInviteInfo))

        inviteViewModel = InviteViewModel(interactor, router, progress, deviceParamsProvider, invitationHandler, timer, resourceManager)
    }

    @Test fun `load user invite info for user with parent`() {
        given(deviceParamsProvider.getCurrentTimeMillis()).willReturn(30)

        inviteViewModel.loadUserInviteInfo(true)

        inviteViewModel.hideSwipeRefreshEventLiveData.observeForever {
            assertNotNull(it)
        }

        inviteViewModel.invitedUsersLiveData.observeForever {
            assertEquals(invitedUsers, it)
        }

        inviteViewModel.enterInviteCodeButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }

        assertNotNull(inviteViewModel.hideSwipeRefreshEventLiveData.value!!)
        assertFalse(inviteViewModel.enterInviteCodeButtonVisibilityLiveData.value!!)
        assertEquals(invitedUsers, inviteViewModel.invitedUsersLiveData.value)

        verify(invitationHandler).observeInvitationApplies()
        verify(interactor).updateInvitationInfo()
        verify(interactor).getUserInviteInfo(anyBoolean())
        verifyNoMoreInteractions(interactor, invitationHandler)
        verifyZeroInteractions(router, progress)
    }

    @Test fun `load user invite info for user without parent`() {
        given(deviceParamsProvider.getCurrentTimeMillis()).willReturn(10)
        given(timer.start(anyLong(), anyLong())).willReturn(Observable.error(Throwable()))

        inviteViewModel.loadUserInviteInfo(true)

        inviteViewModel.hideSwipeRefreshEventLiveData.observeForever {
            assertNotNull(it)
        }

        inviteViewModel.invitedUsersLiveData.observeForever {
            assertEquals(invitedUsers, it)
        }

        inviteViewModel.enterInviteCodeButtonVisibilityLiveData.observeForever {
            assertTrue(it)
        }

        assertNotNull(inviteViewModel.hideSwipeRefreshEventLiveData.value!!)
        assertTrue(inviteViewModel.enterInviteCodeButtonVisibilityLiveData.value!!)
        assertEquals(invitedUsers, inviteViewModel.invitedUsersLiveData.value)

        verify(invitationHandler).observeInvitationApplies()
        verify(interactor).updateInvitationInfo()
        verify(interactor).getUserInviteInfo(anyBoolean())
        verifyNoMoreInteractions(interactor, invitationHandler)
        verifyZeroInteractions(router, progress)
    }

    @Test fun `send invitation`() {
        val inviteLink = "test invite link"

        given(interactor.getInviteLink()).willReturn(Single.just(inviteLink))
        given(timer.start(anyLong(), anyLong())).willReturn(Observable.just(0))

        inviteViewModel.loadUserInviteInfo(true)
        inviteViewModel.sendInviteClick()

        verify(interactor).getInviteLink()

        inviteViewModel.shareCodeLiveData.observeForever {
            assertEquals(inviteLink, it)
        }

        assertEquals(inviteLink, inviteViewModel.shareCodeLiveData.value)
    }

    @Test fun `btnHelpClicked() calls router showFaq()`() {
        inviteViewModel.btnHelpClicked()

        verify(router).showFaq()
    }

    @Test fun `addInvitationClicked() creates invitation dialog event`() {
        inviteViewModel.addInvitationClicked()

        inviteViewModel.showInvitationDialogLiveData.observeForever {
            assertNotNull(it)
        }

        assertNotNull(inviteViewModel.showInvitationDialogLiveData.value)
    }

    @Test fun `invitationCodeEntered() calls enterInviteCode()`() {
        val inviteCode = "test invite code"

        val invitedUsers = mutableListOf<InvitedUser>().apply {
            add(InvitedUser("firstName", "lastName"))
        }

        val parentUser = InvitedUser("", "")

        val invitations = Invitations(invitedUsers, parentUser)

        given(interactor.enterInviteCode(anyString())).willReturn(Single.just(invitations))

        inviteViewModel.invitationCodeEntered(inviteCode)

        inviteViewModel.parentUserLiveData.observeForever {
            assertEquals(parentUser, it)
        }

        inviteViewModel.enterInviteCodeButtonVisibilityLiveData.observeForever {
            assertFalse(it)
        }

        inviteViewModel.enteredCodeAppliedLiveData.observeForever {
            assertNotNull(it)
        }

        assertEquals(parentUser, inviteViewModel.parentUserLiveData.value)
        assertFalse(inviteViewModel.enterInviteCodeButtonVisibilityLiveData.value!!)
        assertNotNull(inviteViewModel.enteredCodeAppliedLiveData.value)

        verify(interactor).enterInviteCode(inviteCode)
    }
}