/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.error

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.eth_wallet_error_bottom_dialog.goToEthereumBtn

class EthereumAccountErrorBottomSheetDialog(
    context: Context,
    debounceClickHandler: DebounceClickHandler,
    itemClickListener: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.eth_wallet_error_bottom_dialog, null))

        goToEthereumBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            itemClickListener()
            dismiss()
        })
    }
}