/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_multiaccount_impl.di

import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository

interface MultiaccountFeatureDependencies {

    fun userRepository(): UserRepository

    fun credentialsRepository(): CredentialsRepository

    fun runtimeManager(): RuntimeManager

    fun debounceClickHandler(): DebounceClickHandler

    fun numbersFormatter(): NumbersFormatter

    fun deviceVibrator(): DeviceVibrator

    fun connectionManager(): ConnectionManager
}
