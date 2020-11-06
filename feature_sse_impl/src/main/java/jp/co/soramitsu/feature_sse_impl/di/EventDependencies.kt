/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_sse_impl.di

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.data.network.sse.SseClient
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface EventDependencies {

    fun preferences(): Preferences

    fun ethRepository(): EthereumRepository

    fun walletRepository(): WalletRepository

    fun didRepository(): DidRepository

    fun contextManager(): ContextManager

    fun sseClient(): SseClient

    fun appLinksProvider(): AppLinksProvider

    fun serializer(): Serializer

    fun cryptoAssistant(): CryptoAssistant

    fun healthChecker(): HealthChecker

    fun networkStateListener(): NetworkStateListener
}