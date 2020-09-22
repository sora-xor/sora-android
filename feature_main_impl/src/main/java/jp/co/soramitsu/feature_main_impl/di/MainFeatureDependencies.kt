/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.did.DidRepository
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.EthServiceStarter
import jp.co.soramitsu.feature_ethereum_api.EthStatusPollingServiceStarter
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_sse_api.EventsObservingStarter
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.ReferendumRepository
import jp.co.soramitsu.feature_votable_api.domain.interfaces.VotesDataSource
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface MainFeatureDependencies {

    fun contextManager(): ContextManager

    fun userRepository(): UserRepository

    fun projectRepository(): ProjectRepository

    fun referendumRepository(): ReferendumRepository

    fun votesDataSource(): VotesDataSource

    fun didRepository(): DidRepository

    fun ethRepository(): EthereumRepository

    fun walletRepository(): WalletRepository

    fun informationRepository(): InformationRepository

    fun resourceManager(): ResourceManager

    fun numbersFormatter(): NumbersFormatter

    fun dateTimeFormatter(): DateTimeFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker

    fun qrCodeGenerator(): QrCodeGenerator

    fun deviceParamsProvider(): DeviceParamsProvider

    fun invitationHandler(): InvitationHandler

    fun debounceClickHandler(): DebounceClickHandler

    fun notificationRepository(): NotificationRepository

    fun eventsObservingStarter(): EventsObservingStarter

    fun ethServiceStarter(): EthServiceStarter

    fun ethStatusPollingServiceStarter(): EthStatusPollingServiceStarter

    fun languagesHolder(): LanguagesHolder
}