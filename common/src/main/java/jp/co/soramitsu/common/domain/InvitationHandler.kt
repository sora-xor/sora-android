package jp.co.soramitsu.common.domain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class InvitationHandler {

    private val inviteEvents =
        MutableSharedFlow<String>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun observeInvitationApplies(): Flow<String> {
        return inviteEvents.asSharedFlow()
    }

    fun invitationApplied() {
        inviteEvents.tryEmit("")
    }
}
