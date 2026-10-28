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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.view.inputmethod.InputMethodManager
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DimenRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.core.content.res.use
import jp.co.soramitsu.androidfoundation.format.safeCast

@ColorInt
fun Context?.attrColor(@AttrRes attr: Int): Int =
    if (this == null) Color.BLACK else obtainStyledAttributes(intArrayOf(attr)).use {
        it.getColor(0, Color.BLACK)
    }

fun Context.getActivity(): AppCompatActivity? = when (this) {
    is AppCompatActivity -> this
    is ContextWrapper -> baseContext.safeCast()
    else -> null
}

@ColorInt
fun Context.colorFromAttr(
    @AttrRes attrColor: Int,
    typedValue: TypedValue = TypedValue(),
    resolveRefs: Boolean = true
): Int {
    theme.resolveAttribute(attrColor, typedValue, resolveRefs)
    return typedValue.data
}

@Composable
fun getColorAttrComposable(color: Int) = colorResource(LocalContext.current.getColorFromAttrs(color).resourceId)

fun Context.getColorFromAttrs(attr: Int): TypedValue {
    return TypedValue().apply {
        theme.resolveAttribute(attr, this, true)
    }
}

fun Context.dpRes2px(@DimenRes res: Int): Int =
    this.resources.getDimensionPixelSize(res)

fun Context.hideSoftKeyboard() {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val windowToken = when (this) {
        is Activity -> currentFocus?.windowToken
        else -> getActivity()?.currentFocus?.windowToken
    }
    windowToken?.let {
        inputMethodManager.hideSoftInputFromWindow(it, 0)
    }
}

fun Context.openSoftKeyboard(view: View) {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view.requestFocus()
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

@Suppress("DEPRECATION")
fun Window.setStatusBarColorCompat(@ColorInt color: Int) {
    statusBarColor = color
}

@Suppress("DEPRECATION")
fun Window.setNavigationBarColorCompat(@ColorInt color: Int) {
    navigationBarColor = color
}
