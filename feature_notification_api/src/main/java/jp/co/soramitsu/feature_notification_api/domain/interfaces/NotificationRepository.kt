/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_api.domain.interfaces

interface NotificationRepository {

    fun saveDeviceToken(notificationToken: String)
}
