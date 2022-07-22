/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.inappupdate

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import jp.co.soramitsu.common.data.SoraPreferences
import jp.co.soramitsu.common.util.ext.safeCast
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class InAppUpdateManager(
    context: Context,
    private val soraPreferences: SoraPreferences
) : LifecycleObserver {

    interface UpdateManagerListener : LifecycleOwner {
        fun readyToShowFlexible(): Int?
        fun askUserToInstall()
    }

    companion object {
        private const val ARG_TIME = "arg_last_time_update_check"
        private const val PERIOD = 1000 * 60 * 60 * 12
    }

    private val googleUpdateManager = AppUpdateManagerFactory.create(context)
    private var mainActivity: UpdateManagerListener? = null

    private val updateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                mainActivity?.askUserToInstall()
            }
            else -> {
            }
        }
    }

    private suspend fun getUpdateInfoResult(): AppUpdateInfo =
        suspendCoroutine { continuation ->
            googleUpdateManager.appUpdateInfo.addOnSuccessListener {
                continuation.resume(it)
            }
        }

    suspend fun start(activity: UpdateManagerListener) {
        activity.lifecycle.addObserver(this)
        mainActivity = activity
        val updateInfo = getUpdateInfoResult()
        if (updateInfo.installStatus() == InstallStatus.DOWNLOADED) {
            mainActivity?.askUserToInstall()
        } else if (updateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
            mainActivity?.safeCast<Activity>()?.let { ma ->
                if (isResumed()) {
                    googleUpdateManager.startUpdateFlowForResult(
                        updateInfo,
                        AppUpdateType.IMMEDIATE,
                        ma,
                        1
                    )
                }
            }
        } else if (updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
            val now = Date().time
            val diff = now - soraPreferences.getLong(ARG_TIME, 0)
            if (diff > PERIOD && TimeUnit.DAYS.convert(
                    diff,
                    TimeUnit.MILLISECONDS
                ) >= 1
            ) {
                getType(updateInfo)?.let { type ->
                    if (isResumed()) {
                        mainActivity?.safeCast<Activity>()?.let { ma ->
                            when (type) {
                                AppUpdateType.FLEXIBLE -> {
                                    googleUpdateManager.registerListener(updateListener)
                                    mainActivity?.readyToShowFlexible()?.let { code ->
                                        soraPreferences.putLong(ARG_TIME, now)
                                        googleUpdateManager.startUpdateFlowForResult(
                                            updateInfo,
                                            type,
                                            ma,
                                            code
                                        )
                                    }
                                }
                                AppUpdateType.IMMEDIATE -> {
                                    soraPreferences.putLong(ARG_TIME, now)
                                    googleUpdateManager.startUpdateFlowForResult(
                                        updateInfo,
                                        type,
                                        ma,
                                        1
                                    )
                                }
                                else -> {
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun startUpdateFlexible() {
        googleUpdateManager.unregisterListener(updateListener)
        googleUpdateManager.completeUpdate()
    }

    fun flexibleDesire(answer: Int) {
        if (answer != Activity.RESULT_OK) {
            googleUpdateManager.unregisterListener(updateListener)
        }
    }

    private fun getType(info: AppUpdateInfo): Int? {
        return when {
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) && info.updatePriority() < 4 -> AppUpdateType.FLEXIBLE
            info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && info.updatePriority() >= 4 -> AppUpdateType.IMMEDIATE
            else -> null
        }
    }

    private fun isResumed(): Boolean {
        return mainActivity?.lifecycle?.currentState?.isAtLeast(
            Lifecycle.State.RESUMED
        ) == true
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        mainActivity?.lifecycle?.removeObserver(this)
        mainActivity = null
    }
}
