package jp.co.soramitsu.sora.splash.domain

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_account_api.domain.model.OnboardingState
import jp.co.soramitsu.test_shared.RxSchedulersRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class SplashInteractorTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var userRepository: UserRepository
    @Mock private lateinit var didRepository: DidRepository

    private lateinit var splashInteractor: SplashInteractor

    @Before fun setUp() {
        splashInteractor = SplashInteractor(userRepository, didRepository)
    }

    @Test fun `getRegistrationState calls userRepository getRegistrationState`() {
        splashInteractor.getRegistrationState()

        verify(userRepository).getRegistrationState()
    }

    @Test fun `restoreAuth calls didRepository restoreAuth`() {
        splashInteractor.restoreAuth()

        verify(didRepository).restoreAuth()
    }

    @Test fun `saveRegistrationState calls userRepository saveRegistrationState`() {
        val state = OnboardingState.INITIAL

        splashInteractor.saveRegistrationState(state)

        verify(userRepository).saveRegistrationState(state)
    }

    @Test fun `saveInviteCode calls userRepository saveInviteCode`() {
        val inviteCode = "inviteCode"

        splashInteractor.saveInviteCode(inviteCode)

        verify(userRepository).saveParentInviteCode(inviteCode)
    }
}