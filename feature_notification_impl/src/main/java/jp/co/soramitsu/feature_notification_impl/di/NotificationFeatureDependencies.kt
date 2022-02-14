/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_notification_impl.di

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.SoraPreferences

interface NotificationFeatureDependencies {

    fun encryptedPreferences(): EncryptedPreferences

    fun preferences(): SoraPreferences
}
