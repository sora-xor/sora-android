/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.logger

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.orhanobut.logger.Logger
import kotlinx.coroutines.CancellationException

object FirebaseWrapper {
    private val blackList = listOf(CancellationException::class)

    fun recordException(t: Throwable) {
        blackList.forEach { kClass ->
            if (!kClass.isInstance(t)) {
                Logger.e(t, "ERROR")
                FirebaseCrashlytics.getInstance().recordException(t)
            }
        }
    }

    fun log(message: String) {
        Logger.d("SORALOG $message")
        FirebaseCrashlytics.getInstance().log(message)
    }
}
