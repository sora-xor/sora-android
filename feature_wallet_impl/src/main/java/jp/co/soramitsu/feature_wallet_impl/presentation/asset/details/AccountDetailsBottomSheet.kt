/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.asset.details

import android.app.Activity
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.util.ext.truncateUserAddress
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.feature_wallet_impl.databinding.BottomSheetAccountDetailsBinding

class AccountDetailsBottomSheet(
    context: Activity,
    address: String,
    userIcon: Drawable,
    private val copyClickListener: () -> Unit,
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        val binding =
            BottomSheetAccountDetailsBinding.inflate(LayoutInflater.from(context), null, false)
                .also {
                    setContentView(it.root)
                }

        binding.userIcon.setImageDrawable(userIcon)

        binding.address.text = address.truncateUserAddress()
        binding.copyIcon.setOnClickListener {
            copyClickListener()
        }
    }
}
