/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class PersonalInfoViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress
) : BaseViewModel(), WithProgress by progress {

    private var countryIso = ""

    val invitationNotValidEventLiveData = MutableLiveData<Event<Unit>>()
    val firstNameIsEmptyEventLiveData = MutableLiveData<Event<Unit>>()
    val firstNameIsNotValidEventLiveData = MutableLiveData<Event<Unit>>()
    val lastNameIsEmptyEventLiveData = MutableLiveData<Event<Unit>>()
    val lastNameIsNotValidEventLiveData = MutableLiveData<Event<Unit>>()
    val inviteCodeLiveData = MutableLiveData<String>()

    init {
        disposables.add(
            interactor.getParentInviteCode()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.isNotEmpty()) inviteCodeLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun register(firstName: String, lastName: String, inviteCode: String) {
        if (!checkFieldsValidation(firstName, lastName)) return
        disposables.add(
            interactor.register(firstName, lastName, countryIso, inviteCode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .subscribe({
                    hideProgress()
                    if (it) {
                        router.showMnemonic()
                    } else {
                        invitationNotValidEventLiveData.value = Event(Unit)
                    }
                }, {
                    hideProgress()
                    onError(it)
                })
        )
    }

    fun backButtonClick() {
        router.onBackButtonPressed()
    }

    private fun checkFieldsValidation(firstName: String, lastName: String): Boolean {
        if (firstName.isEmpty()) {
            firstNameIsEmptyEventLiveData.value = Event(Unit)
            return false
        }

        if (firstName.last().toString() == "-" || firstName.first().toString() == "-" || firstName.contains("--")) {
            firstNameIsNotValidEventLiveData.value = Event(Unit)
            return false
        }

        if (lastName.isEmpty()) {
            lastNameIsEmptyEventLiveData.value = Event(Unit)
            return false
        }

        if (lastName.last().toString() == "-" || lastName.first().toString() == "-" || lastName.contains("--")) {
            lastNameIsNotValidEventLiveData.value = Event(Unit)
            return false
        }
        return true
    }

    fun continueWithoutInvitationCodePressed(firstName: String, lastName: String) {
        if (!checkFieldsValidation(firstName, lastName)) return
        disposables.add(
            interactor.register(firstName, lastName, countryIso)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .subscribe({
                    hideProgress()
                    if (it) {
                        router.showMnemonic()
                    } else {
                        invitationNotValidEventLiveData.value = Event(Unit)
                    }
                }, {
                    hideProgress()
                    onError(it)
                })
        )
    }

    fun setCountryIso(iso: String) {
        countryIso = iso
    }
}