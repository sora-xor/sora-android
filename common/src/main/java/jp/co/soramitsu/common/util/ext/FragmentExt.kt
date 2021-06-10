/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import androidx.annotation.DimenRes
import androidx.fragment.app.Fragment

fun Fragment.showBrowser(link: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(link) }
    startActivity(intent)
}

fun Fragment.dp2px(dp: Int): Int =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp.toFloat(),
        requireContext().resources.displayMetrics
    ).toInt()

fun Fragment.dpRes2px(@DimenRes res: Int): Int =
    requireContext().resources.getDimensionPixelSize(res)
