/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
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
