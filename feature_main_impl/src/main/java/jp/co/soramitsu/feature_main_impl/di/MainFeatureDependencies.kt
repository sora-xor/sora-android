/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_main_impl.di

import android.content.Context
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository
import jp.co.soramitsu.feature_information_api.domain.interfaces.InformationRepository
import jp.co.soramitsu.feature_project_api.domain.interfaces.ProjectRepository
import jp.co.soramitsu.feature_wallet_api.domain.interfaces.WalletRepository

interface MainFeatureDependencies {

    fun userRepository(): UserRepository

    fun projectRepository(): ProjectRepository

    fun didRepository(): DidRepository

    fun walletRepository(): WalletRepository

    fun context(): Context

    fun informationRepository(): InformationRepository

    fun resourseManager(): ResourceManager

    fun numbersFormatter(): NumbersFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker

    fun qrCodeGenerator(): QrCodeGenerator

    fun qrCodeDecoder(): QrCodeDecoder

    fun deviceParamsProvider(): DeviceParamsProvider

    fun invitationHandler(): InvitationHandler
}