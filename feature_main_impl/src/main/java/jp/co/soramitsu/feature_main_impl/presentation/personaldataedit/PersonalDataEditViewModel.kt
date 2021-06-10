/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor

class PersonalDataEditViewModel(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
) : BaseViewModel(), WithProgress by progress {

    private val _accountNameLiveData = MutableLiveData<String>()
    val accountNameLiveData: LiveData<String> = _accountNameLiveData

    private val _nextButtonEnableLiveData = MutableLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    init {
        disposables.add(
            interactor.getAccountName()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        _accountNameLiveData.value = it
                    },
                    {
                        logException(it)
                    }
                )
        )
    }

    fun backPressed() {
        router.popBackStack()
    }

    fun saveData(accountName: String) {
        disposables.add(
            interactor.saveAccountName(accountName)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSubscribe { showProgress() }
                .subscribe(
                    {
                        hideProgress()
                        router.popBackStack()
                    },
                    {
                        hideProgress()
                        onError(it)
                    }
                )
        )
    }

    fun accountNameChanged(accountName: String) {
        _nextButtonEnableLiveData.value = accountName.isNotEmpty()
    }
}
