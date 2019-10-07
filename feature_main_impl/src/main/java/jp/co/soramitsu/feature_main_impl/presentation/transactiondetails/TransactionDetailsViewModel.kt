/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.presentation.transactiondetails

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_main_impl.presentation.MainRouter
import jp.co.soramitsu.feature_main_impl.presentation.wallet.model.SoraTransaction

class TransactionDetailsViewModel(
    private val router: MainRouter
) : BaseViewModel() {

    val transactionLiveData = MutableLiveData<SoraTransaction>()

    fun btnNextClicked(isFromList: Boolean, accountId: String, fullName: String, balance: String) {
        if (isFromList) {
            router.showTransferAmount(accountId, fullName, "", "", balance)
        } else {
            btnNextOrBackClicked()
        }
    }

    fun btnNextOrBackClicked() {
        router.returnToWalletFragment()
    }
}