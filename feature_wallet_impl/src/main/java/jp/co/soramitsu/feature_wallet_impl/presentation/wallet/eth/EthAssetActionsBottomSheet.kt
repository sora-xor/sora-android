/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.eth

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.BottomSheetEthAssetActionsBinding

class EthAssetActionsBottomSheet(
    context: Activity,
    address: String,
    private val copyClickListener: () -> Unit,
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding =
            BottomSheetEthAssetActionsBinding.inflate(LayoutInflater.from(context), null, false)
                .also {
                    setContentView(it.root)
                }
        binding.titleTv.text = address
        binding.copyTv.setOnClickListener {
            copyClickListener()
            dismiss()
        }
    }
}
