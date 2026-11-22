package jp.co.soramitsu.feature_main_impl.presentation

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/** A frame that says when a finger is on it, so what it holds can be drawn set down. */
class PressAwareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    var onPressedChange: ((Boolean) -> Unit)? = null

    override fun dispatchSetPressed(pressed: Boolean) {
        super.dispatchSetPressed(pressed)
        onPressedChange?.invoke(pressed)
    }
}
