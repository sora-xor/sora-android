/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_votable_impl.di

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.core_db.AppDatabase

interface VotableFeatureDependencies {

    fun preferences(): Preferences

    fun appDatabase(): AppDatabase
}
