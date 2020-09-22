/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.eth

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.bottom_sheet_eth_asset_actions.copyTv
import kotlinx.android.synthetic.main.bottom_sheet_eth_asset_actions.retryTv
import kotlinx.android.synthetic.main.bottom_sheet_eth_asset_actions.titleTv

class EthAssetActionsBottomSheet(
    context: Activity,
    ethAddress: String,
    showRetryRegisterButton: Boolean,
    private val copyClickListener: () -> Unit,
    private val retryClickListener: () -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_eth_asset_actions, null))

        titleTv.text = ethAddress

        copyTv.setOnClickListener {
            copyClickListener()
            dismiss()
        }

        retryTv.visibility = if (showRetryRegisterButton) View.VISIBLE else View.GONE

        retryTv.setOnClickListener {
            retryClickListener()
            dismiss()
        }
    }
}