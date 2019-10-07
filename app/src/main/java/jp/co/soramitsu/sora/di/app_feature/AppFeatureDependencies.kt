/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.sora.di.app_feature

import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository

interface AppFeatureDependencies {

    fun userRepository(): UserRepository

    fun didRepository(): DidRepository

    fun pushHandler(): PushHandler
}