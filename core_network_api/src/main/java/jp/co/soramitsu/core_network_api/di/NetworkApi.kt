/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.core_network_api.di

import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import javax.inject.Named

interface NetworkApi {

    fun provideNetworkApiCreator(): NetworkApiCreator

    fun provideAuthHolder(): AuthHolder

    @Named("DEFAULT_MARKET_URL") fun provideDefaultMarketUrl(): String

    @Named("INVITE_LINK_URL") fun provideInviteLinkUrl(): String
}