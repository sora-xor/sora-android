/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.content.Context

fun Int.toPx(context: Context) = this * context.resources.displayMetrics.density

fun Int.trailingZeros(): Int =
    this.toString(2).reversed().toCharArray().takeWhile { it == '0' }.count()
fun Int.colorToHex(): String = java.lang.String.format("#%06X", 0xFFFFFF and this)
