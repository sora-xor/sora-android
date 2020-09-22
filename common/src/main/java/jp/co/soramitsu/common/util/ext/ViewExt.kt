/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import androidx.annotation.ColorRes
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

inline fun View.setDebouncedClickListener(debounceClickHandler: DebounceClickHandler, crossinline listener: (View) -> Unit) {
    setOnClickListener(DebounceClickListener(debounceClickHandler) {
        listener.invoke(it)
    })
}

fun ImageView.setImageTint(@ColorRes colorRes: Int) {
    val color = ContextCompat.getColor(context, colorRes)

    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(color))
}