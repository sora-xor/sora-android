/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.core.content.res.use

@ColorInt
fun Context?.attrColor(@AttrRes attr: Int): Int =
    if (this == null) Color.BLACK else obtainStyledAttributes(intArrayOf(attr)).use {
        it.getColor(0, Color.BLACK)
    }

fun Context.dpRes2px(@DimenRes res: Int): Int =
    this.resources.getDimensionPixelSize(res)

fun Context.restartApplication() {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    startActivity(mainIntent)
    Runtime.getRuntime().exit(0)
}

fun Context.hideSoftKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.toggleSoftInput(
        InputMethodManager.SHOW_IMPLICIT, 0
    )
}

fun Context.openSoftKeyboard(view: View) {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view.requestFocus()
    inputMethodManager.toggleSoftInput(
        InputMethodManager.SHOW_IMPLICIT, 0
    )
}
