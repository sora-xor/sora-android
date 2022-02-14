/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

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

    private val subject = MutableStateFlow(AppStateProvider.AppEvent.ON_CREATE)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    override val isBackground: Boolean
        get() = !isForeground

    override fun observeState(): Flow<AppStateProvider.AppEvent> = subject.asStateFlow()

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onForeground() {
        subject.value = AppStateProvider.AppEvent.ON_RESUME
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onBackground() {
        subject.value = AppStateProvider.AppEvent.ON_PAUSE
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        subject.value = AppStateProvider.AppEvent.ON_CREATE
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        subject.value = AppStateProvider.AppEvent.ON_DESTROY
    }
}
