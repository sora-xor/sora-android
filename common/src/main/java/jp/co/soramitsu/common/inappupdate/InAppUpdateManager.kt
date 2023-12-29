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

package jp.co.soramitsu.common.inappupdate

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import jp.co.soramitsu.common.data.SoraPreferences

class InAppUpdateManager(
    context: Context,
    private val soraPreferences: SoraPreferences
) : LifecycleEventObserver {

    interface UpdateManagerListener : LifecycleOwner {
        fun readyToShowFlexible(): Int?
        fun askUserToInstall()
    }

    companion object {
        private const val ARG_TIME = "arg_last_time_update_check"
        private const val PERIOD = 1000 * 60 * 60 * 12
    }

//    private val googleUpdateManager = AppUpdateManagerFactory.create(context)
    private var mainActivity: UpdateManagerListener? = null

//    private val updateListener = InstallStateUpdatedListener { state ->
//        when (state.installStatus()) {
//            InstallStatus.DOWNLOADED -> {
//                mainActivity?.askUserToInstall()
//            }
//
//            else -> {
//            }
//        }
//    }

//    private suspend fun getUpdateInfoResult(): AppUpdateInfo =
//        suspendCoroutine { continuation ->
//            googleUpdateManager.appUpdateInfo.addOnSuccessListener {
//                continuation.resume(it)
//            }
//        }

    suspend fun start(activity: UpdateManagerListener) {
        activity.lifecycle.addObserver(this)
        mainActivity = activity
//        val updateInfo = getUpdateInfoResult()
//        if (updateInfo.installStatus() == InstallStatus.DOWNLOADED) {
//            mainActivity?.askUserToInstall()
//        } else if (updateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
//            mainActivity?.safeCast<Activity>()?.let { ma ->
//                if (isResumed()) {
//                    googleUpdateManager.startUpdateFlowForResult(
//                        updateInfo,
//                        AppUpdateType.IMMEDIATE,
//                        ma,
//                        1
//                    )
//                }
//            }
//        } else if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
//            val now = Date().time
//            val diff = now - soraPreferences.getLong(ARG_TIME, 0)
//            if (diff > PERIOD && TimeUnit.DAYS.convert(
//                    diff,
//                    TimeUnit.MILLISECONDS
//                ) >= 1
//            ) {
//                getType(updateInfo)?.let { type ->
//                    if (isResumed()) {
//                        mainActivity?.safeCast<Activity>()?.let { ma ->
//                            when (type) {
//                                AppUpdateType.FLEXIBLE -> {
//                                    googleUpdateManager.registerListener(updateListener)
//                                    mainActivity?.readyToShowFlexible()?.let { code ->
//                                        soraPreferences.putLong(ARG_TIME, now)
//                                        googleUpdateManager.startUpdateFlowForResult(
//                                            updateInfo,
//                                            type,
//                                            ma,
//                                            code
//                                        )
//                                    }
//                                }
//
//                                AppUpdateType.IMMEDIATE -> {
//                                    soraPreferences.putLong(ARG_TIME, now)
//                                    googleUpdateManager.startUpdateFlowForResult(
//                                        updateInfo,
//                                        type,
//                                        ma,
//                                        1
//                                    )
//                                }
//
//                                else -> {
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
    }

    fun startUpdateFlexible() {
//        googleUpdateManager.unregisterListener(updateListener)
//        googleUpdateManager.completeUpdate()
    }

    fun flexibleDesire(answer: Int) {
        if (answer != Activity.RESULT_OK) {
//            googleUpdateManager.unregisterListener(updateListener)
        }
    }

//    private fun getType(info: AppUpdateInfo): Int? {
//        return when {
//            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && info.updatePriority() < 4 -> AppUpdateType.FLEXIBLE
//            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && info.updatePriority() >= 4 -> AppUpdateType.IMMEDIATE
//            else -> null
//        }
//    }

    private fun isResumed(): Boolean {
        return mainActivity?.lifecycle?.currentState?.isAtLeast(
            Lifecycle.State.RESUMED
        ) == true
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_STOP) {
            mainActivity?.lifecycle?.removeObserver(this)
            mainActivity = null
        }
    }
}
