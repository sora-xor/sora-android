/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_project_impl.di

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_network_api.NetworkApiCreator

interface ProjectFeatureDependencies {

    fun preferences(): Preferences

    fun networkApiCreator(): NetworkApiCreator

    fun appDatabase(): AppDatabase
}