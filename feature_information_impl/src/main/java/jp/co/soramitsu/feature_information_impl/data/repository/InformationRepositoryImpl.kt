package jp.co.soramitsu.feature_information_impl.data.repository

import io.reactivex.Completable
import io.reactivex.Single
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationDatasource
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_information_api.domain.model.InformationContainer
import jp.co.soramitsu.feature_information_impl.data.mappers.mapInformationRemoteToCurrenciesList
import jp.co.soramitsu.feature_information_impl.data.mappers.mapInformationRemoteToInformationList
import jp.co.soramitsu.feature_information_impl.data.network.InformationNetworkApi
import javax.inject.Inject

class InformationRepositoryImpl @Inject constructor(
    private val datasource: InformationDatasource,
    private val api: InformationNetworkApi
) : InformationRepository {

    companion object {
        private const val HELP = "help"
        private const val REPUTATION = "reputation"
        private const val CURRENCY = "currency"
    }

    override fun getHelpContent(updateCached: Boolean): Single<List<InformationContainer>> {
        return if (updateCached) {
            getInformationRemote()
        } else {
            val informationLocal = datasource.retrieveInformationContent()
            if (informationLocal == null) {
                getInformationRemote()
            } else {
                Single.just(informationLocal)
            }
        }
    }

    private fun getInformationRemote(): Single<List<InformationContainer>> {
        return api.getInformation(HELP)
            .map { mapInformationRemoteToInformationList(it.information) }
            .doOnSuccess { datasource.saveInformationContent(it) }
    }

    override fun getReputationContent(updateCached: Boolean): Single<List<InformationContainer>> {
        return if (updateCached) {
            getReputationRemote()
        } else {
            val reputation = datasource.retrieveReputation()
            if (reputation == null) {
                getReputationRemote()
            } else {
                Single.just(datasource.retrieveReputation()!!)
            }
        }
    }

    private fun getReputationRemote(): Single<List<InformationContainer>> {
        return api.getInformation(REPUTATION)
            .map { mapInformationRemoteToInformationList(it.information) }
            .doOnSuccess { datasource.saveReputation(it) }
    }

    override fun getCurrencies(updateCached: Boolean): Single<List<Currency>> {
        return Single.just(datasource.retrieveSelectedCurrency())
            .flatMap { selectedCurrency ->
                if (updateCached) {
                    getCurrenciesRemote(selectedCurrency)
                } else {
                    val currencyLocal = datasource.retrieveCurrencies(selectedCurrency)
                    if (currencyLocal == null) {
                        getCurrenciesRemote(selectedCurrency)
                    } else {
                        Single.just(currencyLocal)
                    }
                }
            }
    }

    private fun getCurrenciesRemote(selectedCurrency: Currency): Single<List<Currency>> {
        return api.getInformation(CURRENCY)
            .map { mapInformationRemoteToCurrenciesList(it.information, selectedCurrency.code) }
            .doOnSuccess { datasource.saveCurrencies(it) }
    }

    override fun saveSelectedCurrency(currency: Currency): Completable {
        return Completable.fromAction { datasource.saveSelectedCurrency(currency) }
    }

    override fun getSelectedCurrency(): Single<Currency> {
        return Single.just(datasource.retrieveSelectedCurrency())
    }
}