/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_information_impl.di

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder

interface InformationFeatureDependencies {

    fun preferences(): Preferences

    fun authHolder(): AuthHolder

    fun networkApiCreator(): NetworkApiCreator

    fun serializer(): Serializer
}