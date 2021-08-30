package jp.co.soramitsu.common.util

import android.text.TextPaint
import android.text.style.ClickableSpan
import android.view.View

class SoraClickableSpan(private val spanClickListener: () -> Unit) : ClickableSpan() {

    override fun updateDrawState(ds: TextPaint) {
        ds.isUnderlineText = false
    }

    override fun onClick(widget: View) {
        spanClickListener()
    }
}
