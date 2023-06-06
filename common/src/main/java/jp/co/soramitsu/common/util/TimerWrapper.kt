/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: BSD-4-Clause

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright notice, this list
of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright notice, this
list of conditions and the following disclaimer in the documentation and/or other
materials provided with the distribution.

All advertising materials mentioning features or use of this software must display
the following acknowledgement: This product includes software developed by Polka Biome
Ltd., SORA, and Polkaswap.

Neither the name of the Polka Biome Ltd. nor the names of its contributors may be used
to endorse or promote products derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY Polka Biome Ltd. AS IS AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL Polka Biome Ltd. BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package jp.co.soramitsu.common.util

import android.os.CountDownTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class TimerWrapper {

    companion object {
        private const val TIME_LEFT_FORMAT = "%02d:%02d:%02d"
        private const val TIME_LEFT_FORMAT_SHORT = "%02dh %02dm"
        private const val UPDATE_INTERVAL_DEFAULT = 1000L
    }

    private var timerTickSubject = MutableStateFlow<Long>(0)
    private lateinit var timer: Timer

    private var isStarted = false

    fun start(millisInFuture: Long, interval: Long = UPDATE_INTERVAL_DEFAULT): Flow<Long> {
        timer = Timer(millisInFuture, interval)
        timer.start()
        isStarted = true
        return timerTickSubject
    }

    fun cancel() {
        if (isStarted) {
            timer.cancel()
            isStarted = false
        }
    }

    fun calcTimeLeft(millisLeft: Long): String {
        val hoursLeft = millisLeft / 1000 / 60 / 60
        val minutesLeft = (millisLeft - hoursLeft * 60 * 60 * 1000) / 1000 / 60
        val secondsLeft = (millisLeft - hoursLeft * 60 * 60 * 1000 - minutesLeft * 60 * 1000) / 1000
        return TIME_LEFT_FORMAT.format(hoursLeft, minutesLeft, secondsLeft)
    }

    fun formatTime(millis: Long): String {
        val hoursLeft = millis / 1000 / 60 / 60
        val minutesLeft = (millis - hoursLeft * 60 * 60 * 1000) / 1000 / 60
        return if (millis < 60 * 60 * 1000) {
            val secondsLeft = (millis - hoursLeft * 60 * 60 * 1000 - minutesLeft * 60 * 1000) / 1000
            TIME_LEFT_FORMAT_SHORT.format(minutesLeft, secondsLeft)
        } else {
            TIME_LEFT_FORMAT_SHORT.format(hoursLeft, minutesLeft)
        }
    }

    fun isStarted() = isStarted

    private inner class Timer(millisInFuture: Long, interval: Long) :
        CountDownTimer(millisInFuture, interval) {
        override fun onTick(millisUntilFinished: Long) {
            timerTickSubject.value = millisUntilFinished
        }

        override fun onFinish() {
        }
    }
}
