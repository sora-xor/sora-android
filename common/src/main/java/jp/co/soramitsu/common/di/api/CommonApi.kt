package jp.co.soramitsu.common.di.api

import android.content.Context
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.connection.NetworkStateListener
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AppVersionProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.InvitationHandler
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ContextManager
import jp.co.soramitsu.common.resourses.LanguagesHolder
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.DeviceParamsProvider
import jp.co.soramitsu.common.util.DidProvider
import jp.co.soramitsu.common.util.MnemonicProvider
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter

interface CommonApi {
    fun context(): Context

    fun contextManager(): ContextManager

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun resourceManager(): ResourceManager

    fun appVersionProvider(): AppVersionProvider

    fun numbersFormatter(): NumbersFormatter

    fun pushHandler(): PushHandler

    fun healthChecker(): HealthChecker

    fun qrCodeGenerator(): QrCodeGenerator

    fun deviceParams(): DeviceParamsProvider

    fun mnemonicProvider(): MnemonicProvider

    fun cryptoAssistant(): CryptoAssistant

    fun didProvider(): DidProvider

    fun invitationHandler(): InvitationHandler

    fun debounceClickHandler(): DebounceClickHandler

    fun serializer(): Serializer

    fun languagesHolder(): LanguagesHolder

    fun dateTimeFormatter(): DateTimeFormatter

    fun clipboardManager(): ClipboardManager

    fun textFormatter(): TextFormatter

    fun assetHolder(): AssetHolder

    fun networkStateListener(): NetworkStateListener
}