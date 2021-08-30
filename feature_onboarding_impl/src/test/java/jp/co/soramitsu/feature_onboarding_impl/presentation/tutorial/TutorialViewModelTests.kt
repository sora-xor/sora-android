package jp.co.soramitsu.feature_onboarding_impl.presentation.tutorial

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
@Ignore("no tests")
class TutorialViewModelTests {

    @Rule
    @JvmField
    val rule: TestRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var router: OnboardingRouter

    @Mock
    private lateinit var progress: WithProgress

    private lateinit var tutorialViewModel: TutorialViewModel

    @Before
    fun setUp() {
        tutorialViewModel = TutorialViewModel(router, progress)
    }
}
