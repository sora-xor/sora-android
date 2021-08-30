/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app_feature

import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository

interface AppFeatureDependencies {

    fun notificationRepository(): NotificationRepository

    fun userRepository(): UserRepository

    fun credentialsRepository(): CredentialsRepository

    fun pushHandler(): PushHandler

    fun coroutineManager(): CoroutineManager

    fun runtimeManager(): RuntimeManager
}
