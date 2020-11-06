package jp.co.soramitsu.common.util.ext

import android.content.Context

fun Int.toPx(context: Context) = this * context.resources.displayMetrics.density