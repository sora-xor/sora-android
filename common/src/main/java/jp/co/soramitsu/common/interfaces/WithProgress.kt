/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.interfaces

import androidx.lifecycle.LiveData

interface WithProgress {

    fun getProgressVisibility(): LiveData<Boolean>

    fun showProgress()

    fun hideProgress()
}
