/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.app.Activity
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.BottomSheetAssetIdBinding

class AssetIdBottomSheet(
    context: Activity,
    assetId: String,
    icon: Int?,
    private val copyClickListener: () -> Unit,
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding =
            BottomSheetAssetIdBinding.inflate(LayoutInflater.from(context), null, false)
                .also {
                    setContentView(it.root)
                }

        icon?.let {
            binding.ivAssetIcon.setImageResource(it)
        }
        binding.assetIdValue.text = assetId.truncateUserAddress()
        binding.assetIdValue.setOnClickListener {
            copyClickListener()
        }
        setOnShowListener {
            val bottomSheetDialog = it as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
        }
    }
}
