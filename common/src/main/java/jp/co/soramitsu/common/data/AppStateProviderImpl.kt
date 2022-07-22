/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import jp.co.soramitsu.common.domain.AppStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppStateProviderImpl : AppStateProvider, LifecycleEventObserver {

    private val subject = MutableStateFlow(AppStateProvider.AppEvent.ON_CREATE)

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override val isForeground: Boolean
        get() = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    override val isBackground: Boolean
        get() = !isForeground

    override fun observeState(): Flow<AppStateProvider.AppEvent> = subject.asStateFlow()

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_START -> {
                subject.value = AppStateProvider.AppEvent.ON_RESUME
            }
            Lifecycle.Event.ON_STOP -> {
                subject.value = AppStateProvider.AppEvent.ON_PAUSE
            }
            Lifecycle.Event.ON_CREATE -> {
                subject.value = AppStateProvider.AppEvent.ON_CREATE
            }
            Lifecycle.Event.ON_DESTROY -> {
                subject.value = AppStateProvider.AppEvent.ON_DESTROY
            }
        }
    }
}
