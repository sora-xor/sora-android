/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.data.network

import jp.co.soramitsu.common.data.network.auth.AuthHolder
import jp.co.soramitsu.common.data.network.sse.SseClient
import jp.co.soramitsu.common.domain.AppLinksProvider

interface NetworkApi {

    fun provideNetworkApiCreator(): NetworkApiCreator

    fun provideSoranetApiCreator(): SoranetApiCreator

    fun provideDCApiCreator(): DCApiCreator

    fun provideAuthHolder(): AuthHolder

    fun appLinksProvider(): AppLinksProvider

    fun provideSseClient(): SseClient
}