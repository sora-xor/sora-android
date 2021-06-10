/**
* Copyright Soramitsu Co., Ltd. All Rights Reserved.
* SPDX-License-Identifier: GPL-3.0
*/

package jp.co.soramitsu.common.di.api

import android.content.Context
import com.google.gson.Gson
import com.neovisionaries.ws.client.WebSocketFactory
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.data.network.substrate.runtime.RuntimeManager
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AppStateProvider
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.common.vibration.DeviceVibrator
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.logging.Logger

interface CommonApi {
    fun context(): Context

    fun contextManager(): ContextManager

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun resourceManager(): ResourceManager

    fun appVersionProvider(): AppVersionProvider

    fun appStateProvider(): AppStateProvider

    fun connectionManager(): ConnectionManager

    fun runtimeManager(): RuntimeManager

    fun wsLogger(): Logger

    fun wsSocketService(): SocketService

    fun wsSocketFactory(): WebSocketFactory

    fun numbersFormatter(): NumbersFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker

    fun qrCodeGenerator(): QrCodeGenerator

    fun deviceParams(): DeviceParamsProvider

    fun cryptoAssistant(): CryptoAssistant

    fun invitationHandler(): InvitationHandler

    fun debounceClickHandler(): DebounceClickHandler

    fun serializer(): Serializer

    fun gson(): Gson

    fun languagesHolder(): LanguagesHolder

    fun dateTimeFormatter(): DateTimeFormatter

    fun clipboardManager(): ClipboardManager

    fun textFormatter(): TextFormatter

    fun assetHolder(): AssetHolder

    fun avatarGenerator(): AccountAvatarGenerator

    fun networkStateListener(): NetworkStateListener

    fun deviceVibrator(): DeviceVibrator

    fun credentialsRepository(): CredentialsRepository
}
