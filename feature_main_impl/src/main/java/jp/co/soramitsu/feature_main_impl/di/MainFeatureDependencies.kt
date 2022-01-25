/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.inappupdate.InAppUpdateManager
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_notification_api.domain.interfaces.NotificationRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletInteractor
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface MainFeatureDependencies {

    fun contextManager(): ContextManager

    fun userRepository(): UserRepository

    fun credentialsRepository(): CredentialsRepository

    fun ethRepository(): EthereumRepository

    fun walletInteractor(): WalletInteractor

    fun walletRepository(): WalletRepository

    fun appUpdateManager(): InAppUpdateManager

    fun resourceManager(): ResourceManager

    fun numbersFormatter(): NumbersFormatter

    fun dateTimeFormatter(): DateTimeFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker

    fun runtimeManager(): RuntimeManager

    fun qrCodeGenerator(): QrCodeGenerator

    fun deviceParamsProvider(): DeviceParamsProvider

    fun invitationHandler(): InvitationHandler

    fun debounceClickHandler(): DebounceClickHandler

    fun notificationRepository(): NotificationRepository

    fun clipboardManager(): ClipboardManager

    fun accountAvatar(): AccountAvatarGenerator

    fun connectionManager(): ConnectionManager

    fun deviceVibrator(): DeviceVibrator
}
