/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.data.repository

import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationDatasource
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository

class NotificationRepositoryImpl(
    private val notificationDatasource: NotificationDatasource
) : NotificationRepository {

    override fun saveDeviceToken(notificationToken: String) {
        notificationDatasource.savePushToken(notificationToken)
        notificationDatasource.saveIsPushTokenUpdateNeeded(true)
    }
}
