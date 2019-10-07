/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog
import jp.co.soramitsu.common.R

class ChooserDialog(
    context: Context,
    titleResource: Int,
    elementsResource: Int,
    chooseCameraClickListener: () -> Unit,
    chooseGalleryClickListener: () -> Unit
) {

    private val instance: Dialog

    init {
        instance = AlertDialog.Builder(context)
            .setTitle(titleResource)
            .setItems(R.array.qr_dialog_array) { _, item ->
                when (item) {
                    0 -> chooseCameraClickListener()
                    1 -> chooseGalleryClickListener()
                }
            }
            .setCancelable(true)
            .create()
    }

    public fun show() {
        instance.show()
    }

    public fun dismiss() {
        instance.dismiss()
    }
}