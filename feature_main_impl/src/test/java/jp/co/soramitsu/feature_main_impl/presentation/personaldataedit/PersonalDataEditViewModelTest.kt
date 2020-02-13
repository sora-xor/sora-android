package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_account_api.domain.model.UserValues
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.test_shared.RxSchedulersRule
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

@RunWith(MockitoJUnitRunner::class)
class PersonalDataEditViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val schedulersRule = RxSchedulersRule()

    @Mock lateinit var interactor: MainInteractor
    @Mock lateinit var router: MainRouter
    @Mock lateinit var progress: WithProgress

    private lateinit var personalDataEditViewModel: PersonalDataEditViewModel

    private val userValues = UserValues("", "")

    private val user = User(
        "id",
        "firstName",
        "lastName",
        "+123456",
        "registered",
        "parentId",
        "Ru",
        0,
        userValues
    )

    @Before fun setUp() {
        personalDataEditViewModel = PersonalDataEditViewModel(interactor, router, progress)
    }

    @Test fun `back button pressed`() {
        personalDataEditViewModel.backPressed()

        verify(router).popBackStack()
    }

    @Test fun `get user data called`() {
        given(interactor.getUserInfo(true)).willReturn(Single.just(user))

        personalDataEditViewModel.getUserData(true)

        personalDataEditViewModel.userLiveData.observeForever {
            assertEquals(user, it)
        }
    }

    @Test fun `save data called with empty first name`() {
        personalDataEditViewModel.saveData("", user.lastName)

        assertEquals(Unit, personalDataEditViewModel.emptyFirstNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with empty last name`() {
        personalDataEditViewModel.saveData(user.lastName, "")

        assertEquals(Unit, personalDataEditViewModel.emptyLastNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with first name starting on hyphen`() {
        personalDataEditViewModel.saveData("first-", user.lastName)

        assertEquals(Unit, personalDataEditViewModel.incorrectFirstNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with first name ending on hyphen`() {
        personalDataEditViewModel.saveData("-first", user.lastName)

        assertEquals(Unit, personalDataEditViewModel.incorrectFirstNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with first name with double hyphen`() {
        personalDataEditViewModel.saveData("first--aa", user.lastName)

        assertEquals(Unit, personalDataEditViewModel.incorrectFirstNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with last name starting on hyphen`() {
        personalDataEditViewModel.saveData(user.firstName, "-last")

        assertEquals(Unit, personalDataEditViewModel.incorrectLastNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with last name ending on hyphen`() {
        personalDataEditViewModel.saveData(user.firstName, "last-")

        assertEquals(Unit, personalDataEditViewModel.incorrectLastNameLiveData.value?.peekContent())
    }

    @Test fun `save data called with last name with double hyphen`() {
        personalDataEditViewModel.saveData(user.firstName, "l--ast")

        assertEquals(Unit, personalDataEditViewModel.incorrectLastNameLiveData.value?.peekContent())
    }
}