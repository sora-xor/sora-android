/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.country

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_account_api.domain.model.Country
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class SelectCountryViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val preloader: WithPreloader
) : BaseViewModel(), WithPreloader by preloader {

    private val _countriesLiveData = MutableLiveData<List<Country>>()
    val countriesLiveData: LiveData<List<Country>> = _countriesLiveData

    private val _countriesListVisibilitytLiveData = MutableLiveData<Boolean>()
    val countriesListVisibilitytLiveData: LiveData<Boolean> = _countriesListVisibilitytLiveData

    private val _emptyPlaceholderVisibilitytLiveData = MutableLiveData<Boolean>()
    val emptyPlaceholderVisibilitytLiveData: LiveData<Boolean> = _emptyPlaceholderVisibilitytLiveData

    private val countries = mutableListOf<Country>()
    private var searchFilter = ""

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    fun getCountries() {
        disposables.add(
            interactor.getCountries()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { if (countries.isEmpty()) showPreloader() }
                .subscribe({
                    hidePreloader()
                    countries.clear()
                    countries.addAll(it)

                    showFilteredCountries(searchFilter)
                }, {
                    hidePreloader()
                    logException(it)
                })
        )
    }

    fun searchCountries(filter: String) {
        searchFilter = filter

        showFilteredCountries(filter)
    }

    private fun showFilteredCountries(filter: String) {
        val countries = filterCountries(countries, filter)

        _countriesListVisibilitytLiveData.value = countries.isNotEmpty()
        _emptyPlaceholderVisibilitytLiveData.value = countries.isEmpty()
        _countriesLiveData.value = countries
    }

    private fun filterCountries(countries: List<Country>, filter: String): List<Country> {
        return mutableListOf<Country>().apply {
            addAll(countries.filter { it.name.toLowerCase().contains(filter.toLowerCase()) }.map { it.copy() })
        }
    }

    fun countrySelected(countryVm: Country) {
        router.showPhoneNumber(countryVm.id, countryVm.phoneCode)
    }
}