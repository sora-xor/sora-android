/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.send.error

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.ext.setDebouncedClickListener
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.bridge_disabled_error_bottom_dialog.websiteSubtitleTv
import kotlinx.android.synthetic.main.bridge_disabled_error_bottom_dialog.websiteWrapper

class BridgeDisabledErrorBottomSheetDialog(
    context: Context,
    debounceClickHandler: DebounceClickHandler,
    itemClickListener: (url: String) -> Unit
) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    companion object {
        private const val BRIDGE_INFO_URL = "https://github.com/sora-xor/VAL-bridge-activation"
    }

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bridge_disabled_error_bottom_dialog, null))

        websiteSubtitleTv.text = BRIDGE_INFO_URL

        websiteWrapper.setDebouncedClickListener(debounceClickHandler) {
            itemClickListener(BRIDGE_INFO_URL)
        }
    }
}