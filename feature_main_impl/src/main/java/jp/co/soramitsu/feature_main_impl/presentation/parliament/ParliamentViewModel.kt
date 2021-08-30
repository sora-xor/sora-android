/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.parliament

import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_api.launcher.MainRouter

class ParliamentViewModel(private val mainRouter: MainRouter) : BaseViewModel() {

    fun onReferendaCardClicked() {
        mainRouter.showReferenda()
    }
}
