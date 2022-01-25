/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class SoraColoredClickableSpan(private val spanClickListener: () -> Unit, val color: Int, val underlined: Boolean = false) : ClickableSpan() {

    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = underlined
        ds.color = color
    }

    override fun onClick(widget: View) {
        spanClickListener()
    }
}
