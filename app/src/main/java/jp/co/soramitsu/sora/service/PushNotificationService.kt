/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.service

import android.annotation.TargetApi
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import jp.co.soramitsu.common.di.api.FeatureUtils
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.feature_main_impl.presentation.MainActivity
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.sora.R
import jp.co.soramitsu.sora.di.app_feature.AppFeatureComponent
import java.util.Random
import javax.inject.Inject

const val ADMIN_CHANNEL_ID = "admin_channel"

class PushNotificationService : FirebaseMessagingService() {

    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var pushHandler: PushHandler

    override fun onCreate() {
        super.onCreate()

        val component: AppFeatureComponent =
            FeatureUtils.getFeature(this, AppFeatureComponent::class.java)
        component.inject(this)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_ONE_SHOT)

        val notificationId = Random().nextInt(60000)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupChannels(notificationManager)
        }

        val notificationTitle = remoteMessage!!.data["title"] ?: getString(R.string.app_name)
        val notificationBody = remoteMessage.notification!!.body

        val notificationBuilder = NotificationCompat.Builder(this, ADMIN_CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_small_notification)
            .setLargeIcon((application.getDrawable(R.mipmap.ic_launcher) as BitmapDrawable).bitmap)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notificationId, notificationBuilder.build())

        pushHandler.pushReceived()
    }

    override fun onNewToken(s: String) {
        s?.let { notificationRepository.saveDeviceToken(it) }
        super.onNewToken(s)
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun setupChannels(notificationManager: NotificationManager) {
        val channelName = getString(R.string.channel_name)
        val channelDescription = getString(R.string.channel_description)

        val adminChannel = NotificationChannel(
            ADMIN_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = channelDescription
            enableLights(true)
            lightColor = Color.RED
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(adminChannel)
    }
}
