package jp.co.soramitsu.common.resourses

import android.util.TypedValue
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceManager @Inject constructor(
    private val contextManager: ContextManager
) {

    fun getString(resource: Int): String {
        return contextManager.getContext().getString(resource)
    }

    @DrawableRes
    fun getResByName(drawableName: String): Int {
        return contextManager.getContext().resources.getIdentifier(
            drawableName,
            "drawable",
            contextManager.getContext().packageName
        )
    }

    fun getColor(res: Int): Int {
        return ContextCompat.getColor(contextManager.getContext(), res)
    }

    fun getQuantityString(id: Int, quantity: Int): String {
        return contextManager.getContext().resources.getQuantityString(id, quantity)
    }

    fun getQuantityString(id: Int, quantity: Int, value: String): String {
        return contextManager.getContext().resources.getQuantityString(id, quantity).format(value)
    }

    fun dp2px(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            contextManager.getContext().resources.displayMetrics
        ).toInt()
}
