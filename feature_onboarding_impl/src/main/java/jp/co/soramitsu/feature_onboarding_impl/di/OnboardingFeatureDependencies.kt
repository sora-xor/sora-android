/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_onboarding_impl.di

import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository
import jp.co.soramitsu.feature_main_api.launcher.MainStarter
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface OnboardingFeatureDependencies {

    fun userRepository(): UserRepository

    fun ethereumRepository(): EthereumRepository

    fun credentialsRepository(): CredentialsRepository

    fun mainStarter(): MainStarter

    fun walletRepository(): WalletRepository

    fun runtimeManager(): RuntimeManager

    fun invitationHandler(): InvitationHandler

    fun debounceClickHandler(): DebounceClickHandler

    fun numbersFormatter(): NumbersFormatter

    fun deviceVibrator(): DeviceVibrator

    fun connectionManager(): ConnectionManager
}
