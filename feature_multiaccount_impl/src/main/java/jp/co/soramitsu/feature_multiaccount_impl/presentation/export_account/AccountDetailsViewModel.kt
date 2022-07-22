/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.export_account

import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import javax.inject.Inject

@HiltViewModel
class AccountDetailsViewModel @Inject constructor() : BaseViewModel() {

    fun onNameChange(name: String) {
    }

    fun onShowPassphrase() {
    }

    fun onShowRawSeed() {
    }

    fun onLogout() {
    }

    fun backButtonPressed() {
    }
}
