/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DeciminalFormatter
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import java.util.Currency

class ProfileViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter
) : BaseViewModel() {

    val userLiveData = MutableLiveData<User>()
    val profileReputationVisibilityLiveData = MutableLiveData<Boolean>()
    val votesLiveData = MutableLiveData<String>()
    val userReputationLiveData = MutableLiveData<Reputation>()
    val selectedCurrencyLiveData = MutableLiveData<String>()

    fun loadUserData(updateCached: Boolean) {
        disposables.add(
            Completable.mergeArray(
                loadUser(updateCached),
                loadVotes(updateCached),
                loadSelectedCurrency(),
                loadReputation(updateCached)
            ).subscribe({
                if (!updateCached) loadUserData(true)
            }, {
                logException(it)
            })
        )
    }

    fun onReputationClick() {
        router.showReputationScreen()
    }

    fun onVotesClick() {
        router.showVotesScreen()
    }

    private fun loadUser(updateCached: Boolean): Completable {
        return interactor.getUserInfo(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { userLiveData.value = it }
            .ignoreElement()
    }

    private fun loadVotes(updateCached: Boolean): Completable {
        return interactor.getVotes(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { votesLiveData.value = DeciminalFormatter.formatInteger(it) }
            .ignoreElement()
    }

    private fun loadSelectedCurrency(): Completable {
        return interactor.getCurrencySelected()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { selectedCurrencyLiveData.value = Currency.getInstance(it.code).symbol }
            .ignoreElement()
    }

    private fun loadReputation(updateCached: Boolean): Completable {
        return interactor.getUserReputation(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                profileReputationVisibilityLiveData.value = it.reputation.toInt() != -1
                userReputationLiveData.value = it
            }
            .ignoreElement()
    }

    fun profileConversionClicked() {
        router.showConversion()
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun onPassphraseClick() {
        router.showPinCheckToPassphrase()
    }

    fun onEditProfileClicked() {
        router.showPersonalDataEdition()
    }

    fun profileAboutClicked() {
        router.showAbout()
    }
}