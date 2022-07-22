/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.personaldataedit

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.interfaces.WithProgress
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter
import jp.co.soramitsu.feature_main_impl.domain.MainInteractor
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalDataEditViewModel @Inject constructor(
    private val interactor: MainInteractor,
    private val router: MainRouter,
    private val progress: WithProgress,
) : BaseViewModel(), WithProgress by progress {

    private val _accountNameLiveData = MutableLiveData<String>()
    val accountNameLiveData: LiveData<String> = _accountNameLiveData

    private val _nextButtonEnableLiveData = MutableLiveData<Boolean>()
    val nextButtonEnableLiveData: LiveData<Boolean> = _nextButtonEnableLiveData

    init {
        viewModelScope.launch {
            try {
                _accountNameLiveData.value = interactor.getAccountName()
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    fun backPressed() {
        router.popBackStack()
    }

    fun saveData(accountName: String) {
        viewModelScope.launch {
            showProgress()
            try {
                interactor.saveAccountName(accountName)
                hideProgress()
                router.popBackStack()
            } catch (t: Throwable) {
                onError(t)
                hideProgress()
            }
        }
    }

    fun accountNameChanged(accountName: String) {
        _nextButtonEnableLiveData.value = accountName.isNotEmpty()
    }
}
