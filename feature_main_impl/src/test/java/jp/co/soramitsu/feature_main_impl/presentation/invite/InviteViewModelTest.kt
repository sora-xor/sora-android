package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.util.TimerWrapper
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
@Ignore("temp. will be enabled after invitation is done")
class InviteViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var interactor: InvitationInteractor

    @Mock
    private lateinit var router: MainRouter

    @Mock
    private lateinit var progress: WithProgress

    @Mock
    private lateinit var invitationHandler: InvitationHandler

    @Mock
    private lateinit var timer: TimerWrapper

    private lateinit var inviteViewModel: InviteViewModel

    @Before
    fun setUp() {
        given(invitationHandler.observeInvitationApplies()).willReturn(flow { emit("") })

        inviteViewModel = InviteViewModel(
            interactor,
            router,
            progress,
        )
    }

    @Test
    fun `send invitation`() = runBlockingTest {
        val inviteLink = "test invite link"

        given(interactor.getInviteLink()).willReturn(inviteLink)
        given(timer.start(anyLong(), anyLong())).willReturn(flow { emit(0L) })

        inviteViewModel.sendInviteClick()

        verify(interactor).getInviteLink()

        inviteViewModel.shareCodeLiveData.observeForever {
            assertEquals(inviteLink, it)
        }

        assertEquals(inviteLink, inviteViewModel.shareCodeLiveData.value)
    }
}
