package jp.co.soramitsu.common.domain

import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import jp.co.soramitsu.common.util.Event

class HealthChecker {

    enum class State {
        ALIVE,
        DEAD
    }

    private val health = BehaviorSubject.create<Boolean>()
    private val healthState = BehaviorSubject.create<State>()
    private val healthRestoredEventKeeper = PublishSubject.create<Event<Unit>>()

    fun connectionErrorHandled() {
        healthState.onNext(State.DEAD)
        health.onNext(false)
    }

    fun connectionStable() {
        if (State.DEAD == healthState.value) {
            healthRestoredEventKeeper.onNext(Event(Unit))
        }
        healthState.onNext(State.ALIVE)
        health.onNext(true)
    }

    fun observeHealthState(): Observable<Boolean> {
        return health
    }

    fun observeHealthRestoredEvents(): Observable<Event<Unit>> {
        return healthRestoredEventKeeper
    }
}