/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
            else -> {}
        }
    }
}
