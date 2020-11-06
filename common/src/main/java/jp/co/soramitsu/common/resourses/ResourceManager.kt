package jp.co.soramitsu.common.resourses

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

    fun getColor(res: Int): Int {
        return ContextCompat.getColor(contextManager.getContext(), res)
    }

    fun getQuantityString(id: Int, quantity: Int): String {
        return contextManager.getContext().resources.getQuantityString(id, quantity)
    }

    fun getQuantityString(id: Int, quantity: Int, value: String): String {
        return contextManager.getContext().resources.getQuantityString(id, quantity).format(value)
    }
}