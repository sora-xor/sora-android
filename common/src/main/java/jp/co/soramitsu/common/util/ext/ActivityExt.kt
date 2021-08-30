/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util.ext

import android.app.Activity
import android.content.BroadcastReceiver
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun Activity.unregisterReceiverIfNeeded(receiver: BroadcastReceiver) {
    try {
        this.unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
    }
}

fun AppCompatActivity.runDelayed(
    durationInMillis: Long,
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
    block: () -> Unit,
): Job = lifecycleScope.launch(dispatcher) {
    delay(durationInMillis)
    block.invoke()
}
