/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.profile

import androidx.lifecycle.MutableLiveData
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.feature_account_api.domain.model.Reputation
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_main_api.domain.model.PinCodeAction
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor

class ProfileViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val numbersFormatter: NumbersFormatter,
    private val resourceManager: ResourceManager
) : BaseViewModel() {

    val userLiveData = MutableLiveData<User>()
    val selectedLanguageLiveData = MutableLiveData<String>()
    val votesLiveData = MutableLiveData<String>()
    val userReputationLiveData = MutableLiveData<Reputation>()

    init {
        disposables.add(observeVotes())
    }

    fun loadUserData(updateCached: Boolean) {
        disposables.add(
            Completable.mergeArray(
                loadUser(updateCached),
                loadVotes(updateCached),
                loadReputation(updateCached),
                loadSelectedLanguage()
            ).subscribe({
                if (!updateCached) loadUserData(true)
            }, {
                logException(it)
            })
        )
    }

    fun onReputationClick() {
        router.showReputation()
    }

    fun onVotesClick() {
        router.showVotesHistory()
    }

    fun btnHelpClicked() {
        router.showFaq()
    }

    fun onPassphraseClick() {
        router.showPin(PinCodeAction.OPEN_PASSPHRASE)
    }

    fun onEditProfileClicked() {
        router.showPersonalDataEdition()
    }

    fun profileAboutClicked() {
        router.showAbout()
    }

    fun profileLanguageClicked() {
        router.showSelectLanguage()
    }

    private fun loadUser(updateCached: Boolean): Completable {
        return interactor.getUserInfo(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { userLiveData.value = it }
            .ignoreElement()
    }

    private fun loadReputation(updateCached: Boolean): Completable {
        return interactor.getUserReputation(updateCached)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                userReputationLiveData.value = it
            }
            .ignoreElement()
    }

    private fun loadVotes(updateCached: Boolean): Completable {
        return if (updateCached) {
            interactor.syncVotes()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
        } else {
            Completable.complete()
        }
    }

    private fun observeVotes(): Disposable {
        return interactor.observeVotes()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                votesLiveData.value = numbersFormatter.formatInteger(it)
            }, ::onError)
    }

    private fun loadSelectedLanguage(): Completable {
        return interactor.getSelectedLanguage()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                selectedLanguageLiveData.value =
                    resourceManager.getString(it.nativeDisplayNameResource)
            }
            .ignoreElement()
    }
}