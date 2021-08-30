package jp.co.soramitsu.common.util

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class SoraColoredClickableSpan(private val spanClickListener: () -> Unit, val color: Int) : ClickableSpan() {

    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = false
        ds.color = color
    }

    override fun onClick(widget: View) {
        spanClickListener()
    }
}
