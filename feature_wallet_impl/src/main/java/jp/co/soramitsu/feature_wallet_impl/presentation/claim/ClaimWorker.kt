/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.presentation.claim

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.feature_wallet_api.di.WalletFeatureApi
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.model.MigrationStatus
import jp.co.soramitsu.feature_wallet_impl.di.WalletFeatureComponent
import javax.inject.Inject

class ClaimWorker(private val appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "ClaimWorker"
        const val NOTIFICATION_ID = 42
        const val CHANNEL_ID = "Claim progress"

        fun start(context: Context) {
            val ethereumWorkRequest = OneTimeWorkRequestBuilder<ClaimWorker>()
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, ethereumWorkRequest)
        }
    }

    @Inject
    lateinit var walletInteractor: WalletInteractor

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        FeatureUtils.getFeature<WalletFeatureComponent>(appContext, WalletFeatureApi::class.java)
            .claimWorkerComponent()
            .build()
            .inject(this)

        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_small_notification)
            .setLargeIcon(
                (
                    ContextCompat.getDrawable(
                        appContext,
                        R.mipmap.ic_launcher
                    ) as BitmapDrawable
                    ).bitmap
            )
            .setContentTitle(appContext.getString(R.string.claim_notification_text))
            .build()

        val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, notification)
        setForegroundAsync(foregroundInfo)

        FirebaseCrashlytics.getInstance().log("Migration started")
        val result = runCatching { walletInteractor.migrate() }.getOrElse {
            FirebaseCrashlytics.getInstance().recordException(it)
            false
        }
        walletInteractor.saveMigrationStatus(if (result) MigrationStatus.SUCCESS else MigrationStatus.FAILED)

        return Result.success()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var notificationChannel = notificationManager?.getNotificationChannel(CHANNEL_ID)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    CHANNEL_ID, TAG, NotificationManager.IMPORTANCE_LOW
                )
                notificationManager?.createNotificationChannel(notificationChannel)
            }
        }
    }
}
