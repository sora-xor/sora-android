package jp.co.soramitsu.feature_sse_api

interface EventsObservingStarter {

    fun startObserver()

    fun stopObserver()
}