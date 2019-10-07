/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.os.CountDownTimer

class TimerWrapper {

    private lateinit var onTickCallback: ((minutes: Int, seconds: Int) -> Unit)
    private lateinit var onFinishCallback: (() -> Unit)

    private var timer: Timer? = null

    fun setTimerCallbacks(onTick: ((minutes: Int, seconds: Int) -> Unit), onFinish: (() -> Unit)) {
        onTickCallback = onTick
        onFinishCallback = onFinish
    }

    fun start(millisInFuture: Long, interval: Long) {
        timer = Timer(millisInFuture, interval)
        timer?.start()
    }

    fun cancel() {
        timer?.cancel()
    }

    private inner class Timer(millisInFuture: Long, interval: Long) : CountDownTimer(millisInFuture, interval) {
        override fun onTick(millisUntilFinished: Long) {
            val time = (millisUntilFinished / 1000).toInt()
            val minutes = time / 60
            val seconds = time % 60
            onTickCallback(minutes, seconds)
        }

        override fun onFinish() {
            onFinishCallback()
        }
    }
}
