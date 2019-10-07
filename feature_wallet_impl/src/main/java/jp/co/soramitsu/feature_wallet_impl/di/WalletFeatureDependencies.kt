/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context

import jp.co.soramitsu.common.util.PrefsUtil
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder

interface WalletFeatureDependencies {

    fun context(): Context

    fun prefsUtil(): PrefsUtil

    fun authHolder(): AuthHolder

    fun networkApiCreator(): NetworkApiCreator

    fun appDatabase(): AppDatabase
}