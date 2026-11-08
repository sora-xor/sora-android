package jp.co.soramitsu.ui_core.modifier.debounce

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

@OptIn(FlowPreview::class)
class DebounceHandler(
    scope: CoroutineScope,
    coroutineContext: CoroutineContext = Dispatchers.Main,
    private val debounce: Long = 300,
) {

    private val debounceState = MutableStateFlow {}

    init {
        scope.launch(coroutineContext) {
            debounceState
                .debounce(debounce)
                .collect { onClick ->
                    onClick.invoke()
                }
        }
    }

    fun debounceClicks(onClick: () -> Unit) {
        debounceState.value = onClick
    }
}
