/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util.ext

import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
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
import jp.co.soramitsu.androidfoundation.format.safeCast
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

fun View.hideOrGone(v: Boolean) {
    this.visibility = if (v) View.INVISIBLE else View.GONE
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
