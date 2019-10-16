/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.country

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

    val countriesLiveData = MutableLiveData<List<Country>>()
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
                    countriesLiveData.value = filterCountries(countries, searchFilter)
                }, {
                    hidePreloader()
                    logException(it)
                })
        )
    }

    fun searchCountries(filter: String) {
        searchFilter = filter
        countriesLiveData.value = filterCountries(countries, searchFilter)
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