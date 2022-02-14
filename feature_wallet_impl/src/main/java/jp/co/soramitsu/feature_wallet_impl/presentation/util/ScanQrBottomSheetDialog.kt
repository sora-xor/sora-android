/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.util

import android.app.Activity
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserBottomSheet
import jp.co.soramitsu.common.presentation.view.chooserbottomsheet.ChooserItem
import jp.co.soramitsu.feature_wallet_impl.R

class ScanQrBottomSheetDialog(
    context: Activity,
    uploadListener: () -> Unit,
    cameraListener: () -> Unit
) : ChooserBottomSheet(
    context,
    R.string.qr_code,
    listOf(
        ChooserItem(
            title = R.string.qr_upload,
            icon = R.drawable.ic_neu_upload,
            clickHandler = uploadListener,
        ),
        ChooserItem(
            title = R.string.contacts_scan,
            icon = R.drawable.ic_scan_wrapped,
            clickHandler = cameraListener,
        ),
    )
)
