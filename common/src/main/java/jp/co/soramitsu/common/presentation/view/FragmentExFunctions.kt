/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.view

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

fun Fragment.setMultipleOnClickListeners(vararg views: View, body: () -> Unit) {
    views.forEach {
        it.setOnClickListener {
            body.invoke()
        }
    }
}

fun Fragment.toast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(activity, message, duration).show()
}

fun Fragment.toast(@StringRes messageResId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(activity, messageResId, duration).show()
}

fun Fragment.requestPermission(permission: String, permisionRequestKey: Int): Boolean {

    var permissionRequested = false

    if (activity != null) {
        if (ContextCompat.checkSelfPermission(activity!!,
                        permission)
                != PackageManager.PERMISSION_GRANTED) {

//           TODO add permision explonasion screeen
//
//            if (ActivityCompat.shouldShowRequestPermissionRationale(activity!!,
//                            permission)) {
//                // Show an explanation to the user *asynchronously* -- don't block
//                // this thread waiting for the user's response! After the user
//                // sees the explanation, try again to request the permission.
//            } else {

            requestPermissions(arrayOf(permission),
                    permisionRequestKey)
            permissionRequested = true
        }
    }
    return permissionRequested
}

fun Fragment.openSoftKeyboard(view: View) {
    val inputMethodManager = activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    view.requestFocus()
    inputMethodManager.toggleSoftInput(
            InputMethodManager.SHOW_IMPLICIT, 0)
}

fun Fragment.hideSoftKeyboard(activity: FragmentActivity?) {
    if (activity != null) {
        val inputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.toggleSoftInput(
            InputMethodManager.SHOW_IMPLICIT, 0)
    }
}