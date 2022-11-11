/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.annotation.DimenRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import jp.co.soramitsu.common.domain.ResponseCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun Fragment.safeStartActivity(intent: Intent, responseCode: ResponseCode) {
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(
            requireContext(),
            getString(responseCode.messageResource),
            Toast.LENGTH_LONG
        ).show()
    }
}

inline fun <T> CoroutineScope.lazyAsync(crossinline producer: suspend () -> T) = lazy {
    async { producer() }
}

fun Fragment.dp2px(dp: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        requireContext().resources.displayMetrics
    ).toInt()

fun Fragment.dpRes2px(@DimenRes res: Int): Int =
    requireContext().resources.getDimensionPixelSize(res)

fun <T : Parcelable> Fragment.requireParcelable(key: String): T {
    return requireNotNull(requireArguments().getParcelable(key), { "Argument [$key] not found" })
}

fun Fragment.runDelayed(
    durationInMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit,
): Job = viewLifecycleOwner.lifecycle.coroutineScope.launch(dispatcher) {
    delay(durationInMillis)
    if (isActive) {
        block.invoke()
    }
}

fun Fragment.onBackPressed(block: () -> Unit) {
    requireActivity().onBackPressedDispatcher.addCallback(
        viewLifecycleOwner,
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                block()
            }
        }
    )
}

fun Fragment.hideSoftKeyboard() {
    requireActivity().hideSoftKeyboard()
}

fun Fragment.openSoftKeyboard(view: View) {
    requireActivity().openSoftKeyboard(view)
}
