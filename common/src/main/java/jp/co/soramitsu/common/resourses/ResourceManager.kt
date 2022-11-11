/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

class ResourceManager {

    fun getString(resource: Int): String {
        return ContextManager.context.getString(resource)
    }

    fun getString(resource: Int, vararg formatArgs: Any): String {
        return ContextManager.context.getString(resource, *formatArgs)
    }

    @DrawableRes
    fun getResByName(drawableName: String): Int {
        return ContextManager.context.resources.getIdentifier(
            drawableName,
            "drawable",
            ContextManager.context.packageName
        )
    }

    fun getColor(res: Int): Int {
        return ContextCompat.getColor(ContextManager.context, res)
    }

    fun getQuantityString(id: Int, quantity: Int): String {
        return ContextManager.context.resources.getQuantityString(id, quantity)
    }

    fun getQuantityString(id: Int, quantity: Int, value: String): String {
        return ContextManager.context.resources.getQuantityString(id, quantity).format(value)
    }

    fun dp2px(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            ContextManager.context.resources.displayMetrics
        ).toInt()
}
