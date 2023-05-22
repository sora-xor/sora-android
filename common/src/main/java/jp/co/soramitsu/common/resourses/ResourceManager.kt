/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.resourses

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.lang.ref.WeakReference

class ResourceManager : Application.ActivityLifecycleCallbacks {

    private var resourcesNullable: Resources? = null
    private val resources: Resources
        get() {
            return resourcesNullable ?: throw IllegalStateException("ResourceManager: no resources")
        }

    private var contextNullable: WeakReference<Context>? = null
    private val context: Context?
        get() {
            return contextNullable?.get()
        }

    fun getString(resource: Int): String {
        return resources.getString(resource)
    }

    fun getString(resource: Int, vararg formatArgs: Any): String {
        return resources.getString(resource, *formatArgs)
    }

//    @DrawableRes
//    fun getResByName(drawableName: String): Int {
//        return context.resources.getIdentifier(
//            drawableName,
//            "drawable",
//            context.packageName,
//        )
//    }

    fun getColor(res: Int): Int {
        return context?.let {
            ContextCompat.getColor(it, res)
        } ?: 0xffffff
    }

    fun getQuantityString(id: Int, quantity: Int): String {
        return resources.getQuantityString(id, quantity)
    }

    fun getQuantityString(id: Int, quantity: Int, value: String): String {
        return resources.getQuantityString(id, quantity).format(value)
    }

    fun dp2px(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        resourcesNullable = activity.resources
        contextNullable = WeakReference(activity)
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivityDestroyed(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
}
