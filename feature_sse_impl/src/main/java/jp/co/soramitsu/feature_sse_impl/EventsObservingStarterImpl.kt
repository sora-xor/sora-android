package jp.co.soramitsu.feature_sse_impl

import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.feature_sse_api.EventsObservingStarter
import jp.co.soramitsu.feature_sse_impl.presentation.EventService

class EventsObservingStarterImpl(
    private val contextManager: ContextManager
) : EventsObservingStarter {

    override fun startObserver() {
        EventService.start(contextManager.getContext())
    }

    override fun stopObserver() {
        EventService.stop(contextManager.getContext())
    }
}