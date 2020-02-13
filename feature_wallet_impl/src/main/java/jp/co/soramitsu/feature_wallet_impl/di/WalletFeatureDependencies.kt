/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.feature_wallet_impl.di

import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.QrCodeDecoder
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.core_network_api.NetworkApiCreator
import jp.co.soramitsu.core_network_api.data.auth.AuthHolder
import jp.co.soramitsu.feature_did_api.domain.interfaces.DidRepository

interface WalletFeatureDependencies {

    fun preferences(): Preferences

    fun authHolder(): AuthHolder

    fun networkApiCreator(): NetworkApiCreator

    fun appDatabase(): AppDatabase

    fun serializer(): Serializer

    fun didRepository(): DidRepository

    fun resourceManager(): ResourceManager

    fun dateTimeFormatter(): DateTimeFormatter

    fun qrCodeGenerator(): QrCodeGenerator

    fun debounceClickHandler(): DebounceClickHandler

    fun pushHandler(): PushHandler

    fun qrCodeDecoder(): QrCodeDecoder
}