/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.ext.setValueIfNew
import jp.co.soramitsu.feature_account_api.domain.model.AddInvitationCase
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor

class MainViewModel(
    healthChecker: HealthChecker,
    private val interactor: MainInteractor,
    private val deviceParamsProvider: DeviceParamsProvider,
    private val progress: WithProgress,
    private val invitationHandler: InvitationHandler
) : BaseViewModel(), WithProgress by progress {

    private val _showInviteErrorTimeIsUpLiveData = MutableLiveData<Event<Unit>>()
    val showInviteErrorTimeIsUpLiveData: LiveData<Event<Unit>> = _showInviteErrorTimeIsUpLiveData

    private val _showInviteErrorAlreadyAppliedLiveData = MutableLiveData<Event<Unit>>()
    val showInviteErrorAlreadyAppliedLiveData: LiveData<Event<Unit>> = _showInviteErrorAlreadyAppliedLiveData

    private val _badConnectionVisibilityLiveData = MutableLiveData<Boolean>()
    val badConnectionVisibilityLiveData: LiveData<Boolean> = _badConnectionVisibilityLiveData

    private val _ethereumConfigStateLiveData = MutableLiveData<Boolean>()
    val ethereumConfigStateLiveData: LiveData<Boolean> = _ethereumConfigStateLiveData

    private val _addInviteIsPossibleLiveData = MutableLiveData<Event<String>>()
    val addInviteIsPossibleLiveData: LiveData<Event<String>> = _addInviteIsPossibleLiveData

    private val _invitationCodeAppliedSuccessful = MutableLiveData<Event<Unit>>()
    val invitationCodeAppliedSuccessful: LiveData<Event<Unit>> = _invitationCodeAppliedSuccessful

    init {
        disposables.add(
            healthChecker.observeHealthState()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _badConnectionVisibilityLiveData.setValueIfNew(!it)
                }, {
                    it.printStackTrace()
                })
        )
        disposables.add(
            healthChecker.observeEthereumConfigState()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    if (_badConnectionVisibilityLiveData.value != true) {
                        _ethereumConfigStateLiveData.setValueIfNew(it)
                    }
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun startedWithInviteAction() {
        disposables.add(
            interactor.checkAddInviteCodeIsPossible(deviceParamsProvider.getCurrentTimeMillis())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompose())
                .subscribe({
                    when (it) {
                        AddInvitationCase.AVAILABLE -> getInviteCode()
                        AddInvitationCase.ALREADY_APPLIED -> _showInviteErrorAlreadyAppliedLiveData.value = Event(Unit)
                        AddInvitationCase.TIME_IS_UP -> _showInviteErrorTimeIsUpLiveData.value = Event(Unit)
                        else -> {
                        }
                    }
                }, {
                    onError(it)
                })
        )
    }

    private fun getInviteCode() {
        disposables.add(
            interactor.getInviteCode()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    _addInviteIsPossibleLiveData.value = Event(it)
                }, {
                    onError(it)
                })
        )
    }

    fun applyInvitationCode() {
        disposables.add(
            interactor.applyInvitationCode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompletableCompose())
                .subscribe({
                    _invitationCodeAppliedSuccessful.value = Event(Unit)
                    invitationHandler.invitationApplied()
                }, {
                    onError(it)
                })
        )
    }
}