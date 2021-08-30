package jp.co.soramitsu.common.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PushHandler {

    private val pushEvents = MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun observeNewPushes(): Flow<String> {
        return pushEvents.asSharedFlow()
    }

    fun pushReceived() {
        pushEvents.tryEmit("")
    }
}
