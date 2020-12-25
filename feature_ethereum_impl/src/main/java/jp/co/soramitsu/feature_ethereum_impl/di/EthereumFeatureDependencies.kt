/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_ethereum_impl.di

import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.DCApiCreator
import jp.co.soramitsu.common.data.network.NetworkApiCreator
import jp.co.soramitsu.common.data.network.SoranetApiCreator
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.core_db.AppDatabase
import okhttp3.OkHttpClient
import javax.inject.Named

interface EthereumFeatureDependencies {

    fun didRepository(): DidRepository

    fun networkApiCreator(): NetworkApiCreator

    fun soranetApiCreator(): SoranetApiCreator

    fun dcApiCreator(): DCApiCreator

    fun healthChecker(): HealthChecker

    fun encryptedPreferences(): EncryptedPreferences

    fun preferences(): Preferences

    fun serializer(): Serializer

    fun appDatabase(): AppDatabase

    fun appLinksProvider(): AppLinksProvider

    fun contextManager(): ContextManager

    @Named("WEB3J_CLIENT") fun okhttpClient(): OkHttpClient.Builder
}