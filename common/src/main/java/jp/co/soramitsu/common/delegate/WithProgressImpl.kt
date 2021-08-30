/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.delegate

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.interfaces.WithProgress
import javax.inject.Inject

class WithProgressImpl @Inject constructor() : WithProgress {

    private val progressVisibilityLiveData = MutableLiveData<Boolean>()

    override fun getProgressVisibility(): LiveData<Boolean> {
        return progressVisibilityLiveData
    }

    override fun showProgress() {
        progressVisibilityLiveData.postValue(true)
    }

    override fun hideProgress() {
        progressVisibilityLiveData.postValue(false)
    }
}
