/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class SoraClickableSpan(private val spanClickListener: () -> Unit) : ClickableSpan() {

    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = true
    }

    override fun onClick(widget: View) {
        spanClickListener()
    }
}
