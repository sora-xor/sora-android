package jp.co.soramitsu.common.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import jp.co.soramitsu.common.domain.AppStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class AppStateProviderImpl @Inject constructor() : AppStateProvider, LifecycleObserver {

    private val subject = MutableStateFlow(true)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    override val isBackground: Boolean
        get() = !isForeground

    override fun observeState(): Flow<Boolean> = subject.asStateFlow()

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onForeground() {
        subject.value = true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onBackground() {
        subject.value = false
    }
}
