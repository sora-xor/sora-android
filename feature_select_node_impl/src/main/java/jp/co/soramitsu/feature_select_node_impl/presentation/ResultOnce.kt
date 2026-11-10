package jp.co.soramitsu.feature_select_node_impl.presentation

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle

/**
 * Delivers a back stack result once. The handle keeps the last value, so without clearing it every
 * later return to the screen, such as Back on the PIN screen, would deliver the old result again.
 */
internal fun <T : Any> SavedStateHandle.observeResultOnce(
    key: String,
    owner: LifecycleOwner,
    onResult: (T) -> Unit,
) {
    getLiveData<T?>(key).observe(owner) { result ->
        if (result != null) {
            this[key] = null
            onResult(result)
        }
    }
}
