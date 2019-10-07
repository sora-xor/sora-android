/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_account_api.domain.model.User
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter

class PersonalDataEditViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    val userLiveData = MutableLiveData<User>()
    val emptyFirstNameLiveData = MutableLiveData<Event<Unit>>()
    val incorrectFirstNameLiveData = MutableLiveData<Event<Unit>>()
    val emptyLastNameLiveData = MutableLiveData<Event<Unit>>()
    val incorrectLastNameLiveData = MutableLiveData<Event<Unit>>()

    fun getUserData(updateCached: Boolean) {
        disposables.add(
            interactor.getUserInfo(updateCached)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    userLiveData.value = it
                    if (!updateCached) getUserData(true)
                }, {
                    logException(it)
                })
        )
    }

    fun backPressed() {
        router.popBackStackFragment()
    }

    fun saveData(firstName: String, lastName: String) {
        if (firstName.trim().isEmpty()) {
            emptyFirstNameLiveData.value = Event(Unit)
            return
        }

        if (lastName.trim().isEmpty()) {
            emptyLastNameLiveData.value = Event(Unit)
            return
        }

        if (firstName.last().toString() == "-" || firstName.first().toString() == "-" || firstName.contains("--")) {
            incorrectFirstNameLiveData.value = Event(Unit)
            return
        }

        if (lastName.last().toString() == "-" || lastName.first().toString() == "-" || lastName.contains("--")) {
            incorrectLastNameLiveData.value = Event(Unit)
            return
        }

        disposables.add(
            interactor.saveUserInfo(firstName, lastName)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .subscribe({
                    hideProgress()
                    router.popBackStackFragment()
                }, {
                    hideProgress()
                    onError(it)
                })
        )
    }
}