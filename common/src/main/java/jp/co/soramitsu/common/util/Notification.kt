/*
This file is part of the SORA network and Polkaswap app.

Copyright (c) 2020, 2021, Polka Biome Ltd. All rights reserved.
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package jp.co.soramitsu.common.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.co.soramitsu.common.R

object Notification {

    private const val CHANNEL_ID = "jp.co.soramitsu.notification.channel"

    fun checkNotificationChannel(manager: NotificationManagerCompat) {
        if (BuildUtils.sdkAtLeast(Build.VERSION_CODES.O)) {
            var notificationChannel = manager.getNotificationChannel(CHANNEL_ID)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    CHANNEL_ID, Const.SORA, NotificationManager.IMPORTANCE_LOW,
                )
                manager.createNotificationChannel(notificationChannel)
            }
        }
    }

    fun getBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_small_notification)
            .setLargeIcon(
                (
                    ContextCompat.getDrawable(
                        context,
                        R.drawable.notification_brand
                    ) as BitmapDrawable
                    ).bitmap
            )
}
