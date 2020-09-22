/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.xor

import android.app.Activity
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.domain.AssetHolder.Companion.SORA_XOR
import jp.co.soramitsu.common.domain.AssetHolder.Companion.SORA_XOR_ERC_20
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.bottom_sheet_xor_asset_actions.copyEthAddress
import kotlinx.android.synthetic.main.bottom_sheet_xor_asset_actions.copyXorAddress
import kotlinx.android.synthetic.main.bottom_sheet_xor_asset_actions.viewBalance

typealias CopyListener = (assetId: String) -> Unit
typealias ViewBalanceListener = () -> Unit

class XorAssetActionsBottomSheet(
    context: Activity,
    private val copyClickListener: CopyListener,
    private val viewBalanceListener: ViewBalanceListener
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {
    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_xor_asset_actions, null))

        copyXorAddress.setOnClickListener {
            copyClickListener.invoke(SORA_XOR.id)
            dismiss()
        }

        copyEthAddress.setOnClickListener {
            copyClickListener.invoke(SORA_XOR_ERC_20.id)
            dismiss()
        }

        viewBalance.setOnClickListener {
            viewBalanceListener.invoke()
            dismiss()
        }
    }
}