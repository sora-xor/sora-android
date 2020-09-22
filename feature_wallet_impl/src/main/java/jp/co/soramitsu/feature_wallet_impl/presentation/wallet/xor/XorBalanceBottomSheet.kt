/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.xor

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.bottom_sheet_xor_balance.erc20BalanceField
import kotlinx.android.synthetic.main.bottom_sheet_xor_balance.soraBalanceField

class XorBalanceBottomSheet(
    context: Activity,
    soraBalance: String?,
    erc20Balance: String?
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_xor_balance, null))

        soraBalance?.let {
            soraBalanceField.text = it
        }

        erc20Balance?.let {
            erc20BalanceField.text = it
        }
    }
}