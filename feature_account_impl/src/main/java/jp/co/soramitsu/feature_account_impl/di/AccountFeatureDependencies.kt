/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.di

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.core_db.AppDatabase

interface AccountFeatureDependencies {

    fun encryptedPreferences(): EncryptedPreferences

    fun preferences(): Preferences

    fun networkApiCreator(): NetworkApiCreator

    fun appVersionProvider(): AppVersionProvider

    fun resourceManager(): ResourceManager

    fun appDatabase(): AppDatabase

    fun appLinksProvider(): AppLinksProvider

    fun deviceParams(): DeviceParamsProvider

    fun languagesHolder(): LanguagesHolder

    fun serializer(): Serializer

    fun numbersFormatter(): NumbersFormatter
}