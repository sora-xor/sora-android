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
import jp.co.soramitsu.feature_wallet_impl.presentation.send.error.model.FeeDialogErrorData
import kotlinx.android.synthetic.main.fee_error_bottom_dialog.ethereumBalanceTv
import kotlinx.android.synthetic.main.fee_error_bottom_dialog.goToEthereumBtn
import kotlinx.android.synthetic.main.fee_error_bottom_dialog.minerFeeTv

class FeeErrorBottomSheetDialog(
    context: Context,
    debounceClickHandler: DebounceClickHandler,
    feeErrorDialogData: FeeDialogErrorData,
    itemClickListener: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.fee_error_bottom_dialog, null))

        minerFeeTv.text = feeErrorDialogData.minerFee
        ethereumBalanceTv.text = feeErrorDialogData.ethBalance

        goToEthereumBtn.setOnClickListener(DebounceClickListener(debounceClickHandler) {
            itemClickListener()
            dismiss()
        })
    }
}