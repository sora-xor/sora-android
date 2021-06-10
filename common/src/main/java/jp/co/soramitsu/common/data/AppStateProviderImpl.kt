package jp.co.soramitsu.common.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject
import jp.co.soramitsu.common.domain.AppStateProvider
import javax.inject.Inject

class AppStateProviderImpl @Inject constructor() : AppStateProvider, LifecycleObserver {
    private val subject = BehaviorSubject.create<Boolean>()

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override val isOpened: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)
    override val isClosed: Boolean
        get() = !isOpened

    override fun observeState(): Observable<Boolean> = subject

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onOpened() = subject.onNext(true)

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onClosed() = subject.onNext(false)
}
