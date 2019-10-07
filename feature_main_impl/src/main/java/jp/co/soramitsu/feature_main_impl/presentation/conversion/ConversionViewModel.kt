/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.conversion

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithPreloader
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_information_api.domain.model.Currency
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class ConversionViewModel(
    private val interactor: MainInteractor,
    private val preloader: WithPreloader,
    private val router: MainRouter
) : BaseViewModel(), WithPreloader by preloader {

    val currenciesLiveData = MutableLiveData<List<Currency>>()

    fun loadCurrencies(showLoading: Boolean, updateCached: Boolean) {
        disposables.add(
            interactor.getCurrencies(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { if (showLoading) showPreloader() }
                .doFinally {
                    if (!updateCached) {
                        loadCurrencies(true, true)
                    }
                    if (showLoading) hidePreloader()
                }
                .subscribe({
                    currenciesLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun backButtonPressed() {
        router.popBackStackFragment()
    }

    fun setCurrencySelected(currency: Currency) {
        disposables.add(
            interactor.setCurrencySelected(currency)
                .andThen(interactor.getCurrencies(false))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    currenciesLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }
}