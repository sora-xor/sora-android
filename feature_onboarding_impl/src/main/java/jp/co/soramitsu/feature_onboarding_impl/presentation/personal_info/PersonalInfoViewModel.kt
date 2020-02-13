/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.presentation.personal_info

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.feature_onboarding_impl.domain.OnboardingInteractor
import jp.co.soramitsu.feature_onboarding_impl.presentation.OnboardingRouter

class PersonalInfoViewModel(
    private val interactor: OnboardingInteractor,
    private val router: OnboardingRouter,
    private val progress: WithProgress,
    invitationHandler: InvitationHandler
) : BaseViewModel(), WithProgress by progress {

    private var countryIso = ""

    private val _invitationNotValidEventLiveData = MutableLiveData<Event<Unit>>()
    val invitationNotValidEventLiveData: LiveData<Event<Unit>> = _invitationNotValidEventLiveData

    private val _firstNameIsEmptyEventLiveData = MutableLiveData<Event<Unit>>()
    val firstNameIsEmptyEventLiveData: LiveData<Event<Unit>> = _firstNameIsEmptyEventLiveData

    private val _firstNameIsNotValidEventLiveData = MutableLiveData<Event<Unit>>()
    val firstNameIsNotValidEventLiveData: LiveData<Event<Unit>> = _firstNameIsNotValidEventLiveData

    private val _lastNameIsEmptyEventLiveData = MutableLiveData<Event<Unit>>()
    val lastNameIsEmptyEventLiveData: LiveData<Event<Unit>> = _lastNameIsEmptyEventLiveData

    private val _lastNameIsNotValidEventLiveData = MutableLiveData<Event<Unit>>()
    val lastNameIsNotValidEventLiveData: LiveData<Event<Unit>> = _lastNameIsNotValidEventLiveData

    private val _inviteCodeLiveData = MutableLiveData<String>()
    val inviteCodeLiveData: LiveData<String> = _inviteCodeLiveData

    init {
        disposables.add(
            invitationHandler.observeInvitationApplies()
                .flatMapSingle { interactor.getParentInviteCode() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _inviteCodeLiveData.value = it
                }, {
                    it.printStackTrace()
                })
        )

        disposables.add(
            interactor.getParentInviteCode()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (it.isNotEmpty()) _inviteCodeLiveData.value = it
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
                        _invitationNotValidEventLiveData.value = Event(Unit)
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
                        _invitationNotValidEventLiveData.value = Event(Unit)
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

    private fun checkFieldsValidation(firstName: String, lastName: String): Boolean {
        if (firstName.isEmpty()) {
            _firstNameIsEmptyEventLiveData.value = Event(Unit)
            return false
        }

        if (firstName.last().toString() == "-" || firstName.first().toString() == "-" || firstName.contains("--")) {
            _firstNameIsNotValidEventLiveData.value = Event(Unit)
            return false
        }

        if (lastName.isEmpty()) {
            _lastNameIsEmptyEventLiveData.value = Event(Unit)
            return false
        }

        if (lastName.last().toString() == "-" || lastName.first().toString() == "-" || lastName.contains("--")) {
            _lastNameIsNotValidEventLiveData.value = Event(Unit)
            return false
        }
        return true
    }
}