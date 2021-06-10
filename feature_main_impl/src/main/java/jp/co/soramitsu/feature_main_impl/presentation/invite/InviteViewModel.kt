/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.invite

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.InvitationInteractor

class InviteViewModel(
    private val interactor: InvitationInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
) : BaseViewModel(), WithProgress by progress {

    private val _shareCodeLiveData = MutableLiveData<String>()
    val shareCodeLiveData: LiveData<String> = _shareCodeLiveData

    fun sendInviteClick() {
        disposables.add(
            interactor.getInviteLink()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .compose(progressCompose())
                .subscribe(
                    {
                        _shareCodeLiveData.value = it
                    },
                    {
                        onError(it)
                    }
                )
        )
    }

    fun backButtonPressed() {
        router.popBackStack()
    }
}
