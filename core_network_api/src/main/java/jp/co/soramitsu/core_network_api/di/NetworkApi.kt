/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_api.di

import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import jp.co.soramitsu.core_network_api.domain.model.AppLinksProvider

interface NetworkApi {

    fun provideNetworkApiCreator(): NetworkApiCreator

    fun provideAuthHolder(): AuthHolder

    fun appLinksProvider(): AppLinksProvider
}