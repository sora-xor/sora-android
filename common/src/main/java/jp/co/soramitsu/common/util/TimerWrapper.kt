/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.util

import android.os.CountDownTimer
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

class TimerWrapper {

    companion object {
        private const val TIME_LEFT_FORMAT = "%02d:%02d:%02d"
        private const val TIME_LEFT_FORMAT_SHORT = "%02d:%02d"
        private const val UPDATE_INTERVAL_DEFAULT = 1000L
    }

    private lateinit var timerTickSubject: BehaviorSubject<Long>
    private lateinit var timer: Timer

    private var isStarted = false

    fun start(millisInFuture: Long, interval: Long = UPDATE_INTERVAL_DEFAULT): Observable<Long> {
        timerTickSubject = BehaviorSubject.create()
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

    private inner class Timer(millisInFuture: Long, interval: Long) : CountDownTimer(millisInFuture, interval) {
        override fun onTick(millisUntilFinished: Long) {
            timerTickSubject.onNext(millisUntilFinished)
        }

        override fun onFinish() {
            timerTickSubject.onComplete()
        }
    }
}