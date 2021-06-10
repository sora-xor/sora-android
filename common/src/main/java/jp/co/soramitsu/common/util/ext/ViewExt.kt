/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.res.ColorStateList
import android.view.View
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.presentation.view.DebounceClickListener

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

inline fun View.setDebouncedClickListener(debounceClickHandler: DebounceClickHandler, crossinline listener: (View) -> Unit) {
    setOnClickListener(
        DebounceClickListener(debounceClickHandler) {
            listener.invoke(it)
        }
    )
}

fun ImageView.setImageTint(@ColorRes colorRes: Int) {
    val color = ContextCompat.getColor(context, colorRes)

    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
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

    val widthInPx = if (widthInDp != null) (resources.displayMetrics.density * widthInDp).toInt() else drawable.intrinsicWidth
    val heightInPx = if (heightInDp != null) (resources.displayMetrics.density * heightInDp).toInt() else drawable.intrinsicHeight

    drawable.setBounds(0, 0, widthInPx, heightInPx)

    setCompoundDrawablesRelative(null, null, drawable, null)
}
