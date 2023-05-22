/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.presentation.compose.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun keyboardState(): State<Boolean> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val isResumed = lifecycle.currentState == Lifecycle.State.RESUMED
    return rememberUpdatedState(WindowInsets.isImeVisible && isResumed)
}
