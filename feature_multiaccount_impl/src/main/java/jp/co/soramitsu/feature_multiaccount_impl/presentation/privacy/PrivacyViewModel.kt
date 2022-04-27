/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.presentation.privacy

import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_multiaccount_impl.presentation.MultiaccountRouter

class PrivacyViewModel(
    private val router: MultiaccountRouter
) : BaseViewModel() {

    fun onBackPressed() {
        router.onBackButtonPressed()
    }
}
