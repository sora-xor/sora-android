package jp.co.soramitsu.feature_sse_api.di

import jp.co.soramitsu.feature_sse_api.EventsObservingStarter
import jp.co.soramitsu.feature_sse_api.interfaces.EventRepository

interface EventFeatureApi {

    fun eventRepository(): EventRepository

    fun eventsObservingStarter(): EventsObservingStarter
}