/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.content.Context
import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

class ResourceManager constructor(
    val context: Context
) {

    fun getString(resource: Int): String {
        return context.getString(resource)
    }

    @DrawableRes
    fun getResByName(drawableName: String): Int {
        return context.resources.getIdentifier(
            drawableName,
            "drawable",
            context.packageName
        )
    }

    fun getColor(res: Int): Int {
        return ContextCompat.getColor(context, res)
    }

    fun getQuantityString(id: Int, quantity: Int): String {
        return context.resources.getQuantityString(id, quantity)
    }

    fun getQuantityString(id: Int, quantity: Int, value: String): String {
        return context.resources.getQuantityString(id, quantity).format(value)
    }

    fun dp2px(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
}
