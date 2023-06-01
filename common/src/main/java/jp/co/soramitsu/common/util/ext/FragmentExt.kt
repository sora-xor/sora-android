/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.util.ext

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.annotation.DimenRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

fun Context.safeStartActivity(intent: Intent, responseCode: ResponseCode) {
    try {
        startActivity(intent)
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(
            this,
            getString(responseCode.messageResource),
            Toast.LENGTH_LONG
        ).show()
    }
}

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

fun Fragment.hideSoftKeyboard() {
    requireActivity().hideSoftKeyboard()
}

fun Fragment.openSoftKeyboard(view: View) {
    requireActivity().openSoftKeyboard(view)
}

fun Fragment.setStatusBarColor(color: Color) {
    this.requireActivity().window.statusBarColor = color.toArgb()
}

fun Fragment.setNavbarColor(color: Color) {
    this.requireActivity().window.navigationBarColor = color.toArgb()
}
