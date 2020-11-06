package jp.co.soramitsu.common.domain

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject

class PushHandler {

    private val pushEvents = PublishSubject.create<String>()

    fun observeNewPushes(): Observable<String> {
        return pushEvents
    }

    fun pushReceived() {
        pushEvents.onNext("")
    }
}