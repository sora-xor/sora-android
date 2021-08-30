/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.wallet.events

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import jp.co.soramitsu.feature_wallet_impl.R

class HistoryLimitationsInfoBottomSheet(context: Context) : BottomSheetDialog(context, R.style.BottomSheetDialog) {

    init {
        setContentView(LayoutInflater.from(context).inflate(R.layout.bottom_sheet_history_limitation, null))
    }
}
