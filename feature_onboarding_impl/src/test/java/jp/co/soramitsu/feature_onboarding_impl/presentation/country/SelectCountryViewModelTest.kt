package jp.co.soramitsu.feature_onboarding_impl.presentation.country

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import io.reactivex.Single
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter
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
class SelectCountryViewModelTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var interactor: OnboardingInteractor
    @Mock private lateinit var router: OnboardingRouter
    @Mock private lateinit var preloader: WithPreloader

    private lateinit var selectCountryViewModel: SelectCountryViewModel

    private val countryVm = Country("1", "Russia", "+7")
    private val countryVm1 = Country("1", "Romania", "+7")
    private val countryVm2 = Country("1", "United States of Sora", "+7")

    @Before fun setUp() {
        given(interactor.getCountries()).willReturn(Single.just(mutableListOf(countryVm, countryVm1, countryVm2)))

        selectCountryViewModel = SelectCountryViewModel(interactor, router, preloader)
    }

    @Test fun `back button clicked`() {
        selectCountryViewModel.backButtonClick()

        verify(router).onBackButtonPressed()
    }

    @Test fun `country selected`() {
        selectCountryViewModel.countrySelected(countryVm)

        verify(router).showPhoneNumber(countryVm.id, countryVm.phoneCode)
    }

    @Test fun `search countries called`() {
        selectCountryViewModel.getCountries()

        selectCountryViewModel.searchCountries("United")

        assertEquals(selectCountryViewModel.countriesLiveData.value, mutableListOf(countryVm2))
    }

    @Test fun `get countries called`() {
        selectCountryViewModel.getCountries()

        assertEquals(selectCountryViewModel.countriesListVisibilitytLiveData.value, true)
        assertEquals(selectCountryViewModel.emptyPlaceholderVisibilitytLiveData.value, false)
        assertEquals(selectCountryViewModel.countriesLiveData.value, mutableListOf(countryVm, countryVm1, countryVm2))
    }

    @Test fun `empty get countries called`() {
        given(interactor.getCountries()).willReturn(Single.just(mutableListOf()))

        selectCountryViewModel.getCountries()

        assertEquals(selectCountryViewModel.countriesListVisibilitytLiveData.value, false)
        assertEquals(selectCountryViewModel.emptyPlaceholderVisibilitytLiveData.value, true)
        assertEquals(selectCountryViewModel.countriesLiveData.value, mutableListOf<Country>())
    }
}