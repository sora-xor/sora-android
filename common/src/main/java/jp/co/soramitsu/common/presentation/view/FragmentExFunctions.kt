package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

fun Fragment.openSoftKeyboard(view: View) {
    val inputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view.requestFocus()
    inputMethodManager.toggleSoftInput(
        InputMethodManager.SHOW_IMPLICIT, 0
    )
}

fun Fragment.hideSoftKeyboard(activity: FragmentActivity?) {
    if (activity != null) {
        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.toggleSoftInput(
            InputMethodManager.SHOW_IMPLICIT, 0
        )
    }
}
