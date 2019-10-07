/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_account_impl.di

import android.content.Context
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import javax.inject.Named

interface AccountFeatureDependencies {

    fun context(): Context

    fun prefsUtil(): PrefsUtil

    fun networkApiCreator(): NetworkApiCreator

    fun appVersionProvider(): AppVersionProvider

    fun resourceManager(): ResourceManager

    fun appDatabase(): AppDatabase

    @Named("DEFAULT_MARKET_URL") fun provideMarketUrl(): String

    @Named("INVITE_LINK_URL") fun provideInviteLinkUrl(): String
}