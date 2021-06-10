/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation

import android.app.Dialog
import android.content.Context
import androidx.appcompat.app.AlertDialog

class ChooserDialog(
    context: Context,
    titleResource: Int,
    firstItem: String,
    secondItem: String,
    firstOptionClickListener: () -> Unit,
    secondOptionClickListener: () -> Unit
) {

    private val instance: Dialog

    init {
        val items = arrayOf(firstItem, secondItem)
        instance = AlertDialog.Builder(context)
            .setTitle(titleResource)
            .setItems(items) { _, item ->
                when (item) {
                    0 -> firstOptionClickListener()
                    1 -> secondOptionClickListener()
                }
            }
            .setCancelable(true)
            .create()
    }

    fun show() {
        instance.show()
    }

    fun dismiss() {
        instance.dismiss()
    }
}
