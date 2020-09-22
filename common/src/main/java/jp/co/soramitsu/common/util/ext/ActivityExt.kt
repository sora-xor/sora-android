package jp.co.soramitsu.common.util.ext

import android.app.Activity
import android.content.BroadcastReceiver

fun Activity.unregisterReceiverIfNeeded(receiver: BroadcastReceiver) {
    try {
        this.unregisterReceiver(receiver)
    } catch (e: IllegalArgumentException) {
    }
}