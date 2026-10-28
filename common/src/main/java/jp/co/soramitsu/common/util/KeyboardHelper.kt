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

package jp.co.soramitsu.common.util

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

const val KEYBOARD_COEFFICIENT = 0.15

class KeyboardHelper(
    private val contentView: View,
    private var listener: KeyboardListener? = null
) {

    var isKeyboardShowing = false
        private set

    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        val r = Rect()
        contentView.getWindowVisibleDisplayFrame(r)
        val screenHeight = contentView.rootView.height
        val keypadHeight = screenHeight - r.bottom

        if (keypadHeight > screenHeight * KEYBOARD_COEFFICIENT) {
            if (!isKeyboardShowing) {
                isKeyboardShowing = true
                listener?.onKeyboardShow()
            }
        } else {
            if (isKeyboardShowing) {
                isKeyboardShowing = false
                listener?.onKeyboardHide()
            }
        }
    }

    init {
        contentView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
    }

    fun release() {
        contentView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        listener = null
    }

    fun setKeyboardListener(listener: KeyboardListener) {
        this.listener = listener
    }

    interface KeyboardListener {

        fun onKeyboardShow()

        fun onKeyboardHide()
    }

    val isVisible: Boolean
        get() = ViewCompat.getRootWindowInsets(contentView)?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
}
