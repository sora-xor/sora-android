/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app_feature

import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository

interface AppFeatureDependencies {

    fun notificationRepository(): NotificationRepository

    fun userRepository(): UserRepository

    fun didRepository(): DidRepository

    fun pushHandler(): PushHandler
}