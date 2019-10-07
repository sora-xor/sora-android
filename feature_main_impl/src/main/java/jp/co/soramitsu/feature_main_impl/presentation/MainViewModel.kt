/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation

import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.common.util.Event
import jp.co.soramitsu.common.util.ext.setValueIfNew

class MainViewModel(
    healthChecker: HealthChecker
) : BaseViewModel() {

    val showInviteErrorLiveData = MutableLiveData<Event<Unit>>()
    val badConnectionVisibilityLiveData = MutableLiveData<Boolean>()

    init {
        disposables.add(
            healthChecker.observeHealthState()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    badConnectionVisibilityLiveData.setValueIfNew(!it)
                }, {
                    it.printStackTrace()
                })
        )
    }

    fun inviteAction() {
        if (showInviteErrorLiveData.value == null) {
            showInviteErrorLiveData.value = Event(Unit)
        }
    }
}