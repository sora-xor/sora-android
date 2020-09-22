package jp.co.soramitsu.feature_information_impl.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_information_impl.data.network.InformationNetworkApi
import jp.co.soramitsu.feature_information_impl.data.repository.datasource.PrefsInformationDatasource
import jp.co.soramitsu.test_shared.RxSchedulersRule
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
class InformationRepositoryTest {

    @Rule @JvmField val rule: TestRule = InstantTaskExecutorRule()
    @Rule @JvmField val rxSchedulerRule = RxSchedulersRule()

    @Mock private lateinit var informationDatasource: PrefsInformationDatasource
    @Mock private lateinit var informationNetworkApi: InformationNetworkApi

    private lateinit var informationRepository: InformationRepositoryImpl

    @Before fun setUp() {
        informationRepository = InformationRepositoryImpl(informationDatasource, informationNetworkApi)
    }

    @Test fun `get help content called`() {
        val list = mutableListOf(InformationContainer("title", "description"))
        given(informationDatasource.retrieveInformationContent()).willReturn(list)

        informationRepository.getHelpContent(false)
            .test()
            .assertResult(list)
    }

    @Test fun `get repuation content called`() {
        val list = mutableListOf(InformationContainer("title", "description"))
        given(informationDatasource.retrieveReputation()).willReturn(list)

        informationRepository.getReputationContent(false)
            .test()
            .assertResult(list)
    }

    @Test fun `get currencies called`() {
        val currencies = mutableListOf(Currency("RUB", "P", "RUB", 2f, false), Currency("USD", "$", "USD", 2f, false))
        given(informationDatasource.retrieveSelectedCurrency()).willReturn(currencies[0])
        given(informationDatasource.retrieveCurrencies(currencies[0])).willReturn(currencies)

        informationRepository.getCurrencies(false)
            .test()
            .assertResult(currencies)
    }

    @Test fun `save selected currency called`() {
        val currency = Currency("RUB", "P", "RUB", 2f, false)

        informationRepository.saveSelectedCurrency(currency)
            .test()
            .assertNoErrors()
            .assertComplete()

        verify(informationDatasource).saveSelectedCurrency(currency)
    }

    @Test fun `get selected currency called`() {
        val currency = Currency("RUB", "P", "RUB", 2f, false)
        given(informationDatasource.retrieveSelectedCurrency()).willReturn(currency)

        informationRepository.getSelectedCurrency()
            .test()
            .assertNoErrors()
            .assertResult(currency)
    }
}
