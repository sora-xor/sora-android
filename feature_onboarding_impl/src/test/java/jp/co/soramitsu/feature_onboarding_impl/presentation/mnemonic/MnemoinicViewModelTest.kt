package jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import jp.co.soramitsu.feature_onboarding_impl.presentation.mnemonic.model.MnemonicWord
import jp.co.soramitsu.test_shared.MainCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.BDDMockito.given
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class)
class MnemoinicViewModelTest {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    @Mock
    private lateinit var interactor: OnboardingInteractor

    @Mock
    private lateinit var router: OnboardingRouter

    @Mock
    private lateinit var preloader: WithPreloader

    private lateinit var mnemonicViewModel: MnemonicViewModel

    @Before
    fun setUp() {
        mnemonicViewModel = MnemonicViewModel(interactor, router, preloader)
    }

    @Test
    fun `btn next clicked`() {
        mnemonicViewModel.btnNextClicked()
        verify(router).showMnemonicConfirmation()
    }

    @Test
    fun `get passphrase called`() = runBlockingTest {
        val mnemonic = listOf(
            MnemonicWord(1, "mnemonic"),
            MnemonicWord(3, "zxcasd"),
            MnemonicWord(2, "qwerty"),
        )
        given(interactor.getMnemonic()).willReturn("mnemonic qwerty zxcasd")
        mnemonicViewModel.getPassphrase()
        assertEquals(mnemonicViewModel.mnemonicLiveData.value, mnemonic)
    }
}