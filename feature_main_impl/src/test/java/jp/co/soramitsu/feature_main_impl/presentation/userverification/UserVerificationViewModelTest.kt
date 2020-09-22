package jp.co.soramitsu.feature_main_impl.presentation.userverification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.feature_account_api.domain.model.AppVersion
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.PinCodeInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import junit.framework.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class UserVerificationViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var interactor: PinCodeInteractor

    private lateinit var userVerificationViewModel: UserVerificationViewModel

    @Before
    fun setUp() {
        userVerificationViewModel = UserVerificationViewModel(router, interactor)
    }

    @Test
    fun `check user called with version supported`() {
        given(interactor.runCheckUserFlow()).willReturn(Single.just(AppVersion(true, "")))

        userVerificationViewModel.checkUser()

        assertEquals(userVerificationViewModel.checkInviteLiveData.value?.peekContent(), Unit)
        verify(router).popBackStack()
    }

    @Test
    fun `check user called with version not supported`() {
        val downloadUrlString = "https://test.url/"
        given(interactor.runCheckUserFlow()).willReturn(Single.just(AppVersion(false, downloadUrlString)))

        userVerificationViewModel.checkUser()

        verify(router).showUnsupportedScreen(downloadUrlString)
    }
}