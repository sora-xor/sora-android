package jp.co.soramitsu.feature_wallet_impl.di

import android.content.Context
import com.google.gson.Gson
import jp.co.soramitsu.common.account.AccountAvatarGenerator
import jp.co.soramitsu.common.data.EncryptedPreferences
import jp.co.soramitsu.common.data.Preferences
import jp.co.soramitsu.common.data.network.Sora2CoroutineApiCreator
import jp.co.soramitsu.common.data.network.substrate.ConnectionManager
import jp.co.soramitsu.common.date.DateTimeFormatter
import jp.co.soramitsu.common.domain.AppLinksProvider
import jp.co.soramitsu.common.domain.AssetHolder
import jp.co.soramitsu.common.domain.CoroutineManager
import jp.co.soramitsu.common.domain.HealthChecker
import jp.co.soramitsu.common.domain.PushHandler
import jp.co.soramitsu.common.domain.Serializer
import jp.co.soramitsu.common.domain.credentials.CredentialsRepository
import jp.co.soramitsu.common.io.FileManager
import jp.co.soramitsu.common.presentation.DebounceClickHandler
import jp.co.soramitsu.common.resourses.ClipboardManager
import jp.co.soramitsu.common.resourses.ResourceManager
import jp.co.soramitsu.common.util.CryptoAssistant
import jp.co.soramitsu.common.util.NumbersFormatter
import jp.co.soramitsu.common.util.QrCodeGenerator
import jp.co.soramitsu.common.util.TextFormatter
import jp.co.soramitsu.core_db.AppDatabase
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.feature_account_api.domain.interfaces.UserRepository
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumInteractor
import jp.co.soramitsu.feature_ethereum_api.domain.interfaces.EthereumRepository

interface WalletFeatureDependencies {

    fun preferences(): Preferences

    fun encryptedPreferences(): EncryptedPreferences

    fun sora2CoroutineApiCreator(): Sora2CoroutineApiCreator

    fun socketService(): SocketService

    fun coroutineManager(): CoroutineManager

    fun cryptAssistant(): CryptoAssistant

    fun avatarGenerator(): AccountAvatarGenerator

    fun connectionManager(): ConnectionManager

    fun fileManager(): FileManager

    fun serializer(): Serializer

    fun appDatabase(): AppDatabase

    fun gson(): Gson

    fun credentialsRepository(): CredentialsRepository

    fun userRepo(): UserRepository

    fun resourceManager(): ResourceManager

    fun dateTimeFormatter(): DateTimeFormatter

    fun qrCodeGenerator(): QrCodeGenerator

    fun debounceClickHandler(): DebounceClickHandler

    fun pushHandler(): PushHandler

    fun numbersFormatter(): NumbersFormatter

    fun textFormatter(): TextFormatter

    fun clipboardManager(): ClipboardManager

    fun appLinksProvider(): AppLinksProvider

    fun context(): Context

    fun ethereumInteractor(): EthereumInteractor

    fun ethereumRepository(): EthereumRepository

    fun assetHolder(): AssetHolder

    fun healthCheker(): HealthChecker
}
