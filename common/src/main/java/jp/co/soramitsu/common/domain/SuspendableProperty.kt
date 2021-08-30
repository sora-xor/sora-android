package jp.co.soramitsu.common.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

@ExperimentalCoroutinesApi
class SuspendableProperty<T>(r: Int) {
    private val value = MutableSharedFlow<T>(replay = r, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun invalidate() {
        value.resetReplayCache()
    }

    fun set(new: T) {
        value.tryEmit(new) // always successful, since BufferOverflow.DROP_OLDEST is used
    }

    suspend fun get(): T = value.first()

    fun observe(): Flow<T> = value.asSharedFlow()
}

@ExperimentalCoroutinesApi
suspend inline fun <T, R> SuspendableProperty<T>.useValue(action: (T) -> R): R = action(get())
