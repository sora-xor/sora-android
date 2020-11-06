package jp.co.soramitsu.common.util

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.ViewCompat

class ScreenshotBlockHelper(
    private val activity: Activity
) {

    fun enableScreenshoting() {
        setSecureEnabled(false)
    }

    fun disableScreenshoting() {
        setSecureEnabled(true)
    }

    private fun setSecureEnabled(enabled: Boolean) {
        val changed = isSecureEnabled() != enabled
        if (changed) {
            updateSecureFlags(enabled)
        }

        with(activity) {
            if (changed && ViewCompat.isAttachedToWindow(window.decorView)) {
                windowManager.removeViewImmediate(window.decorView)
                windowManager.addView(window.decorView, window.attributes)
            }
        }
    }

    private fun updateSecureFlags(enabled: Boolean) {
        if (enabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun isSecureEnabled() = (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
}