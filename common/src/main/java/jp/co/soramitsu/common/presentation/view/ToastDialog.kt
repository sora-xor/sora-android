/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.util.ext.runDelayed

class ToastDialog(
    private val iconResource: Int,
    private val textResource: Int,
    durationInMillis: Long,
    activity: FragmentActivity,
) : Dialog(activity) {

    init {
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setCancelable(false)
        setContentView(R.layout.custom_toast)

        activity.runDelayed(durationInMillis) {
            this.dismiss()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textTv = findViewById<TextView>(R.id.text)
        textTv.setText(textResource)
        textTv.setCompoundDrawablesWithIntrinsicBounds(0, iconResource, 0, 0)
    }
}
