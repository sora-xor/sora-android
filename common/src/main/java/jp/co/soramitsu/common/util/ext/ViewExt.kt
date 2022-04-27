/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.text.Editable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.TextAppearanceSpan
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import jp.co.soramitsu.common.presentation.AssetBalanceData
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.FiatBalanceData
import jp.co.soramitsu.common.presentation.view.DebounceClickListener
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

fun View.show() {
    this.visibility = View.VISIBLE
}

fun View.hide() {
    this.visibility = View.INVISIBLE
}

fun View.gone() {
    this.visibility = View.GONE
}

fun View.doAnimation(start: Boolean, animation: Animation) {
    if (start) startAnimation(animation) else clearAnimation()
}

/**
 * set view visibility
 *
 * @param v true - [View.VISIBLE], false - [View.GONE]
 */
fun View.showOrGone(v: Boolean) {
    this.visibility = if (v) View.VISIBLE else View.GONE
}

/**
 * set view visibility
 *
 * @param v true - [View.VISIBLE], false - [View.INVISIBLE]
 */
fun View.showOrHide(v: Boolean) {
    this.visibility = if (v) View.VISIBLE else View.INVISIBLE
}

inline fun View.setDebouncedClickListener(
    debounceClickHandler: DebounceClickHandler,
    crossinline listener: (View) -> Unit
) {
    setOnClickListener(
        DebounceClickListener(debounceClickHandler) {
            listener.invoke(it)
        }
    )
}

fun ImageView.setImageTint(@ColorRes colorRes: Int) {
    val color = ContextCompat.getColor(context, colorRes)
    this.setImageTint2(color)
}

fun ImageView.setImageTint2(@ColorInt color: Int) {
    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
}

fun ImageView.animateLoader(animate: Boolean) {
    this.showOrHide(animate)
    this.drawable.safeCast<Animatable>()?.let {
        if (animate) {
            if (it.isRunning.not()) it.start()
        } else {
            if (it.isRunning) it.stop()
        }
    }
}

@ExperimentalCoroutinesApi
fun EditText.asFlow() = callbackFlow {

    val onChanged: (CharSequence?, Int, Int, Int) -> Unit = { c, _, _, _ ->
        trySend(c.toString())
    }

    val listener = addTextChangedListener(
        onTextChanged = onChanged
    )

    awaitClose { removeTextChangedListener(listener) }
}

inline fun EditText.onDoneClicked(crossinline listener: () -> Unit) {
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            listener.invoke()

            true
        }

        false
    }
}

inline fun EditText.onTextChanged(crossinline listener: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            listener.invoke(s.toString())
        }
    })
}

fun TextView.setDrawableEnd(
    @DrawableRes start: Int? = null,
    widthInDp: Int? = null,
    heightInDp: Int? = widthInDp,
    @ColorRes tint: Int? = null
) {
    if (start == null) {
        setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
        return
    }

    val drawable = requireNotNull(ContextCompat.getDrawable(context, start))

    tint?.let { drawable.mutate().setTint(ContextCompat.getColor(context, it)) }

    val widthInPx =
        if (widthInDp != null) (resources.displayMetrics.density * widthInDp).toInt() else drawable.intrinsicWidth
    val heightInPx =
        if (heightInDp != null) (resources.displayMetrics.density * heightInDp).toInt() else drawable.intrinsicHeight

    drawable.setBounds(0, 0, widthInPx, heightInPx)

    setCompoundDrawablesRelative(null, null, drawable, null)
}

fun TextView.showOrGone(t: String?) {
    if (t == null) {
        gone()
    } else {
        show()
        text = t
    }
}

fun TextView.showOrHide(t: String?) {
    if (t == null) {
        hide()
    } else {
        show()
        text = t
    }
}

fun View.runDelayed(
    durationInMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit,
): Job? = findViewTreeLifecycleOwner()?.let { lifecycleOwner ->
    lifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
        delay(durationInMillis)
        block.invoke()
    }
}

@ColorInt
fun View.getColorAttr(@AttrRes attr: Int): Int = MaterialColors.getColor(this, attr)

fun WebView.setPageBackground(color: Int) {
    val colorHex = color.colorToHex()
    this.loadUrl("javascript:(function() {document.getElementsByTagName(\"body\")[0].style.background = \"$colorHex\";})()")
}

fun TextView.setBalance(amount: AssetBalanceData, decimalSeparator: Char = '.') {
    if (amount.amount.isEmpty()) {
        this.text = ""
    } else {
        val pos = amount.amount.indexOf(decimalSeparator)
        val text = SpannableString(amount.amount)
        when {
            pos >= 0 -> {
                text.setSpan(
                    TextAppearanceSpan(context, amount.style.intStyle),
                    0,
                    pos,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text.setSpan(
                    TextAppearanceSpan(context, amount.style.decStyle),
                    pos,
                    text.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            else -> {
                text.setSpan(
                    TextAppearanceSpan(context, amount.style.intStyle),
                    0,
                    text.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        text.setSpan(
            ForegroundColorSpan(getColorAttr(amount.style.color)),
            0,
            text.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        if (amount.ticker != null) {
            val builder = SpannableStringBuilder()
            builder.append(text).append(" ")
            val ticker = SpannableString(amount.ticker)
            ticker.setSpan(
                TextAppearanceSpan(context, amount.style.tickerStyle ?: amount.style.intStyle),
                0,
                ticker.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            ticker.setSpan(
                ForegroundColorSpan(
                    getColorAttr(
                        amount.style.tickerColor ?: amount.style.color
                    )
                ),
                0,
                ticker.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            builder.append(ticker)
            this.setText(builder, TextView.BufferType.SPANNABLE)
        } else {
            this.setText(text, TextView.BufferType.SPANNABLE)
        }
    }
}

private const val TILDA = "~"
fun TextView.setFiatBalance(
    amount: FiatBalanceData,
    decimalSeparator: Char = '.'
) {
    if (amount.amount.isEmpty()) {
        text = ""
        return
    }

    val text = SpannableStringBuilder.valueOf(TILDA)

    if (amount.symbol != null) {
        text.append(amount.symbol)
        text.append(" ")
    }

    val pos = amount.amount.indexOf(decimalSeparator)
    val amountSpannable = SpannableString.valueOf(amount.amount)
    amountSpannable.setSpan(
        TextAppearanceSpan(context, amount.style.intStyle),
        0,
        text.length,
        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    when {
        pos >= 0 -> {
            amountSpannable.setSpan(
                TextAppearanceSpan(context, amount.style.intStyle),
                0,
                pos,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            amountSpannable.setSpan(
                TextAppearanceSpan(context, amount.style.decStyle),
                pos,
                amountSpannable.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        else -> {
            amountSpannable.setSpan(
                TextAppearanceSpan(context, amount.style.intStyle),
                0,
                amountSpannable.length,
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
    amountSpannable.setSpan(
        ForegroundColorSpan(getColorAttr(amount.style.color)),
        0,
        amountSpannable.length,
        SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
    )

    this.setText(text.append(amountSpannable), TextView.BufferType.SPANNABLE)
}

fun FloatingActionButton.slideUpOrDown(isVisible: Boolean) {
    val layoutParams: ViewGroup.LayoutParams = this.layoutParams
    if (layoutParams is CoordinatorLayout.LayoutParams) {
        val behavior = layoutParams.behavior
        if (behavior is HideBottomViewOnScrollBehavior) {
            if (isVisible) {
                behavior.slideUp(this)
            } else {
                behavior.slideDown(this)
            }
        }
    }
}
