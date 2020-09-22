package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_account_api.domain.model.Language
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.R
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
import jp.co.soramitsu.test_shared.anyNonNull
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
import java.math.BigDecimal

@RunWith(MockitoJUnitRunner::class)
class ProfileViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: MainInteractor
    @Mock private lateinit var router: MainRouter
    @Mock private lateinit var numbersFormatter: NumbersFormatter
    @Mock private lateinit var resourceManager: ResourceManager

    private lateinit var profileViewModel: ProfileViewModel

    private val userValues = UserValues("", "")

    private val user = User(
        "1",
        "Test",
        "Testy",
        "+1234567890",
        "status",
        "2",
        "ru",
        0,
        userValues
    )

    private val votesCount = BigDecimal.ONE

    private val reputation = Reputation(1, 1f, 2)
    private val language = Language("ru", R.string.common_russian, R.string.common_russian_native)
    private val russian = "Русский"

    @Before
    fun setUp() {
        given(interactor.getUserInfo(true)).willReturn(Single.just(user))
        given(interactor.observeVotes()).willReturn(Observable.just(BigDecimal.TEN))
        given(interactor.syncVotes()).willReturn(Completable.complete())
        given(interactor.getUserReputation(true)).willReturn(Single.just(reputation))
        given(interactor.getSelectedLanguage()).willReturn(Single.just(language))
        given(numbersFormatter.formatInteger(anyNonNull())).willReturn(votesCount.toString())
        given(resourceManager.getString(language.nativeDisplayNameResource)).willReturn(russian)

        profileViewModel = ProfileViewModel(interactor, router, numbersFormatter, resourceManager)
    }

    @Test
    fun `load user data`() {
        profileViewModel.loadUserData(true)

        profileViewModel.userLiveData.observeForever {
            assertEquals(user, it)
        }

        profileViewModel.votesLiveData.observeForever {
            assertEquals(votesCount.toString(), it)
        }

        profileViewModel.userReputationLiveData.observeForever {
            assertEquals(reputation, it)
        }

        profileViewModel.selectedLanguageLiveData.observeForever {
            assertEquals(russian, it)
        }
    }

    @Test
    fun `help card clicked`() {
        profileViewModel.btnHelpClicked()

        verify(router).showFaq()
    }

    @Test
    fun `passphrase item clicked`() {
        profileViewModel.onPassphraseClick()

        verify(router).showPin(PinCodeAction.OPEN_PASSPHRASE)
    }

    @Test
    fun `edit profile item clicked`() {
        profileViewModel.onEditProfileClicked()

        verify(router).showPersonalDataEdition()
    }

    @Test
    fun `about item clicked`() {
        profileViewModel.profileAboutClicked()

        verify(router).showAbout()
    }

    @Test
    fun `reputation item clicked`() {
        profileViewModel.onReputationClick()

        verify(router).showReputation()
    }

    @Test
    fun `votes item clicked`() {
        profileViewModel.onVotesClick()

        verify(router).showVotesHistory()
    }
}